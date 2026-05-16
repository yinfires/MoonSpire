package com.yinfires.moonspire.card;

import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record CardEffect(CardEffectKind kind, int amount, CardTarget target, int count, String entityTypeId) {
    public static final String DEFAULT_SUMMON_ENTITY_TYPE_ID = "minecraft:vex";

    public static final StreamCodec<RegistryFriendlyByteBuf, CardEffect> STREAM_CODEC = StreamCodec.of(
            CardEffect::write,
            CardEffect::read);

    public CardEffect(CardEffectKind kind, int amount) {
        this(kind, amount, null);
    }

    public CardEffect(CardEffectKind kind, int amount, CardTarget target) {
        this(kind, amount, target, 1);
    }

    public CardEffect(CardEffectKind kind, int amount, CardTarget target, int count) {
        this(kind, amount, target, count, "");
    }

    public CardEffect {
        kind = kind == null ? CardEffectKind.DAMAGE : kind;
        amount = kind.usesAmount() ? Math.max(0, amount) : 1;
        target = target == null ? kind.defaultTarget() : target;
        count = kind.isKeyword() || kind.isHandSelection() ? 1 : Math.max(1, count);
        entityTypeId = defaultEntityTypeId(kind, entityTypeId);
    }

    public static boolean isSummonKind(CardEffectKind kind) {
        return kind == CardEffectKind.SUMMON
                || kind == CardEffectKind.SUMMON_VEX
                || kind == CardEffectKind.SUMMON_SILVERFISH;
    }

    public static String defaultEntityTypeId(CardEffectKind kind, String entityTypeId) {
        if (!isSummonKind(kind)) {
            return "";
        }
        if (entityTypeId != null && !entityTypeId.isBlank()) {
            return entityTypeId.trim().toLowerCase(Locale.ROOT);
        }
        return kind == CardEffectKind.SUMMON_SILVERFISH ? "minecraft:silverfish" : DEFAULT_SUMMON_ENTITY_TYPE_ID;
    }

    private static void write(RegistryFriendlyByteBuf buf, CardEffect effect) {
        buf.writeEnum(effect.kind);
        buf.writeVarInt(effect.amount);
        buf.writeEnum(effect.target);
        buf.writeVarInt(effect.count);
        buf.writeUtf(effect.entityTypeId);
    }

    private static CardEffect read(RegistryFriendlyByteBuf buf) {
        return new CardEffect(buf.readEnum(CardEffectKind.class), buf.readVarInt(), buf.readEnum(CardTarget.class), buf.readVarInt(), buf.readUtf());
    }
}
