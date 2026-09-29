package com.blackberry.cave;

import net.rim.device.api.system.Bitmap;
import java.util.Vector;

public class Unit {
    public String name;
    public int curHp, baseMaxHp;
    public int curFocus, baseMaxFocus;
    public int q, r;
    public Bitmap sprite, miniSprite, hudSprite;
    public int strength, intelligence, speed, curMove;
    private Item weapon, armor, accessory;
    public int baseAttackStat = Item.ATTACK_STRENGTH;
    public int baseAttackDamage = 8;

    public boolean isMoving = false;
    public int worldX, worldY;
    public Vector movePath;
    private int targetWX, targetWY;
    private int[] scratch = new int[2];
    private int moveSpeed = 10;

    public Unit(String name, String spritePath, int q, int r) {
        this.name = name;
        this.q = q;
        this.r = r;
        this.sprite = Bitmap.getBitmapResource(spritePath);
        this.miniSprite = Bitmap.getBitmapResource("mini_" + spritePath);
        this.hudSprite = Bitmap.getBitmapResource("hud_" + spritePath);
        this.baseMaxHp = 20;
        this.curHp = 20;
        this.baseMaxFocus = 3;
        this.curFocus = 3;

        if (name.equals("Warrior")) {
            this.speed = 4;
            this.strength = 80;
            this.intelligence = 40;
            this.baseAttackDamage = 14;
        } else if (name.equals("Thief")) {
            this.speed = 6;
            this.strength = 65;
            this.intelligence = 60;
            this.baseAttackDamage = 6;
        } else if (name.equals("Mage")) {
            this.speed = 5;
            this.strength = 40;
            this.intelligence = 80;
            this.baseAttackDamage = 14;
        } else if (name.equals("Slime")) {
            this.speed = 2;
            this.strength = 65;
            this.intelligence = 30;
            this.baseAttackDamage = 10;
        } else if (name.equals("Wolf")) {
            this.speed = 7;
            this.strength = 75;
            this.intelligence = 20;
            this.baseAttackDamage = 8;
        } else if (name.equals("Goblin")) {
            this.speed = 5;
            this.strength = 65;
            this.intelligence = 50;
            this.baseAttackDamage = 7;
        } else {
            this.speed = 5;
            this.strength = 50;
            this.intelligence = 75;
        }

        if (name.equals("Nun"))
            baseAttackStat = Item.ATTACK_INTELLIGENCE;
        this.curMove = Math.max(0, Math.min(9, getSpeed()));
    }

    public Item getEquipment(int slot) {
        if (slot == Item.TYPE_WEAPON) return weapon;
        if (slot == Item.TYPE_ARMOR) return armor;
        if (slot == Item.TYPE_ACCESSORY) return accessory;
        throw new IllegalArgumentException("Unknown equipment slot");
    }

    public void setEquipment(int slot, Item item) {
        // Validate before changing equipment or resources.
        getEquipment(slot);
        if (item != null && item.type != slot)
            throw new IllegalArgumentException("Item does not fit equipment slot");
        if (slot == Item.TYPE_WEAPON && item != null
                && item.attackStat != Item.ATTACK_STRENGTH && item.attackStat != Item.ATTACK_INTELLIGENCE)
            throw new IllegalArgumentException("Unknown weapon attack stat");
        if (slot == Item.TYPE_WEAPON && item != null && item.attackSlots < 1)
            throw new IllegalArgumentException("Weapon requires at least one attack slot");
        if (slot == Item.TYPE_WEAPON) weapon = item;
        else if (slot == Item.TYPE_ARMOR) armor = item;
        else accessory = item;
        clampResources();
    }

    public void clampResources() {
        curHp = Math.max(0, Math.min(curHp, getMaxHp()));
        curFocus = Math.max(0, Math.min(curFocus, getMaxFocus()));
    }

    // Explicit healing also revives, matching the current town service.
    public void restoreResourcesToFull() {
        curHp = getMaxHp();
        curFocus = getMaxFocus();
    }

    public int getMaxHp() {
        int bonus = (weapon != null ? weapon.hpBonus : 0) + (armor != null ? armor.hpBonus : 0)
                + (accessory != null ? accessory.hpBonus : 0);
        return Math.max(1, baseMaxHp + bonus);
    }

    public int getMaxFocus() {
        int bonus = (weapon != null ? weapon.focusBonus : 0) + (armor != null ? armor.focusBonus : 0)
                + (accessory != null ? accessory.focusBonus : 0);
        return Math.max(0, Math.min(9, baseMaxFocus + bonus));
    }

    public int getStrength() {
        int bonus = (weapon != null ? weapon.strengthBonus : 0) + (armor != null ? armor.strengthBonus : 0)
                + (accessory != null ? accessory.strengthBonus : 0);
        return Math.max(0, Math.min(95, strength + bonus));
    }

    public int getIntelligence() {
        int bonus = (weapon != null ? weapon.intelligenceBonus : 0) + (armor != null ? armor.intelligenceBonus : 0)
                + (accessory != null ? accessory.intelligenceBonus : 0);
        return Math.max(0, Math.min(95, intelligence + bonus));
    }

    public int getSpeed() {
        int bonus = (weapon != null ? weapon.speedBonus : 0) + (armor != null ? armor.speedBonus : 0)
                + (accessory != null ? accessory.speedBonus : 0);
        return speed + bonus;
    }

    public int getAttackSlotCount() {
        return weapon != null ? weapon.attackSlots : 3;
    }

    public int getAttackStat() {
        return weapon != null ? weapon.attackStat : baseAttackStat;
    }

    public boolean canFocusAttack() {
        return weapon == null || weapon.focusable;
    }

    public int getAttackSuccessChance() {
        return getAttackStat() == Item.ATTACK_INTELLIGENCE ? getIntelligence() : getStrength();
    }

    public int getDamage() {
        int base = weapon != null ? weapon.attackDamage : baseAttackDamage;
        int bonus = (weapon != null ? weapon.damageBonus : 0) + (armor != null ? armor.damageBonus : 0)
                + (accessory != null ? accessory.damageBonus : 0);
        return Math.max(0, base + bonus);
    }

    // FTK1 accuracy bonus for one through four focused slots.
    public static int focusedChance(int baseChance, int focus) {
        int bonus = focus <= 0 ? 0 : focus == 1 ? 10 : focus == 2 ? 15 : focus == 3 ? 17 : 19;
        return Math.max(0, Math.min(100, baseChance + bonus));
    }

    public boolean spendFocusForMovement() {
        if (curHp <= 0 || isMoving || curFocus <= 0 || curMove >= 9)
            return false;
        curFocus--;
        curMove++;
        return true;
    }

    public void resetTurn() {
        this.curMove = curHp > 0 ? Math.max(0, Math.min(9, getSpeed())) : 0;
        this.isMoving = false;
        this.movePath = null;
    }

    public void setPath(Vector path, HexRenderer renderer) {
        this.movePath = path;
        this.isMoving = true;
        renderer.getHexWorldPoint(q, r, scratch);
        this.worldX = scratch[0];
        this.worldY = scratch[1];
        if (path != null && !path.isEmpty()) {
            HexTile next = (HexTile) path.elementAt(0);
            renderer.getHexWorldPoint(next.q, next.r, scratch);
            this.targetWX = scratch[0];
            this.targetWY = scratch[1];
        } else {
            isMoving = false;
        }
    }

    public void pauseMovement() {
        isMoving = false;
    }

    public void resumeMovement() {
        isMoving = movePath != null && !movePath.isEmpty() && curMove > 0;
    }

    public void cancelMovement() {
        isMoving = false;
        movePath = null;
    }

    // Returns true for every tile arrival, including intermediate path tiles.
    public boolean updateAnimation(HexRenderer renderer) {
        if (!isMoving)
            return false;
        int dx = targetWX - worldX;
        int dy = targetWY - worldY;
        int dist = (int) Math.sqrt(dx * dx + dy * dy);
        if (dist <= moveSpeed) {
            worldX = targetWX;
            worldY = targetWY;
            HexTile reached = (HexTile) movePath.elementAt(0);
            this.q = reached.q;
            this.r = reached.r;
            movePath.removeElementAt(0);
            if (movePath.isEmpty()) {
                isMoving = false;
                return true;
            } else {
                HexTile next = (HexTile) movePath.elementAt(0);
                renderer.getHexWorldPoint(next.q, next.r, scratch);
                this.targetWX = scratch[0];
                this.targetWY = scratch[1];
                return true;
            }
        } else {
            worldX += (dx * moveSpeed) / dist;
            worldY += (dy * moveSpeed) / dist;
            return false;
        }
    }
}
