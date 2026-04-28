package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UsePreparedCardPayload(int targetId) implements CustomPacketPayload {
    public static final Type<UsePreparedCardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "use_prepared_card"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UsePreparedCardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.targetId),
            buf -> new UsePreparedCardPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UsePreparedCardPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BattleManager.usePreparedCard(player, payload.targetId);
        }
    }
}
