package com.blackberry.cave;

import java.util.Vector;

public class Party {
    public Vector members;
    private Vector worldTurnOrder;
    private Vector revivedThisRound = new Vector();
    private int turnIndex = 0;
    private int worldRound = 0;

    void writeSave(java.io.DataOutputStream out) throws java.io.IOException {
        out.writeInt(members.size());
        for (int i = 0; i < members.size(); i++) ((Unit) members.elementAt(i)).writeSave(out);
        out.writeInt(worldRound); out.writeInt(turnIndex); out.writeInt(worldTurnOrder.size());
        for (int i = 0; i < worldTurnOrder.size(); i++)
            out.writeInt(members.indexOf(worldTurnOrder.elementAt(i)));
        out.writeInt(revivedThisRound.size());
        for (int i = 0; i < revivedThisRound.size(); i++)
            out.writeInt(members.indexOf(revivedThisRound.elementAt(i)));
    }

    private Party() { members = new Vector(); }

    static Party readSave(java.io.DataInputStream in, HexMap map, HexRenderer renderer)
            throws java.io.IOException {
        Party party = new Party();
        if (in.readInt() != 4) throw new java.io.IOException("Invalid party size");
        String[] names = { "Thief", "Mage", "Nun", "Warrior" };
        for (int i = 0; i < 4; i++) {
            Unit unit = Unit.readSave(in, map, renderer);
            if (!unit.name.equals(names[i])) throw new java.io.IOException("Invalid party member");
            party.members.addElement(unit);
        }
        party.worldRound = in.readInt(); party.turnIndex = in.readInt();
        int count = in.readInt();
        if (party.worldRound < 1 || count < 1 || count > 4
                || party.turnIndex < 0 || party.turnIndex >= count)
            throw new java.io.IOException("Invalid turn");
        party.worldTurnOrder = new Vector();
        for (int i = 0; i < count; i++) {
            int index = in.readInt();
            if (index < 0 || index >= 4 || party.worldTurnOrder.contains(party.members.elementAt(index)))
                throw new java.io.IOException("Invalid turn order");
            party.worldTurnOrder.addElement(party.members.elementAt(index));
        }
        count = in.readInt();
        if (count < 0 || count > 4) throw new java.io.IOException("Invalid revive count");
        for (int i = 0; i < count; i++) {
            int index = in.readInt();
            if (index < 0 || index >= 4 || party.revivedThisRound.contains(party.members.elementAt(index)))
                throw new java.io.IOException("Invalid revive list");
            party.revivedThisRound.addElement(party.members.elementAt(index));
        }
        if (party.getActiveMember() == null) throw new java.io.IOException("No active member");
        return party;
    }

    public Party(HexMap map) {
        members = new Vector();
        HexTile startPos = map.getStartTile();
        if (startPos == null)
            throw new IllegalStateException("Map has no party start tile");

        Unit warrior = new Unit("Warrior", "warrior.png", startPos.q, startPos.r);
        Item sword = Item.create(Item.ID_IRON_SWORD);
        warrior.setEquipment(Item.TYPE_WEAPON, sword);
        Item plate = Item.create(Item.ID_PLATE_ARMOR);
        warrior.setEquipment(Item.TYPE_ARMOR, plate);

        Unit mage = new Unit("Mage", "mage.png", startPos.q, startPos.r);
        Item staff = Item.create(Item.ID_MAGIC_STAFF);
        mage.setEquipment(Item.TYPE_WEAPON, staff);

        Unit nun = new Unit("Nun", "nun.png", startPos.q, startPos.r);
        nun.setEquipment(Item.TYPE_WEAPON, Item.create(Item.ID_HEALING_STAFF));
        Item cross = Item.create(Item.ID_HOLY_CROSS);
        nun.setEquipment(Item.TYPE_ACCESSORY, cross);

        Unit thief = new Unit("Thief", "thief.png", startPos.q, startPos.r);
        Item dagger = Item.create(Item.ID_DAGGER);
        thief.setEquipment(Item.TYPE_WEAPON, dagger);

        members.addElement(thief);
        members.addElement(mage);
        members.addElement(nun);
        members.addElement(warrior);

        // Initial equipment starts with full effective resources.
        for (int i = 0; i < members.size(); i++) {
            Unit member = (Unit) members.elementAt(i);
            member.restoreResourcesToFull();
            member.addItem(Item.create(Item.ID_RESURRECTION_HERB));
        }
        beginRound();
    }

    // Read-only: drawing the HUD must never advance the world round.
    public Unit getActiveMember() {
        for (int i = turnIndex; i < worldTurnOrder.size(); i++) {
            Unit member = (Unit) worldTurnOrder.elementAt(i);
            if (member.curHp > 0 && !revivedThisRound.contains(member))
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
                && (((Unit) worldTurnOrder.elementAt(turnIndex)).curHp <= 0
                    || revivedThisRound.contains(worldTurnOrder.elementAt(turnIndex))))
            turnIndex++;
        if (turnIndex >= worldTurnOrder.size())
            beginRound();
    }

    private void beginRound() {
        worldRound++;
        revivedThisRound.removeAllElements();
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

    public int getEnemyLevel() {
        long total = 0;
        for (int i = 0; i < members.size(); i++)
            total += ((Unit) members.elementAt(i)).getLevel();
        return members.size() == 0 ? 1
                : (int) ((total + members.size() - 1) / members.size());
    }

    public boolean transferGold(Unit sender, Unit recipient, long amount) {
        return members.contains(sender) && members.contains(recipient)
                && sender.transferGoldTo(recipient, amount);
    }

    public boolean transferItem(Unit sender, Unit recipient, Item item) {
        return members.contains(sender) && members.contains(recipient)
                && sender.transferItemTo(recipient, item);
    }

    private boolean canShop(Unit member, HexTile town) {
        return members.contains(member) && town != null && town.type == HexTile.TYPE_TOWN
                && member.curHp > 0 && !member.isMoving
                && member.q == town.q && member.r == town.r;
    }

    public boolean buyAtTown(Unit buyer, HexTile town, int itemId) {
        int price = Item.getBuyPrice(itemId);
        if (!canShop(buyer, town) || price <= 0 || !buyer.hasInventorySpace()
                || buyer.getGold() < price) return false;
        Item item = Item.create(itemId);
        if (!buyer.addItem(item)) return false;
        if (!buyer.spendGold(price)) {
            buyer.removeInventoryItem(item);
            return false;
        }
        return true;
    }

    public boolean sellAtTown(Unit seller, HexTile town, Item item) {
        int price = item == null ? 0 : Item.getSellPrice(item.id);
        if (!canShop(seller, town) || price <= 0 || !seller.ownsItem(item)
                || seller.getGold() > Long.MAX_VALUE - price) return false;
        if (!seller.removeInventoryItem(item)) return false;
        seller.addGold(price);
        return true;
    }

    public boolean useHealingHerb(Unit user, Unit target, Item item) {
        if (user != getActiveMember() || !members.contains(target) || user.isMoving
                || target.isMoving || user.curHp <= 0 || target.curHp <= 0
                || user.q != target.q || user.r != target.r
                || item == null || item.id != Item.ID_HEALING_HERB
                || !user.ownsItem(item) || target.curHp >= target.getMaxHp()) return false;
        if (!user.consumeItem(item)) return false;
        target.curHp = (int) Math.min((long) target.getMaxHp(), (long) target.curHp + 10L);
        return true;
    }

    int healWithWeapon(Unit user, Unit target, int focus, java.util.Random random) {
        if (user != getActiveMember() || !members.contains(target)
                || !HealingAction.eligible(user, target) || user.isMoving || target.isMoving
                || user.q != target.q || user.r != target.r || random == null
                || focus < 0 || focus > Math.min(user.curFocus, HealingAction.SLOTS))
            return -1;
        int chance = Unit.focusedChance(user.getIntelligence(), focus);
        user.curFocus -= focus;
        int successes = 0;
        for (int i = 0; i < HealingAction.SLOTS; i++)
            if (i < focus || random.nextInt(100) < chance) successes++;
        HealingAction.apply(user, target, successes);
        cycleMember();
        return successes;
    }

    public boolean useResurrectionHerb(Unit user, Unit target, Item item, HexMap map) {
        if (user != getActiveMember() || !members.contains(target) || user == target
                || user.isMoving || target.isMoving || user.curHp <= 0 || target.curHp > 0
                || user.q != target.q || user.r != target.r
                || item == null || item.id != Item.ID_RESURRECTION_HERB
                || !user.ownsItem(item) || map == null) return false;
        HexTile tile = map.getTile(user.q, user.r);
        if (tile == null || tile.type == HexTile.TYPE_TOWN || !user.consumeItem(item)) return false;
        target.curHp = (int) (((long) target.getMaxHp() + 1L) / 2L);
        target.curFocus = 0;
        target.curMove = 0;
        target.cancelMovement();
        revivedThisRound.addElement(target);
        cycleMember();
        return true;
    }

    public long getInnPrice(Unit member) {
        return 5L * member.getLevel();
    }

    public boolean restAtTown(Unit member, HexTile town) {
        if (!members.contains(member) || town == null || town.type != HexTile.TYPE_TOWN
                || member.curHp <= 0 || member.isMoving || member.q != town.q || member.r != town.r
                || (member.curHp == member.getMaxHp() && member.curFocus == member.getMaxFocus()))
            return false;
        if (!member.spendGold(getInnPrice(member)))
            return false;
        member.curHp = member.getMaxHp();
        member.curFocus = member.getMaxFocus();
        return true;
    }
}
