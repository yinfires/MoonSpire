package com.yinfires.moonspire.card;

import java.util.Locale;

public enum CardEffectKind {
    DAMAGE(CardTarget.SINGLE_ENEMY),
    BLOCK(CardTarget.SELF),
    BLEED(CardTarget.SINGLE_ENEMY),
    GUARD(CardTarget.SELF),
    EXHAUST(CardTarget.SELF),
    EXHAUST_HAND(CardTarget.SELF),
    DISCARD_HAND(CardTarget.SELF);

    private final CardTarget defaultTarget;

    CardEffectKind(CardTarget defaultTarget) {
        this.defaultTarget = defaultTarget;
    }

    public CardTarget defaultTarget() {
        return defaultTarget;
    }

    public boolean isHandSelection() {
        return this == EXHAUST_HAND || this == DISCARD_HAND;
    }

    public static CardEffectKind byName(String name) {
        try {
            return CardEffectKind.valueOf((name == null ? "" : name).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DAMAGE;
        }
    }
}
