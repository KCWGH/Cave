package com.blackberry.cave;

import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.component.EditField;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.ObjectChoiceField;
import net.rim.device.api.ui.container.MainScreen;

public final class GoldTransferScreen extends MainScreen {
    private Party party;
    private ObjectChoiceField sender;
    private ObjectChoiceField recipient;
    private EditField amount;
    private LabelField[] balances;
    private LabelField feedback;
    private boolean closed;

    public GoldTransferScreen(Party party) {
        this.party = party;
        setTitle("Transfer Gold");
        add(new LabelField("Party members on the same tile can transfer gold."));
        String[] names = new String[party.members.size()];
        balances = new LabelField[names.length];
        int activeIndex = 0;
        for (int i = 0; i < names.length; i++) {
            Unit member = (Unit) party.members.elementAt(i);
            names[i] = member.name;
            if (member == party.getActiveMember()) activeIndex = i;
            balances[i] = new LabelField("");
            add(balances[i]);
        }
        sender = new ObjectChoiceField("From: ", names, activeIndex);
        recipient = new ObjectChoiceField("To: ", names, (activeIndex + 1) % names.length);
        amount = new EditField("Gold: ", "", 19, EditField.FILTER_NUMERIC);
        add(sender);
        add(recipient);
        add(amount);
        feedback = new LabelField("");
        ButtonField transfer = new ButtonField("Transfer", ButtonField.CONSUME_CLICK);
        transfer.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                transfer();
            }
        });
        add(transfer);
        add(feedback);
        ButtonField leave = new ButtonField("Back", ButtonField.CONSUME_CLICK);
        leave.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { close(); }
        });
        add(leave);
        refreshBalances();
    }

    private void refreshBalances() {
        for (int i = 0; i < balances.length; i++) {
            Unit member = (Unit) party.members.elementAt(i);
            balances[i].setText(member.name + ": " + member.getGold()
                    + " gold  Tile(" + member.q + "," + member.r + ")");
        }
    }

    private void transfer() {
        if (closed) return;
        Unit from = (Unit) party.members.elementAt(sender.getSelectedIndex());
        Unit to = (Unit) party.members.elementAt(recipient.getSelectedIndex());
        long value;
        try {
            value = Long.parseLong(amount.getText());
        } catch (NumberFormatException e) {
            feedback.setText("Enter a positive whole number.");
            return;
        }
        if (from == to) {
            feedback.setText("Choose a different recipient.");
        } else if (from.q != to.q || from.r != to.r) {
            feedback.setText("Both members must be on the same tile.");
        } else if (value <= 0 || value > from.getGold()) {
            feedback.setText("Enter an amount within the sender's balance.");
        } else if (!party.transferGold(from, to, value)) {
            feedback.setText("Transfer unavailable.");
        } else {
            amount.setText("");
            refreshBalances();
            feedback.setText("Transferred " + value + " gold to " + to.name + ".");
        }
    }

    // Gold transfers are immediate; the amount field is not a document to save.
    public void close() { onClose(); }

    public boolean onClose() {
        if (!closed) {
            closed = true;
            UiApplication.getUiApplication().popScreen(this);
        }
        return true;
    }
}
