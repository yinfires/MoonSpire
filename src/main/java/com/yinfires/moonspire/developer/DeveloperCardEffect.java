package com.yinfires.moonspire.developer;

import com.yinfires.moonspire.card.CardTarget;
import java.util.Locale;

public record DeveloperCardEffect(Kind kind, int amount, CardTarget target, int count) {
    public DeveloperCardEffect {
        kind = kind == null ? Kind.DAMAGE : kind;
        amount = Math.max(0, amount);
        target = target == null ? defaultTarget(kind) : target;
        count = Math.max(1, count);
    }

    public DeveloperCardEffect(Kind kind, int amount) {
        this(kind, amount, null, 1);
    }

    public DeveloperCardEffect(Kind kind, int amount, CardTarget target) {
        this(kind, amount, target, 1);
    }

    public static DeveloperCardEffect damage(int amount) {
        return new DeveloperCardEffect(Kind.DAMAGE, amount);
    }

    public static DeveloperCardEffect block(int amount) {
        return new DeveloperCardEffect(Kind.BLOCK, amount);
    }

    public static DeveloperCardEffect bleed(int amount) {
        return new DeveloperCardEffect(Kind.BLEED, amount);
    }

    public static DeveloperCardEffect guard(int amount) {
        return new DeveloperCardEffect(Kind.GUARD, amount);
    }

    public static CardTarget defaultTarget(Kind kind) {
        return kind == Kind.BLOCK
                || kind == Kind.HEAL
                || kind == Kind.GUARD
                || kind == Kind.STRENGTH
                || kind == Kind.REGENERATION
                || kind == Kind.HASTE
                || kind == Kind.DRAW_CARDS
                || kind == Kind.GAIN_ENERGY
                || kind == Kind.EXHAUST
                || kind == Kind.REMOTE
                || kind == Kind.ARROW
                || kind == Kind.INNATE
                || kind == Kind.RETAIN
                || kind == Kind.ETHEREAL
                || kind == Kind.RETAIN_REDUCE_COST
                || kind == Kind.EXHAUST_HAND
                || kind == Kind.DISCARD_HAND ? CardTarget.SELF : CardTarget.SINGLE_ENEMY;
    }

    public CardTarget resolvedTarget() {
        return target;
    }

    public boolean canChangeTarget() {
        return kind.usesTarget() && amount > 0;
    }

    public boolean canChangeCount() {
        return kind.usesAmount() && !kind.isHandSelection() && amount > 0;
    }

    public enum Kind {
        DAMAGE,
        REMOTE,
        CONSUME_ARROW,
        ARROW,
        HEAL,
        BLOCK,
        BLEED,
        GLOWING,
        GUARD,
        STRENGTH,
        LOSE_STRENGTH,
        REGENERATION,
        HASTE,
        POISON,
        BURN,
        WEAKNESS,
        SLOWNESS,
        DRAW_CARDS,
        GAIN_ENERGY,
        EXHAUST,
        INNATE,
        RETAIN,
        ETHEREAL,
        RETAIN_REDUCE_COST,
        EXHAUST_HAND,
        DISCARD_HAND;

        public boolean usesTarget() {
            return !isKeyword() && this != RETAIN_REDUCE_COST;
        }

        public boolean usesAmount() {
            return !isKeyword();
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

        public static Kind byName(String name) {
            try {
                return Kind.valueOf((name == null ? "" : name).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return DAMAGE;
            }
        }
    }
}
