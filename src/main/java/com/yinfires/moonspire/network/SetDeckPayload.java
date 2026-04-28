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

public record SetDeckPayload(List<UUID> cardIds) implements CustomPacketPayload {
    public static final Type<SetDeckPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "set_deck"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetDeckPayload> STREAM_CODEC = StreamCodec.of(
            SetDeckPayload::write,
            SetDeckPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetDeckPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BattleManager.setDeck(player, payload.cardIds);
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, SetDeckPayload payload) {
        buf.writeVarInt(payload.cardIds.size());
        for (UUID id : payload.cardIds) {
            buf.writeUUID(id);
        }
    }

    private static SetDeckPayload read(RegistryFriendlyByteBuf buf) {
        int size = Math.min(30, buf.readVarInt());
        List<UUID> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readUUID());
        }
        return new SetDeckPayload(ids);
    }
}
