package com.yinfires.moonspire.battle;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record BattleCombatantSnapshot(
        int entityId,
        float health,
        float maxHealth,
        int defense,
        int energyLeft,
        int maxEnergy,
        int baseSpeed,
        int roundSpeed,
        List<BattleEffectSnapshot> effects) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleCombatantSnapshot> STREAM_CODEC = StreamCodec.of(
            BattleCombatantSnapshot::write,
            BattleCombatantSnapshot::read);

    public static BattleCombatantSnapshot empty() {
        return new BattleCombatantSnapshot(-1, 0.0F, 0.0F, 0, 0, 0, 0, 0, List.of());
    }

    private static void write(RegistryFriendlyByteBuf buf, BattleCombatantSnapshot snapshot) {
        buf.writeVarInt(snapshot.entityId);
        buf.writeFloat(snapshot.health);
        buf.writeFloat(snapshot.maxHealth);
        buf.writeVarInt(snapshot.defense);
        buf.writeVarInt(snapshot.energyLeft);
        buf.writeVarInt(snapshot.maxEnergy);
        buf.writeVarInt(snapshot.baseSpeed);
        buf.writeVarInt(snapshot.roundSpeed);
        buf.writeVarInt(snapshot.effects.size());
        for (BattleEffectSnapshot effect : snapshot.effects) {
            BattleEffectSnapshot.STREAM_CODEC.encode(buf, effect);
        }
    }

    private static BattleCombatantSnapshot read(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        float health = buf.readFloat();
        float maxHealth = buf.readFloat();
        int defense = buf.readVarInt();
        int energyLeft = buf.readVarInt();
        int maxEnergy = buf.readVarInt();
        int baseSpeed = buf.readVarInt();
        int roundSpeed = buf.readVarInt();
        int effectCount = Math.min(16, buf.readVarInt());
        List<BattleEffectSnapshot> effects = new ArrayList<>(effectCount);
        for (int i = 0; i < effectCount; i++) {
            effects.add(BattleEffectSnapshot.STREAM_CODEC.decode(buf));
        }
        return new BattleCombatantSnapshot(entityId, health, maxHealth, defense, energyLeft, maxEnergy, baseSpeed, roundSpeed, effects);
    }
}
