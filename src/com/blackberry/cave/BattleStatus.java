package com.blackberry.cave;

import java.util.Vector;

// Turn effects belong to one battle, not the persistent Unit model.
final class BattleStatus {
    static final class Definition {
        final String label;
        final int damage, turns;
        Definition(String label, int damage, int turns) {
            this.label = label; this.damage = damage; this.turns = turns;
        }
    }
    static final Definition BLEED = new Definition("Bleed", 3, 2);
    private static final class Entry {
        Unit target;
        Definition definition;
        int remaining;
        Entry(Unit target, Definition definition) {
            this.target = target; this.definition = definition; remaining = definition.turns;
        }
    }
    private Vector entries = new Vector();

    void apply(Unit target, Definition definition) {
        if (target == null || target.curHp <= 0) return;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = (Entry) entries.elementAt(i);
            if (entry.target == target && entry.definition == definition) {
                entry.remaining = definition.turns;
                return;
            }
        }
        entries.addElement(new Entry(target, definition));
    }

    void beginTurn(Unit target) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = (Entry) entries.elementAt(i);
            if (entry.target.curHp <= 0) {
                entries.removeElementAt(i);
            } else if (entry.target == target) {
                target.curHp = Math.max(0, target.curHp - entry.definition.damage);
                if (--entry.remaining == 0 || target.curHp == 0) entries.removeElementAt(i);
            }
        }
    }

    String label(Unit target) {
        if (target.curHp <= 0) return "";
        String text = "";
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = (Entry) entries.elementAt(i);
            if (entry.target == target) text += entry.definition.label + " " + entry.remaining + " ";
        }
        return text;
    }

    void clear() { entries.removeAllElements(); }
}
