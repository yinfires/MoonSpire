package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectHandCardsPayload(List<UUID> cardIds) implements CustomPacketPayload {
    public static final Type<SelectHandCardsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "select_hand_cards"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectHandCardsPayload> STREAM_CODEC = StreamCodec.of(
            SelectHandCardsPayload::write,
            SelectHandCardsPayload::read);

    public SelectHandCardsPayload {
        cardIds = List.copyOf(cardIds == null ? List.of() : cardIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectHandCardsPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> BattleManager.confirmHandSelection(player, payload.cardIds));
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, SelectHandCardsPayload payload) {
        buf.writeVarInt(payload.cardIds.size());
        for (UUID cardId : payload.cardIds) {
            buf.writeUUID(cardId);
        }
    }

    private static SelectHandCardsPayload read(RegistryFriendlyByteBuf buf) {
        int size = Math.min(32, buf.readVarInt());
        List<UUID> cardIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            cardIds.add(buf.readUUID());
        }
        return new SelectHandCardsPayload(cardIds);
    }
}
