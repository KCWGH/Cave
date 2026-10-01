package com.blackberry.cave;

public class Item {
    public static final int TYPE_WEAPON = 0;
    public static final int TYPE_ARMOR = 1;
    public static final int TYPE_ACCESSORY = 2;
    public static final int TYPE_CONSUMABLE = 3;

    public static final int ID_IRON_SWORD = 1;
    public static final int ID_PLATE_ARMOR = 2;
    public static final int ID_MAGIC_STAFF = 3;
    public static final int ID_HOLY_CROSS = 4;
    public static final int ID_DAGGER = 5;
    public static final int ID_HEALING_HERB = 6;
    public static final int ID_RESURRECTION_HERB = 7;
    public static final int ID_HEALING_STAFF = 8;
    public int healingPower;
    public boolean nunOnly;
    BattleAction[] battleActions;

    public static final int ATTACK_STRENGTH = 0;
    public static final int ATTACK_INTELLIGENCE = 1;

    public int attackStat = ATTACK_STRENGTH;
    public int attackSlots = 3;
    public boolean focusable = true;
    public int attackDamage;
    public String name;
    public int type;
    public int id;
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

    // A catalog ID identifies the definition; each Item object is one owned copy.
    public static Item create(int id) {
        Item item;
        switch (id) {
        case ID_IRON_SWORD:
            item = new Item("Iron Sword", TYPE_WEAPON);
            item.attackDamage = 16; item.damageBonus = 5; item.strengthBonus = 2;
            break;
        case ID_PLATE_ARMOR:
            item = new Item("Plate Armor", TYPE_ARMOR);
            item.hpBonus = 10;
            item.battleActions = new BattleAction[] { BattleAction.TAUNT };
            break;
        case ID_MAGIC_STAFF:
            item = new Item("Magic Staff", TYPE_WEAPON);
            item.attackDamage = 18; item.damageBonus = 4; item.intelligenceBonus = 4;
            item.attackStat = ATTACK_INTELLIGENCE;
            item.battleActions = new BattleAction[] { BattleAction.ARCANE_WAVE };
            break;
        case ID_HOLY_CROSS:
            item = new Item("Holy Cross", TYPE_ACCESSORY);
            item.focusBonus = 2;
            break;
        case ID_DAGGER:
            item = new Item("Dagger", TYPE_WEAPON);
            item.attackDamage = 6; item.attackSlots = 2;
            item.damageBonus = 3; item.speedBonus = 2;
            item.battleActions = new BattleAction[] { BattleAction.BLEEDING_CUT };
            break;
        case ID_HEALING_HERB:
            item = new Item("Healing Herb", TYPE_CONSUMABLE);
            break;
        case ID_RESURRECTION_HERB:
            item = new Item("Resurrection Herb", TYPE_CONSUMABLE);
            break;
        case ID_HEALING_STAFF:
            item = new Item("Healing Staff", TYPE_WEAPON);
            item.attackStat = ATTACK_INTELLIGENCE;
            item.attackDamage = 8;
            item.healingPower = 16;
            item.nunOnly = true;
            item.battleActions = new BattleAction[] { BattleAction.HEAL };
            break;
        default:
            throw new IllegalArgumentException("Unknown item ID");
        }
        item.id = id;
        return item;
    }

    public static int getBuyPrice(int id) {
        switch (id) {
        case ID_HEALING_HERB: return 10;
        case ID_RESURRECTION_HERB: return 40;
        default: return 0;
        }
    }

    public static int getSellPrice(int id) {
        return getBuyPrice(id) / 2;
    }
}
