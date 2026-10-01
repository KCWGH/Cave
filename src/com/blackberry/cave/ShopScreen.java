package com.blackberry.cave;

import java.util.Vector;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.ObjectChoiceField;
import net.rim.device.api.ui.container.MainScreen;

public final class ShopScreen extends MainScreen {
    private static final int[] STOCK = {
            Item.ID_HEALING_HERB, Item.ID_RESURRECTION_HERB
    };

    private final Party party;
    private final HexTile town;
    private final Vector shoppers = new Vector();
    private final Vector sellableItems = new Vector();
    private String[] stockNames;
    private ObjectChoiceField buyerChoice;
    private ObjectChoiceField buyChoice;
    private ObjectChoiceField sellChoice;
    private LabelField balance;
    private LabelField buyQuote;
    private LabelField sellQuote;
    private LabelField feedback;
    private boolean closed;

    public ShopScreen(Party party, HexTile town) {
        Unit visitor = party.getActiveMember();
        if (town == null || town.type != HexTile.TYPE_TOWN || visitor == null
                || visitor.q != town.q || visitor.r != town.r || visitor.isMoving)
            throw new IllegalArgumentException("Shop requires a resident visitor");
        this.party = party;
        this.town = town;
        setTitle("Town Shop");
        add(new LabelField("Buy and sell with each visitor's own gold and bag."));
        String[] names;
        int selected = 0;
        for (int i = 0; i < party.members.size(); i++) {
            Unit member = (Unit) party.members.elementAt(i);
            if (member.curHp <= 0 || member.isMoving || member.q != town.q || member.r != town.r)
                continue;
            if (member == visitor) selected = shoppers.size();
            shoppers.addElement(member);
        }
        if (shoppers.isEmpty()) throw new IllegalArgumentException("Shop has no visitors");
        names = new String[shoppers.size()];
        for (int i = 0; i < names.length; i++) names[i] = ((Unit) shoppers.elementAt(i)).name;
        buyerChoice = new ObjectChoiceField("Customer: ", names, selected);
        buyerChoice.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { refresh(); }
        });
        add(buyerChoice);
        balance = new LabelField("");
        add(balance);
        stockNames = new String[STOCK.length];
        for (int i = 0; i < STOCK.length; i++)
            stockNames[i] = Item.create(STOCK[i]).name;
        buyChoice = new ObjectChoiceField("Buy: ", stockNames, 0);
        buyChoice.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { refreshQuotes(); }
        });
        add(buyChoice);
        buyQuote = new LabelField("");
        add(buyQuote);
        ButtonField buy = new ButtonField("Buy selected item", ButtonField.CONSUME_CLICK);
        buy.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { buy(); }
        });
        add(buy);
        sellChoice = new ObjectChoiceField("Sell from bag: ", new String[] { "(empty)" }, 0);
        sellChoice.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { refreshQuotes(); }
        });
        add(sellChoice);
        sellQuote = new LabelField("");
        add(sellQuote);
        ButtonField sell = new ButtonField("Sell selected item", ButtonField.CONSUME_CLICK);
        sell.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { sell(); }
        });
        add(sell);
        feedback = new LabelField("");
        add(feedback);
        ButtonField leave = new ButtonField("Back to Town", ButtonField.CONSUME_CLICK);
        leave.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { close(); }
        });
        add(leave);
        refresh();
    }

    private Unit customer() {
        int index = buyerChoice.getSelectedIndex();
        return index < 0 || index >= shoppers.size() ? null : (Unit) shoppers.elementAt(index);
    }

    private Item selectedSale(Unit member) {
        int index = sellChoice.getSelectedIndex();
        return member == null || index < 0 || index >= sellableItems.size()
                ? null : (Item) sellableItems.elementAt(index);
    }

    private void refresh() {
        if (balance == null || sellChoice == null) return;
        Unit member = customer();
        if (member == null) return;
        balance.setText(member.name + ": " + member.getGold() + " gold, bag "
                + member.getInventorySize() + "/" + Unit.INVENTORY_CAPACITY);
        int oldIndex = sellChoice.getSelectedIndex();
        sellableItems.removeAllElements();
        for (int i = 0; i < member.getInventorySize(); i++) {
            Item item = member.getInventoryItem(i);
            if (Item.getSellPrice(item.id) > 0) sellableItems.addElement(item);
        }
        String[] contents = new String[Math.max(1, sellableItems.size())];
        if (sellableItems.isEmpty()) contents[0] = "(no sellable items)";
        for (int i = 0; i < sellableItems.size(); i++)
            contents[i] = ((Item) sellableItems.elementAt(i)).name;
        sellChoice.setChoices(contents);
        if (oldIndex > 0 && oldIndex < contents.length) sellChoice.setSelectedIndex(oldIndex);
        refreshQuotes();
    }

    private void refreshQuotes() {
        if (buyQuote == null || sellQuote == null || buyChoice == null || sellChoice == null)
            return;
        int buyIndex = buyChoice.getSelectedIndex();
        buyQuote.setText(buyIndex < 0 || buyIndex >= STOCK.length ? ""
                : "Cost: " + Item.getBuyPrice(STOCK[buyIndex]) + " gold.");
        Item sale = selectedSale(customer());
        sellQuote.setText(sale == null ? "Only bagged herbs can be sold."
                : "Receive: " + Item.getSellPrice(sale.id) + " gold.");
    }

    private void buy() {
        if (closed || UiApplication.getUiApplication().getActiveScreen() != this) return;
        Unit member = customer();
        int index = buyChoice.getSelectedIndex();
        if (member == null || index < 0 || index >= STOCK.length) return;
        int itemId = STOCK[index];
        if (party.buyAtTown(member, town, itemId))
            feedback.setText(member.name + " bought " + stockNames[index] + ".");
        else if (!member.hasInventorySpace())
            feedback.setText("Bag full. No gold spent.");
        else if (member.getGold() < Item.getBuyPrice(itemId))
            feedback.setText("Not enough gold. No item added.");
        else
            feedback.setText("Purchase unavailable. No gold spent.");
        refresh();
    }

    private void sell() {
        if (closed || UiApplication.getUiApplication().getActiveScreen() != this) return;
        Unit member = customer();
        Item item = selectedSale(member);
        if (item != null && party.sellAtTown(member, town, item))
            feedback.setText(member.name + " sold " + item.name + " for "
                    + Item.getSellPrice(item.id) + " gold.");
        else
            feedback.setText("Only owned bagged herbs can be sold here.");
        refresh();
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
