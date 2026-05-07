package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardInstance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record BattleVisualEvent(
        int attackerId,
        int targetId,
        ItemStack itemStack,
        CardInstance playedCard,
        int blockedDamage,
        int healthDamage,
        int gainedBlock,
        int delayTicks,
        boolean shieldSound,
        boolean hurtSound,
        boolean armorEquipSound) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleVisualEvent> STREAM_CODEC = StreamCodec.of(
            BattleVisualEvent::write,
            BattleVisualEvent::read);

    private static void write(RegistryFriendlyByteBuf buf, BattleVisualEvent event) {
        buf.writeVarInt(event.attackerId);
        buf.writeVarInt(event.targetId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, event.itemStack);
        buf.writeBoolean(event.playedCard != null);
        if (event.playedCard != null) {
            CardInstance.STREAM_CODEC.encode(buf, event.playedCard);
        }
        buf.writeVarInt(event.blockedDamage);
        buf.writeVarInt(event.healthDamage);
        buf.writeVarInt(event.gainedBlock);
        buf.writeVarInt(event.delayTicks);
        buf.writeBoolean(event.shieldSound);
        buf.writeBoolean(event.hurtSound);
        buf.writeBoolean(event.armorEquipSound);
    }

    private static BattleVisualEvent read(RegistryFriendlyByteBuf buf) {
        return new BattleVisualEvent(
                buf.readVarInt(),
                buf.readVarInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                readOptionalCard(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean());
    }

    private static CardInstance readOptionalCard(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? CardInstance.STREAM_CODEC.decode(buf) : null;
    }
}
