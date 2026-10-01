package com.blackberry.cave;

// Catalog data drives the menu, target selection, roll preview and resolution.
final class BattleAction {
    static final int ENEMY = 0, ALLY = 1, SELF = 2, PARTY = 3, ALL_ENEMIES = 4;
    static final int DAMAGE = 0, RESTORE = 1, PROVOKE = 2, ESCAPE = 3;
    static final int WEAPON_STAT = 0, STR = 1, INT = 2, AGILITY = 3;
    static final BattleAction ATTACK = new BattleAction(0, "Attack", ENEMY, DAMAGE, WEAPON_STAT, 0, 0,
            "Damage varies with successes");
    static final BattleAction FLEE = new BattleAction(1, "Flee", PARTY, ESCAPE, AGILITY, 3, 2,
            "Need 2 successes to escape");
    static final BattleAction HEAL = new BattleAction(2, "Heal", ALLY, RESTORE, INT, 3, 0,
            "Healing varies with successes");
    static final BattleAction TAUNT = new BattleAction(3, "Taunt", SELF, PROVOKE, STR, 3, 2,
            "Draw attacks until your next turn");
    static final BattleAction ARCANE_WAVE = new BattleAction(4, "Arcane Wave", ALL_ENEMIES, DAMAGE, INT, 3, 0,
            "Hit all living enemies at half power", 50);
    static final BattleAction BLEEDING_CUT = new BattleAction(5, "Bleeding Cut", ENEMY, DAMAGE, STR, 3, 0,
            "2 successes: bleed 3 HP for 2 turns", 50, BattleStatus.BLEED, 2);
    final int id, target, effect, stat, slots, required;
    final int powerPercent;
    final String label, description;
    final BattleStatus.Definition status;
    final int statusSuccesses;

    private BattleAction(int id, String label, int target, int effect, int stat,
            int slots, int required, String description) {
        this(id, label, target, effect, stat, slots, required, description, 100);
    }

    private BattleAction(int id, String label, int target, int effect, int stat,
            int slots, int required, String description, int powerPercent) {
        this(id, label, target, effect, stat, slots, required, description, powerPercent, null, 0);
    }

    private BattleAction(int id, String label, int target, int effect, int stat,
            int slots, int required, String description, int powerPercent,
            BattleStatus.Definition status, int statusSuccesses) {
        this.id = id; this.label = label; this.target = target; this.effect = effect;
        this.stat = stat; this.slots = slots; this.required = required; this.description = description;
        this.powerPercent = powerPercent;
        this.status = status; this.statusSuccesses = statusSuccesses;
    }

    static BattleAction get(int id) {
        switch (id) {
        case 0: return ATTACK;
        case 1: return FLEE;
        case 2: return HEAL;
        case 3: return TAUNT;
        case 4: return ARCANE_WAVE;
        case 5: return BLEEDING_CUT;
        default: throw new IllegalArgumentException("Unknown battle action");
        }
    }

    boolean available(Unit unit) {
        if (unit == null || unit.curHp <= 0) return false;
        if (effect == RESTORE) return unit.canHeal();
        return true;
    }

    boolean validTarget(Unit actor, Unit target) {
        return effect == RESTORE ? HealingAction.eligible(actor, target) : target != null && target.curHp > 0;
    }

    boolean selectsOneTarget() { return target == ENEMY || target == ALLY; }

    boolean resolve(Unit actor, Unit selected, java.util.Vector enemies, int successes, int count, BattleStatus statuses) {
        if (successes < required) return false;
        if (target != ALL_ENEMIES) return apply(actor, selected, successes, count, statuses);
        for (int i = 0; i < enemies.size(); i++) {
            Unit enemy = (Unit) enemies.elementAt(i);
            if (validTarget(actor, enemy)) apply(actor, enemy, successes, count, statuses);
        }
        return true;
    }

    int slotCount(Unit actor) { return slots == 0 ? actor.getAttackSlotCount() : slots; }

    int focusLimit(Unit actor) {
        return stat == WEAPON_STAT && !actor.canFocusAttack() ? 0 : Math.min(actor.curFocus, slotCount(actor));
    }

    int chance(Unit actor, java.util.Vector enemies) {
        if (stat == WEAPON_STAT) return actor.getAttackSuccessChance();
        if (stat == STR) return actor.getStrength();
        if (stat == INT) return actor.getIntelligence();
        int fastest = 0;
        for (int i = 0; i < enemies.size(); i++) {
            Unit enemy = (Unit) enemies.elementAt(i);
            if (enemy.curHp > 0) fastest = Math.max(fastest, enemy.getSpeed());
        }
        return actor.escapeChance(50 + 5 * (actor.getSpeed() - fastest));
    }

    int maximum(Unit actor) {
        int base = effect == RESTORE ? actor.getMaxHealing() : actor.getDamage();
        return (int) ((long) base * powerPercent / 100);
    }

    private boolean apply(Unit actor, Unit target, int successes, int slotCount, BattleStatus statuses) {
        if (effect == RESTORE) HealingAction.apply(actor, target, successes);
        else if (effect == DAMAGE && target != null) {
            int damage = (int) ((long) maximum(actor) * successes / slotCount);
            target.receiveDirectDamage(damage);
        }
        if (status != null && successes >= statusSuccesses) statuses.apply(target, status);
        return successes >= required;
    }
}
