package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record BattleEnemyIntentSnapshot(int entityId, List<CardInstance> cards) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleEnemyIntentSnapshot> STREAM_CODEC = StreamCodec.of(
            BattleEnemyIntentSnapshot::write,
            BattleEnemyIntentSnapshot::read);

    public BattleEnemyIntentSnapshot {
        cards = List.copyOf(cards == null ? List.of() : cards);
    }

    private static void write(RegistryFriendlyByteBuf buf, BattleEnemyIntentSnapshot snapshot) {
        buf.writeVarInt(snapshot.entityId);
        buf.writeVarInt(snapshot.cards.size());
        for (CardInstance card : snapshot.cards) {
            CardInstance.STREAM_CODEC.encode(buf, card);
        }
    }

    private static BattleEnemyIntentSnapshot read(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int size = Math.min(30, buf.readVarInt());
        List<CardInstance> cards = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            cards.add(CardInstance.STREAM_CODEC.decode(buf));
        }
        return new BattleEnemyIntentSnapshot(entityId, cards);
    }
}
