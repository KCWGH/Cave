package com.blackberry.cave;

import net.rim.device.api.system.Bitmap;
import net.rim.device.api.ui.Graphics;
import java.util.Vector;
import java.util.Hashtable;
import java.lang.ref.WeakReference;

public class Unit {
    private static final Hashtable sourceBitmaps = new Hashtable();

    private static synchronized Bitmap getSourceBitmap(String path) {
        WeakReference reference = (WeakReference) sourceBitmaps.get(path);
        Bitmap bitmap = reference == null ? null : (Bitmap) reference.get();
        if (bitmap == null) {
            bitmap = Bitmap.getBitmapResource(path);
            if (bitmap != null) sourceBitmaps.put(path, new WeakReference(bitmap));
        }
        return bitmap;
    }
    public static final int INVENTORY_CAPACITY = 12;
    public String name;
    public int curHp, baseMaxHp;
    public int curFocus, baseMaxFocus;
    public int q, r;
    public Bitmap sprite, miniSprite, hudSprite;
    public int strength, intelligence, speed, curMove;
    private Item weapon, armor, accessory;
    private Vector inventory = new Vector();
    public int baseAttackStat = Item.ATTACK_STRENGTH;
    public int baseAttackDamage = 8;
    private int level = 1;
    private long experience;
    private long gold;

    void writeSave(java.io.DataOutputStream out) throws java.io.IOException {
        out.writeUTF(name); out.writeInt(q); out.writeInt(r);
        out.writeInt(baseMaxHp); out.writeInt(baseMaxFocus);
        out.writeInt(strength); out.writeInt(intelligence); out.writeInt(speed);
        out.writeInt(baseAttackStat); out.writeInt(baseAttackDamage);
        out.writeInt(level); out.writeLong(experience); out.writeLong(gold);
        out.writeInt(curHp); out.writeInt(curFocus); out.writeInt(curMove);
        for (int slot = 0; slot < 3; slot++) {
            Item item = getEquipment(slot); out.writeInt(item == null ? 0 : item.id);
        }
        out.writeInt(inventory.size());
        for (int i = 0; i < inventory.size(); i++) out.writeInt(((Item) inventory.elementAt(i)).id);
    }

    static Unit readSave(java.io.DataInputStream in, HexMap map, HexRenderer renderer)
            throws java.io.IOException {
        String name = in.readUTF();
        String image;
        if (name.equals("Goblin Captain")) image = "goblin.png";
        else if (name.equals("Warrior") || name.equals("Thief") || name.equals("Mage")
                || name.equals("Nun") || name.equals("Slime") || name.equals("Wolf")
                || name.equals("Goblin")) image = name.toLowerCase() + ".png";
        else throw new java.io.IOException("Unknown unit");
        int q = in.readInt(), r = in.readInt();
        HexTile tile = map.getTile(q, r);
        if (tile == null || tile.type == HexTile.TYPE_MOUNTAIN)
            throw new java.io.IOException("Invalid unit tile");
        Unit u = new Unit(name, image, q, r);
        u.baseMaxHp = in.readInt(); u.baseMaxFocus = in.readInt();
        u.strength = in.readInt(); u.intelligence = in.readInt(); u.speed = in.readInt();
        u.baseAttackStat = in.readInt(); u.baseAttackDamage = in.readInt();
        u.level = in.readInt(); u.experience = in.readLong(); u.gold = in.readLong();
        int hp = in.readInt(), focus = in.readInt(), move = in.readInt();
        if (u.baseMaxHp < 1 || u.baseMaxFocus < 0 || u.baseMaxFocus > 9
                || u.strength < 0 || u.intelligence < 0 || u.speed < 0
                || u.baseAttackStat < 0 || u.baseAttackStat > 1 || u.baseAttackDamage < 0
                || u.level < 1 || u.experience < 0 || u.experience >= 20L * u.level
                || u.gold < 0 || move < 0) throw new java.io.IOException("Invalid unit state");
        for (int slot = 0; slot < 3; slot++) {
            int id = in.readInt();
            if (id != 0) u.setEquipment(slot, Item.create(id));
        }
        int count = in.readInt();
        if (count < 0 || count > INVENTORY_CAPACITY) throw new java.io.IOException("Invalid bag");
        for (int i = 0; i < count; i++) u.addItem(Item.create(in.readInt()));
        if (hp < 0 || hp > u.getMaxHp() || focus < 0 || focus > u.getMaxFocus())
            throw new java.io.IOException("Invalid resources");
        u.curHp = hp; u.curFocus = focus; u.curMove = move;
        u.returnToTile(q, r, renderer);
        return u;
    }

    public int getLevel() { return level; }
    public long getExperience() { return experience; }
    public long getExperienceToNextLevel() { return 20L * level; }
    public long getGold() { return gold; }

    public void addGold(long amount) {
        if (amount < 0 || amount > Long.MAX_VALUE - gold)
            throw new IllegalArgumentException("Invalid gold amount");
        gold += amount;
    }

    public boolean spendGold(long amount) {
        if (amount <= 0 || amount > gold)
            return false;
        gold -= amount;
        return true;
    }

    public boolean transferGoldTo(Unit recipient, long amount) {
        if (recipient == null || recipient == this || amount <= 0 || amount > gold
                || isMoving || recipient.isMoving || q != recipient.q || r != recipient.r
                || amount > Long.MAX_VALUE - recipient.gold)
            return false;
        gold -= amount;
        recipient.gold += amount;
        return true;
    }

    public void addExperience(long amount) {
        if (amount < 0 || amount > Long.MAX_VALUE - experience)
            throw new IllegalArgumentException("Invalid experience amount");
        experience += amount;
        while (level < Integer.MAX_VALUE && experience >= getExperienceToNextLevel()) {
            experience -= getExperienceToNextLevel();
            level++;
            applyClassGrowth();
        }
        // Level growth raises maxima only; KO, current HP/Focus/movement stay unchanged.
    }

    private void applyClassGrowth() {
        // The existing level-derived +2 HP/+1 damage remains save-compatible.
        // Only additional class HP and primary accuracy gains are stored here.
        int extraHp = name.equals("Warrior") ? 2 : name.equals("Nun") ? 1 : 0;
        baseMaxHp = (int) Math.min(Integer.MAX_VALUE, (long) baseMaxHp + extraHp);
        if ((name.equals("Warrior") || name.equals("Thief")) && strength < 95) strength++;
        else if ((name.equals("Mage") || name.equals("Nun")) && intelligence < 95) intelligence++;
    }

    public void initializeEnemyLevel(int targetLevel) {
        level = Math.max(1, targetLevel);
        restoreResourcesToFull();
    }

    public long getVictoryGold() {
        int base = name.equals("Slime") ? 5 : name.equals("Wolf") ? 8
                : name.equals("Goblin") || name.equals("Goblin Captain") ? 10 : 0;
        return (long) base * level + (name.equals("Goblin Captain") ? 60 : 0);
    }

    public long getVictoryExperience() {
        int base = name.equals("Slime") ? 8 : name.equals("Wolf") ? 12
                : name.equals("Goblin") || name.equals("Goblin Captain") ? 15 : 0;
        return (long) base * level + (name.equals("Goblin Captain") ? 40 : 0);
    }

    // Experimental world-only animation. Set false to return to the original sprite.
    private static final boolean WARRIOR_WALK_TRIAL = true;
    private static final boolean THIEF_WALK_TRIAL = true;
    private static final boolean MAGE_WALK_TRIAL = true;
    private static final boolean NUN_WALK_TRIAL = true;
    private Bitmap walkAtlas;
    private Bitmap combatSprite;
    private int combatSpriteSize;
    private int walkDirection;
    private int walkTicks;

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
        this.sprite = getSourceBitmap(spritePath);
        this.miniSprite = getSourceBitmap("mini_" + spritePath);
        this.hudSprite = getSourceBitmap("hud_" + spritePath);
        String walkResource = null;
        if (WARRIOR_WALK_TRIAL && name.equals("Warrior"))
            walkResource = "warrior_walk_trial.png";
        else if (THIEF_WALK_TRIAL && name.equals("Thief"))
            walkResource = "thief_walk_trial.png";
        else if (MAGE_WALK_TRIAL && name.equals("Mage"))
            walkResource = "mage_walk_trial.png";
        else if (NUN_WALK_TRIAL && name.equals("Nun"))
            walkResource = "nun_walk_trial.png";
        if (walkResource != null) {
            Bitmap candidate = getSourceBitmap(walkResource);
            if (candidate != null && candidate.getWidth() == 420 && candidate.getHeight() == 280)
                walkAtlas = candidate;
        }
        this.baseMaxHp = 20;
        this.curHp = 20;
        this.baseMaxFocus = 3;
        this.curFocus = 3;

        if (name.equals("Warrior")) {
            this.baseMaxHp = 30;
            this.speed = 4;
            this.strength = 80;
            this.intelligence = 35;
            this.baseAttackDamage = 14;
        } else if (name.equals("Thief")) {
            this.baseMaxHp = 22;
            this.speed = 6;
            this.strength = 75;
            this.intelligence = 50;
            this.baseAttackDamage = 6;
        } else if (name.equals("Mage")) {
            this.baseMaxHp = 18;
            this.baseMaxFocus = 4;
            this.speed = 5;
            this.strength = 30;
            this.intelligence = 80;
            this.baseAttackDamage = 14;
        } else if (name.equals("Nun")) {
            this.baseMaxHp = 26;
            this.speed = 5;
            this.strength = 40;
            this.intelligence = 80;
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
        restoreResourcesToFull();
        this.curMove = Math.max(0, Math.min(9, getSpeed()));
    }

    public static Unit createGoblinCaptain(int level, int q, int r) {
        Unit captain = new Unit("Goblin", "goblin.png", q, r);
        captain.initializeEnemyLevel(level);
        int ordinaryDamage = captain.getDamage();
        captain.name = "Goblin Captain";
        captain.baseMaxHp = (int) Math.min(Integer.MAX_VALUE, 40L + 2L * (captain.level - 1));
        long boostedDamage = (3L * ordinaryDamage + 1L) / 2L;
        captain.baseAttackDamage = (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, boostedDamage - (captain.level - 1)));
        captain.restoreResourcesToFull();
        return captain;
    }

    public Bitmap getCombatSprite(int side) {
        if (sprite == null) return null;
        if (sprite.getWidth() == side && sprite.getHeight() == side) return sprite;
        if (combatSprite == null || combatSpriteSize != side) {
            combatSprite = new Bitmap(side, side);
            sprite.scaleInto(combatSprite, Bitmap.FILTER_LANCZOS, Bitmap.SCALE_STRETCH);
            combatSpriteSize = side;
        }
        return combatSprite;
    }

    public Item getEquipment(int slot) {
        if (slot == Item.TYPE_WEAPON) return weapon;
        if (slot == Item.TYPE_ARMOR) return armor;
        if (slot == Item.TYPE_ACCESSORY) return accessory;
        throw new IllegalArgumentException("Unknown equipment slot");
    }

    public int getInventorySize() { return inventory.size(); }

    public boolean hasInventorySpace() { return inventory.size() < INVENTORY_CAPACITY; }

    public Item getInventoryItem(int index) {
        return (Item) inventory.elementAt(index);
    }

    public boolean ownsItem(Item item) {
        return item != null && inventory.contains(item);
    }

    public boolean addItem(Item item) {
        if (item == null || inventory.contains(item) || item == weapon
                || item == armor || item == accessory
                || inventory.size() >= INVENTORY_CAPACITY) return false;
        inventory.addElement(item);
        return true;
    }

    public boolean transferItemTo(Unit recipient, Item item) {
        if (recipient == null || recipient == this || item == null || isMoving
                || recipient.isMoving || q != recipient.q || r != recipient.r
                || !inventory.contains(item) || recipient.inventory.contains(item)
                || recipient.inventory.size() >= INVENTORY_CAPACITY) return false;
        inventory.removeElement(item);
        recipient.inventory.addElement(item);
        return true;
    }

    public boolean equipItem(Item item) {
        if (item == null || item.type == Item.TYPE_CONSUMABLE || !inventory.contains(item)
                || (item.nunOnly && !name.equals("Nun")))
            return false;
        // Validation happens before inventory or equipment is changed.
        validateEquipment(item.type, item);
        Item old = getEquipment(item.type);
        inventory.removeElement(item);
        setEquipment(item.type, item);
        if (old != null) inventory.addElement(old);
        return true;
    }

    public boolean unequipItem(int slot) {
        Item old = getEquipment(slot);
        if (old == null || inventory.size() >= INVENTORY_CAPACITY) return false;
        setEquipment(slot, null);
        inventory.addElement(old);
        return true;
    }

    public boolean consumeItem(Item item) {
        return item != null && item.type == Item.TYPE_CONSUMABLE
                && inventory.removeElement(item);
    }

    boolean removeInventoryItem(Item item) {
        return item != null && inventory.removeElement(item);
    }

    private void validateEquipment(int slot, Item item) {
        getEquipment(slot);
        if (item != null && item.nunOnly && !name.equals("Nun"))
            throw new IllegalArgumentException("Nun-only equipment");
        if (item != null && item.type != slot)
            throw new IllegalArgumentException("Item does not fit equipment slot");
        if (slot == Item.TYPE_WEAPON && item != null
                && item.attackStat != Item.ATTACK_STRENGTH && item.attackStat != Item.ATTACK_INTELLIGENCE)
            throw new IllegalArgumentException("Unknown weapon attack stat");
        if (slot == Item.TYPE_WEAPON && item != null && item.attackSlots < 1)
            throw new IllegalArgumentException("Weapon requires at least one attack slot");
    }

    public void setEquipment(int slot, Item item) {
        // Validate before changing equipment or resources.
        validateEquipment(slot, item);
        if (slot == Item.TYPE_WEAPON) weapon = item;
        else if (slot == Item.TYPE_ARMOR) armor = item;
        else accessory = item;
        clampResources();
    }

    public void clampResources() {
        curHp = Math.max(0, Math.min(curHp, getMaxHp()));
        curFocus = Math.max(0, Math.min(curFocus, getMaxFocus()));
    }

    // Full initialization only. Town services must reject KO before healing.
    public void restoreResourcesToFull() {
        curHp = getMaxHp();
        curFocus = getMaxFocus();
    }

    public int getMaxHp() {
        int bonus = (weapon != null ? weapon.hpBonus : 0) + (armor != null ? armor.hpBonus : 0)
                + (accessory != null ? accessory.hpBonus : 0);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE,
                (long) baseMaxHp + bonus + 2L * (level - 1)));
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

    public boolean canHeal() {
        return name.equals("Nun") && weapon != null && weapon.healingPower > 0 && curHp > 0;
    }

    void collectBattleActions(Vector result, boolean fleeAllowed) {
        result.removeAllElements();
        result.addElement(BattleAction.ATTACK);
        for (int slot = 0; slot < 3; slot++) {
            Item item = getEquipment(slot);
            if (item == null || item.battleActions == null) continue;
            for (int i = 0; i < item.battleActions.length; i++) {
                BattleAction action = item.battleActions[i];
                if (action.available(this) && !result.contains(action)) result.addElement(action);
            }
        }
        if (fleeAllowed) result.addElement(BattleAction.FLEE);
    }

    public int getMaxHealing() {
        return canHeal() ? (int) Math.min(Integer.MAX_VALUE,
                ((long) weapon.healingPower + 4) * getIntelligence() / 100) : 0;
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
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE,
                (long) base + bonus + level - 1));
    }

    void receiveDirectDamage(int damage) {
        int reduction = name.equals("Warrior") ? 1 : 0;
        curHp = Math.max(0, curHp - Math.max(0, damage - reduction));
    }

    int escapeChance(int baseChance) {
        int chance = Math.max(10, Math.min(90, baseChance));
        return Math.min(90, chance + (name.equals("Thief") ? 10 : 0));
    }

    void refundAttackFocus(int spent, int successes, int slots) {
        if (name.equals("Mage") && spent > 0 && slots > 0 && successes == slots)
            curFocus = Math.min(getMaxFocus(), curFocus + 1);
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
        walkTicks = 0;
        renderer.getHexWorldPoint(q, r, scratch);
        this.worldX = scratch[0];
        this.worldY = scratch[1];
        if (path != null && !path.isEmpty()) {
            HexTile next = (HexTile) path.elementAt(0);
            aimAtNextTile(next, renderer);
        } else {
            isMoving = false;
        }
    }

    private void aimAtNextTile(HexTile next, HexRenderer renderer) {
        int dq = next.q - q, dr = next.r - r;
        // Atlas columns: E, NE, NW, W, SW, SE in axial coordinates.
        if (dq == 1 && dr == 0) walkDirection = 0;
        else if (dq == 1 && dr == -1) walkDirection = 1;
        else if (dq == 0 && dr == -1) walkDirection = 2;
        else if (dq == -1 && dr == 0) walkDirection = 3;
        else if (dq == -1 && dr == 1) walkDirection = 4;
        else if (dq == 0 && dr == 1) walkDirection = 5;
        renderer.getHexWorldPoint(next.q, next.r, scratch);
        targetWX = scratch[0];
        targetWY = scratch[1];
    }

    public void drawWorldSprite(Graphics g, int x, int y) {
        if (isMoving && walkAtlas != null) {
            g.drawBitmap(x, y, 70, 70, walkAtlas, walkDirection * 70, (walkTicks / 2) * 70);
        } else if (sprite != null) {
            g.drawBitmap(x, y, 70, 70, sprite, 0, 0);
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

    public void returnToTile(int previousQ, int previousR, HexRenderer renderer) {
        cancelMovement();
        q = previousQ;
        r = previousR;
        renderer.getHexWorldPoint(q, r, scratch);
        worldX = scratch[0];
        worldY = scratch[1];
    }

    // Returns true for every tile arrival, including intermediate path tiles.
    public boolean updateAnimation(HexRenderer renderer) {
        if (!isMoving)
            return false;
        walkTicks = (walkTicks + 1) % 8;
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
                aimAtNextTile(next, renderer);
                return true;
            }
        } else {
            worldX += (dx * moveSpeed) / dist;
            worldY += (dy * moveSpeed) / dist;
            return false;
        }
    }
}
