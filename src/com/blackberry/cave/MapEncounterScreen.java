package com.blackberry.cave;

import java.util.Random;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.TouchEvent;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.container.MainScreen;

public final class MapEncounterScreen extends MainScreen {
    private static final Font TITLE_FONT = Font.getDefault().derive(Font.BOLD, 22);
    private static final Font BODY_FONT = Font.getDefault().derive(Font.PLAIN, 16);
    public static final int ATTACK = 0;
    public static final int AMBUSH = 1;
    public static final int FLEE = 2;

    private static final String[] ACTION_LABELS = { "ATTACK", "AMBUSH", "FLEE" };

    public interface ResultListener {
        void encounterFinished(MapEncounterScreen screen, int action, boolean success);
    }

    private static final int CHOICE = 0;
    private static final int ROLLING = 1;
    private static final int RESULT = 2;
    private static final int FINISHED = 3;
    private int state = CHOICE;
    private int selectedAction = ATTACK;
    private final Unit hero;
    private int plannedFocus;
    private int spentFocus;
    private int rollChance;
    private final String enemyName;
    private final int successChance;
    private final int requiredSuccesses;
    private int[] slots;
    private int rollingIndex = 0;
    private boolean success = false;
    private boolean exposed = false;
    private boolean navigationPressed;
    private boolean touchActive;
    private boolean touchDragged;
    private int touchStartX, touchStartY;
    private ResultListener listener;
    private Random random;
    private UiTickTimer timer = new UiTickTimer(new Runnable() {
        public void run() {
            nextSlot();
        }
    });

    public MapEncounterScreen(Unit hero, Unit enemy, int chance, int slotCount,
            int requiredSuccesses, ResultListener listener, Random random) {
        super(MainScreen.NO_VERTICAL_SCROLL | MainScreen.NO_HORIZONTAL_SCROLL);
        this.random = random;
        this.hero = hero;
        enemyName = enemy.name + " L" + enemy.getLevel();
        successChance = chance;
        this.requiredSuccesses = requiredSuccesses;
        this.listener = listener;
        slots = new int[slotCount];
        for (int i = 0; i < slots.length; i++)
            slots[i] = -1;
    }

    protected void paint(Graphics g) {
        g.setColor(0x111820);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.WHITE);
        g.setFont(TITLE_FONT);
        g.drawText("ENCOUNTER: " + enemyName, 20, 20);
        g.setFont(BODY_FONT);
        g.drawText(hero.name + " - choose an action", 20, 55);
        for (int i = 0; i < ACTION_LABELS.length; i++) {
            int y = 100 + i * 42;
            g.setColor(selectedAction == i ? Color.YELLOW : Color.WHITE);
            g.drawText((selectedAction == i ? "> " : "  ") + ACTION_LABELS[i], 30, y);
        }
        g.setColor(Color.WHITE);
        g.drawText("Roll: " + (state == CHOICE ? Unit.focusedChance(selectedChance(), selectedAction == ATTACK ? 0 : plannedFocus) : rollChance) + "% each, need " + requiredSuccesses
                + "/" + slots.length, 20, 240);
        if (state == ROLLING || state == RESULT) {
            for (int i = 0; i < slots.length; i++) {
                int x = 25 + i * 55;
                g.setColor(slots[i] == 1 ? Color.GREEN : slots[i] == 0 ? Color.RED : Color.GRAY);
                g.drawRect(x, 278, 42, 36);
                g.drawText(slots[i] == 1 ? "OK" : slots[i] == 0 ? "X" : "?", x + 8, 284);
            }
        }
        g.setColor(Color.WHITE);
        if (state == CHOICE && selectedAction != ATTACK)
            g.drawText("Focus: " + hero.curFocus + "  Use: " + plannedFocus + "  [P/O] +/-", 20, 280);
        if (state == RESULT)
            g.drawText((success ? "SUCCESS" : "FAILED") + " - [ENTER] continue", 20, 330);
        else if (state == ROLLING)
            g.drawText("ROLLING...", 20, 330);
        else
            g.drawText("[W/S] select  [ENTER] confirm", 20, 330);
    }

    private int selectedChance() {
        return selectedAction == FLEE ? hero.escapeChance(successChance) : successChance;
    }

    private void confirm() {
        if (!exposed || UiApplication.getUiApplication().getActiveScreen() != this)
            return;
        if (state == RESULT) {
            finish(success);
        } else if (state == CHOICE) {
            if (selectedAction == ATTACK) {
                finish(true);
            } else {
                spentFocus = Math.min(plannedFocus, Math.min(hero.curFocus, slots.length));
                rollChance = Unit.focusedChance(selectedChance(), spentFocus);
                hero.curFocus -= spentFocus;
                plannedFocus = 0;
                state = ROLLING;
                resumeUpdates();
                invalidate();
            }
        }
    }

    private void nextSlot() {
        if (!exposed || state != ROLLING || UiApplication.getUiApplication().getActiveScreen() != this)
            return;
        slots[rollingIndex] = rollingIndex < spentFocus || random.nextInt(100) < rollChance ? 1 : 0;
        rollingIndex++;
        if (rollingIndex == slots.length) {
            pauseUpdates();
            int successes = 0;
            for (int i = 0; i < slots.length; i++)
                successes += slots[i];
            success = successes >= requiredSuccesses;
            state = RESULT;
        }
        invalidate();
    }

    private void finish(boolean successful) {
        if (state == FINISHED)
            return;
        state = FINISHED;
        pauseUpdates();
        UiApplication.getUiApplication().popScreen(this);
        ResultListener resultListener = listener;
        listener = null;
        if (resultListener != null)
            resultListener.encounterFinished(this, selectedAction, successful);
    }

    private void select(int direction) {
        if (state == CHOICE) {
            selectedAction = (selectedAction + direction + 3) % 3;
            plannedFocus = 0;
            invalidate();
        }
    }

    protected boolean keyChar(char c, int status, int time) {
        if (c == Keypad.KEY_ENTER || c == ' ')
            confirm();
        else if ((c == 'p' || c == 'P' || c == 'o' || c == 'O') && state == CHOICE && selectedAction != ATTACK) {
            int delta = c == 'p' || c == 'P' ? 1 : -1;
            plannedFocus = Math.max(0, Math.min(plannedFocus + delta, Math.min(hero.curFocus, slots.length)));
            invalidate();
        }
        else if (c == 'w' || c == 'W' || c == '2')
            select(-1);
        else if (c == 's' || c == 'S' || c == '8')
            select(1);
        return true;
    }

    protected boolean keyRepeat(int keycode, int time) {
        return true;
    }

    protected boolean navigationMovement(int dx, int dy, int status, int time) {
        if (!exposed || UiApplication.getUiApplication().getActiveScreen() != this) return true;
        if (dy != 0)
            select(dy > 0 ? 1 : -1);
        if (dx != 0 && state == CHOICE && selectedAction != ATTACK) {
            plannedFocus = Math.max(0, Math.min(plannedFocus + (dx > 0 ? 1 : -1),
                    Math.min(hero.curFocus, slots.length)));
            invalidate();
        }
        return true;
    }

    protected boolean navigationClick(int status, int time) {
        if (!navigationPressed) {
            navigationPressed = true;
            confirm();
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
            if (event.getEvent() != TouchEvent.UP) return true;
            touchActive = false;
            if (touchDragged || !exposed) return true;
            if (state == CHOICE) {
                if (x >= 20 && x < getWidth() - 20 && y >= 100 && y < 226) {
                    int tappedAction = (y - 100) / 42;
                    if (tappedAction != selectedAction) plannedFocus = 0;
                    selectedAction = tappedAction;
                    confirm();
                }
            } else if (state == RESULT) {
                confirm();
            }
        }
        return true;
    }

    public void close() { onClose(); }
    public boolean onClose() { return false; }
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
    void pauseUpdates() { timer.stop(); }
    void resumeUpdates() {
        if (exposed && state == ROLLING)
            timer.start(300);
    }
}
