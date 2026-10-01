package com.blackberry.cave;

import java.util.Vector;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.container.MainScreen;

// Snapshot labels: dismissing or repainting this screen never applies rewards.
public final class VictoryScreen extends MainScreen {
    private boolean closed;

    public void markCampaignComplete() {
        setTitle("Campaign Victory");
        insert(new LabelField("CAMPAIGN COMPLETE - the Goblin Captain has fallen!"), 0);
    }
    public VictoryScreen(Vector heroes, Vector enemies, long gold, long experience, Vector awards) {
        setTitle("Victory");
        add(new LabelField("Enemies defeated: " + enemies.size()));
        for (int i = 0; i < enemies.size(); i++)
            add(new LabelField(((Unit) enemies.elementAt(i)).name
                    + " L" + ((Unit) enemies.elementAt(i)).getLevel()));
        add(new LabelField("Gold earned: " + gold + " (shared among participants)"));
        add(new LabelField("Experience: " + experience + " per participant"));
        for (int i = 0; i < awards.size(); i++)
            add(new LabelField((String) awards.elementAt(i)));
        add(new LabelField("Party condition:"));
        for (int i = 0; i < heroes.size(); i++) {
            Unit hero = (Unit) heroes.elementAt(i);
            add(new LabelField(hero.name + "  " + (hero.curHp <= 0
                    ? "KO" : "HP " + hero.curHp + "/" + hero.getMaxHp())));
        }
        ButtonField continueButton = new ButtonField("Continue", ButtonField.CONSUME_CLICK);
        continueButton.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                close();
            }
        });
        add(continueButton);
    }

    protected boolean keyChar(char c, int status, int time) {
        if (c == ' ' || c == Keypad.KEY_ENTER) {
            close();
            return true;
        }
        return super.keyChar(c, status, time);
    }

    public void close() { onClose(); }

    public boolean onClose() {
        if (!closed) {
            closed = true;
            UiApplication.getUiApplication().popScreen(this);
        }
        return true;
    }
}
