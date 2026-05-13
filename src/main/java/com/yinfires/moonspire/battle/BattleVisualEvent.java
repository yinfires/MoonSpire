package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardInstance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public record BattleVisualEvent(
        int attackerId,
        int targetId,
        ItemStack itemStack,
        ItemStack projectileStack,
        CardInstance playedCard,
        int blockedDamage,
        int healthDamage,
        int gainedBlock,
        int healedHealth,
        int delayTicks,
        AnimationType animationType,
        int animationTicks,
        boolean shieldSound,
        boolean hurtSound,
        boolean armorEquipSound,
        Vec3 animationStart,
        Vec3 animationStrike,
        Vec3 knockbackDelta,
        Vec3 lookTarget) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleVisualEvent> STREAM_CODEC = StreamCodec.of(
            BattleVisualEvent::write,
            BattleVisualEvent::read);

    public BattleVisualEvent(
            int attackerId,
            int targetId,
            ItemStack itemStack,
            ItemStack projectileStack,
            CardInstance playedCard,
            int blockedDamage,
            int healthDamage,
            int gainedBlock,
            int healedHealth,
            int delayTicks,
            AnimationType animationType,
            int animationTicks,
            boolean shieldSound,
            boolean hurtSound,
            boolean armorEquipSound) {
        this(
                attackerId,
                targetId,
                itemStack,
                projectileStack,
                playedCard,
                blockedDamage,
                healthDamage,
                gainedBlock,
                healedHealth,
                delayTicks,
                animationType,
                animationTicks,
                shieldSound,
                hurtSound,
                armorEquipSound,
                null,
                null,
                null,
                null);
    }

    private static void write(RegistryFriendlyByteBuf buf, BattleVisualEvent event) {
        buf.writeVarInt(event.attackerId);
        buf.writeVarInt(event.targetId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, event.itemStack);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, event.projectileStack);
        buf.writeBoolean(event.playedCard != null);
        if (event.playedCard != null) {
            CardInstance.STREAM_CODEC.encode(buf, event.playedCard);
        }
        buf.writeVarInt(event.blockedDamage);
        buf.writeVarInt(event.healthDamage);
        buf.writeVarInt(event.gainedBlock);
        buf.writeVarInt(event.healedHealth);
        buf.writeVarInt(event.delayTicks);
        buf.writeVarInt(event.animationType.ordinal());
        buf.writeVarInt(event.animationTicks);
        buf.writeBoolean(event.shieldSound);
        buf.writeBoolean(event.hurtSound);
        buf.writeBoolean(event.armorEquipSound);
        writeOptionalVec3(buf, event.animationStart);
        writeOptionalVec3(buf, event.animationStrike);
        writeOptionalVec3(buf, event.knockbackDelta);
        writeOptionalVec3(buf, event.lookTarget);
    }

    private static BattleVisualEvent read(RegistryFriendlyByteBuf buf) {
        return new BattleVisualEvent(
                buf.readVarInt(),
                buf.readVarInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                readOptionalCard(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                animationTypeByOrdinal(buf.readVarInt()),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                readOptionalVec3(buf),
                readOptionalVec3(buf),
                readOptionalVec3(buf),
                readOptionalVec3(buf));
    }

    private static CardInstance readOptionalCard(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? CardInstance.STREAM_CODEC.decode(buf) : null;
    }

    private static void writeOptionalVec3(RegistryFriendlyByteBuf buf, Vec3 value) {
        buf.writeBoolean(value != null);
        if (value != null) {
            buf.writeDouble(value.x);
            buf.writeDouble(value.y);
            buf.writeDouble(value.z);
        }
    }

    private static Vec3 readOptionalVec3(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static AnimationType animationTypeByOrdinal(int ordinal) {
        AnimationType[] values = AnimationType.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AnimationType.NONE;
    }

    public enum AnimationType {
        NONE,
        MELEE_LUNGE,
        BOW_DRAW,
        CROSSBOW_LOAD,
        SELF_DESTRUCT,
        TRIDENT_THROW,
        CHANNELING_TRIDENT_THROW,
        RIPTIDE_RUSH,
        GUARDIAN_BEAM
    }
}
