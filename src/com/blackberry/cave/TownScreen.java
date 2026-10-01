package com.blackberry.cave;

import java.util.Vector;
import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.component.ObjectChoiceField;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.UiApplication;

public class TownScreen extends MainScreen {
    public interface QuestActions {
        String acceptQuest(HexTile town);
        String reportQuest(HexTile town, Unit reporter);
        void sailFromPort();
    }

    private Party party;
    private HexTile town;
    private QuestCampaign quest;
    private QuestActions questActions;
    private Vector residents = new Vector();
    private Vector guests = new Vector();
    private LabelField[] statusLabels;
    private ObjectChoiceField guestChoice;
    private LabelField quote;
    private LabelField feedback;
    private LabelField questStatus;
    private boolean closed;

    public TownScreen(Party party, HexTile town, QuestCampaign quest, QuestActions questActions) {
        Unit visitor = party.getActiveMember();
        if (town == null || town.type != HexTile.TYPE_TOWN || visitor == null
                || visitor.q != town.q || visitor.r != town.r || visitor.isMoving)
            throw new IllegalArgumentException("Town entry requires a resident visitor");
        this.party = party;
        this.town = town;
        this.quest = quest;
        this.questActions = questActions;
        setTitle(town.port ? "Welcome to Port" : "Welcome to Town");
        if (town.port) {
            ButtonField sail = new ButtonField("Sailing / Choose Passengers", ButtonField.CONSUME_CLICK);
            sail.setChangeListener(new FieldChangeListener() {
                public void fieldChanged(Field field, int context) {
                    if (closed || UiApplication.getUiApplication().getActiveScreen() != TownScreen.this) return;
                    onClose();
                    TownScreen.this.questActions.sailFromPort();
                }
            });
            add(sail);
        }
        add(new LabelField("Town (" + town.q + "," + town.r + ") - " + visitor.name));
        add(new LabelField("Members at this town:"));
        int selected = 0;
        for (int i = 0; i < party.members.size(); i++) {
            Unit member = (Unit) party.members.elementAt(i);
            if (member.q != town.q || member.r != town.r) continue;
            residents.addElement(member);
            if (member.curHp > 0) {
                if (member == visitor) selected = guests.size();
                guests.addElement(member);
            }
        }
        statusLabels = new LabelField[residents.size()];
        for (int i = 0; i < statusLabels.length; i++) {
            statusLabels[i] = new LabelField("");
            add(statusLabels[i]);
        }
        String[] names = new String[guests.size()];
        for (int i = 0; i < names.length; i++) names[i] = ((Unit) guests.elementAt(i)).name;
        guestChoice = new ObjectChoiceField("Resting member: ", names, selected);
        add(guestChoice);
        quote = new LabelField("");
        add(quote);
        guestChoice.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { refreshQuote(); }
        });
        ButtonField inn = new ButtonField("Pay and Rest", ButtonField.CONSUME_CLICK);
        inn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { rest(); }
        });
        add(inn);
        feedback = new LabelField("");
        add(feedback);
        add(new LabelField("KO requires a Resurrection Herb in the field. The inn cannot revive."));
        ButtonField transfer = new ButtonField("Transfer Gold", ButtonField.CONSUME_CLICK);
        transfer.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (!closed && UiApplication.getUiApplication().getActiveScreen() == TownScreen.this)
                    UiApplication.getUiApplication().pushScreen(new GoldTransferScreen(TownScreen.this.party));
            }
        });
        add(transfer);
        ButtonField inventory = new ButtonField("Inventory", ButtonField.CONSUME_CLICK);
        inventory.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (!closed && UiApplication.getUiApplication().getActiveScreen() == TownScreen.this)
                    UiApplication.getUiApplication().pushScreen(new InventoryScreen(TownScreen.this.party, null, null));
            }
        });
        add(inventory);
        ButtonField shop = new ButtonField("Shop", ButtonField.CONSUME_CLICK);
        shop.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (!closed && UiApplication.getUiApplication().getActiveScreen() == TownScreen.this)
                    UiApplication.getUiApplication().pushScreen(new ShopScreen(TownScreen.this.party, TownScreen.this.town));
            }
        });
        add(shop);
        questStatus = new LabelField("");
        add(questStatus);
        ButtonField quests = new ButtonField("First Quest", ButtonField.CONSUME_CLICK);
        quests.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                handleQuest();
            }
        });
        add(quests);
        ButtonField leave = new ButtonField("Leave Town", ButtonField.CONSUME_CLICK);
        leave.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { close(); }
        });
        add(leave);
        refreshStatus();
        refreshQuote();
        refreshQuestStatus();
    }

    private void refreshQuestStatus() {
        questStatus.setText(quest == null || !quest.isHome(town)
                ? "No quest at this town." : quest.getStatusText());
    }

    private void handleQuest() {
        if (closed || UiApplication.getUiApplication().getActiveScreen() != this) return;
        if (quest == null || questActions == null || !quest.isHome(town)) {
            feedback.setText("No quest at this town.");
            return;
        }
        if (quest.getState() == QuestCampaign.OFFERED)
            feedback.setText(questActions.acceptQuest(town));
        else if (quest.getState() == QuestCampaign.REPORT_READY)
            feedback.setText(questActions.reportQuest(town, selectedGuest()));
        else
            feedback.setText(quest.getStatusText());
        refreshQuestStatus();
        refreshStatus();
        refreshQuote();
    }

    private Unit selectedGuest() {
        int index = guestChoice.getSelectedIndex();
        return index < 0 || index >= guests.size() ? null : (Unit) guests.elementAt(index);
    }

    private void refreshQuote() {
        Unit guest = selectedGuest();
        quote.setText(guest == null ? "No eligible member."
                : "Cost: " + party.getInnPrice(guest) + " of " + guest.name
                + "'s gold. Restore all HP and Focus.");
    }

    private void refreshStatus() {
        for (int i = 0; i < residents.size(); i++) {
            Unit member = (Unit) residents.elementAt(i);
            statusLabels[i].setText(member.name + " L" + member.getLevel() + "  "
                    + (member.curHp <= 0 ? "KO" : "HP " + member.curHp + "/" + member.getMaxHp())
                    + "  Focus " + member.curFocus + "/" + member.getMaxFocus()
                    + "  Gold " + member.getGold());
        }
    }

    private void rest() {
        if (closed || UiApplication.getUiApplication().getActiveScreen() != this) return;
        Unit guest = selectedGuest();
        if (guest == null || guest.curHp <= 0) {
            feedback.setText("The inn cannot revive a KO member.");
            return;
        }
        long price = party.getInnPrice(guest);
        if (guest.curHp == guest.getMaxHp() && guest.curFocus == guest.getMaxFocus()) {
            feedback.setText("Already fully rested. No gold spent.");
        } else if (guest.getGold() < price) {
            feedback.setText(guest.name + " needs " + price + " gold. No gold spent.");
        } else if (!party.restAtTown(guest, town)) {
            feedback.setText("That member cannot rest here. No gold spent.");
        } else {
            feedback.setText(guest.name + " rested for " + price + " gold. Turn and movement unchanged.");
        }
        refreshStatus();
        refreshQuote();
    }

    protected void onExposed() {
        super.onExposed();
        if (!closed) {
            refreshStatus();
            refreshQuote();
            refreshQuestStatus();
        }
    }

    protected boolean keyRepeat(int keycode, int time) {
        int key = Keypad.key(keycode);
        if (key == Keypad.KEY_ENTER || key == Keypad.KEY_SPACE) return true;
        return super.keyRepeat(keycode, time);
    }

    protected boolean keyChar(char c, int status, int time) {
        if (getFieldWithFocus() == guestChoice && guests.size() > 0
                && (c == 'a' || c == 'A' || c == '4' || c == 'd' || c == 'D' || c == '6')) {
            int direction = c == 'a' || c == 'A' || c == '4' ? -1 : 1;
            guestChoice.setSelectedIndex((guestChoice.getSelectedIndex() + direction
                    + guests.size()) % guests.size());
            refreshQuote();
            return true;
        }
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
        if (current == null) return;
        int index = current.getIndex();
        int count = getFieldCount();
        for (int i = 1; i < count; i++) {
            int next = (index + i * direction + count) % count;
            Field field = getField(next);
            if (field.isFocusable()) {
                field.setFocus();
                return;
            }
        }
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
