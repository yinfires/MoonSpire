package com.yinfires.moonspire.card;

import java.util.Locale;

public enum CardEffectKind {
    DAMAGE(CardTarget.SINGLE_ENEMY),
    REMOTE(CardTarget.SELF),
    CONSUME_ARROW(CardTarget.SINGLE_ENEMY),
    ARROW(CardTarget.SELF),
    HEAL(CardTarget.SELF),
    BLOCK(CardTarget.SELF),
    BLEED(CardTarget.SINGLE_ENEMY),
    GLOWING(CardTarget.SINGLE_ENEMY),
    GUARD(CardTarget.SELF),
    STRENGTH(CardTarget.SELF),
    LOSE_STRENGTH(CardTarget.SINGLE_ENEMY),
    REGENERATION(CardTarget.SELF),
    HASTE(CardTarget.SELF),
    POISON(CardTarget.SINGLE_ENEMY),
    BURN(CardTarget.SINGLE_ENEMY),
    WITHER(CardTarget.SINGLE_ENEMY),
    WEAKNESS(CardTarget.SINGLE_ENEMY),
    SLOWNESS(CardTarget.SINGLE_ENEMY),
    FUSE(CardTarget.SELF),
    DRAW_CARDS(CardTarget.SELF),
    GAIN_ENERGY(CardTarget.SELF),
    EXHAUST(CardTarget.SELF),
    INNATE(CardTarget.SELF),
    RETAIN(CardTarget.SELF),
    ETHEREAL(CardTarget.SELF),
    RETAIN_REDUCE_COST(CardTarget.SELF),
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
        return this == EXHAUST || this == REMOTE || this == ARROW || isPassiveKeyword();
    }

    public boolean usesAmount() {
        return !isKeyword();
    }

    public boolean usesTarget() {
        return !isKeyword() && this != RETAIN_REDUCE_COST;
    }

    public boolean isResolvedEffect() {
        return usesAmount() && !isHandSelection() && this != CONSUME_ARROW && this != RETAIN_REDUCE_COST;
    }

    public boolean makesCardPlayable() {
        return !isPassiveKeyword() && this != REMOTE && this != ARROW && this != RETAIN_REDUCE_COST;
    }

    public static CardEffectKind byName(String name) {
        try {
            return CardEffectKind.valueOf((name == null ? "" : name).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DAMAGE;
        }
    }
}
