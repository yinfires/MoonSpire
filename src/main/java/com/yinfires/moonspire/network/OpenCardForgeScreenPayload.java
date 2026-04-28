package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.client.ClientScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenCardForgeScreenPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenCardForgeScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "open_card_forge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCardForgeScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.pos),
            buf -> new OpenCardForgeScreenPayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCardForgeScreenPayload payload, IPayloadContext context) {
        ClientScreens.openCardForge(payload.pos);
    }
}
