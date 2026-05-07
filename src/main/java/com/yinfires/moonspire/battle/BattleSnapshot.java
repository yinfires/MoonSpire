package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record BattleSnapshot(
        boolean active,
        BattlePhase phase,
        boolean resolvingEffects,
        int round,
        int selectedTargetId,
        BattleCombatantSnapshot player,
        BattleCombatantSnapshot monster,
        int drawPile,
        int discardPile,
        int exhaustPile,
        List<CardInstance> hand,
        List<CardInstance> drawPileCards,
        List<CardInstance> discardPileCards,
        List<CardInstance> exhaustPileCards,
        PendingHandSelectionSnapshot pendingHandSelection,
        List<CardInstance> monsterHand,
        CardInstance monsterIntent,
        List<CardInstance> monsterIntentCards,
        List<BattleVisualEvent> visualEvents) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleSnapshot> STREAM_CODEC = StreamCodec.of(
            BattleSnapshot::write,
            BattleSnapshot::read);

    public static BattleSnapshot inactive() {
        return new BattleSnapshot(
                false,
                BattlePhase.PLAYER_TURN,
                false,
                0,
                -1,
                BattleCombatantSnapshot.empty(),
                BattleCombatantSnapshot.empty(),
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                PendingHandSelectionSnapshot.NONE,
                List.of(),
                null,
                List.of(),
                List.of());
    }

    public int monsterEntityId() {
        return monster.entityId();
    }

    public float playerHealth() {
        return player.health();
    }

    public float playerMaxHealth() {
        return player.maxHealth();
    }

    public int playerDefense() {
        return player.defense();
    }

    public int playerEnergyLeft() {
        return player.energyLeft();
    }

    public int playerMaxEnergy() {
        return player.maxEnergy();
    }

    public float monsterHealth() {
        return monster.health();
    }

    public float monsterMaxHealth() {
        return monster.maxHealth();
    }

    public int monsterDefense() {
        return monster.defense();
    }

    private static void write(RegistryFriendlyByteBuf buf, BattleSnapshot snapshot) {
        buf.writeBoolean(snapshot.active);
        buf.writeEnum(snapshot.phase);
        buf.writeBoolean(snapshot.resolvingEffects);
        buf.writeVarInt(snapshot.round);
        buf.writeVarInt(snapshot.selectedTargetId);
        BattleCombatantSnapshot.STREAM_CODEC.encode(buf, snapshot.player);
        BattleCombatantSnapshot.STREAM_CODEC.encode(buf, snapshot.monster);
        buf.writeVarInt(snapshot.drawPile);
        buf.writeVarInt(snapshot.discardPile);
        buf.writeVarInt(snapshot.exhaustPile);
        writeCards(buf, snapshot.hand);
        writeCards(buf, snapshot.drawPileCards);
        writeCards(buf, snapshot.discardPileCards);
        writeCards(buf, snapshot.exhaustPileCards);
        PendingHandSelectionSnapshot.STREAM_CODEC.encode(buf, snapshot.pendingHandSelection);
        writeCards(buf, snapshot.monsterHand);
        writeOptionalCard(buf, snapshot.monsterIntent);
        writeCards(buf, snapshot.monsterIntentCards);
        buf.writeVarInt(snapshot.visualEvents.size());
        for (BattleVisualEvent visualEvent : snapshot.visualEvents) {
            BattleVisualEvent.STREAM_CODEC.encode(buf, visualEvent);
        }
    }

    private static BattleSnapshot read(RegistryFriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        BattlePhase phase = buf.readEnum(BattlePhase.class);
        boolean resolvingEffects = buf.readBoolean();
        int round = buf.readVarInt();
        int selectedTargetId = buf.readVarInt();
        BattleCombatantSnapshot player = BattleCombatantSnapshot.STREAM_CODEC.decode(buf);
        BattleCombatantSnapshot monster = BattleCombatantSnapshot.STREAM_CODEC.decode(buf);
        int drawPile = buf.readVarInt();
        int discardPile = buf.readVarInt();
        int exhaustPile = buf.readVarInt();
        List<CardInstance> hand = readCards(buf);
        List<CardInstance> drawPileCards = readCards(buf);
        List<CardInstance> discardPileCards = readCards(buf);
        List<CardInstance> exhaustPileCards = readCards(buf);
        PendingHandSelectionSnapshot pendingHandSelection = PendingHandSelectionSnapshot.STREAM_CODEC.decode(buf);
        List<CardInstance> monsterHand = readCards(buf);
        CardInstance monsterIntent = readOptionalCard(buf);
        List<CardInstance> monsterIntentCards = readCards(buf);
        int visualCount = Math.min(64, buf.readVarInt());
        List<BattleVisualEvent> visualEvents = new ArrayList<>(visualCount);
        for (int i = 0; i < visualCount; i++) {
            visualEvents.add(BattleVisualEvent.STREAM_CODEC.decode(buf));
        }
        return new BattleSnapshot(active, phase, resolvingEffects, round, selectedTargetId, player, monster, drawPile, discardPile, exhaustPile, hand, drawPileCards, discardPileCards, exhaustPileCards, pendingHandSelection, monsterHand, monsterIntent, monsterIntentCards, visualEvents);
    }

    private static void writeCards(RegistryFriendlyByteBuf buf, List<CardInstance> cards) {
        buf.writeVarInt(cards.size());
        for (CardInstance card : cards) {
            CardInstance.STREAM_CODEC.encode(buf, card);
        }
    }

    private static List<CardInstance> readCards(RegistryFriendlyByteBuf buf) {
        int size = Math.min(60, buf.readVarInt());
        List<CardInstance> cards = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            cards.add(CardInstance.STREAM_CODEC.decode(buf));
        }
        return cards;
    }

    private static void writeOptionalCard(RegistryFriendlyByteBuf buf, CardInstance card) {
        buf.writeBoolean(card != null);
        if (card != null) {
            CardInstance.STREAM_CODEC.encode(buf, card);
        }
    }

    private static CardInstance readOptionalCard(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? CardInstance.STREAM_CODEC.decode(buf) : null;
    }
}
