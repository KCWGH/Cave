package com.blackberry.cave;

import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.Font;
import net.rim.device.api.system.Bitmap;
import java.util.Vector;
import java.util.Random;

public class CombatScreen extends MainScreen {
    public static final int RESULT_WIN = 0;
    public static final int RESULT_FLEE = 1;
    public static final int RESULT_PARTICIPANTS_KO = 2;
    public static final int RESULT_PARTY_WIPED = 3;

    public interface ResultListener {
        void combatFinished(CombatScreen combat, int result);
    }

    private ResultListener resultListener;
    private static final int STATE_ACTION = 0;
    private static final int STATE_TARGET = 1;
    private static final int STATE_ROLLING = 2;
    private static final int STATE_FINISHED = 3;
    private static final int STATE_ENEMY_WAIT = 4;
    private int state = STATE_ACTION;
    private int enemyWaitTicks;
    private static final int STATE_FLEE_SELECT = 5;
    private int plannedFocus;
    private int spentFocus;
    private int rollChance;
    private boolean exposed = false;
    private Vector heroes;
    private Vector enemies;
    private Party party;
    private Vector turnOrder;
    private int currentTurnIndex = 0;
    private boolean heroFirstRound;

    private int currentAction = 0;
    private static final int ACTION_ATTACK = 0;
    private static final int ACTION_FLEE = 1;

    private int[] slots = new int[0];
    private int rollingIndex = -1;
    private Random rand;
    private UiTickTimer animTimer = new UiTickTimer(new Runnable() {
        public void run() {
            advanceCombat();
        }
    });

    // Target selection
    private int selectedTargetIndex = 0;
    private Bitmap imgFocus;

    public CombatScreen(Vector heroes, Vector enemies, Party party, ResultListener resultListener, boolean heroFirstRound, Random random) {
        super(MainScreen.NO_VERTICAL_SCROLL | MainScreen.NO_HORIZONTAL_SCROLL);
        this.rand = random;
        this.heroes = heroes;
        this.enemies = enemies;
        this.party = party;
        this.heroFirstRound = heroFirstRound;
        this.resultListener = resultListener;
        Bitmap rawFocus = Bitmap.getBitmapResource("icon_focus.png");
        if (rawFocus != null) {
            this.imgFocus = new Bitmap(30, 30);
            rawFocus.scaleInto(this.imgFocus, Bitmap.FILTER_LANCZOS, Bitmap.SCALE_STRETCH);
        }
        calculateTurnOrder();
        prepareCurrentTurn();
    }

    private void calculateTurnOrder() {
        turnOrder = new Vector();
        for (int i = 0; i < heroes.size(); i++) {
            Unit u = (Unit) heroes.elementAt(i);
            if (u.curHp > 0)
                turnOrder.addElement(u);
        }
        for (int i = 0; i < enemies.size(); i++) {
            Unit u = (Unit) enemies.elementAt(i);
            if (u.curHp > 0)
                turnOrder.addElement(u);
        }

        // Stable sort. A successful ambush gives heroes priority only this round.
        for (int i = 1; i < turnOrder.size(); i++) {
            Unit unit = (Unit) turnOrder.elementAt(i);
            int j = i;
            while (j > 0 && actsBefore(unit, (Unit) turnOrder.elementAt(j - 1))) {
                turnOrder.setElementAt(turnOrder.elementAt(j - 1), j);
                j--;
            }
            turnOrder.setElementAt(unit, j);
        }
    }

    private boolean actsBefore(Unit first, Unit second) {
        if (heroFirstRound && heroes.contains(first) != heroes.contains(second))
            return heroes.contains(first);
        return first.getSpeed() > second.getSpeed();
    }

    protected void paint(Graphics g) {
        g.setColor(0x220000);
        g.fillRect(0, 0, getWidth(), getHeight());
        drawUnits(g);
        drawHUD(g);
        if (state == STATE_ROLLING)
            drawRolls(g);
    }

    private void drawUnits(Graphics g) {
        // Draw Heroes (Left)
        int startY = 80;
        for (int i = 0; i < heroes.size(); i++) {
            Unit u = (Unit) heroes.elementAt(i);
            int x = 40;
            int y = startY + i * 70;

            if (u.curHp <= 0) {
                g.setGlobalAlpha(100); // Dead/KO
            }

            if (u.sprite != null)
                g.drawBitmap(x, y - 10, 70, 70, u.sprite, 0, 0);

            g.setGlobalAlpha(255);

            if (getActiveUnit() == u) {
                g.setColor(Color.YELLOW);
                g.drawRect(x - 5, y - 15, 80, 80);
            }

            // HP Bar
            g.setColor(Color.RED);
            g.fillRect(x, y + 65, 60, 4);
            g.setColor(Color.GREEN);
            int w = (int) ((long) u.curHp * 60 / u.getMaxHp());
            if (w < 0)
                w = 0;
            g.fillRect(x, y + 65, w, 4);
        }

        // Draw Enemies (Right)
        int ex = getWidth() - 100;
        for (int i = 0; i < enemies.size(); i++) {
            Unit u = (Unit) enemies.elementAt(i);
            int y = startY + i * 70;

            if (u.curHp <= 0) {
                g.setGlobalAlpha(50);
            }

            if (u.sprite != null)
                if (heroes.contains(u)) {
                    g.drawBitmap(ex, y - 10, 70, 70, u.sprite, 0, 0); // fallback case if enemy somehow gets marked as
                                                                      // hero (shouldn't happen here)
                } else {
                    g.drawBitmap(ex, y, 60, 60, u.sprite, 0, 0);
                }

            g.setGlobalAlpha(255);

            if (getActiveUnit() == u) {
                g.setColor(Color.YELLOW);
                g.drawRect(ex - 5, y - 15, 80, 80);
            }

            if (state == STATE_TARGET && selectedTargetIndex == i) {
                g.setColor(Color.RED); // Selection indicator
                g.drawRect(ex - 5, y - 5, 70, 70);
                g.drawText("TARGET", ex, y - 20);
            }

            // Name & HP
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().derive(net.rim.device.api.ui.Font.PLAIN, 12));
            g.drawText(u.name, ex, y - 15);
            g.setColor(Color.RED);
            g.fillRect(ex, y + 65, 60, 4);
            g.setColor(Color.GREEN);
            int ew = (int) ((long) u.curHp * 60 / u.getMaxHp());
            if (ew < 0)
                ew = 0;
            g.fillRect(ex, y + 65, ew, 4);
        }
    }

    private void drawHUD(Graphics g) {
        // Turn Order Bar
        g.setColor(0x444444);
        g.fillRect(0, 0, getWidth(), 40);
        g.setColor(0x888888);
        g.drawLine(0, 40, getWidth(), 40);

        for (int i = 0; i < turnOrder.size(); i++) {
            Unit u = (Unit) turnOrder.elementAt(i);
            if (u.curHp <= 0)
                continue;

            int x = 10 + i * 45;
            if (i == currentTurnIndex) {
                g.setColor(Color.WHITE);
                g.fillRect(x - 2, 8, 34, 24);
            }

            if (heroes.contains(u)) {
                g.setColor(0x004466);
            } else {
                g.setColor(0x660000);
            }
            g.fillRect(x, 10, 30, 20);

            if (u.miniSprite != null) {
                g.drawBitmap(x + 5, 10, 20, 20, u.miniSprite, 0, 0);
            }

            if (i < turnOrder.size() - 1) {
                g.setColor(0x888888);
                g.drawLine(x + 35, 20, x + 40, 20);
            }
        }

        Unit current = getActiveUnit();
        if (current != null && state != STATE_ROLLING && state != STATE_FINISHED) {
            g.setColor(0x222222);
            g.fillRect(0, getHeight() - 60, getWidth(), 60);
            g.setColor(0x888888);
            g.drawLine(0, getHeight() - 60, getWidth(), getHeight() - 60);

            g.setColor(Color.WHITE);
            g.setFont(g.getFont().derive(Font.BOLD, 14));
            g.drawText(current.name, 10, getHeight() - 50);
            g.setFont(g.getFont().derive(Font.PLAIN, 12));
            g.drawText("HP: " + current.curHp + "/" + current.getMaxHp() + "  STR: " + current.getStrength() + "  INT: "
                    + current.getIntelligence() + "  SPD: " + current.getSpeed(), 10, getHeight() - 30);
            g.drawText("DMG: " + current.getDamage() + "  FOCUS: " + current.curFocus + "/" + current.getMaxFocus(), 10, getHeight() - 15);

            if (heroes.contains(current)) {
                if (state == STATE_TARGET || state == STATE_FLEE_SELECT) {
                    g.setColor(Color.YELLOW);
                    g.drawText("[P/O] FOCUS +/-  [SPACE] ROLL", 220, getHeight() - 35);
                    drawCombatPreview(g);
                } else {
                    g.setColor(Color.CYAN);
                    g.drawText("[SPACE] ATTACK   [F] FLEE", 350, getHeight() - 35);
                }
            } else {
                g.setColor(Color.ORANGE);
                g.drawText("ENEMY TURN...", 350, getHeight() - 35);
            }
        }
    }

    private void drawCombatPreview(Graphics g) {
        Unit acting = getActiveUnit();
        if (acting == null) return;
        int bw = 380, bh = 145;
        int bx = (getWidth() - bw) / 2, by = (getHeight() - bh) / 2 - 20;
        int count = rollSlotCount(acting, currentAction);
        int focus = Math.min(plannedFocus, focusLimit(acting, currentAction));
        g.setGlobalAlpha(210);
        g.setColor(Color.BLACK);
        g.fillRect(bx, by, bw, bh);
        g.setGlobalAlpha(255);
        g.setColor(0x888888);
        g.drawRect(bx, by, bw, bh);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().derive(Font.BOLD, 16));
        if (currentAction == ACTION_FLEE) {
            g.drawText("FLEE: need 2 / 3 successes", bx + 10, by + 10);
        } else {
            Unit target = (Unit) enemies.elementAt(selectedTargetIndex);
            g.drawText("VS " + target.name + "  HP " + target.curHp + "/" + target.getMaxHp(), bx + 10, by + 10);
        }
        g.setFont(g.getFont().derive(Font.PLAIN, 14));
        g.drawText("Slots: " + count + "  Guaranteed: " + focus, bx + 10, by + 38);
        g.drawText(focus == count ? "All slots guaranteed"
                : "Other slots: " + Unit.focusedChance(baseRollChance(acting, currentAction), focus) + "% each", bx + 10, by + 61);
        if (currentAction == ACTION_ATTACK)
            g.drawText("Max DMG: " + calculateDamage(acting, count, count), bx + 10, by + 84);
        g.drawText(currentAction == ACTION_ATTACK && !acting.canFocusAttack() ? "Focus unavailable for this weapon"
                : "Focus: " + acting.curFocus + "  Use: " + focus + "  [P/O] +/-", bx + 10, by + 106);
        g.drawText("[SPACE] ROLL  [BACK] CANCEL", bx + 10, by + 125);
    }
    private void drawRolls(Graphics g) {
        int cx = (getWidth() - slots.length * 40 + 10) / 2;
        int cy = getHeight() / 2;
        g.setColor(Color.WHITE);
        g.drawText("ROLLING... " + rollChance + "%  FOCUS " + spentFocus, cx, cy - 30);
        for (int i = 0; i < slots.length; i++) {
            int sx = cx + i * 40;
            g.setColor(i < spentFocus ? Color.CYAN : 0x555555);
            g.drawRect(sx, cy, 30, 30);
            if (slots[i] == 1) {
                if (imgFocus != null) {
                    g.drawBitmap(sx, cy, 30, 30, imgFocus, 0, 0);
                } else {
                    g.setColor(Color.YELLOW);
                    g.fillArc(sx + 5, cy + 5, 20, 20, 0, 360);
                }
            } else if (slots[i] == 0) {
                g.setColor(Color.RED);
                g.drawLine(sx + 5, cy + 5, sx + 25, cy + 25);
                g.drawLine(sx + 25, cy + 5, sx + 5, cy + 25);
            }
        }
    }

    private Unit getActiveUnit() {
        if (turnOrder == null || turnOrder.isEmpty())
            return null;
        return (Unit) turnOrder.elementAt(currentTurnIndex);
    }

    private int rollSlotCount(Unit unit, int action) {
        return action == ACTION_ATTACK ? unit.getAttackSlotCount() : 3;
    }

    private int focusLimit(Unit unit, int action) {
        if (action == ACTION_ATTACK && !unit.canFocusAttack()) return 0;
        return Math.min(unit.curFocus, rollSlotCount(unit, action));
    }

    private int baseRollChance(Unit unit, int action) {
        if (action == ACTION_ATTACK) return unit.getAttackSuccessChance();
        int fastest = 0;
        for (int i = 0; i < enemies.size(); i++) {
            Unit enemy = (Unit) enemies.elementAt(i);
            if (enemy.curHp > 0) fastest = Math.max(fastest, enemy.getSpeed());
        }
        return Math.max(10, Math.min(90, 50 + 5 * (unit.getSpeed() - fastest)));
    }

    private void startAutoRoll(int action) {
        if (!exposed || UiApplication.getUiApplication().getActiveScreen() != this
                || state == STATE_ROLLING || state == STATE_FINISHED)
            return;
        if (checkWinCondition()) return;
        Unit active = getActiveUnit();
        if (active == null || active.curHp <= 0) return;
        this.currentAction = action;
        spentFocus = heroes.contains(active) ? Math.min(plannedFocus, focusLimit(active, action)) : 0;
        rollChance = Unit.focusedChance(baseRollChance(active, action), spentFocus);
        slots = new int[rollSlotCount(active, action)];
        for (int i = 0; i < slots.length; i++) slots[i] = -1;
        active.curFocus -= spentFocus;
        plannedFocus = 0;
        state = STATE_ROLLING;
        rollingIndex = 0;
        resumeUpdates();
        invalidate();
    }
    private void advanceCombat() {
        if (!exposed || UiApplication.getUiApplication().getActiveScreen() != this || state == STATE_FINISHED)
            return;
        if (state == STATE_ENEMY_WAIT) {
            if (checkWinCondition()) return;
            if (++enemyWaitTicks >= 2)
                startAutoRoll(ACTION_ATTACK);
        } else if (state == STATE_ROLLING) {
            resolveNextSlot();
        }
    }
    private void resolveNextSlot() {
        if (state != STATE_ROLLING || !exposed || UiApplication.getUiApplication().getActiveScreen() != this)
            return;
        if (rollingIndex < slots.length) {
            if (rollingIndex < spentFocus || rand.nextInt(100) < rollChance)
                slots[rollingIndex] = 1;
            else
                slots[rollingIndex] = 0;
            rollingIndex++;
            invalidate();
        } else {
            animTimer.stop();
            finishTurn();
        }
    }

    private int calculateDamage(Unit unit, int successes, int slotCount) {
        return (int) ((long) unit.getDamage() * successes / slotCount);
    }
    private void finishTurn() {
        if (state != STATE_ROLLING)
            return;
        int successCount = 0;
        for (int i = 0; i < slots.length; i++)
            if (slots[i] == 1)
                successCount++;

        if (currentAction == ACTION_FLEE) {
            if (successCount >= 2) {
                finishCombat(RESULT_FLEE);
                return;
            }
            state = STATE_ACTION;
        } else {
            Unit active = getActiveUnit();
            int damage = calculateDamage(active, successCount, slots.length);
            Unit target = null;

            if (heroes.contains(active)) {
                target = (Unit) enemies.elementAt(selectedTargetIndex);
            } else {
                Vector liveHeroes = new Vector();
                for (int i = 0; i < heroes.size(); i++) {
                    Unit h = (Unit) heroes.elementAt(i);
                    if (h.curHp > 0)
                        liveHeroes.addElement(h);
                }
                if (!liveHeroes.isEmpty()) {
                    target = (Unit) liveHeroes.elementAt(rand.nextInt(liveHeroes.size()));
                }
            }

            if (target != null) {
                target.curHp -= damage;
                if (target.curHp < 0)
                    target.curHp = 0;
            }

            state = STATE_ACTION;
            if (checkWinCondition())
                return;
        }
        nextTurn();
    }

    private boolean checkWinCondition() {
        boolean allHeroesDead = true;
        for (int i = 0; i < heroes.size(); i++) {
            if (((Unit) heroes.elementAt(i)).curHp > 0)
                allHeroesDead = false;
        }
        if (allHeroesDead) {
            finishCombat(party == null || party.isWiped()
                    ? RESULT_PARTY_WIPED : RESULT_PARTICIPANTS_KO);
            return true;
        }

        for (int i = 0; i < enemies.size(); i++) {
            if (((Unit) enemies.elementAt(i)).curHp > 0)
                return false;
        }
        finishCombat(RESULT_WIN);
        return true;
    }

    private void finishCombat(int result) {
        if (state == STATE_FINISHED)
            return;
        state = STATE_FINISHED;
        pauseUpdates();
        UiApplication.getUiApplication().popScreen(this);
        ResultListener listener = resultListener;
        resultListener = null;
        if (listener != null)
            listener.combatFinished(this, result);
    }

    // Default menu close must follow the same policy as the Back key.
    public void close() {
        onClose();
    }

    // Combat exits only through a resolved result; Back cannot bypass fleeing.
    public boolean onClose() {
        if (state == STATE_TARGET || state == STATE_FLEE_SELECT) {
            state = STATE_ACTION;
            plannedFocus = 0;
            currentAction = ACTION_ATTACK;
            invalidate();
        }
        return false;
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
        if (exposed && (state == STATE_ROLLING || state == STATE_ENEMY_WAIT))
            animTimer.start(300);
    }

    private void nextTurn() {
        if (state == STATE_FINISHED || turnOrder.isEmpty())
            return;
        // Find next living unit
        int safety = 0;
        do {
            currentTurnIndex++;
            if (currentTurnIndex >= turnOrder.size()) {
                heroFirstRound = false;
                calculateTurnOrder();
                currentTurnIndex = 0;
            }
            safety++;
        } while (((Unit) turnOrder.elementAt(currentTurnIndex)).curHp <= 0 && safety < turnOrder.size() * 2);

        prepareCurrentTurn();
        resumeUpdates();
        invalidate();
    }

    private void prepareCurrentTurn() {
        Unit next = getActiveUnit();
        state = heroes.contains(next) ? STATE_ACTION : STATE_ENEMY_WAIT;
        enemyWaitTicks = 0;
        plannedFocus = 0;
        currentAction = ACTION_ATTACK;
        selectedTargetIndex = 0;
        for (int i = 0; i < enemies.size(); i++) {
            if (((Unit) enemies.elementAt(i)).curHp > 0) {
                selectedTargetIndex = i;
                break;
            }
        }
    }

    protected boolean keyRepeat(int keycode, int time) {
        // Holding confirm must not select a target and immediately spend an action.
        return true;
    }
    protected boolean keyDown(int keycode, int time) {
        int key = Keypad.key(keycode);
        if (key == Keypad.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (!exposed || UiApplication.getUiApplication().getActiveScreen() != this
                || state == STATE_ROLLING || state == STATE_FINISHED || state == STATE_ENEMY_WAIT)
            return true;
        if (checkWinCondition()) return true;
        Unit active = getActiveUnit();
        if (!heroes.contains(active)) return true;
        if (key == Keypad.KEY_SPACE || key == Keypad.KEY_ENTER) {
            if (state == STATE_ACTION) {
                currentAction = ACTION_ATTACK;
                plannedFocus = 0;
                state = STATE_TARGET;
                invalidate();
            } else {
                startAutoRoll(currentAction);
            }
            return true;
        }
        if ((key == 'f' || key == 'F') && state == STATE_ACTION) {
            currentAction = ACTION_FLEE;
            plannedFocus = 0;
            state = STATE_FLEE_SELECT;
            invalidate();
            return true;
        }
        if (state == STATE_TARGET || state == STATE_FLEE_SELECT) {
            if (key == 'p' || key == 'P') {
                plannedFocus = Math.min(plannedFocus + 1, focusLimit(active, currentAction));
                invalidate();
            } else if (key == 'o' || key == 'O') {
                plannedFocus = Math.max(0, plannedFocus - 1);
                invalidate();
            }
        }
        if (state == STATE_TARGET) {
            int direction = (key == 'w' || key == 'W' || key == '2') ? -1
                    : (key == 's' || key == 'S' || key == '8') ? 1 : 0;
            if (direction != 0) {
                do {
                    selectedTargetIndex = (selectedTargetIndex + direction + enemies.size()) % enemies.size();
                } while (((Unit) enemies.elementAt(selectedTargetIndex)).curHp <= 0);
                invalidate();
            }
        }
        return true;
    }
}