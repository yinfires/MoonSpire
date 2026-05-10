package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardFactory;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.PlayerCardData;
import com.yinfires.moonspire.network.BattleSnapshotPayload;
import com.yinfires.moonspire.network.BattlePileContentsPayload;
import com.yinfires.moonspire.network.PlayerCardDataPayload;
import com.yinfires.moonspire.registry.ModAttachments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BattleManager {
    private static final double CHALLENGE_RANGE = 10.0D;
    private static final double CHALLENGE_RANGE_SQR = CHALLENGE_RANGE * CHALLENGE_RANGE;
    private static final Map<UUID, BattleState> BY_PLAYER = new HashMap<>();
    private static final Map<Integer, BattleState> BY_ENTITY_ID = new HashMap<>();

    private BattleManager() {
    }

    public static void tick() {
        List<BattleState> finished = new ArrayList<>();
        Set<BattleState> battles = new LinkedHashSet<>(BY_PLAYER.values());
        for (BattleState battle : battles) {
            if (battle.tick()) {
                finished.add(battle);
            }
            if (battle.shouldSyncNow()) {
                sync(battle);
            }
        }
        for (BattleState battle : finished) {
            endBattle(battle);
        }
    }

    public static boolean isInBattle(LivingEntity entity) {
        return battleFor(entity) != null;
    }

    public static BattleState battleFor(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            return BY_PLAYER.get(player.getUUID());
        }
        return BY_ENTITY_ID.get(entity.getId());
    }

    public static void challenge(ServerPlayer player, int targetId) {
        Entity entity = player.level().getEntity(targetId);
        if (!(entity instanceof LivingEntity monster)) {
            player.displayClientMessage(Component.translatable("message.moonspire.no_target"), true);
            return;
        }
        if (!canChallenge(player, monster)) {
            return;
        }
        PlayerCardData data = player.getData(ModAttachments.PLAYER_CARDS.get());
        if (!data.hasValidDeck()) {
            player.displayClientMessage(Component.translatable("message.moonspire.invalid_deck", data.validDeckSize()), true);
            syncCardData(player);
            return;
        }
        List<ServerPlayer> players = collectPlayers(player);
        if (!players.contains(player)) {
            players.add(0, player);
        }
        List<LivingEntity> enemies = collectEnemies(players, monster);
        Map<UUID, List<CardInstance>> playerCards = new LinkedHashMap<>();
        for (ServerPlayer participant : players) {
            PlayerCardData participantData = participant.getData(ModAttachments.PLAYER_CARDS.get());
            if (participantData.hasValidDeck()) {
                playerCards.put(participant.getUUID(), participantData.deckCards());
            }
        }
        Map<Integer, List<CardInstance>> enemyCards = new LinkedHashMap<>();
        for (LivingEntity enemy : enemies) {
            enemyCards.put(enemy.getId(), MonsterDeckProfile.createDeck(enemy));
        }
        BattleState battle = new BattleState(player, players, enemies, playerCards, enemyCards);
        for (ServerPlayer participant : battle.players()) {
            BY_PLAYER.put(participant.getUUID(), battle);
            BY_ENTITY_ID.put(participant.getId(), battle);
        }
        for (LivingEntity participant : battle.entities()) {
            BY_ENTITY_ID.put(participant.getId(), battle);
        }
        battle.start();
        sync(battle);
        player.displayClientMessage(Component.translatable("message.moonspire.battle_started", monster.getDisplayName()), true);
    }

    public static void prepare(ServerPlayer player, List<Integer> handIndexes) {
        endTurn(player);
    }

    public static void usePreparedCard(ServerPlayer player, int handIndex, int targetId) {
        useCard(player, handIndex, targetId);
    }

    public static void useCard(ServerPlayer player, int handIndex, int targetId) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            battle.usePlayerCard(player, handIndex, targetId);
            sync(battle);
        }
    }

    public static void setThinking(ServerPlayer player, boolean thinking) {
        // The turn-based battle screen no longer pauses with Tab.
    }

    public static void endTurn(ServerPlayer player) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            battle.endPlayerTurn(player);
            sync(battle);
        }
    }

    public static void cancelBattle(ServerPlayer player) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            endBattle(battle);
        }
    }

    public static void selectTarget(ServerPlayer player, int targetId) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            battle.selectTarget(player, targetId);
            sync(battle);
        }
    }

    public static void confirmHandSelection(ServerPlayer player, List<UUID> cardIds) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            battle.confirmHandSelection(player, cardIds);
            sync(battle);
        }
    }

    public static void requestPile(ServerPlayer player, UUID battleId, BattlePileSource source, long deckVersion) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle == null || source == null || !battle.matchesId(battleId)) {
            return;
        }
        long currentVersion = battle.deckVersionFor(player);
        PacketDistributor.sendToPlayer(player, new BattlePileContentsPayload(
                battle.id(),
                source,
                currentVersion,
                battle.pileCountFor(player, source),
                battle.pileCardsFor(player, source)));
    }

    public static boolean handleDamage(LivingEntity target, Entity sourceEntity) {
        BattleState battle = battleFor(target);
        if (battle == null) {
            return false;
        }
        if (battle.suppressDamageEvent()) {
            return false;
        }
        if (sourceEntity instanceof LivingEntity attacker && battle.involves(attacker)) {
            if (!(attacker instanceof ServerPlayer)) {
                battle.handleAttack(attacker, target);
            }
            sync(battle);
        } else if (sourceEntity instanceof LivingEntity attacker) {
            battle.pacifyOutsideAttacker(attacker);
        }
        return battle.involves(target);
    }

    public static void cleanup(LivingEntity entity) {
        BattleState battle = battleFor(entity);
        if (battle != null) {
            endBattle(battle);
        }
    }

    public static void syncCardData(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new PlayerCardDataPayload(player.getData(ModAttachments.PLAYER_CARDS.get())));
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            sync(battle);
        }
    }

    public static void convertInventorySlot(ServerPlayer player, int slot, BlockPos forgePos) {
        if (isInBattle(player)) {
            return;
        }
        if (!player.blockPosition().closerThan(forgePos, 8.0D)) {
            player.displayClientMessage(Component.translatable("message.moonspire.forge_too_far"), true);
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (!CardFactory.canConvert(stack)) {
            player.displayClientMessage(Component.translatable("message.moonspire.not_convertible"), true);
            return;
        }
        PlayerCardData data = player.getData(ModAttachments.PLAYER_CARDS.get());
        CardInstance card = CardFactory.fromItem(stack);
        if (card == null) {
            player.displayClientMessage(Component.translatable("message.moonspire.not_convertible"), true);
            return;
        }
        data.addCard(card);
        player.setData(ModAttachments.PLAYER_CARDS.get(), data);
        stack.shrink(1);
        player.getInventory().setChanged();
        player.syncData(ModAttachments.PLAYER_CARDS.get());
        syncCardData(player);
        player.displayClientMessage(Component.translatable("message.moonspire.card_created"), true);
    }

    public static void setDeck(ServerPlayer player, List<UUID> cardIds) {
        if (isInBattle(player)) {
            return;
        }
        PlayerCardData data = player.getData(ModAttachments.PLAYER_CARDS.get());
        data.setDeck(cardIds);
        player.setData(ModAttachments.PLAYER_CARDS.get(), data);
        player.syncData(ModAttachments.PLAYER_CARDS.get());
        syncCardData(player);
    }

    private static boolean canChallenge(ServerPlayer player, LivingEntity target) {
        if (!target.isAlive() || target == player || !MonsterDeckProfile.hasBattleDeck(target)) {
            player.displayClientMessage(Component.translatable("message.moonspire.invalid_target"), true);
            return false;
        }
        if (isInBattle(player) || isInBattle(target)) {
            player.displayClientMessage(Component.translatable("message.moonspire.already_in_battle"), true);
            return false;
        }
        if (player.distanceToSqr(target) > CHALLENGE_RANGE_SQR) {
            player.displayClientMessage(Component.translatable("message.moonspire.target_too_far"), true);
            return false;
        }
        return true;
    }

    private static List<ServerPlayer> collectPlayers(ServerPlayer challenger) {
        List<ServerPlayer> players = new ArrayList<>();
        AABB area = challenger.getBoundingBox().inflate(CHALLENGE_RANGE);
        for (ServerPlayer nearby : challenger.level().getEntitiesOfClass(ServerPlayer.class, area)) {
            if (nearby.isSpectator() || nearby.hasDisconnected() || isInBattle(nearby) || challenger.distanceToSqr(nearby) > CHALLENGE_RANGE_SQR) {
                continue;
            }
            PlayerCardData data = nearby.getData(ModAttachments.PLAYER_CARDS.get());
            if (data.hasValidDeck()) {
                players.add(nearby);
            }
        }
        return players;
    }

    private static List<LivingEntity> collectEnemies(List<ServerPlayer> players, LivingEntity challengedTarget) {
        List<LivingEntity> enemies = new ArrayList<>();
        enemies.add(challengedTarget);
        AABB area = challengedTarget.getBoundingBox().inflate(CHALLENGE_RANGE);
        for (ServerPlayer player : players) {
            area = area.minmax(player.getBoundingBox().inflate(CHALLENGE_RANGE));
        }
        for (Mob mob : challengedTarget.level().getEntitiesOfClass(Mob.class, area)) {
            if (mob == challengedTarget || !mob.isAlive() || isInBattle(mob) || !MonsterDeckProfile.hasBattleDeck(mob)) {
                continue;
            }
            if (players.stream().anyMatch(player -> mob.distanceToSqr(player) <= CHALLENGE_RANGE_SQR && hostileTo(mob, player))) {
                enemies.add(mob);
            }
        }
        return enemies;
    }

    private static boolean hostileTo(Mob mob, ServerPlayer player) {
        return mob.getTarget() == player || mob.getLastHurtByMob() == player || mob.getLastHurtMob() == player;
    }

    private static void sync(BattleState battle) {
        battle.nextSnapshotSequence();
        for (ServerPlayer player : battle.players()) {
            PacketDistributor.sendToPlayer(player, new BattleSnapshotPayload(battle.snapshotFor(player)));
        }
        battle.clearPendingVisualEvents();
    }

    private static void endBattle(BattleState battle) {
        for (ServerPlayer player : battle.players()) {
            BY_PLAYER.remove(player.getUUID());
            BY_ENTITY_ID.remove(player.getId());
        }
        for (LivingEntity entity : battle.entities()) {
            BY_ENTITY_ID.remove(entity.getId());
        }
        battle.finish();
        long sequence = battle.nextSnapshotSequence();
        for (ServerPlayer player : battle.players()) {
            PacketDistributor.sendToPlayer(player, new BattleSnapshotPayload(BattleSnapshot.inactive(battle.id(), sequence)));
        }
    }
}
