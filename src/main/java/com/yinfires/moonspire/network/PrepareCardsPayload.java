package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PrepareCardsPayload(List<Integer> handIndexes) implements CustomPacketPayload {
    public static final Type<PrepareCardsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "prepare_cards"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PrepareCardsPayload> STREAM_CODEC = StreamCodec.of(
            PrepareCardsPayload::write,
            PrepareCardsPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PrepareCardsPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BattleManager.prepare(player, payload.handIndexes);
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, PrepareCardsPayload payload) {
        buf.writeVarInt(payload.handIndexes.size());
        for (int index : payload.handIndexes) {
            buf.writeVarInt(index);
        }
    }

    private static PrepareCardsPayload read(RegistryFriendlyByteBuf buf) {
        int size = Math.min(30, buf.readVarInt());
        List<Integer> indexes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            indexes.add(buf.readVarInt());
        }
        return new PrepareCardsPayload(indexes);
    }
}
