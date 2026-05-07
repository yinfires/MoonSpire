package com.yinfires.moonspire.card;

import java.util.Locale;

public enum CardTarget {
    SELF,
    SINGLE_ENEMY,
    SINGLE_ALLY,
    ALL_ENEMIES,
    ALL_ALLIES,
    ALL_UNITS,
    ALL_OTHER_UNITS,
    ALL_OTHER_ALLIES,
    RANDOM_ENEMY,
    RANDOM_ALLY;

    public static CardTarget byName(String name, CardTarget fallback) {
        try {
            return CardTarget.valueOf((name == null ? "" : name).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback == null ? SINGLE_ENEMY : fallback;
        }
    }

    public boolean targetsSelf() {
        return this == SELF || this == ALL_ALLIES || this == ALL_UNITS;
    }

    public boolean targetsEnemy() {
        return this == SINGLE_ENEMY || this == ALL_ENEMIES || this == ALL_UNITS || this == ALL_OTHER_UNITS || this == RANDOM_ENEMY;
    }

    public boolean targetsAlly() {
        return this == SELF || this == SINGLE_ALLY || this == ALL_ALLIES || this == ALL_UNITS || this == ALL_OTHER_UNITS || this == ALL_OTHER_ALLIES || this == RANDOM_ALLY;
    }

    public boolean requiresExplicitTarget() {
        return this == SINGLE_ENEMY || this == SINGLE_ALLY;
    }

    public int targetCountRank() {
        return switch (this) {
            case ALL_UNITS -> 4;
            case ALL_OTHER_UNITS -> 3;
            case ALL_ENEMIES, ALL_ALLIES, ALL_OTHER_ALLIES -> 2;
            case SELF, SINGLE_ENEMY, SINGLE_ALLY, RANDOM_ENEMY, RANDOM_ALLY -> 1;
        };
    }
}
