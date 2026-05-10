package com.yinfires.moonspire.card;

import java.util.Locale;

public enum CardEffectKind {
    DAMAGE(CardTarget.SINGLE_ENEMY),
    HEAL(CardTarget.SELF),
    BLOCK(CardTarget.SELF),
    BLEED(CardTarget.SINGLE_ENEMY),
    GUARD(CardTarget.SELF),
    STRENGTH(CardTarget.SELF),
    LOSE_STRENGTH(CardTarget.SINGLE_ENEMY),
    REGENERATION(CardTarget.SELF),
    HASTE(CardTarget.SELF),
    POISON(CardTarget.SINGLE_ENEMY),
    BURN(CardTarget.SINGLE_ENEMY),
    WEAKNESS(CardTarget.SINGLE_ENEMY),
    SLOWNESS(CardTarget.SINGLE_ENEMY),
    DRAW_CARDS(CardTarget.SELF),
    GAIN_ENERGY(CardTarget.SELF),
    EXHAUST(CardTarget.SELF),
    INNATE(CardTarget.SELF),
    RETAIN(CardTarget.SELF),
    ETHEREAL(CardTarget.SELF),
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

    public boolean isPassiveKeyword() {
        return this == INNATE || this == RETAIN || this == ETHEREAL;
    }

    public boolean isKeyword() {
        return this == EXHAUST || isPassiveKeyword();
    }

    public boolean usesAmount() {
        return !isKeyword();
    }

    public boolean usesTarget() {
        return !isKeyword();
    }

    public boolean isResolvedEffect() {
        return usesAmount() && !isHandSelection();
    }

    public boolean makesCardPlayable() {
        return !isPassiveKeyword();
    }

    public static CardEffectKind byName(String name) {
        try {
            return CardEffectKind.valueOf((name == null ? "" : name).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DAMAGE;
        }
    }
}
