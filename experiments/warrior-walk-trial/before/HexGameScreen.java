package com.blackberry.cave;

import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.TouchEvent;
import net.rim.device.api.ui.UiApplication;
import java.util.Vector;
import java.util.Random;

public class HexGameScreen extends MainScreen implements CombatScreen.ResultListener, MapEncounterScreen.ResultListener {
    private HexMap map;
    private HexRenderer renderer;
    private Party party;
    private Vector mapEnemies = new Vector();
    private CombatScreen activeCombat;
    private MapEncounterScreen activeEncounter;
    private Unit pendingMapEnemy;
    private Unit encounterMover;
    private int observedRound = 1;
    private static final int MAX_MAP_ENEMIES = 12;
    private static final int ROUND_SPAWN_CHANCE = 30;
    private Unit encounterMapEnemy;
    private int encounterMapEnemyHp;
    private boolean gameOver = false;
    private HexHUD hud;
    private Random rand;

    private int cursorQ = 0;
    private int cursorR = 0;
    private boolean cursorCameraActive = false;
    private Vector reachableTiles;
    private Vector previewPath;

    private int[] scratch = new int[2];

    private int touchStartX, touchStartY;
    private int originStartX, originStartY;
    private boolean exposed = false;
    private boolean closed = false;
    private UiTickTimer animTimer = new UiTickTimer(new Runnable() {
        public void run() {
            updateGame();
        }
    });
    private int minimapTimer = 0;
    private int minimapAlpha = 0;

    public HexGameScreen() {
        this(System.currentTimeMillis());
    }

    public HexGameScreen(long mapSeed) {
        super(MainScreen.NO_VERTICAL_SCROLL | MainScreen.NO_HORIZONTAL_SCROLL);
        map = new HexMap(mapSeed);
        rand = new Random(mapSeed ^ 0x5DEECE66DL);
        renderer = new HexRenderer();
        party = new Party(map);
        hud = new HexHUD();

        for (int i = 0; i < party.members.size(); i++) {
            Unit u = (Unit) party.members.elementAt(i);
            map.revealArea(u.q, u.r, 1);
        }

        Unit first = party.getActiveMember();
        cursorQ = first.q;
        cursorR = first.r;
        centerOnUnit(first);
        updateReachable();

        // Initial spawn
        for (int i = 0; i < 3; i++) {
            spawnMonster();
        }

    }

    protected void onUiEngineAttached(boolean attached) {
        super.onUiEngineAttached(attached);
        exposed = attached;
        if (attached)
            resumeUpdates();
        else
            pauseUpdates();
    }

    protected void onExposed() {
        super.onExposed();
        exposed = true;
        resumeUpdates();
    }

    protected void onObscured() {
        exposed = false;
        pauseUpdates();
        super.onObscured();
    }


    void pauseUpdates() {
        animTimer.stop();
    }

    void resumeUpdates() {
        if (exposed && !closed && !gameOver && activeCombat == null && activeEncounter == null)
            animTimer.start(50);
    }

    private void centerOnUnit(Unit u) {
        cursorCameraActive = false;
        renderer.getHexWorldPoint(u.q, u.r, scratch);
        int sx = 320 - scratch[0];
        int sy = (480 - hud.getHeight()) / 2 - scratch[1];
        renderer.setOrigin(sx, sy);
        invalidate();
    }

    private void followUnit(Unit u) {
        if (cursorCameraActive)
            return;
        int sx = 320 - u.worldX;
        int sy = (480 - hud.getHeight()) / 2 - u.worldY;
        renderer.setOrigin(sx, sy);
    }

    private void updateGame() {
        if (!exposed || closed || activeCombat != null || activeEncounter != null || gameOver || UiApplication.getUiApplication().getActiveScreen() != this)
            return;
        synchronizeWorldTurn();
        Unit u = party.getActiveMember();
        if (u == null)
            return;
        if (u.isMoving) {
            boolean enteredTile = u.updateAnimation(renderer);
            followUnit(u);
            if (enteredTile) {
                u.curMove--;
                map.revealArea(u.q, u.r, 1);
                if (handleTileArrival(u))
                    return;
                if (!u.isMoving)
                    updateReachable();
            }
            invalidate();
            showMinimap();
        }

        if (minimapTimer > 0) {
            minimapTimer--;
            if (minimapTimer > 20) {
                minimapAlpha = Math.min(200, minimapAlpha + 20);
            } else {
                minimapAlpha = Math.max(0, minimapAlpha - 10);
            }
            invalidate();
        }
    }

    private boolean handleTileArrival(Unit mover) {
        HexTile tile = map.getTile(mover.q, mover.r);
        // Towns are safe: resolve town entry before any monster encounter.
        if (tile != null && tile.type == HexTile.TYPE_TOWN) {
            mover.cancelMovement();
            cursorQ = mover.q;
            cursorR = mover.r;
            centerOnUnit(mover);
            updateReachable();
            pauseUpdates();
            UiApplication.getUiApplication().pushScreen(new TownScreen(party));
            return true;
        }

        for (int i = 0; i < mapEnemies.size(); i++) {
            Unit enemy = (Unit) mapEnemies.elementAt(i);
            if (enemy.curHp > 0 && mover.q == enemy.q && mover.r == enemy.r) {
                mover.pauseMovement();
                pendingMapEnemy = enemy;
                encounterMover = mover;
                int chance = 50 + 5 * (mover.getSpeed() - enemy.getSpeed());
                chance = Math.max(10, Math.min(90, chance));
                previewPath = null;
                reachableTiles = null;
                pauseUpdates();
                activeEncounter = new MapEncounterScreen(mover, enemy, chance, 3, 2, this, rand);
                UiApplication.getUiApplication().pushScreen(activeEncounter);
                return true;
            }
        }
        // Each entered non-town tile gets one random check unless occupied by a map enemy.
        int rate = tile != null && tile.type == HexTile.TYPE_FOREST ? 15 : 5;
        if (rand.nextInt(100) < rate) {
            Unit enemy = rand.nextInt(2) == 0
                    ? new Unit("Wolf", "wolf.png", mover.q, mover.r)
                    : new Unit("Slime", "slime.png", mover.q, mover.r);
            mover.cancelMovement();
            startCombat(enemy);
            return true;
        }
        return false;
    }

    public void encounterFinished(MapEncounterScreen screen, int action, boolean success) {
        if (screen != activeEncounter)
            return;
        Unit enemy = pendingMapEnemy;
        Unit mover = encounterMover;
        activeEncounter = null;
        pendingMapEnemy = null;
        encounterMover = null;
        if (action == MapEncounterScreen.FLEE && success) {
            // The map enemy remains. This already-entered tile is not checked again.
            mover.resumeMovement();
            if (!mover.isMoving) {
                cursorQ = mover.q;
                cursorR = mover.r;
                centerOnUnit(mover);
            }
            updateReachable();
            showMinimap();
            resumeUpdates();
            invalidate();
        } else {
            mover.cancelMovement();
            startCombat(enemy, action == MapEncounterScreen.AMBUSH && success);
        }
    }

    private void synchronizeWorldTurn() {
        if (party.isWiped())
            return;
        party.refreshTurn();
        if (party.getWorldRound() != observedRound) {
            observedRound = party.getWorldRound();
            if (rand.nextInt(100) < ROUND_SPAWN_CHANCE)
                spawnMonster();
        }
    }

    private void showMinimap() {
        minimapTimer = 60;
    }

    private void startCombat(Unit leadEnemy) {
        startCombat(leadEnemy, false);
    }

    private void startCombat(Unit leadEnemy, boolean heroFirstRound) {
        if (!exposed || closed || activeCombat != null || activeEncounter != null || gameOver || party.isWiped())
            return;
        encounterMapEnemy = mapEnemies.contains(leadEnemy) ? leadEnemy : null;
        encounterMapEnemyHp = leadEnemy.curHp;
        previewPath = null;
        reachableTiles = null;
        Vector allies = new Vector();
        allies.addElement(party.getActiveMember());

        Unit triggerUnit = party.getActiveMember();

        // Find nearby allies (Range <= 1)
        for (int i = 0; i < party.members.size(); i++) {
            Unit m = (Unit) party.members.elementAt(i);
            if (m == triggerUnit || m.curHp <= 0)
                continue;
            int dist = getDist(triggerUnit.q, triggerUnit.r, m.q, m.r);
            if (dist <= 1 && allies.size() < 4) {
                allies.addElement(m);
            }
        }

        Vector enemies = new Vector();
        enemies.addElement(leadEnemy);

        int extraEnemies = rand.nextInt(3);
        for (int i = 0; i < extraEnemies; i++) {
            int type = rand.nextInt(3);
            Unit extra;
            if (type == 0)
                extra = new Unit("Goblin", "goblin.png", 0, 0);
            else if (type == 1)
                extra = new Unit("Wolf", "wolf.png", 0, 0);
            else
                extra = new Unit("Slime", "slime.png", 0, 0);
            enemies.addElement(extra);
        }

        pauseUpdates();
        activeCombat = new CombatScreen(allies, enemies, party, this, heroFirstRound, rand);
        UiApplication.getUiApplication().pushScreen(activeCombat);
    }

    public void combatFinished(CombatScreen combat, int result) {
        // Ignore duplicate or stale callbacks from a previous encounter.
        if (combat != activeCombat)
            return;
        if (encounterMapEnemy != null) {
            if (result == CombatScreen.RESULT_WIN) {
                mapEnemies.removeElement(encounterMapEnemy);
            } else {
                // An unresolved encounter resets its map leader, even if it was KO'd.
                encounterMapEnemy.curHp = encounterMapEnemyHp;
            }
        }
        encounterMapEnemy = null;
        activeCombat = null;
        previewPath = null;
        reachableTiles = null;
        if (result == CombatScreen.RESULT_PARTY_WIPED || party.isWiped()) {
            gameOver = true;
            pauseUpdates();
            UiApplication.getUiApplication().pushScreen(new GameOverScreen());
            return;
        }
        synchronizeWorldTurn();
        Unit active = party.getActiveMember();
        cursorQ = active.q;
        cursorR = active.r;
        centerOnUnit(active);
        updateReachable();
        showMinimap();
        resumeUpdates();
        invalidate();
    }

    private boolean spawnMonster() {
        if (mapEnemies.size() >= MAX_MAP_ENEMIES)
            return false;
        // Reservoir sampling chooses uniformly from legal tiles without a retry
        // limit or a temporary candidate array. Empty maps fail safely.
        HexTile chosen = null;
        int candidates = 0;
        Vector tiles = map.getTiles();
        for (int i = 0; i < tiles.size(); i++) {
            HexTile tile = (HexTile) tiles.elementAt(i);
            if (!map.isTraversable(tile.q, tile.r) || tile.type == HexTile.TYPE_TOWN
                    || isOccupied(tile.q, tile.r))
                continue;
            candidates++;
            if (rand.nextInt(candidates) == 0)
                chosen = tile;
        }
        if (chosen == null)
            return false;
        int type = rand.nextInt(3);
        Unit enemy;
        if (type == 0)
            enemy = new Unit("Slime", "slime.png", chosen.q, chosen.r);
        else if (type == 1)
            enemy = new Unit("Wolf", "wolf.png", chosen.q, chosen.r);
        else
            enemy = new Unit("Goblin", "goblin.png", chosen.q, chosen.r);
        mapEnemies.addElement(enemy);
        return true;
    }

    private boolean isOccupied(int q, int r) {
        for (int i = 0; i < party.members.size(); i++) {
            Unit member = (Unit) party.members.elementAt(i);
            if (member.q == q && member.r == r)
                return true;
        }
        for (int i = 0; i < mapEnemies.size(); i++) {
            Unit enemy = (Unit) mapEnemies.elementAt(i);
            if (enemy.q == q && enemy.r == r)
                return true;
        }
        return false;
    }

    private int getDist(int q1, int r1, int q2, int r2) {
        return (Math.abs(q1 - q2) + Math.abs(q1 + r1 - q2 - r2) + Math.abs(r1 - r2)) / 2;
    }

    private void updateReachable() {
        Unit u = party.getActiveMember();
        if (u != null && !u.isMoving && u.curMove > 0) {
            reachableTiles = map.getReachableTiles(u.q, u.r, u.curMove);
        } else {
            reachableTiles = null;
        }
    }

    protected void paint(Graphics g) {
        g.setColor(0x000000);
        g.fillRect(0, 0, getWidth(), getHeight());
        renderer.drawMap(g, map);
        renderer.drawRange(g, reachableTiles);
        if (previewPath != null) {
            drawPreviewPath(g);
        }
        drawUnits(g);
        renderer.drawSelector(g, cursorQ, cursorR);
        hud.draw(g, party, getWidth(), getHeight());
        renderer.drawMinimap(g, map, party, getWidth() - 110, 10, minimapAlpha);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().derive(net.rim.device.api.ui.Font.PLAIN, 12));
        g.drawText("ROUND " + party.getWorldRound() + "  TURN " + party.getTurnNumber()
                + "/" + party.getTurnCount() + "  [SPACE] END TURN  [P] FOCUS +MOVE", 8, 8);
    }

    private void drawPreviewPath(Graphics g) {
        g.setColor(Color.WHITE);
        g.setGlobalAlpha(150);
        int[] pPos = new int[2];
        for (int i = 0; i < previewPath.size(); i++) {
            HexTile t = (HexTile) previewPath.elementAt(i);
            renderer.getHexScreenPoint(t.q, t.r, pPos);
            g.fillArc(pPos[0] - 10, pPos[1] - 10, 20, 20, 0, 360);
        }
        g.setGlobalAlpha(255);
    }

    private void drawUnits(Graphics g) {
        // Collect all units to draw
        Vector allUnits = new Vector();
        for (int i = 0; i < party.members.size(); i++) {
            allUnits.addElement(party.members.elementAt(i));
        }
        for (int i = 0; i < mapEnemies.size(); i++) {
            Unit e = (Unit) mapEnemies.elementAt(i);
            if (e != null && e.curHp > 0) {
                HexTile et = map.getTile(e.q, e.r);
                if (et != null && et.revealed) {
                    allUnits.addElement(e);
                }
            }
        }

        int[] pos = new int[2];
        // Identify groups by q, r
        Vector processed = new Vector();

        for (int i = 0; i < allUnits.size(); i++) {
            Unit u = (Unit) allUnits.elementAt(i);
            if (processed.contains(u))
                continue;

            // If a unit is moving, we draw it independently to avoid snapping to a grouped
            // hex
            if (u.isMoving) {
                int vx = renderer.getOriginX() + u.worldX;
                int vy = renderer.getOriginY() + u.worldY;

                if (u.sprite != null) {
                    boolean ko = party.members.contains(u) && u.curHp <= 0;
                    if (ko)
                        g.setGlobalAlpha(90);
                    if (party.members.contains(u)) {
                        g.drawBitmap(vx - 35, vy - 45, 70, 70, u.sprite, 0, 0);
                    } else {
                        g.drawBitmap(vx - 30, vy - 40, 60, 60, u.sprite, 0, 0);
                    }
                    if (ko)
                        g.setGlobalAlpha(255);
                }
                processed.addElement(u);
                continue;
            }

            // Find all units on this tile (that are NOT moving)
            Vector sharedTile = new Vector();
            for (int j = i; j < allUnits.size(); j++) {
                Unit other = (Unit) allUnits.elementAt(j);
                if (u.q == other.q && u.r == other.r && !other.isMoving) {
                    sharedTile.addElement(other);
                    processed.addElement(other);
                }
            }

            // Draw units on this tile with offset
            int count = sharedTile.size();
            for (int k = 0; k < count; k++) {
                Unit tileUnit = (Unit) sharedTile.elementAt(k);
                int vx, vy;

                if (tileUnit.isMoving) {
                    vx = renderer.getOriginX() + tileUnit.worldX;
                    vy = renderer.getOriginY() + tileUnit.worldY;
                } else {
                    renderer.getHexScreenPoint(tileUnit.q, tileUnit.r, pos);
                    vx = pos[0];
                    vy = pos[1];

                    // Apply offset based on count and index
                    if (count == 2) {
                        vx += (k == 0) ? -10 : 10;
                    } else if (count == 3) {
                        if (k == 0) {
                            vx += 0;
                            vy -= 10;
                        } else if (k == 1) {
                            vx -= 15;
                            vy += 10;
                        } else {
                            vx += 15;
                            vy += 10;
                        }
                    } else if (count >= 4) {
                        if (k == 0) {
                            vx -= 15;
                            vy -= 10;
                        } else if (k == 1) {
                            vx += 15;
                            vy -= 10;
                        } else if (k == 2) {
                            vx -= 15;
                            vy += 15;
                        } else if (k == 3) {
                            vx += 15;
                            vy += 15;
                        }
                    }
                }

                if (tileUnit.sprite != null) {
                    boolean ko = party.members.contains(tileUnit) && tileUnit.curHp <= 0;
                    if (ko)
                        g.setGlobalAlpha(90);
                    if (party.members.contains(tileUnit)) {
                        g.drawBitmap(vx - 35, vy - 45, 70, 70, tileUnit.sprite, 0, 0);
                    } else {
                        g.drawBitmap(vx - 30, vy - 40, 60, 60, tileUnit.sprite, 0, 0); // Enemy keeps 60x60
                    }
                    if (ko)
                        g.setGlobalAlpha(255);
                } else if (!party.members.contains(tileUnit)) { // Fallback for enemy
                    g.setColor(Color.RED);
                    g.drawArc(vx - 25, vy - 25, 50, 50, 0, 360);
                }
            }
        }
    }

    private void cursorMoved() {
        cursorCameraActive = true;
        ensureCursorVisible();
        showMinimap();
        invalidate();
    }

    private void ensureCursorVisible() {
        int viewWidth = getWidth();
        int viewHeight = getHeight() - hud.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0)
            return;
        renderer.getHexScreenPoint(cursorQ, cursorR, scratch);
        // Keep the whole selector, including its AA fringe, clear of the HUD.
        int marginX = renderer.getHexHalfWidth() + 8;
        int marginY = renderer.getHexRadius() + 8;
        int shiftX = cursorCameraShift(scratch[0], viewWidth, marginX);
        int shiftY = cursorCameraShift(scratch[1], viewHeight, marginY);
        if (shiftX != 0 || shiftY != 0) {
            renderer.setOrigin(renderer.getOriginX() + shiftX,
                    renderer.getOriginY() + shiftY);
        }
    }

    private static int cursorCameraShift(int position, int viewport, int margin) {
        // A smaller viewport cannot fit the whole hex; keep its center visible.
        if (viewport <= margin * 2)
            return viewport / 2 - position;
        if (position < margin)
            return margin - position;
        if (position > viewport - margin)
            return viewport - margin - position;
        return 0;
    }

    protected boolean navigationMovement(int dx, int dy, int status, int time) {
        if (dx < 0)
            cursorQ--;
        if (dx > 0)
            cursorQ++;
        if (dy < 0)
            cursorR--;
        if (dy > 0)
            cursorR++;
        cursorMoved();
        return true;
    }

    protected boolean keyRepeat(int keycode, int time) {
        int key = Keypad.key(keycode);
        if (key == 'p' || key == 'P' || key == Keypad.KEY_SPACE || key == Keypad.KEY_ENTER)
            return true;
        return super.keyRepeat(keycode, time);
    }
    protected boolean keyChar(char c, int status, int time) {
        if (c == Keypad.KEY_ENTER) {
            handleAction();
            return true;
        }
        if (c == 'p' || c == 'P') {
            handleMovementFocus();
            return true;
        }
        if (c == ' ') {
            handleSpace();
            return true;
        }
        if (c == Keypad.KEY_ESCAPE || c == Keypad.KEY_MENU) {
            onClose();
            return true;
        }
        if (c == 'w' || c == 'W' || c == '2') {
            cursorR--;
            cursorMoved();
            return true;
        }
        if (c == 's' || c == 'S' || c == '8') {
            cursorR++;
            cursorMoved();
            return true;
        }
        if (c == 'a' || c == 'A' || c == '4') {
            cursorQ--;
            cursorMoved();
            return true;
        }
        if (c == 'd' || c == 'D' || c == '6') {
            cursorQ++;
            cursorMoved();
            return true;
        }
        return super.keyChar(c, status, time);
    }

    protected boolean touchEvent(TouchEvent message) {
        int x = message.getX(1), y = message.getY(1);
        int event = message.getEvent();
        if (event == TouchEvent.DOWN) {
            touchStartX = x;
            touchStartY = y;
            originStartX = renderer.getOriginX();
            originStartY = renderer.getOriginY();
            return true;
        } else if (event == TouchEvent.MOVE) {
            renderer.setOrigin(originStartX + (x - touchStartX), originStartY + (y - touchStartY));
            showMinimap();
            invalidate();
            return true;
        }
        return super.touchEvent(message);
    }

    private void handleAction() {
        if (!exposed || closed || activeCombat != null || activeEncounter != null || gameOver || party.isWiped())
            return;
        synchronizeWorldTurn();
        Unit u = party.getActiveMember();
        if (u == null || u.isMoving)
            return;

        updateReachable();
        HexTile target = map.getTile(cursorQ, cursorR);
        if (target != null && reachableTiles != null && reachableTiles.contains(target)) {
            // Check if we are already previewing this path
            if (previewPath != null && !previewPath.isEmpty()) {
                HexTile last = (HexTile) previewPath.elementAt(previewPath.size() - 1);
                if (last.q == cursorQ && last.r == cursorR) {
                    // Second click: Execute move
                    Vector confirmedPath = map.getPath(u.q, u.r, cursorQ, cursorR);
                    int cost = confirmedPath != null ? confirmedPath.size() : 0;
                    if (cost > 0 && u.curMove >= cost) {
                        cursorCameraActive = false;
                        u.setPath(confirmedPath, renderer);
                        previewPath = null;
                        updateReachable();
                    }
                    invalidate();
                    return;
                }
            }

            // First click or different tile: Preview path
            Vector path = map.getPath(u.q, u.r, cursorQ, cursorR);
            if (path != null) {
                previewPath = path;
            }
        } else {
            previewPath = null;
        }
        invalidate();
    }

    private void handleMovementFocus() {
        if (!exposed || closed || activeCombat != null || activeEncounter != null || gameOver
                || UiApplication.getUiApplication().getActiveScreen() != this || party.isWiped())
            return;
        synchronizeWorldTurn();
        Unit unit = party.getActiveMember();
        if (unit != null && unit.spendFocusForMovement()) {
            previewPath = null;
            updateReachable();
            invalidate();
        }
    }
    private void handleSpace() {
        if (!exposed || closed || activeCombat != null || activeEncounter != null || gameOver || party.isWiped())
            return;
        synchronizeWorldTurn();
        Unit u = party.getActiveMember();
        if (u == null || u.isMoving)
            return;

        party.cycleMember();
        synchronizeWorldTurn();
        Unit next = party.getActiveMember();
        cursorQ = next.q;
        cursorR = next.r;
        previewPath = null;
        centerOnUnit(next);
        updateReachable();
        invalidate();
    }

    public boolean onClose() {
        closed = true;
        pauseUpdates();
        System.exit(0);
        return true;
    }
}
