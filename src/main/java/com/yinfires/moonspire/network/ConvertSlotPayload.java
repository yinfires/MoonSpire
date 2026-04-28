package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConvertSlotPayload(int slot, BlockPos forgePos) implements CustomPacketPayload {
    public static final Type<ConvertSlotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "convert_slot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConvertSlotPayload> STREAM_CODEC = StreamCodec.of(
            ConvertSlotPayload::write,
            ConvertSlotPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConvertSlotPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BattleManager.convertInventorySlot(player, payload.slot, payload.forgePos);
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ConvertSlotPayload payload) {
        buf.writeVarInt(payload.slot);
        buf.writeBlockPos(payload.forgePos);
    }

    private static ConvertSlotPayload read(RegistryFriendlyByteBuf buf) {
        return new ConvertSlotPayload(buf.readVarInt(), buf.readBlockPos());
    }
}
