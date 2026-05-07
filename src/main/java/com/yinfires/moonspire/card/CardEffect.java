package com.yinfires.moonspire.card;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record CardEffect(CardEffectKind kind, int amount, CardTarget target, int count) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CardEffect> STREAM_CODEC = StreamCodec.of(
            CardEffect::write,
            CardEffect::read);

    public CardEffect(CardEffectKind kind, int amount) {
        this(kind, amount, null);
    }

    public CardEffect(CardEffectKind kind, int amount, CardTarget target) {
        this(kind, amount, target, 1);
    }

    public CardEffect {
        kind = kind == null ? CardEffectKind.DAMAGE : kind;
        amount = kind == CardEffectKind.EXHAUST ? 1 : Math.max(0, amount);
        target = target == null ? kind.defaultTarget() : target;
        count = kind == CardEffectKind.EXHAUST || kind.isHandSelection() ? 1 : Math.max(1, count);
    }

    private static void write(RegistryFriendlyByteBuf buf, CardEffect effect) {
        buf.writeEnum(effect.kind);
        buf.writeVarInt(effect.amount);
        buf.writeEnum(effect.target);
        buf.writeVarInt(effect.count);
    }

    private static CardEffect read(RegistryFriendlyByteBuf buf) {
        return new CardEffect(buf.readEnum(CardEffectKind.class), buf.readVarInt(), buf.readEnum(CardTarget.class), buf.readVarInt());
    }
}
