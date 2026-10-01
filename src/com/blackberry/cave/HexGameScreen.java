package com.blackberry.cave;

import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.TouchEvent;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.component.Dialog;
import net.rim.device.api.ui.DrawStyle;
import net.rim.device.api.ui.Font;
import java.util.Vector;
import java.util.Random;

public class HexGameScreen extends MainScreen implements CombatScreen.ResultListener,
        MapEncounterScreen.ResultListener, InventoryScreen.ResultListener, TownScreen.QuestActions {
    private HexMap map;
    private HexRenderer renderer;
    private Party party;
    private QuestCampaign quest;
    private Vector mapEnemies = new Vector();
    private CombatScreen activeCombat;
    private MapEncounterScreen activeEncounter;
    private Unit pendingMapEnemy;
    private Unit encounterMover;
    private int observedRound = 1;
    private static final int MAX_MAP_ENEMIES = 12;
    private static final int ROUND_SPAWN_CHANCE = 30;
    private Unit encounterMapEnemy;
    private Unit bossEnemy;
    private Vector combatParticipants;
    private int encounterMapEnemyHp;
    private boolean gameOver = false;
    private HexHUD hud;
    private Random rand;
    private int saveSlot = -1;
    private int[] sailingGroups = new int[4];

    private int boatGroup(Unit unit) {
        int index = party.members.indexOf(unit);
        return index < 0 ? 0 : sailingGroups[index];
    }

    private boolean aboard(Unit unit) {
        return boatGroup(unit) != 0;
    }

    private void validateSailing() throws java.io.IOException {
        for (int i = 0; i < party.members.size(); i++) {
            Unit unit = (Unit) party.members.elementAt(i);
            HexTile tile = map.getTile(unit.q, unit.r);
            int group = sailingGroups[i];
            if (group < 0 || group > 15 || (group != 0 && (group & (1 << i)) == 0))
                throw new java.io.IOException("Invalid passengers");
            if (!aboard(unit)) {
                if (!map.isTraversable(unit.q, unit.r)) throw new java.io.IOException("Member outside land");
                continue;
            }
            if (unit.curHp <= 0 || tile == null || (tile.type != HexTile.TYPE_WATER && !tile.port))
                throw new java.io.IOException("Invalid sailing position");
            int count = 0;
            for (int j = 0; j < party.members.size(); j++) {
                if ((group & (1 << j)) == 0) continue;
                Unit passenger = (Unit) party.members.elementAt(j);
                if (sailingGroups[j] != group || passenger.q != unit.q || passenger.r != unit.r)
                    throw new java.io.IOException("Separated passengers");
                count++;
            }
            if (count < 2) throw new java.io.IOException("Too few passengers");
        }
    }

    private void syncPassengers(Unit pilot) {
        for (int i = 0; i < party.members.size(); i++) {
            Unit passenger = (Unit) party.members.elementAt(i);
            if (passenger == pilot || boatGroup(passenger) != boatGroup(pilot)) continue;
            passenger.q = pilot.q; passenger.r = pilot.r;
            passenger.worldX = pilot.worldX; passenger.worldY = pilot.worldY;
        }
    }

    public void sailFromPort() { sailingMenu(); }

    private void sailingMenu() {
        if (!acceptsWorldInput() || !canSave()) return;
        Unit pilot = party.getActiveMember();
        HexTile port = map.getTile(pilot.q, pilot.r);
        if (port == null || !port.port) {
            Dialog.alert("Sailing is available only at a port. Land at a port to disembark.");
            return;
        }
        previewPath = null;
        pauseUpdates();
        try {
            if (aboard(pilot)) {
                if (Dialog.ask("Docked at port", new String[] { "Keep sailing", "Disembark passengers" }, 0) == 1) {
                    int group = boatGroup(pilot);
                    for (int i = 0; i < sailingGroups.length; i++)
                        if (sailingGroups[i] == group) sailingGroups[i] = 0;
                }
            } else {
                Vector residents = new Vector();
                int selected = 0;
                for (int i = 0; i < party.members.size(); i++) {
                    Unit member = (Unit) party.members.elementAt(i);
                    if (member.curHp > 0 && member.q == pilot.q && member.r == pilot.r
                            && !member.isMoving && !aboard(member)) {
                        residents.addElement(member);
                        selected |= 1 << i;
                    }
                }
                if (residents.size() < 2) { Dialog.alert("At least two living members must gather at this port."); return; }
                while (true) {
                    String[] choices = new String[residents.size() + 2];
                    int count = 0;
                    for (int i = 0; i < residents.size(); i++) {
                        Unit member = (Unit) residents.elementAt(i);
                        boolean included = (selected & (1 << party.members.indexOf(member))) != 0;
                        if (included) count++;
                        choices[i] = (included ? "[x] " : "[ ] ") + member.name + (member == pilot ? " (Pilot)" : "");
                    }
                    choices[residents.size()] = "Set sail";
                    choices[residents.size() + 1] = "Cancel";
                    int answer = Dialog.ask("Choose passengers (at least 2)", choices, residents.size() + 1);
                    if (answer < 0 || answer > residents.size()) return;
                    if (answer == residents.size()) {
                        if (count < 2) { Dialog.alert("Choose at least two living members."); continue; }
                        for (int i = 0; i < sailingGroups.length; i++)
                            if ((selected & (1 << i)) != 0) {
                                sailingGroups[i] = selected;
                                ((Unit) party.members.elementAt(i)).returnToTile(port.q, port.r, renderer);
                            }
                        break;
                    }
                    Unit member = (Unit) residents.elementAt(answer);
                    if (member != pilot) selected ^= 1 << party.members.indexOf(member);
                }
            }
        } finally {
            updateReachable();
            invalidate();
            resumeUpdates();
        }
    }

    public HexGameScreen(int slot) {
        this(System.currentTimeMillis());
        saveSlot = slot;
    }

    HexGameScreen(java.io.DataInputStream in, int slot, int version) throws java.io.IOException {
        super(MainScreen.NO_VERTICAL_SCROLL | MainScreen.NO_HORIZONTAL_SCROLL);
        map = HexMap.readSave(in, version);
        renderer = new HexRenderer();
        renderer.prepareTerrain(map);
        party = Party.readSave(in, map, renderer);
        quest = QuestCampaign.readSave(in, map, party);
        rand = new Random(in.readLong());
        int count = in.readInt();
        if (count < 0 || count > MAX_MAP_ENEMIES) throw new java.io.IOException("Invalid enemy count");
        for (int i = 0; i < count; i++) {
            Unit enemy = Unit.readSave(in, map, renderer);
            if (!enemy.name.equals("Slime") && !enemy.name.equals("Wolf")
                    && !enemy.name.equals("Goblin") && !enemy.name.equals("Goblin Captain"))
                throw new java.io.IOException("Invalid enemy");
            if (enemy.curHp <= 0 || !map.isTraversable(enemy.q, enemy.r)
                    || map.getTile(enemy.q, enemy.r).type == HexTile.TYPE_TOWN
                    || hasMapEnemyAt(enemy.q, enemy.r))
                throw new java.io.IOException("Invalid enemy placement");
            mapEnemies.addElement(enemy);
            if (enemy.name.equals("Goblin Captain")) {
                if (bossEnemy != null) throw new java.io.IOException("Duplicate boss");
                bossEnemy = enemy;
            }
        }
        if (version >= 2)
            for (int i = 0; i < sailingGroups.length; i++) sailingGroups[i] = in.readInt();
        validateSailing();
        if ((quest.getState() == QuestCampaign.BOSS_ACTIVE) != (bossEnemy != null)
                || (bossEnemy != null && (bossEnemy.q != quest.getBossQ() || bossEnemy.r != quest.getBossR())))
            throw new java.io.IOException("Invalid boss state");
        hud = new HexHUD();
        observedRound = party.getWorldRound();
        saveSlot = slot;
        Unit active = party.getActiveMember();
        cursorQ = active.q; cursorR = active.r;
        centerOnUnit(active);
        updateReachable();
    }

    private boolean canSave() {
        if (closed || gameOver || activeCombat != null || activeEncounter != null
                || pendingMapEnemy != null || party.isWiped() || party.getActiveMember() == null)
            return false;
        for (int i = 0; i < party.members.size(); i++)
            if (((Unit) party.members.elementAt(i)).isMoving) return false;
        return true;
    }

    void writeSave(java.io.DataOutputStream out) throws java.io.IOException {
        if (!canSave()) throw new java.io.IOException("Wait until movement or encounter ends");
        validateSailing();
        map.writeSave(out); party.writeSave(out); quest.writeSave(out);
        // A fresh random stream on resume; existing terrain, enemies and progress are exact.
        out.writeLong(System.currentTimeMillis());
        out.writeInt(mapEnemies.size());
        for (int i = 0; i < mapEnemies.size(); i++) ((Unit) mapEnemies.elementAt(i)).writeSave(out);
        for (int i = 0; i < sailingGroups.length; i++) out.writeInt(sailingGroups[i]);
    }

    private boolean saveToSlot(int slot) {
        try {
            SaveStore.save(slot, this);
            saveSlot = slot;
            return true;
        } catch (Exception failure) {
            Dialog.alert("Save failed. Previous save kept. " + failure.toString());
            return false;
        }
    }

    private void manualSave() {
        if (!canSave()) {
            Dialog.alert("Finish movement or encounter before saving.");
            return;
        }
        pauseUpdates();
        try {
            int slot = Dialog.ask("Save to which slot?",
                    new String[] { SaveStore.label(0), SaveStore.label(1), SaveStore.label(2), "Cancel" }, 3);
            if (slot < 0 || slot >= 3) return;
            if (slot != saveSlot && SaveStore.occupied(slot)
                    && Dialog.ask("Replace this saved game?", new String[] { "Cancel", "Replace" }, 0) != 1)
                return;
            if (saveToSlot(slot)) Dialog.alert("Saved to slot " + (slot + 1) + ".");
        } catch (Exception failure) {
            Dialog.alert("Could not access saves: " + failure.toString());
        } finally { resumeUpdates(); }
    }

    protected void makeMenu(net.rim.device.api.ui.component.Menu menu, int instance) {
        super.makeMenu(menu, instance);
        menu.add(new net.rim.device.api.ui.MenuItem(
                new net.rim.device.api.util.StringProvider("Save Game"), 100, 10) {
            public void run() { manualSave(); }
        });
        menu.add(new net.rim.device.api.ui.MenuItem(
                new net.rim.device.api.util.StringProvider("Sailing"), 110, 10) {
            public void run() { sailingMenu(); }
        });
    }

    private int cursorQ = 0;
    private int cursorR = 0;
    private boolean cursorCameraActive = false;
    private Vector reachableTiles;
    private Vector previewPath;

    private int[] scratch = new int[2];
    private int[] paintPoint = new int[2];
    private Vector paintUnits = new Vector();
    private Vector paintProcessed = new Vector();
    private Vector paintSharedTile = new Vector();
    private Font mapInfoFont = Font.getDefault().derive(Font.PLAIN, 12);

    private int touchStartX, touchStartY;
    private int originStartX, originStartY;
    private int touchLastX, touchLastY;
    private boolean touchActive;
    private boolean touchDragged;
    private boolean navigationPressed;
    private boolean exitPrompt;
    private boolean exposed = false;
    private boolean closed = false;
    private UiTickTimer animTimer = new UiTickTimer(new Runnable() {
        public void run() {
            updateGame();
        }
    });
    private int minimapTimer = 0;
    private int minimapAlpha = 0;

    public HexGameScreen(long mapSeed) {
        super(MainScreen.NO_VERTICAL_SCROLL | MainScreen.NO_HORIZONTAL_SCROLL);
        map = new HexMap(mapSeed);
        rand = new Random(mapSeed ^ 0x5DEECE66DL);
        renderer = new HexRenderer();
        renderer.prepareTerrain(map);
        party = new Party(map);
        quest = new QuestCampaign(map.getFirstTownTile(), party);
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
        navigationPressed = false;
        touchActive = false;
        exposed = true;
        resumeUpdates();
    }

    protected void onObscured() {
        exposed = false;
        touchActive = false;
        navigationPressed = false;
        pauseUpdates();
        super.onObscured();
    }


    void pauseUpdates() {
        animTimer.stop();
    }

    void resumeUpdates() {
        Unit active = party.getActiveMember();
        boolean forestAnimating = renderer.syncForestOccupancy(party);
        if (exposed && !closed && !gameOver && activeCombat == null && activeEncounter == null
                && (forestAnimating || minimapTimer > 0 || (active != null && active.isMoving)))
            animTimer.start(50);
        else
            animTimer.stop();
    }

    private void centerOnUnit(Unit u) {
        cursorCameraActive = false;
        renderer.getHexWorldPoint(u.q, u.r, scratch);
        int sx = getWidth() / 2 - scratch[0];
        int sy = Math.max(0, getHeight() - hud.getHeight()) / 2 - scratch[1];
        renderer.setOrigin(sx, sy);
        invalidate();
    }

    private void followUnit(Unit u) {
        if (cursorCameraActive)
            return;
        int sx = getWidth() / 2 - u.worldX;
        int sy = Math.max(0, getHeight() - hud.getHeight()) / 2 - u.worldY;
        renderer.setOrigin(sx, sy);
    }

    private void updateGame() {
        if (!exposed || closed || activeCombat != null || activeEncounter != null || gameOver || UiApplication.getUiApplication().getActiveScreen() != this) {
            pauseUpdates();
            return;
        }
        synchronizeWorldTurn();
        Unit u = party.getActiveMember();
        if (u == null) {
            pauseUpdates();
            return;
        }
        boolean repaint = false;
        if (u.isMoving) {
            int previousQ = u.q;
            int previousR = u.r;
            boolean enteredTile = u.updateAnimation(renderer);
            if (aboard(u)) syncPassengers(u);
            followUnit(u);
            if (enteredTile) {
                u.curMove--;
                if (u.q != quest.getBossQ() || u.r != quest.getBossR()
                        || quest.getState() != QuestCampaign.BOSS_ACTIVE)
                    map.revealArea(u.q, u.r, 1);
                if (handleTileArrival(u, previousQ, previousR))
                    return;
                if (!u.isMoving)
                    updateReachable();
            }
            repaint = true;
            showMinimap();
        }

        renderer.syncForestOccupancy(party);
        if (renderer.animateForest()) repaint = true;
        if (minimapTimer > 0) {
            int previousAlpha = minimapAlpha;
            minimapTimer--;
            if (minimapTimer > 20) {
                minimapAlpha = Math.min(200, minimapAlpha + 20);
            } else {
                minimapAlpha = Math.max(0, minimapAlpha - 10);
            }
            if (minimapAlpha != previousAlpha) repaint = true;
        }
        if (repaint) invalidate();
        // No world state advances with elapsed time while idle.
        resumeUpdates();
    }

    private boolean handleTileArrival(Unit mover, int previousQ, int previousR) {
        HexTile tile = map.getTile(mover.q, mover.r);
        if (aboard(mover)) {
            if (tile != null && tile.port) {
                mover.cancelMovement();
                cursorQ = mover.q; cursorR = mover.r;
                updateReachable();
                pauseUpdates();
                sailingMenu();
                return true;
            }
            return false;
        }
        // Towns are safe: resolve town entry before any monster encounter.
        if (tile != null && tile.type == HexTile.TYPE_TOWN) {
            mover.cancelMovement();
            cursorQ = mover.q;
            cursorR = mover.r;
            centerOnUnit(mover);
            updateReachable();
            pauseUpdates();
            UiApplication.getUiApplication().pushScreen(new TownScreen(party, tile, quest, this));
            return true;
        }

        for (int i = 0; i < mapEnemies.size(); i++) {
            Unit enemy = (Unit) mapEnemies.elementAt(i);
            if (enemy.curHp > 0 && mover.q == enemy.q && mover.r == enemy.r) {
                if (enemy == bossEnemy) {
                    mover.pauseMovement();
                    mover.cancelMovement();
                    int ready = 0;
                    for (int j = 0; j < party.members.size(); j++) {
                        Unit member = (Unit) party.members.elementAt(j);
                        if (member.curHp > 0 && (aboard(member)
                                || getDist(member.q, member.r, enemy.q, enemy.r) > 1))
                            ready++;
                    }
                    if (ready > 0) {
                        Dialog.alert("Gather every living party member on or next to the boss tile.");
                        declineBossEntry(mover, previousQ, previousR);
                        return true;
                    }
                    pauseUpdates();
                    int choice = Dialog.ask("Fight the Goblin Captain?",
                            new String[] { "Not yet", "Fight" }, 0);
                    if (choice != 1) {
                        declineBossEntry(mover, previousQ, previousR);
                        return true;
                    }
                    map.revealArea(mover.q, mover.r, 1);
                    startCombat(enemy);
                    return true;
                }
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
            enemy.initializeEnemyLevel(party.getEnemyLevel());
            mover.cancelMovement();
            startCombat(enemy);
            return true;
        }
        return false;
    }

    private void declineBossEntry(Unit mover, int previousQ, int previousR) {
        mover.returnToTile(previousQ, previousR, renderer);
        mover.curMove++;
        cursorQ = mover.q;
        cursorR = mover.r;
        centerOnUnit(mover);
        updateReachable();
        showMinimap();
        resumeUpdates();
        invalidate();
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
        resumeUpdates();
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
            if (m == triggerUnit || m.curHp <= 0 || aboard(m))
                continue;
            int dist = getDist(triggerUnit.q, triggerUnit.r, m.q, m.r);
            if (dist <= 1 && allies.size() < 4) {
                allies.addElement(m);
            }
        }
        combatParticipants = allies;

        Vector enemies = new Vector();
        enemies.addElement(leadEnemy);

        int extraEnemies = leadEnemy == bossEnemy ? 1 + rand.nextInt(2) : rand.nextInt(3);
        for (int i = 0; i < extraEnemies; i++) {
            int type = rand.nextInt(3);
            Unit extra;
            if (type == 0)
                extra = new Unit("Goblin", "goblin.png", 0, 0);
            else if (type == 1)
                extra = new Unit("Wolf", "wolf.png", 0, 0);
            else
                extra = new Unit("Slime", "slime.png", 0, 0);
            extra.initializeEnemyLevel(party.getEnemyLevel());
            enemies.addElement(extra);
        }

        pauseUpdates();
        activeCombat = new CombatScreen(allies, enemies, party, this, heroFirstRound, rand,
                leadEnemy != bossEnemy);
        UiApplication.getUiApplication().pushScreen(activeCombat);
    }

    public void combatFinished(CombatScreen combat, int result) {
        // Ignore duplicate or stale callbacks from a previous encounter.
        if (combat != activeCombat)
            return;
        Unit defeatedMapEnemy = encounterMapEnemy;
        boolean bossWon = result == CombatScreen.RESULT_WIN && defeatedMapEnemy == bossEnemy;
        if (defeatedMapEnemy != null) {
            if (result == CombatScreen.RESULT_WIN) {
                mapEnemies.removeElement(defeatedMapEnemy);
            } else {
                // An unresolved encounter resets its map leader, even if it was KO'd.
                defeatedMapEnemy.curHp = encounterMapEnemyHp;
            }
        }
        if (result == CombatScreen.RESULT_WIN && defeatedMapEnemy != null) {
            if (bossWon) {
                quest.completeBoss(defeatedMapEnemy.q, defeatedMapEnemy.r);
                bossEnemy = null;
            } else {
                quest.recordMapWin(combatParticipants, party);
            }
        }
        combatParticipants = null;
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
        VictoryScreen victory = result == CombatScreen.RESULT_WIN
                ? combat.createVictoryScreen() : null;
        if (bossWon && victory != null) victory.markCampaignComplete();
        // Any new round spawn must observe levels earned by this battle.
        synchronizeWorldTurn();
        Unit active = party.getActiveMember();
        cursorQ = active.q;
        cursorR = active.r;
        centerOnUnit(active);
        updateReachable();
        showMinimap();
        if (victory != null)
            UiApplication.getUiApplication().pushScreen(victory);
        else
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
        enemy.initializeEnemyLevel(party.getEnemyLevel());
        mapEnemies.addElement(enemy);
        return true;
    }

    private boolean isOccupied(int q, int r) {
        return isPartyAt(q, r) || hasMapEnemyAt(q, r);
    }

    private boolean hasMapEnemyAt(int q, int r) {
        for (int i = 0; i < mapEnemies.size(); i++) {
            Unit enemy = (Unit) mapEnemies.elementAt(i);
            if (enemy.q == q && enemy.r == r)
                return true;
        }
        return false;
    }

    private boolean isPartyAt(int q, int r) {
        for (int i = 0; i < party.members.size(); i++) {
            Unit member = (Unit) party.members.elementAt(i);
            if (member.q == q && member.r == r)
                return true;
        }
        return false;
    }

    private HexTile farthestFreeLand(Vector land, Vector reserved, HexTile home) {
        HexTile best = null;
        int bestDistance = -1;
        for (int i = 0; i < land.size(); i++) {
            HexTile tile = (HexTile) land.elementAt(i);
            if (tile.type == HexTile.TYPE_TOWN || isOccupied(tile.q, tile.r)
                    || (reserved != null && reserved.contains(tile))) continue;
            int distance = getDist(home.q, home.r, tile.q, tile.r);
            if (distance > bestDistance) {
                best = tile;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean prepareQuestEnemies(HexTile town) {
        Vector land = map.getConnectedLandTiles(town.q, town.r);
        int accessible = 0;
        for (int i = 0; i < mapEnemies.size(); i++) {
            Unit enemy = (Unit) mapEnemies.elementAt(i);
            if (enemy.curHp > 0 && land.contains(map.getTile(enemy.q, enemy.r))) accessible++;
        }
        int needed = Math.max(0, quest.getRequiredKills() - accessible);
        if (needed == 0) return true;
        Vector sites = new Vector();
        for (int i = 0; i < needed; i++) {
            HexTile site = farthestFreeLand(land, sites, town);
            if (site == null) return false;
            sites.addElement(site);
        }
        int excess = Math.max(0, mapEnemies.size() + needed - MAX_MAP_ENEMIES);
        Vector displaced = new Vector();
        for (int i = 0; i < mapEnemies.size() && displaced.size() < excess; i++) {
            Unit enemy = (Unit) mapEnemies.elementAt(i);
            if (!land.contains(map.getTile(enemy.q, enemy.r))) displaced.addElement(enemy);
        }
        if (displaced.size() != excess) return false;
        for (int i = 0; i < displaced.size(); i++) mapEnemies.removeElement(displaced.elementAt(i));
        for (int i = 0; i < sites.size(); i++) {
            HexTile site = (HexTile) sites.elementAt(i);
            int kind = rand.nextInt(3);
            Unit enemy = kind == 0 ? new Unit("Slime", "slime.png", site.q, site.r)
                    : kind == 1 ? new Unit("Wolf", "wolf.png", site.q, site.r)
                    : new Unit("Goblin", "goblin.png", site.q, site.r);
            enemy.initializeEnemyLevel(party.getEnemyLevel());
            mapEnemies.addElement(enemy);
        }
        return true;
    }

    public String acceptQuest(HexTile town) {
        if (!quest.isHome(town) || quest.getState() != QuestCampaign.OFFERED)
            return "Quest unavailable.";
        if (!prepareQuestEnemies(town))
            return "No room for three reachable enemies. Move the party and try again.";
        quest.accept(town);
        showMinimap();
        invalidate();
        return "Quest accepted: defeat three map enemies, then report here.";
    }

    private HexTile chooseBossSite(Vector land, HexTile town) {
        HexTile free = farthestFreeLand(land, null, town);
        if (free != null) return free;
        HexTile best = null;
        int bestDistance = -1;
        for (int i = 0; i < mapEnemies.size(); i++) {
            Unit enemy = (Unit) mapEnemies.elementAt(i);
            HexTile tile = map.getTile(enemy.q, enemy.r);
            if (tile == null || !land.contains(tile) || tile.type == HexTile.TYPE_TOWN
                    || isPartyAt(tile.q, tile.r)) continue;
            int distance = getDist(town.q, town.r, tile.q, tile.r);
            if (distance > bestDistance) {
                best = tile;
                bestDistance = distance;
            }
        }
        return best;
    }

    public String reportQuest(HexTile town, Unit reporter) {
        if (quest.getState() != QuestCampaign.REPORT_READY || !quest.isHome(town))
            return "Quest is not ready to report.";
        Vector land = map.getConnectedLandTiles(town.q, town.r);
        HexTile site = chooseBossSite(land, town);
        if (site == null) return "No reachable boss site. Move the party and try again.";
        Unit captain = Unit.createGoblinCaptain(party.getEnemyLevel(), site.q, site.r);
        if (!quest.report(reporter, party, town, site))
            return "Report unavailable. Gold or XP limit reached.";
        Unit displaced = null;
        for (int i = 0; i < mapEnemies.size(); i++) {
            Unit enemy = (Unit) mapEnemies.elementAt(i);
            if (enemy.q == site.q && enemy.r == site.r) { displaced = enemy; break; }
        }
        if (displaced == null && mapEnemies.size() >= MAX_MAP_ENEMIES) {
            for (int i = 0; i < mapEnemies.size(); i++) {
                Unit enemy = (Unit) mapEnemies.elementAt(i);
                if (!land.contains(map.getTile(enemy.q, enemy.r))) { displaced = enemy; break; }
            }
            if (displaced == null) displaced = (Unit) mapEnemies.elementAt(0);
        }
        if (displaced != null) mapEnemies.removeElement(displaced);
        bossEnemy = captain;
        mapEnemies.addElement(captain);
        map.revealArea(site.q, site.r, 0);
        showMinimap();
        invalidate();
        return reporter.name + " received 30 gold; quest participants received 20 XP. Boss marked on the map.";
    }

    private int getDist(int q1, int r1, int q2, int r2) {
        return (Math.abs(q1 - q2) + Math.abs(q1 + r1 - q2 - r2) + Math.abs(r1 - r2)) / 2;
    }

    private void updateReachable() {
        Unit u = party.getActiveMember();
        if (u != null && !u.isMoving && u.curMove > 0) {
            reachableTiles = map.getReachableTiles(u.q, u.r, u.curMove, aboard(u));
        } else {
            reachableTiles = null;
        }
    }

    protected void sublayout(int width, int height) {
        super.sublayout(width, height);
        if (party != null && renderer != null && !cursorCameraActive) {
            Unit active = party.getActiveMember();
            if (active != null) {
                if (active.isMoving) followUnit(active);
                else centerOnUnit(active);
            }
        }
    }

    protected void paint(Graphics g) {
        g.setColor(0x000000);
        g.fillRect(0, 0, getWidth(), getHeight());
        renderer.drawMap(g, map, getWidth(), Math.max(0, getHeight() - hud.getHeight()));
        renderer.drawRange(g, reachableTiles);
        if (previewPath != null) {
            drawPreviewPath(g);
        }
        renderer.drawTerrainDecorations(g, getWidth(), Math.max(0, getHeight() - hud.getHeight()));
        drawUnits(g);
        if (quest.getState() == QuestCampaign.BOSS_ACTIVE)
            renderer.drawQuestMarker(g, quest.getBossQ(), quest.getBossR());
        renderer.drawSelector(g, cursorQ, cursorR);
        hud.draw(g, party, getWidth(), getHeight());
        int miniSide = Math.min(100, Math.min(getWidth() / 4, Math.max(16, (getHeight() - hud.getHeight()) / 3)));
        renderer.drawMinimap(g, map, party, mapEnemies, quest, cursorQ, cursorR,
                getWidth() - miniSide - 8, 8, miniSide, minimapAlpha);
        g.setColor(Color.WHITE);
        g.setFont(mapInfoFont);
        int helpWidth = Math.max(0, getWidth() - miniSide - 24);
        g.drawText("ROUND " + party.getWorldRound() + "  TURN " + party.getTurnNumber()
                + "/" + party.getTurnCount() + "  [SPACE] END TURN  [P] FOCUS +MOVE", 8, 8, DrawStyle.ELLIPSIS, helpWidth);
        Unit active = party.getActiveMember();
        if (active != null)
            g.drawText((aboard(active) ? "SAILING  GOLD " : "GOLD ") + active.getGold()
                    + (aboard(active) ? "  MENU: SAILING  [I] BAG" : "  [G] GOLD  [I] BAG"),
                    8, 24, DrawStyle.ELLIPSIS, helpWidth);
        g.drawText("[ENTER/CLICK] PREVIEW/MOVE  [BACK] CANCEL", 8, 40, DrawStyle.ELLIPSIS, helpWidth);
        g.drawText("QUEST: " + quest.getStatusText(), 8, 56, DrawStyle.ELLIPSIS, helpWidth);
    }

    private void drawPreviewPath(Graphics g) {
        g.setColor(Color.WHITE);
        g.setGlobalAlpha(150);
        int[] pPos = paintPoint;
        for (int i = 0; i < previewPath.size(); i++) {
            HexTile t = (HexTile) previewPath.elementAt(i);
            renderer.getHexScreenPoint(t.q, t.r, pPos);
            g.fillArc(pPos[0] - 10, pPos[1] - 10, 20, 20, 0, 360);
        }
        g.setGlobalAlpha(255);
    }

    private void drawUnits(Graphics g) {
        for (int groupIndex = 0; groupIndex < sailingGroups.length; groupIndex++) {
            int group = sailingGroups[groupIndex];
            if (group == 0 || (group & ((1 << groupIndex) - 1)) != 0) continue;
            Unit pilot = party.getActiveMember();
            if (boatGroup(pilot) != group) pilot = (Unit) party.members.elementAt(groupIndex);
            int x = pilot.worldX + renderer.getOriginX(), y = pilot.worldY + renderer.getOriginY();
            if (!pilot.isMoving) {
                renderer.getHexScreenPoint(pilot.q, pilot.r, paintPoint);
                x = paintPoint[0]; y = paintPoint[1];
            }
            g.setColor(0x885522); g.fillRect(x - 20, y - 5, 40, 12);
            g.setColor(Color.WHITE); g.drawLine(x, y - 28, x, y);
            g.drawLine(x, y - 28, x + 15, y - 12); g.drawLine(x + 15, y - 12, x, y - 12);
            g.setFont(mapInfoFont); g.drawText("BOAT", x - 18, y + 9);
        }
        // Collect all units to draw
        Vector allUnits = paintUnits;
        allUnits.removeAllElements();
        for (int i = 0; i < party.members.size(); i++) {
            Unit member = (Unit) party.members.elementAt(i);
            if (!aboard(member)) allUnits.addElement(member);
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

        int[] pos = paintPoint;
        // Identify groups by q, r
        Vector processed = paintProcessed;
        processed.removeAllElements();

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
                        u.drawWorldSprite(g, vx - 35, vy - 45);
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
            Vector sharedTile = paintSharedTile;
            sharedTile.removeAllElements();
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
                        tileUnit.drawWorldSprite(g, vx - 35, vy - 45);
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
        previewPath = null;
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

    private boolean acceptsWorldInput() {
        return exposed && !closed && !gameOver && activeCombat == null && activeEncounter == null
                && UiApplication.getUiApplication().getActiveScreen() == this;
    }

    private void moveCursor(int dq, int dr) {
        if (!acceptsWorldInput() || map.getTile(cursorQ + dq, cursorR + dr) == null) return;
        cursorQ += dq;
        cursorR += dr;
        cursorMoved();
    }

    protected boolean navigationMovement(int dx, int dy, int status, int time) {
        if (dx != 0 || dy != 0)
            moveCursor(dx < 0 ? -1 : dx > 0 ? 1 : 0, dy < 0 ? -1 : dy > 0 ? 1 : 0);
        return true;
    }

    protected boolean navigationClick(int status, int time) {
        if (!navigationPressed) {
            navigationPressed = true;
            handleAction();
        }
        return true;
    }

    protected boolean navigationUnclick(int status, int time) {
        navigationPressed = false;
        return true;
    }

    protected boolean keyDown(int keycode, int time) {
        if (Keypad.key(keycode) == Keypad.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyDown(keycode, time);
    }

    protected boolean keyRepeat(int keycode, int time) {
        int key = Keypad.key(keycode);
        if (key == 'p' || key == 'P' || key == 'g' || key == 'G'
                || key == 'i' || key == 'I'
                || key == Keypad.KEY_SPACE || key == Keypad.KEY_ENTER || key == Keypad.KEY_ESCAPE)
            return true;
        return super.keyRepeat(keycode, time);
    }
    protected boolean keyChar(char c, int status, int time) {
        if (c == 'i' || c == 'I') {
            handleInventory();
            return true;
        }
        if (c == 'g' || c == 'G') {
            handleGoldTransfer();
            return true;
        }
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
        if (c == Keypad.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (c == 'w' || c == 'W' || c == '2') {
            moveCursor(0, -1);
            return true;
        }
        if (c == 's' || c == 'S' || c == '8') {
            moveCursor(0, 1);
            return true;
        }
        if (c == 'a' || c == 'A' || c == '4') {
            moveCursor(-1, 0);
            return true;
        }
        if (c == 'd' || c == 'D' || c == '6') {
            moveCursor(1, 0);
            return true;
        }
        return super.keyChar(c, status, time);
    }

    protected boolean touchEvent(TouchEvent message) {
        if (!acceptsWorldInput()) return true;
        int x = message.getX(1), y = message.getY(1);
        int event = message.getEvent();
        if (event == TouchEvent.CANCEL || message.getX(2) >= 0) {
            touchActive = false;
            return true;
        }
        if (event == TouchEvent.DOWN) {
            touchActive = x >= 0 && x < getWidth() && y >= 0
                    && y < getHeight() - hud.getHeight();
            touchDragged = false;
            touchStartX = x;
            touchStartY = y;
            touchLastX = x;
            touchLastY = y;
            originStartX = renderer.getOriginX();
            originStartY = renderer.getOriginY();
            return true;
        }
        if (!touchActive) return true;
        if (x >= 0 && y >= 0) {
            touchLastX = x;
            touchLastY = y;
        }
        int dx = touchLastX - touchStartX, dy = touchLastY - touchStartY;
        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) touchDragged = true;
        if (event == TouchEvent.MOVE && touchDragged) {
            cursorCameraActive = true;
            renderer.setOrigin(originStartX + dx, originStartY + dy);
            showMinimap();
            invalidate();
        } else if (event == TouchEvent.UP) {
            touchActive = false;
            if (!touchDragged && touchLastX >= 0 && touchLastX < getWidth()
                    && touchLastY >= 0 && touchLastY < getHeight() - hud.getHeight()) {
                HexTile tile = renderer.getTileAtScreenPoint(map, touchLastX, touchLastY);
                if (tile != null) {
                    if (cursorQ != tile.q || cursorR != tile.r) previewPath = null;
                    cursorQ = tile.q;
                    cursorR = tile.r;
                    cursorCameraActive = true;
                    ensureCursorVisible();
                    showMinimap();
                    handleAction();
                    invalidate();
                }
            }
        }
        // CLICK/UNCLICK never confirm again after the release tap.
        return true;
    }

    private void handleAction() {
        if (!acceptsWorldInput() || party.isWiped())
            return;
        synchronizeWorldTurn();
        Unit u = party.getActiveMember();
        if (u == null || u.isMoving)
            return;

        HexTile currentTile = map.getTile(u.q, u.r);
        if (cursorQ == u.q && cursorR == u.r && currentTile != null
                && currentTile.type == HexTile.TYPE_TOWN) {
            if (aboard(u)) { sailingMenu(); return; }
            previewPath = null;
            pauseUpdates();
            UiApplication.getUiApplication().pushScreen(new TownScreen(party, currentTile, quest, this));
            return;
        }
        updateReachable();
        HexTile target = map.getTile(cursorQ, cursorR);
        if (target != null && reachableTiles != null && reachableTiles.contains(target)) {
            // Check if we are already previewing this path
            if (previewPath != null && !previewPath.isEmpty()) {
                HexTile last = (HexTile) previewPath.elementAt(previewPath.size() - 1);
                if (last.q == cursorQ && last.r == cursorR) {
                    // Second click: Execute move
                    Vector confirmedPath = map.getPath(u.q, u.r, cursorQ, cursorR, aboard(u));
                    int cost = confirmedPath != null ? confirmedPath.size() : 0;
                    if (cost > 0 && u.curMove >= cost) {
                        cursorCameraActive = false;
                        u.setPath(confirmedPath, renderer);
                        resumeUpdates();
                        previewPath = null;
                        updateReachable();
                    }
                    invalidate();
                    return;
                }
            }

            // First click or different tile: Preview path
            Vector path = map.getPath(u.q, u.r, cursorQ, cursorR, aboard(u));
            if (path != null) {
                previewPath = path;
            }
        } else {
            previewPath = null;
        }
        invalidate();
    }

    private void handleGoldTransfer() {
        if (!exposed || closed || activeCombat != null || activeEncounter != null || gameOver
                || UiApplication.getUiApplication().getActiveScreen() != this || party.isWiped())
            return;
        for (int i = 0; i < party.members.size(); i++)
            if (((Unit) party.members.elementAt(i)).isMoving) return;
        previewPath = null;
        pauseUpdates();
        UiApplication.getUiApplication().pushScreen(new GoldTransferScreen(party));
    }

    private void handleInventory() {
        if (!acceptsWorldInput() || party.isWiped()) return;
        for (int i = 0; i < party.members.size(); i++)
            if (((Unit) party.members.elementAt(i)).isMoving) return;
        previewPath = null;
        pauseUpdates();
        UiApplication.getUiApplication().pushScreen(new InventoryScreen(party, map, this));
    }

    public void inventoryTurnEnded() {
        synchronizeWorldTurn();
        Unit active = party.getActiveMember();
        if (active != null) {
            cursorQ = active.q;
            cursorR = active.r;
            centerOnUnit(active);
            updateReachable();
            showMinimap();
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

    public void close() { onClose(); }

    public boolean onClose() {
        if (!acceptsWorldInput() || exitPrompt) return false;
        Unit active = party.getActiveMember();
        if (active == null || active.isMoving) return false;
        if (previewPath != null) {
            previewPath = null;
            invalidate();
            return false;
        }
        if (cursorQ != active.q || cursorR != active.r) {
            cursorQ = active.q;
            cursorR = active.r;
            cursorCameraActive = false;
            centerOnUnit(active);
            showMinimap();
            return false;
        }
        exitPrompt = true;
        int choice;
        try {
            choice = Dialog.ask("Save and exit game?",
                    new String[] { "Continue", "Exit" }, 0);
        } finally {
            exitPrompt = false;
        }
        if (choice != 1) return false;
        if (saveSlot < 0) {
            manualSave();
            if (saveSlot < 0) return false;
        } else if (!saveToSlot(saveSlot)) return false;
        closed = true;
        pauseUpdates();
        System.exit(0);
        return true;
    }
}
