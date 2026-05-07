package com.yinfires.moonspire.battle;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record BattleEffectSnapshot(
        BattleEffectType type,
        String nameKey,
        String descriptionKey,
        int amount) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleEffectSnapshot> STREAM_CODEC = StreamCodec.of(
            BattleEffectSnapshot::write,
            BattleEffectSnapshot::read);

    public static BattleEffectSnapshot of(BattleEffectType type, int amount) {
        return new BattleEffectSnapshot(type, type.nameKey(), type.activeDescriptionKey(), amount);
    }

    private static void write(RegistryFriendlyByteBuf buf, BattleEffectSnapshot snapshot) {
        buf.writeEnum(snapshot.type);
        buf.writeUtf(snapshot.nameKey);
        buf.writeUtf(snapshot.descriptionKey);
        buf.writeVarInt(snapshot.amount);
    }

    private static BattleEffectSnapshot read(RegistryFriendlyByteBuf buf) {
        return new BattleEffectSnapshot(
                buf.readEnum(BattleEffectType.class),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt());
    }
}
