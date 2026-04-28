package com.yinfires.moonspire.card;

import java.util.Locale;

public enum CardSourceType {
    WEAPON,
    ARMOR,
    TOOL,
    MONSTER,
    UNKNOWN;

    public static CardSourceType byName(String name) {
        try {
            return CardSourceType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
