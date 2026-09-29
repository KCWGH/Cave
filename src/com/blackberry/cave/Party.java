package com.blackberry.cave;

import java.util.Vector;

public class Party {
    public Vector members;
    private Vector worldTurnOrder;
    private int turnIndex = 0;
    private int worldRound = 0;

    public Party(HexMap map) {
        members = new Vector();
        HexTile startPos = map.getStartTile();
        if (startPos == null)
            throw new IllegalStateException("Map has no party start tile");

        Unit warrior = new Unit("Warrior", "warrior.png", startPos.q, startPos.r);
        Item sword = new Item("Iron Sword", Item.TYPE_WEAPON);
        sword.attackDamage = 16;
        sword.damageBonus = 5;
        sword.strengthBonus = 2;
        warrior.setEquipment(Item.TYPE_WEAPON, sword);
        Item plate = new Item("Plate Armor", Item.TYPE_ARMOR);
        plate.hpBonus = 10;
        warrior.setEquipment(Item.TYPE_ARMOR, plate);

        Unit mage = new Unit("Mage", "mage.png", startPos.q, startPos.r);
        Item staff = new Item("Magic Staff", Item.TYPE_WEAPON);
        staff.attackDamage = 18;
        staff.damageBonus = 4;
        staff.intelligenceBonus = 4;
        staff.attackStat = Item.ATTACK_INTELLIGENCE;
        mage.setEquipment(Item.TYPE_WEAPON, staff);

        Unit nun = new Unit("Nun", "nun.png", startPos.q, startPos.r);
        Item cross = new Item("Holy Cross", Item.TYPE_ACCESSORY);
        cross.focusBonus = 2;
        nun.setEquipment(Item.TYPE_ACCESSORY, cross);

        Unit thief = new Unit("Thief", "thief.png", startPos.q, startPos.r);
        Item dagger = new Item("Dagger", Item.TYPE_WEAPON);
        dagger.attackDamage = 6;
        dagger.attackSlots = 2;
        dagger.damageBonus = 3;
        dagger.speedBonus = 2;
        thief.setEquipment(Item.TYPE_WEAPON, dagger);

        members.addElement(thief);
        members.addElement(mage);
        members.addElement(nun);
        members.addElement(warrior);

        // Initial equipment starts with full effective resources.
        for (int i = 0; i < members.size(); i++)
            ((Unit) members.elementAt(i)).restoreResourcesToFull();
        beginRound();
    }

    // Read-only: drawing the HUD must never advance the world round.
    public Unit getActiveMember() {
        for (int i = turnIndex; i < worldTurnOrder.size(); i++) {
            Unit member = (Unit) worldTurnOrder.elementAt(i);
            if (member.curHp > 0)
                return member;
        }
        return null;
    }

    public int getWorldRound() {
        return worldRound;
    }

    public int getTurnNumber() {
        return turnIndex + 1;
    }

    public int getTurnCount() {
        return worldTurnOrder.size();
    }

    // Space ends the current member's turn, rather than merely selecting a member.
    public void cycleMember() {
        if (isWiped())
            return;
        refreshTurn();
        Unit current = (Unit) worldTurnOrder.elementAt(turnIndex);
        current.curMove = 0;
        turnIndex++;
        refreshTurn();
    }

    // Called by the world after combat or before accepting world input.
    public void refreshTurn() {
        if (isWiped())
            return;
        while (turnIndex < worldTurnOrder.size()
                && ((Unit) worldTurnOrder.elementAt(turnIndex)).curHp <= 0)
            turnIndex++;
        if (turnIndex >= worldTurnOrder.size())
            beginRound();
    }

    private void beginRound() {
        worldRound++;
        turnIndex = 0;
        worldTurnOrder = new Vector();
        for (int i = 0; i < members.size(); i++) {
            Unit member = (Unit) members.elementAt(i);
            member.resetTurn();
            if (member.curHp > 0)
                worldTurnOrder.addElement(member);
        }
        // Stable descending agility order; ties retain the original party order.
        for (int i = 1; i < worldTurnOrder.size(); i++) {
            Unit member = (Unit) worldTurnOrder.elementAt(i);
            int j = i;
            while (j > 0 && ((Unit) worldTurnOrder.elementAt(j - 1)).getSpeed() < member.getSpeed()) {
                worldTurnOrder.setElementAt(worldTurnOrder.elementAt(j - 1), j);
                j--;
            }
            worldTurnOrder.setElementAt(member, j);
        }
    }

    public boolean isWiped() {
        for (int i = 0; i < members.size(); i++) {
            if (((Unit) members.elementAt(i)).curHp > 0) {
                return false;
            }
        }
        return true;
    }
}
