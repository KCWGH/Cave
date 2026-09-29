package com.blackberry.cave;

public class Item {
    public static final int TYPE_WEAPON = 0;
    public static final int TYPE_ARMOR = 1;
    public static final int TYPE_ACCESSORY = 2;

    public static final int ATTACK_STRENGTH = 0;
    public static final int ATTACK_INTELLIGENCE = 1;

    public int attackStat = ATTACK_STRENGTH;
    public int attackSlots = 3;
    public boolean focusable = true;
    public int attackDamage;
    public String name;
    public int type;
    public int strengthBonus;
    public int intelligenceBonus;
    public int speedBonus;
    public int hpBonus;
    public int focusBonus;
    public int damageBonus;

    public Item(String name, int type) {
        this.name = name;
        this.type = type;
    }
}
