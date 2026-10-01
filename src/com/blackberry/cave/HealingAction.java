package com.blackberry.cave;

// Shared field/combat rules; equipment INT bonuses are included by Unit.
final class HealingAction {
    static final int SLOTS = 3;

    static boolean eligible(Unit caster, Unit target) {
        return caster != null && caster.canHeal() && caster.getMaxHealing() > 0
                && target != null && target.curHp > 0 && target.curHp < target.getMaxHp();
    }

    static int amount(Unit caster, int successes) {
        return (int) ((long) caster.getMaxHealing() * Math.max(0, Math.min(SLOTS, successes)) / SLOTS);
    }

    static void apply(Unit caster, Unit target, int successes) {
        if (!eligible(caster, target)) return;
        target.curHp = (int) Math.min((long) target.getMaxHp(),
                (long) target.curHp + amount(caster, successes));
    }
}
