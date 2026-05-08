package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.client.developer.DeveloperCenterScreen;
import com.yinfires.moonspire.developer.DeveloperData;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DeveloperCenterPayload(boolean allowed, String json, boolean openScreen) implements CustomPacketPayload {
    public static final Type<DeveloperCenterPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "developer_center"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeveloperCenterPayload> STREAM_CODEC = StreamCodec.of(
            DeveloperCenterPayload::write,
            DeveloperCenterPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public DeveloperCenterPayload(boolean allowed, String json) {
        this(allowed, json, true);
    }

    public static void handle(DeveloperCenterPayload payload, IPayloadContext context) {
        DeveloperData data = DeveloperData.fromJson(payload.json);
        DeveloperDataManager.setClientData(data);
        if (!payload.openScreen) {
            return;
        }
        DeveloperCenterScreen.open(payload.allowed, payload.json);
    }

    private static void write(RegistryFriendlyByteBuf buf, DeveloperCenterPayload payload) {
        buf.writeBoolean(payload.allowed);
        buf.writeUtf(payload.json, 262144);
        buf.writeBoolean(payload.openScreen);
    }

    private static DeveloperCenterPayload read(RegistryFriendlyByteBuf buf) {
        return new DeveloperCenterPayload(buf.readBoolean(), buf.readUtf(262144), buf.readBoolean());
    }
}
