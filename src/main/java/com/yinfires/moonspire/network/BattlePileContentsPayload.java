package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattlePileSource;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.client.ClientBattleState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BattlePileContentsPayload(UUID battleId, BattlePileSource source, long deckVersion, int expectedCount, List<CardInstance> cards) implements CustomPacketPayload {
    public static final Type<BattlePileContentsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "battle_pile_contents"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BattlePileContentsPayload> STREAM_CODEC = StreamCodec.of(
            BattlePileContentsPayload::write,
            BattlePileContentsPayload::read);

    public BattlePileContentsPayload {
        battleId = battleId == null ? com.yinfires.moonspire.battle.BattleSnapshot.INACTIVE_BATTLE_ID : battleId;
        source = source == null ? BattlePileSource.BATTLE_DECK : source;
        expectedCount = Math.max(0, expectedCount);
        cards = List.copyOf(cards == null ? List.of() : cards);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BattlePileContentsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBattleState.setPileContents(payload.battleId, payload.source, payload.deckVersion, payload.expectedCount, payload.cards));
    }

    private static void write(RegistryFriendlyByteBuf buf, BattlePileContentsPayload payload) {
        buf.writeUUID(payload.battleId);
        buf.writeEnum(payload.source);
        buf.writeVarLong(payload.deckVersion);
        buf.writeVarInt(payload.expectedCount);
        buf.writeVarInt(payload.cards.size());
        for (CardInstance card : payload.cards) {
            CardInstance.STREAM_CODEC.encode(buf, card);
        }
    }

    private static BattlePileContentsPayload read(RegistryFriendlyByteBuf buf) {
        UUID battleId = buf.readUUID();
        BattlePileSource source = buf.readEnum(BattlePileSource.class);
        long deckVersion = buf.readVarLong();
        int expectedCount = buf.readVarInt();
        int size = Math.max(0, buf.readVarInt());
        List<CardInstance> cards = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            cards.add(CardInstance.STREAM_CODEC.decode(buf));
        }
        return new BattlePileContentsPayload(battleId, source, deckVersion, expectedCount, cards);
    }
}
