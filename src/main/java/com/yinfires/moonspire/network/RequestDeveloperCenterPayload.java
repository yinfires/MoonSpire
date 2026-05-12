package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestDeveloperCenterPayload() implements CustomPacketPayload {
    public static final Type<RequestDeveloperCenterPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "request_developer_center"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestDeveloperCenterPayload> STREAM_CODEC = StreamCodec.unit(new RequestDeveloperCenterPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestDeveloperCenterPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            if (!player.hasPermissions(2)) {
                PacketDistributor.sendToPlayer(player, new DeveloperCenterPayload(false, DeveloperDataManager.reload().toJson()));
                return;
            }
            PacketDistributor.sendToPlayer(player, new DeveloperCenterPayload(true, DeveloperDataManager.reload().toJson()));
        }
    }
}
