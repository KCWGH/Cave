package com.blackberry.cave;

import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.TouchEvent;
import net.rim.device.api.ui.DrawStyle;
import net.rim.device.api.system.Bitmap;
import java.util.Vector;
import java.util.Random;

public class CombatScreen extends MainScreen {
    private static final Font FONT_PLAIN_12 = Font.getDefault().derive(Font.PLAIN, 12);
    private static final Font FONT_PLAIN_14 = Font.getDefault().derive(Font.PLAIN, 14);
    private static final Font FONT_BOLD_14 = Font.getDefault().derive(Font.BOLD, 14);
    private static final Font FONT_BOLD_16 = Font.getDefault().derive(Font.BOLD, 16);
    public static final int RESULT_WIN = 0;
    public static final int RESULT_FLEE = 1;
    public static final int RESULT_PARTICIPANTS_KO = 2;
    public static final int RESULT_PARTY_WIPED = 3;

    public interface ResultListener {
        void combatFinished(CombatScreen combat, int result);
    }

    private ResultListener resultListener;
    private int finishedResult = -1;
    private VictoryScreen victoryScreen;
    private boolean navigationPressed;
    private boolean touchActive;
    private boolean touchDragged;
    private int touchStartX, touchStartY;
    private static final int STATE_ACTION = 0;
    private static final int STATE_TARGET = 1;
    private static final int STATE_ROLLING = 2;
    private static final int STATE_FINISHED = 3;
    private static final int STATE_ENEMY_WAIT = 4;
    private int state = STATE_ACTION;
    private int enemyWaitTicks;
    private static final int STATE_CONFIRM = 5;
    private int plannedFocus;
    private int spentFocus;
    private int rollChance;
    private boolean exposed = false;
    private Vector heroes;
    private Vector enemies;
    private Vector liveHeroesScratch = new Vector();
    private Unit[] lastAttackers;
    private Party party;
    private Vector turnOrder;
    private int currentTurnIndex = 0;
    private boolean heroFirstRound;
    private boolean fleeAllowed;

    private int currentAction = 0;
    private Vector actionMenu = new Vector();
    private static final int ACTION_ATTACK = 0;
    private Vector tauntingHeroes = new Vector();
    private BattleStatus statuses = new BattleStatus();

    private Unit tauntTarget() {
        for (int i = tauntingHeroes.size() - 1; i >= 0; i--) {
            Unit unit = (Unit) tauntingHeroes.elementAt(i);
            if (unit.curHp > 0) return unit;
        }
        return null;
    }

    private BattleAction action() { return BattleAction.get(currentAction); }
    private Vector targetUnits() { return action().target == BattleAction.ALLY ? heroes : enemies; }

    private boolean validTarget(Unit target) {
        return action().validTarget(getActiveUnit(), target);
    }

    private int actionCount() {
        return actionMenu.size();
    }

    private int actionAt(int index) {
        return ((BattleAction) actionMenu.elementAt(index)).id;
    }

    private void cycleAction(int direction) {
        int count = actionCount();
        for (int i = 0; i < count; i++)
            if (actionAt(i) == currentAction) {
                currentAction = actionAt((i + direction + count) % count);
                break;
            }
    }

    private void beginTargetSelection() {
        plannedFocus = 0;
        if (!action().selectsOneTarget()) {
            state = STATE_CONFIRM;
        } else {
            Vector targets = targetUnits();
            boolean found = false;
            for (int i = 0; i < targets.size(); i++)
                if (validTarget((Unit) targets.elementAt(i))) {
                    selectedTargetIndex = i;
                    found = true;
                    break;
                }
            if (!found) {
                net.rim.device.api.ui.component.Dialog.alert("No eligible target.");
                return;
            }
            state = STATE_TARGET;
        }
        invalidate();
    }

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

    public CombatScreen(Vector heroes, Vector enemies, Party party, ResultListener resultListener, boolean heroFirstRound, Random random, boolean fleeAllowed) {
        super(MainScreen.NO_VERTICAL_SCROLL | MainScreen.NO_HORIZONTAL_SCROLL);
        this.rand = random;
        this.heroes = heroes;
        this.enemies = enemies;
        this.lastAttackers = new Unit[enemies.size()];
        this.party = party;
        this.heroFirstRound = heroFirstRound;
        this.fleeAllowed = fleeAllowed;
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
        if (state == STATE_ACTION && heroes.contains(getActiveUnit())) drawActionMenu(g);
        Unit taunter = tauntTarget();
        if (taunter != null) {
            g.setFont(FONT_BOLD_14);
            g.setColor(Color.CYAN);
            g.drawText("TAUNT: " + taunter.name, getWidth() / 3, 45, DrawStyle.ELLIPSIS, getWidth() / 3);
        }
        if (state == STATE_ROLLING)
            drawRolls(g);
    }

    private int battleRowStep() {
        int rows=Math.max(1,Math.max(heroes.size(),enemies.size()));
        return Math.max(1,(getHeight()-60-48)/rows);
    }
    private int battleRowY(int index) { return 48+index*battleRowStep(); }
    private int battleSpriteSide(boolean hero) { return Math.max(1,Math.min(hero?70:60,battleRowStep()-25)); }
    private int controlsLeft() { return getWidth()*55/100; }
    private int controlX(int column,int columns) { return controlsLeft()+(getWidth()-controlsLeft())*column/columns; }
    private int controlColumn(int x,int columns) { return (x-controlsLeft())*columns/Math.max(1,getWidth()-controlsLeft()); }

    private int menuWidth() { return Math.max(120, getWidth() - 2 * Math.min(110, getWidth() / 4) - 16); }
    private int menuLeft() { return (getWidth() - menuWidth()) / 2; }
    private int visibleActionRows() { return Math.max(1, Math.min(actionCount(), Math.max(1, (getHeight() - 160) / 32))); }
    private int selectedActionIndex() {
        for (int i = 0; i < actionCount(); i++) if (actionAt(i) == currentAction) return i;
        return 0;
    }
    private int firstActionRow() { return Math.max(0, selectedActionIndex() - visibleActionRows() + 1); }

    private void drawActionMenu(Graphics g) {
        int left = menuLeft(), width = menuWidth(), rows = visibleActionRows(), first = firstActionRow();
        g.setGlobalAlpha(230);
        g.setColor(Color.BLACK);
        g.fillRect(left, 65, width, 28 + rows * 32 + 24);
        g.setGlobalAlpha(255);
        g.setFont(FONT_BOLD_16);
        g.setColor(Color.WHITE);
        g.drawText("Actions", left + 10, 70);
        for (int row = 0; row < rows; row++) {
            BattleAction definition = (BattleAction) actionMenu.elementAt(first + row);
            int y = 93 + row * 32;
            if (definition.id == currentAction) {
                g.setColor(0x335566); g.fillRect(left + 4, y, width - 8, 30);
            }
            g.setColor(Color.WHITE);
            g.drawText(definition.label, left + 12, y + 5, DrawStyle.ELLIPSIS, width - 24);
        }
        g.setFont(FONT_PLAIN_12);
        g.setColor(Color.CYAN);
        g.drawText("Move to select. Click / Enter to confirm.", left + 8, 97 + rows * 32,
                DrawStyle.ELLIPSIS, width - 16);
    }

    private void drawUnits(Graphics g) {
        for(int team=0;team<2;team++) {
            Vector units=team==0?heroes:enemies;
            boolean hero=team==0;
            int side=battleSpriteSide(hero);
            int panel=Math.max(1,Math.min(110,getWidth()/4));
            int x=hero?Math.max(4,(panel-side)/2):getWidth()-panel+(panel-side)/2;
            for(int i=0;i<units.size();i++) {
                Unit unit=(Unit)units.elementAt(i); int y=battleRowY(i);
                g.setColor(Color.WHITE);g.setFont(FONT_PLAIN_12);
                g.drawText(unit.name+" L"+unit.getLevel(),hero?4:getWidth()-panel,y,DrawStyle.ELLIPSIS,panel-8);
                Bitmap image=unit.getCombatSprite(side);
                g.setGlobalAlpha(unit.curHp<=0?80:255);
                if(image!=null)g.drawBitmap(x,y+16,side,side,image,0,0);
                g.setGlobalAlpha(255);
                if(getActiveUnit()==unit) {g.setColor(Color.YELLOW);g.drawRect(x-3,y+13,side+6,side+6);}
                if(state==STATE_TARGET && hero==(action().target==BattleAction.ALLY) && selectedTargetIndex==i) {
                    g.setColor(action().target==BattleAction.ALLY?Color.CYAN:Color.RED);g.drawRect(x-3,y+13,side+6,side+6);
                }
                int barY=y+battleRowStep()-7;
                g.setFont(FONT_PLAIN_12);
                g.setColor(Color.ORANGE);
                g.drawText(statuses.label(unit), x, barY - 16, DrawStyle.ELLIPSIS, side);
                g.setColor(Color.RED);g.fillRect(x,barY,side,4);
                g.setColor(unit.curHp>0?0x00CC00:0x777777);
                int hp=(int)((long)unit.curHp*side/unit.getMaxHp());
                if(hp>0)g.fillRect(x,barY,Math.min(hp,side),4);
            }
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

            int x = 10 + i * Math.min(45, Math.max(1, (getWidth() - 20) / Math.max(1, turnOrder.size())));
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
            g.setFont(FONT_BOLD_14);
            g.drawText(current.name + " L" + current.getLevel(), 10, getHeight() - 50, DrawStyle.ELLIPSIS, controlsLeft() - 20);
            g.setFont(FONT_PLAIN_12);
            g.drawText("HP: " + current.curHp + "/" + current.getMaxHp() + "  STR: " + current.getStrength() + "  INT: "
                    + current.getIntelligence() + "  SPD: " + current.getSpeed(), 10, getHeight() - 30, DrawStyle.ELLIPSIS, controlsLeft() - 20);
            g.drawText("DMG: " + current.getDamage() + "  FOCUS: " + current.curFocus + "/" + current.getMaxFocus(), 10, getHeight() - 15, DrawStyle.ELLIPSIS, controlsLeft() - 20);

            if (heroes.contains(current)) {
                if (state == STATE_TARGET || state == STATE_CONFIRM) {
                    g.setColor(Color.YELLOW);
                    g.drawText("[P/O|L/R] FOCUS  [CLICK] ROLL", controlX(0, 2), getHeight() - 35, DrawStyle.ELLIPSIS, getWidth() - controlsLeft() - 8);
                    g.drawText("-FOCUS", controlX(0, 3), getHeight() - 15);
                    g.drawText("+FOCUS", controlX(1, 3), getHeight() - 15);
                    g.drawText("CONFIRM", controlX(2, 3), getHeight() - 15);
                    drawCombatPreview(g);
                } else {
                    g.setColor(Color.CYAN);
                    g.drawText("Choose an action from the menu", controlX(0, 2), getHeight() - 35,
                            DrawStyle.ELLIPSIS, getWidth() - controlsLeft() - 8);
                    g.drawText(fleeAllowed ? "[PAD] SELECT  [CLICK/ENTER] OK"
                            : "NO FLEE  [PAD] SELECT  [CLICK] OK", controlX(0, 2), getHeight() - 15, DrawStyle.ELLIPSIS, getWidth() - controlsLeft() - 8);
                }
            } else {
                g.setColor(Color.ORANGE);
                g.drawText("ENEMY TURN...", controlX(0, 2), getHeight() - 35);
            }
        }
    }

    private void drawCombatPreview(Graphics g) {
        Unit acting = getActiveUnit();
        if (acting == null) return;
        int extra = action().status == null ? 0 : 20;
        int bw = Math.min(380, Math.max(120, getWidth() - 2 * Math.min(110, getWidth() / 4) - 16)), bh = 145 + extra;
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
        g.setFont(FONT_BOLD_16);
        if (action().target == BattleAction.ALL_ENEMIES) {
            g.drawText(action().label + ": all living enemies", bx + 10, by + 10,
                    DrawStyle.ELLIPSIS, bw - 20);
        } else if (!action().selectsOneTarget()) {
            g.drawText(action().label + ": need " + action().required + " / " + count + " successes",
                    bx + 10, by + 10, DrawStyle.ELLIPSIS, bw - 20);
        } else {
            Unit target = (Unit) targetUnits().elementAt(selectedTargetIndex);
            g.drawText(action().label + ": " + target.name + "  HP " + target.curHp + "/" + target.getMaxHp(), bx + 10, by + 10, DrawStyle.ELLIPSIS, bw - 20);
        }
        g.setFont(FONT_PLAIN_14);
        g.drawText("Slots: " + count + "  Guaranteed: " + focus
                + (action().required > 0 ? "  Need: " + action().required : ""),
                bx + 10, by + 38, DrawStyle.ELLIPSIS, bw - 20);
        g.drawText(focus == count ? "All slots guaranteed"
                : "Other slots: " + Unit.focusedChance(baseRollChance(acting, currentAction), focus) + "% each", bx + 10, by + 61, DrawStyle.ELLIPSIS, bw - 20);
        g.drawText(action().effect == BattleAction.DAMAGE || action().effect == BattleAction.RESTORE
                ? "Maximum" + (action().target == BattleAction.ALL_ENEMIES ? " per enemy" : "") + ": " + action().maximum(acting) : action().description,
                bx + 10, by + 84, DrawStyle.ELLIPSIS, bw - 20);
        if (extra > 0) g.drawText(action().description, bx + 10, by + 106, DrawStyle.ELLIPSIS, bw - 20);
        g.drawText(action().stat == BattleAction.WEAPON_STAT && !acting.canFocusAttack() ? "Focus unavailable for this weapon"
                : "Focus: " + acting.curFocus + "  Use: " + focus + "  [P/O] +/-", bx + 10, by + 106 + extra, DrawStyle.ELLIPSIS, bw - 20);
        g.drawText("[SPACE] ROLL  [BACK] CANCEL", bx + 10, by + 125 + extra, DrawStyle.ELLIPSIS, bw - 20);
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
        return BattleAction.get(action).slotCount(unit);
    }

    private int focusLimit(Unit unit, int action) {
        return BattleAction.get(action).focusLimit(unit);
    }

    private int baseRollChance(Unit unit, int action) {
        return BattleAction.get(action).chance(unit, enemies);
    }

    private void startAutoRoll(int action) {
        if (!exposed || UiApplication.getUiApplication().getActiveScreen() != this
                || state == STATE_ROLLING || state == STATE_FINISHED
                || (BattleAction.get(action).effect == BattleAction.ESCAPE && !fleeAllowed))
            return;
        if (checkWinCondition()) return;
        Unit active = getActiveUnit();
        if (active == null || active.curHp <= 0) return;
        BattleAction definition = BattleAction.get(action);
        if (!definition.available(active)) return;
        if (heroes.contains(active) && !actionMenu.contains(definition)) return;
        if (definition.target == BattleAction.ALLY
                && (selectedTargetIndex < 0 || selectedTargetIndex >= heroes.size()
                    || !definition.validTarget(active, (Unit) heroes.elementAt(selectedTargetIndex)))) return;
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

    private Unit resolveActionTarget(Unit active) {
        if (action().target == BattleAction.SELF) return active;
        if (!action().selectsOneTarget()) return null;
        if (heroes.contains(active)) return (Unit) targetUnits().elementAt(selectedTargetIndex);
        Vector liveHeroes = liveHeroesScratch;
        liveHeroes.removeAllElements();
        for (int i = 0; i < heroes.size(); i++) {
            Unit hero = (Unit) heroes.elementAt(i);
            if (hero.curHp > 0) liveHeroes.addElement(hero);
        }
        Unit target = tauntTarget();
        if (target == null && active.name.equals("Wolf")) {
            // Reservoir sampling gives tied HP ratios equal probability without allocation.
            int ties = 0;
            for (int i = 0; i < liveHeroes.size(); i++) {
                Unit candidate = (Unit) liveHeroes.elementAt(i);
                long ratio = target == null ? -1 : (long) candidate.curHp * target.getMaxHp()
                        - (long) target.curHp * candidate.getMaxHp();
                if (target == null || ratio < 0) {
                    target = candidate;
                    ties = 1;
                } else if (ratio == 0 && rand.nextInt(++ties) == 0) target = candidate;
            }
        }
        if (target == null && (active.name.equals("Goblin") || active.name.equals("Goblin Captain"))) {
            int index = enemies.indexOf(active);
            Unit attacker = index < 0 ? null : lastAttackers[index];
            if (attacker != null && attacker.curHp > 0 && heroes.contains(attacker)) target = attacker;
        }
        if (target == null && !liveHeroes.isEmpty())
            target = (Unit) liveHeroes.elementAt(rand.nextInt(liveHeroes.size()));
        return target;
    }

    private void rememberAttack(Unit actor, BattleAction definition, Unit selected) {
        if (!heroes.contains(actor) || definition.effect != BattleAction.DAMAGE) return;
        for (int i = 0; i < enemies.size(); i++) {
            Unit enemy = (Unit) enemies.elementAt(i);
            if (enemy.curHp > 0 && (definition.target == BattleAction.ALL_ENEMIES || enemy == selected))
                lastAttackers[i] = actor;
        }
    }

    private void finishTurn() {
        if (state != STATE_ROLLING) return;
        int successes = 0;
        for (int i = 0; i < slots.length; i++) if (slots[i] == 1) successes++;
        Unit active = getActiveUnit();
        BattleAction definition = action();
        Unit selected = resolveActionTarget(active);
        rememberAttack(active, definition, selected);
        boolean succeeded = definition.resolve(active, selected, enemies, successes, slots.length, statuses);
        if (definition.effect == BattleAction.DAMAGE && heroes.contains(active))
            active.refundAttackFocus(spentFocus, successes, slots.length);
        if (succeeded && definition.effect == BattleAction.ESCAPE) {
            finishCombat(RESULT_FLEE);
            return;
        }
        if (succeeded && definition.effect == BattleAction.PROVOKE) {
            tauntingHeroes.removeElement(active);
            tauntingHeroes.addElement(active);
        }
        state = STATE_ACTION;
        if (!checkWinCondition()) nextTurn();
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

    public VictoryScreen createVictoryScreen() {
        if (state != STATE_FINISHED || finishedResult != RESULT_WIN)
            throw new IllegalStateException("Victory rewards require a resolved win");
        if (victoryScreen != null)
            return victoryScreen;
        long gold = 0;
        long experience = 0;
        for (int i = 0; i < enemies.size(); i++) {
            Unit enemy = (Unit) enemies.elementAt(i);
            gold += enemy.getVictoryGold();
            experience += enemy.getVictoryExperience();
        }
        Vector recipients = new Vector();
        // Remainder coins follow party order, independent of the encounter initiator.
        for (int i = 0; i < party.members.size(); i++) {
            Unit member = (Unit) party.members.elementAt(i);
            if (heroes.contains(member)) recipients.addElement(member);
        }
        if (recipients.isEmpty())
            throw new IllegalStateException("Victory has no party participants");
        long share = gold / recipients.size();
        long remainder = gold % recipients.size();
        // Validate the whole award before modifying any recipient.
        for (int i = 0; i < recipients.size(); i++) {
            Unit member = (Unit) recipients.elementAt(i);
            long coins = share + (i < remainder ? 1 : 0);
            if (coins > Long.MAX_VALUE - member.getGold()
                    || experience > Long.MAX_VALUE - member.getExperience())
                throw new IllegalStateException("Reward balance overflow");
        }
        Vector awards = new Vector();
        for (int i = 0; i < recipients.size(); i++) {
            Unit member = (Unit) recipients.elementAt(i);
            int previousLevel = member.getLevel();
            long coins = share + (i < remainder ? 1 : 0);
            member.addGold(coins);
            member.addExperience(experience);
            awards.addElement(member.name + ": +" + coins + " gold, +" + experience + " XP"
                    + "  L" + previousLevel + (member.getLevel() != previousLevel
                    ? " -> L" + member.getLevel() : "")
                    + "  XP " + member.getExperience() + "/" + member.getExperienceToNextLevel());
        }
        victoryScreen = new VictoryScreen(heroes, enemies, gold, experience, awards);
        return victoryScreen;
    }

    private void finishCombat(int result) {
        if (state == STATE_FINISHED)
            return;
        state = STATE_FINISHED;
        tauntingHeroes.removeAllElements();
        statuses.clear();
        for (int i = 0; i < lastAttackers.length; i++) lastAttackers[i] = null;
        finishedResult = result;
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
        if (state == STATE_TARGET || state == STATE_CONFIRM) {
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
        navigationPressed = false;
        touchActive = false;
        exposed = true;
        resumeUpdates();
    }

    protected void onObscured() {
        exposed = false;
        navigationPressed = false;
        touchActive = false;
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
        // A turn-start effect can KO its owner; advance without letting it act.
        do {
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
            if (prepareCurrentTurn()) break;
            if (state == STATE_FINISHED) return;
        } while (true);
        resumeUpdates();
        invalidate();
    }

    private boolean prepareCurrentTurn() {
        Unit next = getActiveUnit();
        tauntingHeroes.removeElement(next);
        statuses.beginTurn(next);
        if (checkWinCondition() || next.curHp <= 0) return false;
        next.collectBattleActions(actionMenu, fleeAllowed);
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
        return true;
    }

    protected boolean keyRepeat(int keycode, int time) {
        // Holding confirm must not select a target and immediately spend an action.
        return true;
    }
    private boolean handleCombatKey(int key) {
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
                beginTargetSelection();
            } else {
                startAutoRoll(currentAction);
            }
            return true;
        }
        if (state == STATE_ACTION) {
            if (key == 'w' || key == 'W' || key == '2' || key == 'a' || key == 'A' || key == '4')
                cycleAction(-1);
            else if (key == 's' || key == 'S' || key == '8' || key == 'd' || key == 'D' || key == '6')
                cycleAction(1);
            invalidate();
        }
        if (state == STATE_TARGET || state == STATE_CONFIRM) {
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
                selectTarget(direction);
            }
        }
        return true;
    }

    private void selectTarget(int direction) {
        Vector targets = targetUnits();
        if (state != STATE_TARGET || !exposed || targets.isEmpty()) return;
        for (int i = 1; i <= targets.size(); i++) {
            int index = (selectedTargetIndex + i * direction + targets.size()) % targets.size();
            if (validTarget((Unit) targets.elementAt(index))) {
                selectedTargetIndex = index;
                invalidate();
                return;
            }
        }
    }

    protected boolean keyDown(int keycode, int time) {
        int key = Keypad.key(keycode);
        if (key == Keypad.KEY_ESCAPE || key == Keypad.KEY_SPACE || key == Keypad.KEY_ENTER)
            return handleCombatKey(key);
        return super.keyDown(keycode, time);
    }

    protected boolean keyChar(char c, int status, int time) {
        return handleCombatKey(c);
    }

    protected boolean navigationMovement(int dx, int dy, int status, int time) {
        if (!exposed || UiApplication.getUiApplication().getActiveScreen() != this) return true;
        if (state == STATE_ACTION && (dx != 0 || dy != 0)) {
            cycleAction((dy != 0 ? dy : dx) > 0 ? 1 : -1);
            invalidate();
        } else if (state == STATE_TARGET || state == STATE_CONFIRM) {
            if (dx != 0) handleCombatKey(dx > 0 ? 'p' : 'o');
            if (dy != 0 && state == STATE_TARGET) selectTarget(dy < 0 ? -1 : 1);
        }
        return true;
    }

    protected boolean navigationClick(int status, int time) {
        if (!navigationPressed) {
            navigationPressed = true;
            handleCombatKey(Keypad.KEY_ENTER);
        }
        return true;
    }

    protected boolean navigationUnclick(int status, int time) {
        navigationPressed = false;
        return true;
    }

    protected boolean touchEvent(TouchEvent event) {
        int x = event.getX(1), y = event.getY(1);
        if (event.getEvent() == TouchEvent.CANCEL || event.getX(2) >= 0) {
            touchActive = false;
            return true;
        }
        if (event.getEvent() == TouchEvent.DOWN) {
            touchActive = exposed && x >= 0 && y >= 0;
            touchDragged = false;
            touchStartX = x;
            touchStartY = y;
        } else if (touchActive && (event.getEvent() == TouchEvent.MOVE || event.getEvent() == TouchEvent.UP)) {
            if (x < 0 || y < 0 || Math.abs(x - touchStartX) > 8 || Math.abs(y - touchStartY) > 8)
                touchDragged = true;
            if (event.getEvent() == TouchEvent.UP) {
                touchActive = false;
                if (touchDragged || !exposed) return true;
                if (state == STATE_ACTION && x >= menuLeft() && x < menuLeft() + menuWidth()
                        && y >= 93 && y < 93 + visibleActionRows() * 32) {
                    currentAction = actionAt(firstActionRow() + (y - 93) / 32);
                    beginTargetSelection();
                    return true;
                }
                Vector targets = targetUnits();
                int panel = Math.min(110, getWidth() / 4);
                boolean onTargetSide = action().target == BattleAction.ALLY ? x >= 0 && x < panel
                        : x >= getWidth() - panel && x < getWidth();
                if (state == STATE_TARGET && onTargetSide
                        && y >= battleRowY(0) && y < battleRowY(targets.size()) && y < getHeight() - 60) {
                    int index = (y - battleRowY(0)) / battleRowStep();
                    if (y >= battleRowY(0) && index >= 0 && index < targets.size()
                            && validTarget((Unit) targets.elementAt(index))) {
                        selectedTargetIndex = index;
                        invalidate();
                    }
                } else if (y >= getHeight() - 60 && y < getHeight() && x >= controlsLeft() && x < getWidth()) {
                    if (state == STATE_ACTION) {
                        // Confirm the highlighted menu item through the shared path.
                        handleCombatKey(Keypad.KEY_ENTER);
                    } else if (state == STATE_TARGET || state == STATE_CONFIRM) {
                        if (y >= getHeight() - 22 && controlColumn(x, 3) == 0) handleCombatKey('o');
                        else if (y >= getHeight() - 22 && controlColumn(x, 3) == 1) handleCombatKey('p');
                        else handleCombatKey(Keypad.KEY_ENTER);
                    }
                }
            }
        }
        return true;
    }
}
