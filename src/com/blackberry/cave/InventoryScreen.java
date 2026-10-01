package com.blackberry.cave;

import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.ObjectChoiceField;
import net.rim.device.api.ui.container.MainScreen;

public final class InventoryScreen extends MainScreen {
    public interface ResultListener {
        void inventoryTurnEnded();
    }

    private Party party;
    private HexMap map;
    private ResultListener listener;
    private ObjectChoiceField ownerChoice;
    private ObjectChoiceField itemChoice;
    private ObjectChoiceField targetChoice;
    private ObjectChoiceField recipientChoice;
    private ObjectChoiceField slotChoice;
    private ObjectChoiceField healingFocus;
    private java.util.Random random = new java.util.Random();
    private LabelField equipment;
    private LabelField attributes;
    private LabelField status;
    private boolean closed;

    public InventoryScreen(Party party, HexMap map, ResultListener listener) {
        this.party = party;
        this.map = map;
        this.listener = listener;
        setTitle("Inventory");
        add(new LabelField("12 items per member. Equipment does not use a bag slot."));
        add(new LabelField("Only the current turn's member can use herbs. Give items on the same tile."));
        String[] names = new String[party.members.size()];
        int active = 0;
        for (int i = 0; i < names.length; i++) {
            Unit unit = (Unit) party.members.elementAt(i);
            names[i] = unit.name;
            if (unit == party.getActiveMember()) active = i;
        }
        ownerChoice = new ObjectChoiceField("Owner: ", names, active);
        ownerChoice.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { refresh(); }
        });
        add(ownerChoice);
        equipment = new LabelField("");
        add(equipment);
        attributes = new LabelField("");
        add(attributes);
        itemChoice = new ObjectChoiceField("Bag item: ", new String[] { "(empty)" }, 0);
        add(itemChoice);
        targetChoice = new ObjectChoiceField("Use on: ", names, active);
        add(targetChoice);
        healingFocus = new ObjectChoiceField("Heal Focus: ", new String[] { "0", "1", "2", "3" }, 0);
        if (map != null) {
            add(new LabelField("Weapon heal: injured living ally on same tile; 3 INT rolls; ends turn even on failure."));
            add(healingFocus);
            addButton("Heal target with equipped weapon (ends turn)", 5);
        }
        recipientChoice = new ObjectChoiceField("Give to: ", names, (active + 1) % names.length);
        add(recipientChoice);
        slotChoice = new ObjectChoiceField("Unequip: ",
                new String[] { "Weapon", "Armor", "Accessory" }, 0);
        add(slotChoice);
        addButton("Equip selected item", 0);
        addButton("Unequip selected slot", 1);
        addButton("Use selected item", 2);
        addButton("Give selected item", 3);
        status = new LabelField("");
        add(status);
        addButton("Back", 4);
        refresh();
    }

    private void addButton(String label, final int action) {
        ButtonField button = new ButtonField(label, ButtonField.CONSUME_CLICK);
        button.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { act(action); }
        });
        add(button);
    }

    private Unit selected(ObjectChoiceField choice) {
        int index = choice.getSelectedIndex();
        return index < 0 || index >= party.members.size() ? null
                : (Unit) party.members.elementAt(index);
    }

    private Item selectedItem(Unit owner) {
        int index = itemChoice.getSelectedIndex();
        return owner == null || index < 0 || index >= owner.getInventorySize()
                ? null : owner.getInventoryItem(index);
    }

    private void refresh() {
        if (equipment == null || itemChoice == null) return;
        Unit owner = selected(ownerChoice);
        if (owner == null) return;
        equipment.setText("Weapon: " + itemName(owner.getEquipment(Item.TYPE_WEAPON))
                + " | Armor: " + itemName(owner.getEquipment(Item.TYPE_ARMOR))
                + " | Accessory: " + itemName(owner.getEquipment(Item.TYPE_ACCESSORY))
                + " | Bag " + owner.getInventorySize() + "/" + Unit.INVENTORY_CAPACITY);
        attributes.setText("HP " + owner.curHp + "/" + owner.getMaxHp()
                + "  Focus " + owner.curFocus + "/" + owner.getMaxFocus()
                + "  STR " + owner.getStrength() + "  INT " + owner.getIntelligence()
                + "  SPD " + owner.getSpeed() + "  DMG " + owner.getDamage()
                + "  Slots " + owner.getAttackSlotCount()
                + (owner.canHeal() ? "  Max Heal " + owner.getMaxHealing() + " (INT, 3 slots)" : ""));
        int selectedIndex = itemChoice.getSelectedIndex();
        String[] choices = new String[Math.max(1, owner.getInventorySize())];
        if (owner.getInventorySize() == 0) choices[0] = "(empty)";
        for (int i = 0; i < owner.getInventorySize(); i++)
            choices[i] = owner.getInventoryItem(i).name;
        itemChoice.setChoices(choices);
        if (selectedIndex > 0 && selectedIndex < choices.length)
            itemChoice.setSelectedIndex(selectedIndex);
    }

    private static String itemName(Item item) {
        return item == null ? "-" : item.name;
    }

    private void act(int action) {
        if (closed) return;
        if (action == 4) { close(); return; }
        Unit owner = selected(ownerChoice);
        Item item = selectedItem(owner);
        boolean done = false;
        if (action == 5) {
            if (map == null) return;
            Unit target = selected(targetChoice);
            int before = target == null ? 0 : target.curHp;
            int successes = party.healWithWeapon(owner, target, healingFocus.getSelectedIndex(), random);
            if (successes < 0) {
                status.setText("Heal unavailable: active Nun, healing weapon, injured living ally on same tile, enough Focus.");
                return;
            }
            net.rim.device.api.ui.component.Dialog.alert("Heal: " + successes + "/3 successes. +"
                    + (target.curHp - before) + " HP. Turn ended.");
            close();
            if (listener != null) listener.inventoryTurnEnded();
            return;
        }
        if (owner != null && action == 0 && item != null)
            done = owner.equipItem(item);
        else if (owner != null && action == 1)
            done = owner.unequipItem(slotChoice.getSelectedIndex());
        else if (owner != null && action == 3 && item != null)
            done = party.transferItem(owner, selected(recipientChoice), item);
        else if (owner != null && action == 2 && item != null) {
            Unit target = selected(targetChoice);
            if (item.id == Item.ID_HEALING_HERB)
                done = party.useHealingHerb(owner, target, item);
            else if (item.id == Item.ID_RESURRECTION_HERB) {
                done = party.useResurrectionHerb(owner, target, item, map);
                if (done) {
                    close();
                    if (listener != null) listener.inventoryTurnEnded();
                    return;
                }
            }
        }
        status.setText(done ? "Done." : "Unavailable: check item, tile, turn, HP and bag space.");
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
