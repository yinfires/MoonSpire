package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.developer.DeveloperData;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import java.io.IOException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveDeveloperDataPayload(String json) implements CustomPacketPayload {
    public static final Type<SaveDeveloperDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "save_developer_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveDeveloperDataPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.json, 262144),
            buf -> new SaveDeveloperDataPayload(buf.readUtf(262144)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveDeveloperDataPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.hasPermissions(2)) {
            try {
                DeveloperData data = DeveloperData.fromJson(payload.json);
                DeveloperDataManager.save(data);
                PacketDistributor.sendToPlayer(player, new DeveloperCenterPayload(true, data.toJson()));
            } catch (IOException ignored) {
                PacketDistributor.sendToPlayer(player, new DeveloperCenterPayload(true, DeveloperDataManager.load().toJson()));
            }
        }
    }
}
