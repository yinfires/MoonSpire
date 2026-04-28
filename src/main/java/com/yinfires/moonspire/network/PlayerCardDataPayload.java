package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.card.PlayerCardData;
import com.yinfires.moonspire.client.ClientCardState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayerCardDataPayload(PlayerCardData data) implements CustomPacketPayload {
    public static final Type<PlayerCardDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "player_card_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerCardDataPayload> STREAM_CODEC = PlayerCardData.STREAM_CODEC.map(PlayerCardDataPayload::new, PlayerCardDataPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerCardDataPayload payload, IPayloadContext context) {
        ClientCardState.setCards(payload.data);
    }
}
