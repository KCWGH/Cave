package com.blackberry.cave;

import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.Keypad;

public class TownScreen extends MainScreen {
    private Party party;

    public TownScreen(Party party) {
        this.party = party;
        setTitle("Welcome to Town");

        add(new LabelField("How can we help you, heroes?"));

        ButtonField innBtn = new ButtonField("Inn (Heal All)", ButtonField.CONSUME_CLICK);
        innBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                for (int i = 0; i < TownScreen.this.party.members.size(); i++) {
                    Unit u = (Unit) TownScreen.this.party.members.elementAt(i);
                    u.restoreResourcesToFull();
                }
                net.rim.device.api.ui.component.Dialog.inform("Your party is fully rested!");
            }
        });
        add(innBtn);

        ButtonField shopBtn = new ButtonField("Shop (Enter)", ButtonField.CONSUME_CLICK);
        shopBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                net.rim.device.api.ui.component.Dialog.inform("The shop is currently closed.");
            }
        });
        add(shopBtn);

        ButtonField questBtn = new ButtonField("Quests (Available)", ButtonField.CONSUME_CLICK);
        questBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                net.rim.device.api.ui.component.Dialog.inform("No new quests at the moment.");
            }
        });
        add(questBtn);

        ButtonField backBtn = new ButtonField("Leave Town", ButtonField.CONSUME_CLICK);
        backBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                close();
            }
        });
        add(backBtn);
    }

    protected boolean keyChar(char c, int status, int time) {
        if (c == 'w' || c == 'W' || c == '2') {
            moveMenuFocus(-1);
            return true;
        }
        if (c == 's' || c == 'S' || c == '8') {
            moveMenuFocus(1);
            return true;
        }
        return super.keyChar(c, status, time);
    }

    private void moveMenuFocus(int direction) {
        Field current = getFieldWithFocus();
        if (current == null)
            return;
        int index = current.getIndex();
        int count = getFieldCount();

        for (int i = 1; i < count; i++) {
            int next = (index + (i * direction) + count) % count;
            Field f = getField(next);
            if (f.isFocusable() && f instanceof ButtonField) {
                f.setFocus();
                return;
            }
        }
    }
}
