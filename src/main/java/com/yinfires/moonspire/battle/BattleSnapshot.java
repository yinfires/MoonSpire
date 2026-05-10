package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record BattleSnapshot(
        UUID battleId,
        long sequence,
        boolean active,
        BattlePhase phase,
        boolean resolvingEffects,
        int round,
        int selectedTargetId,
        int localPlayerEntityId,
        boolean localPlayerEndedTurn,
        List<BattleCombatantSnapshot> players,
        List<BattleCombatantSnapshot> enemies,
        int drawPile,
        int discardPile,
        int exhaustPile,
        long localDeckVersion,
        List<CardInstance> hand,
        PendingHandSelectionSnapshot pendingHandSelection,
        List<CardInstance> monsterHand,
        CardInstance monsterIntent,
        List<CardInstance> monsterIntentCards,
        List<BattleEnemyIntentSnapshot> enemyIntents,
        List<BattleEntityCardsSnapshot> entityHands,
        List<BattleVisualEvent> visualEvents) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleSnapshot> STREAM_CODEC = StreamCodec.of(
            BattleSnapshot::write,
            BattleSnapshot::read);
    public static final UUID INACTIVE_BATTLE_ID = new UUID(0L, 0L);

    public BattleSnapshot {
        battleId = battleId == null ? INACTIVE_BATTLE_ID : battleId;
        players = List.copyOf(players == null ? List.of() : players);
        enemies = List.copyOf(enemies == null ? List.of() : enemies);
        hand = List.copyOf(hand == null ? List.of() : hand);
        pendingHandSelection = pendingHandSelection == null ? PendingHandSelectionSnapshot.NONE : pendingHandSelection;
        monsterHand = List.copyOf(monsterHand == null ? List.of() : monsterHand);
        monsterIntentCards = List.copyOf(monsterIntentCards == null ? List.of() : monsterIntentCards);
        enemyIntents = List.copyOf(enemyIntents == null ? List.of() : enemyIntents);
        entityHands = List.copyOf(entityHands == null ? List.of() : entityHands);
        visualEvents = List.copyOf(visualEvents == null ? List.of() : visualEvents);
    }

    public static BattleSnapshot inactive() {
        return inactive(INACTIVE_BATTLE_ID, 0L);
    }

    public static BattleSnapshot inactive(UUID battleId, long sequence) {
        return new BattleSnapshot(
                battleId,
                sequence,
                false,
                BattlePhase.PLAYER_TURN,
                false,
                0,
                -1,
                -1,
                false,
                List.of(),
                List.of(),
                0,
                0,
                0,
                0L,
                List.of(),
                PendingHandSelectionSnapshot.NONE,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public BattleCombatantSnapshot player() {
        BattleCombatantSnapshot local = combatant(localPlayerEntityId);
        if (local != null) {
            return local;
        }
        return players.isEmpty() ? BattleCombatantSnapshot.empty() : players.getFirst();
    }

    public BattleCombatantSnapshot monster() {
        return enemies.isEmpty() ? BattleCombatantSnapshot.empty() : enemies.getFirst();
    }

    public BattleCombatantSnapshot combatant(int entityId) {
        for (BattleCombatantSnapshot player : players) {
            if (player.entityId() == entityId) {
                return player;
            }
        }
        for (BattleCombatantSnapshot enemy : enemies) {
            if (enemy.entityId() == entityId) {
                return enemy;
            }
        }
        return null;
    }

    public boolean isPlayerEntity(int entityId) {
        return players.stream().anyMatch(player -> player.entityId() == entityId);
    }

    public boolean isEnemyEntity(int entityId) {
        return enemies.stream().anyMatch(enemy -> enemy.entityId() == entityId);
    }

    public boolean localPlayerFakeDead() {
        return player().fakeDead();
    }

    public int monsterEntityId() {
        return monster().entityId();
    }

    public float playerHealth() {
        return player().health();
    }

    public float playerMaxHealth() {
        return player().maxHealth();
    }

    public int playerDefense() {
        return player().defense();
    }

    public int playerEnergyLeft() {
        return player().energyLeft();
    }

    public int playerMaxEnergy() {
        return player().maxEnergy();
    }

    public float monsterHealth() {
        return monster().health();
    }

    public float monsterMaxHealth() {
        return monster().maxHealth();
    }

    public int monsterDefense() {
        return monster().defense();
    }

    public List<CardInstance> intentCardsFor(int enemyEntityId) {
        for (BattleEnemyIntentSnapshot intent : enemyIntents) {
            if (intent.entityId() == enemyEntityId) {
                return intent.cards();
            }
        }
        return enemyEntityId == monster().entityId() ? monsterIntentCards : List.of();
    }

    public List<CardInstance> handCardsFor(int entityId) {
        if (entityId == player().entityId()) {
            return hand;
        }
        if (entityId == monster().entityId() && !monsterHand.isEmpty()) {
            return monsterHand;
        }
        for (BattleEntityCardsSnapshot entityHand : entityHands) {
            if (entityHand.entityId() == entityId) {
                return entityHand.cards();
            }
        }
        return List.of();
    }

    private static void write(RegistryFriendlyByteBuf buf, BattleSnapshot snapshot) {
        buf.writeUUID(snapshot.battleId);
        buf.writeVarLong(snapshot.sequence);
        buf.writeBoolean(snapshot.active);
        buf.writeEnum(snapshot.phase);
        buf.writeBoolean(snapshot.resolvingEffects);
        buf.writeVarInt(snapshot.round);
        buf.writeVarInt(snapshot.selectedTargetId);
        buf.writeVarInt(snapshot.localPlayerEntityId);
        buf.writeBoolean(snapshot.localPlayerEndedTurn);
        writeCombatants(buf, snapshot.players);
        writeCombatants(buf, snapshot.enemies);
        buf.writeVarInt(snapshot.drawPile);
        buf.writeVarInt(snapshot.discardPile);
        buf.writeVarInt(snapshot.exhaustPile);
        buf.writeVarLong(snapshot.localDeckVersion);
        writeCards(buf, snapshot.hand);
        PendingHandSelectionSnapshot.STREAM_CODEC.encode(buf, snapshot.pendingHandSelection);
        writeCards(buf, snapshot.monsterHand);
        writeOptionalCard(buf, snapshot.monsterIntent);
        writeCards(buf, snapshot.monsterIntentCards);
        buf.writeVarInt(snapshot.enemyIntents.size());
        for (BattleEnemyIntentSnapshot intent : snapshot.enemyIntents) {
            BattleEnemyIntentSnapshot.STREAM_CODEC.encode(buf, intent);
        }
        buf.writeVarInt(snapshot.entityHands.size());
        for (BattleEntityCardsSnapshot entityHand : snapshot.entityHands) {
            BattleEntityCardsSnapshot.STREAM_CODEC.encode(buf, entityHand);
        }
        buf.writeVarInt(snapshot.visualEvents.size());
        for (BattleVisualEvent visualEvent : snapshot.visualEvents) {
            BattleVisualEvent.STREAM_CODEC.encode(buf, visualEvent);
        }
    }

    private static BattleSnapshot read(RegistryFriendlyByteBuf buf) {
        UUID battleId = buf.readUUID();
        long sequence = buf.readVarLong();
        boolean active = buf.readBoolean();
        BattlePhase phase = buf.readEnum(BattlePhase.class);
        boolean resolvingEffects = buf.readBoolean();
        int round = buf.readVarInt();
        int selectedTargetId = buf.readVarInt();
        int localPlayerEntityId = buf.readVarInt();
        boolean localPlayerEndedTurn = buf.readBoolean();
        List<BattleCombatantSnapshot> players = readCombatants(buf);
        List<BattleCombatantSnapshot> enemies = readCombatants(buf);
        int drawPile = buf.readVarInt();
        int discardPile = buf.readVarInt();
        int exhaustPile = buf.readVarInt();
        long localDeckVersion = buf.readVarLong();
        List<CardInstance> hand = readCards(buf);
        PendingHandSelectionSnapshot pendingHandSelection = PendingHandSelectionSnapshot.STREAM_CODEC.decode(buf);
        List<CardInstance> monsterHand = readCards(buf);
        CardInstance monsterIntent = readOptionalCard(buf);
        List<CardInstance> monsterIntentCards = readCards(buf);
        int intentCount = Math.min(64, buf.readVarInt());
        List<BattleEnemyIntentSnapshot> enemyIntents = new ArrayList<>(intentCount);
        for (int i = 0; i < intentCount; i++) {
            enemyIntents.add(BattleEnemyIntentSnapshot.STREAM_CODEC.decode(buf));
        }
        int handCount = Math.min(64, buf.readVarInt());
        List<BattleEntityCardsSnapshot> entityHands = new ArrayList<>(handCount);
        for (int i = 0; i < handCount; i++) {
            entityHands.add(BattleEntityCardsSnapshot.STREAM_CODEC.decode(buf));
        }
        int visualCount = Math.min(128, buf.readVarInt());
        List<BattleVisualEvent> visualEvents = new ArrayList<>(visualCount);
        for (int i = 0; i < visualCount; i++) {
            visualEvents.add(BattleVisualEvent.STREAM_CODEC.decode(buf));
        }
        return new BattleSnapshot(battleId, sequence, active, phase, resolvingEffects, round, selectedTargetId, localPlayerEntityId, localPlayerEndedTurn, players, enemies, drawPile, discardPile, exhaustPile, localDeckVersion, hand, pendingHandSelection, monsterHand, monsterIntent, monsterIntentCards, enemyIntents, entityHands, visualEvents);
    }

    private static void writeCombatants(RegistryFriendlyByteBuf buf, List<BattleCombatantSnapshot> combatants) {
        buf.writeVarInt(combatants.size());
        for (BattleCombatantSnapshot combatant : combatants) {
            BattleCombatantSnapshot.STREAM_CODEC.encode(buf, combatant);
        }
    }

    private static List<BattleCombatantSnapshot> readCombatants(RegistryFriendlyByteBuf buf) {
        int size = Math.min(32, buf.readVarInt());
        List<BattleCombatantSnapshot> combatants = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            combatants.add(BattleCombatantSnapshot.STREAM_CODEC.decode(buf));
        }
        return combatants;
    }

    private static void writeCards(RegistryFriendlyByteBuf buf, List<CardInstance> cards) {
        buf.writeVarInt(cards.size());
        for (CardInstance card : cards) {
            CardInstance.STREAM_CODEC.encode(buf, card);
        }
    }

    private static List<CardInstance> readCards(RegistryFriendlyByteBuf buf) {
        int size = Math.min(80, buf.readVarInt());
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
