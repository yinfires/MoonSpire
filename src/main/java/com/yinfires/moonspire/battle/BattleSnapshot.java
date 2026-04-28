package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record BattleSnapshot(
        boolean active,
        BattlePhase phase,
        int phaseTicksLeft,
        float playerHealth,
        float playerMaxHealth,
        int playerDefense,
        int playerEnergyLeft,
        int playerMaxEnergy,
        float monsterHealth,
        float monsterMaxHealth,
        int monsterDefense,
        int drawPile,
        int discardPile,
        List<CardInstance> hand,
        List<CardInstance> prepared) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleSnapshot> STREAM_CODEC = StreamCodec.of(
            BattleSnapshot::write,
            BattleSnapshot::read);

    public static BattleSnapshot inactive() {
        return new BattleSnapshot(false, BattlePhase.PREPARE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    private static void write(RegistryFriendlyByteBuf buf, BattleSnapshot snapshot) {
        buf.writeBoolean(snapshot.active);
        buf.writeEnum(snapshot.phase);
        buf.writeVarInt(snapshot.phaseTicksLeft);
        buf.writeFloat(snapshot.playerHealth);
        buf.writeFloat(snapshot.playerMaxHealth);
        buf.writeVarInt(snapshot.playerDefense);
        buf.writeVarInt(snapshot.playerEnergyLeft);
        buf.writeVarInt(snapshot.playerMaxEnergy);
        buf.writeFloat(snapshot.monsterHealth);
        buf.writeFloat(snapshot.monsterMaxHealth);
        buf.writeVarInt(snapshot.monsterDefense);
        buf.writeVarInt(snapshot.drawPile);
        buf.writeVarInt(snapshot.discardPile);
        writeCards(buf, snapshot.hand);
        writeCards(buf, snapshot.prepared);
    }

    private static BattleSnapshot read(RegistryFriendlyByteBuf buf) {
        return new BattleSnapshot(
                buf.readBoolean(),
                buf.readEnum(BattlePhase.class),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                readCards(buf),
                readCards(buf));
    }

    private static void writeCards(RegistryFriendlyByteBuf buf, List<CardInstance> cards) {
        buf.writeVarInt(cards.size());
        for (CardInstance card : cards) {
            CardInstance.STREAM_CODEC.encode(buf, card);
        }
    }

    private static List<CardInstance> readCards(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<CardInstance> cards = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            cards.add(CardInstance.STREAM_CODEC.decode(buf));
        }
        return cards;
    }
}
