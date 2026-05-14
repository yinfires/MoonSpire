package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleManager;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectCardRewardPayload(UUID rewardId, int pageIndex, UUID cardInstanceId) implements CustomPacketPayload {
    public static final Type<SelectCardRewardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "select_card_reward"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectCardRewardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.rewardId);
                buf.writeVarInt(payload.pageIndex);
                buf.writeBoolean(payload.cardInstanceId != null);
                if (payload.cardInstanceId != null) {
                    buf.writeUUID(payload.cardInstanceId);
                }
            },
            buf -> new SelectCardRewardPayload(buf.readUUID(), buf.readVarInt(), buf.readBoolean() ? buf.readUUID() : null));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectCardRewardPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BattleManager.selectCardReward(player, payload.rewardId, payload.pageIndex, payload.cardInstanceId);
        }
    }
}
