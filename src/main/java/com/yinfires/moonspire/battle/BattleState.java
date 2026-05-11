package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.MoonSpirePerfDiagnostics;
import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.CardTarget;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import com.yinfires.moonspire.developer.DeveloperMonsterDefinition;
import com.yinfires.moonspire.developer.DeveloperMonsterInitialEffect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BattleState {
    private static final int MONSTER_ACTION_DELAY_TICKS = 14;
    private static final int ROUND_END_DELAY_TICKS = 8;
    private static final int KNOCKBACK_RELEASE_TICKS = 24;
    private static final int MIN_KNOCKBACK_RELEASE_TICKS = 4;
    private static final int CARD_EFFECT_START_DELAY_TICKS = 10;
    private static final int REPEATED_EFFECT_VISUAL_INTERVAL_TICKS = 14;
    private static final int MELEE_LUNGE_TICKS = 8;
    private static final int MELEE_RECOVER_TICKS = 6;
    private static final int BOW_DRAW_TICKS = 20;
    private static final int CROSSBOW_LOAD_TICKS = 25;
    private static final int MIN_PROJECTILE_TICKS = 5;
    private static final int MAX_PROJECTILE_TICKS = 18;
    private static final int IDLE_SYNC_HEARTBEAT_TICKS = 100;
    private static final double PROJECTILE_BLOCK_DISTANCE = 0.7D;
    private static final double LUNGE_STOP_DISTANCE = 1.55D;
    private static final double LUNGE_REACH = 1.35D;

    private final UUID id = UUID.randomUUID();
    private final ServerPlayer leader;
    private final List<CombatantState> playerStates = new ArrayList<>();
    private final List<CombatantState> enemyStates = new ArrayList<>();
    private final Map<Integer, CombatantState> byEntityId = new HashMap<>();
    private final Map<UUID, CombatantState> byPlayerId = new HashMap<>();
    private final Map<Integer, EntityLock> locks = new HashMap<>();
    private final Map<Integer, Integer> knockbackTicks = new HashMap<>();
    private final Vec3 cameraCenter;
    private BattlePhase phase = BattlePhase.PLAYER_TURN;
    private int round = 1;
    private int phaseTicks;
    private final Map<UUID, Integer> selectedTargets = new HashMap<>();
    private int currentEnemyIndex;
    private int monsterActionDelay;
    private List<CombatantState> enemyTurnOrder = List.of();
    private int openingProtectionTicks;
    private boolean suppressDamageEvent;
    private boolean started;
    private boolean endingAfterAnimations;
    private int syncCooldownTicks;
    private String lastSuppressedSyncReason = "";
    private int suppressedSyncLogTicks;
    private boolean syncDirty = true;
    private long snapshotSequence;
    private final List<BattleVisualEvent> pendingVisualEvents = new ArrayList<>();
    private final List<PendingCardBatch> pendingCardBatches = new ArrayList<>();
    private final List<PendingCardStep> pendingCardSteps = new ArrayList<>();
    private int pendingCardBatchDelay;
    private BattleAnimation pendingAnimation;
    private CombatantState pendingUsedCardOwner;
    private CardInstance pendingUsedCard;
    private PendingHandSelection pendingHandSelection;

    public BattleState(ServerPlayer leader, List<ServerPlayer> players, List<LivingEntity> enemies, Map<UUID, List<CardInstance>> playerCards, Map<Integer, List<CardInstance>> enemyCards) {
        this.leader = leader;
        RandomSource random = leader.getRandom();
        Vec3 total = Vec3.ZERO;
        int totalCount = 0;
        for (ServerPlayer player : players) {
            CombatantState state = new CombatantState(
                    player,
                    new BattleDeck(playerCards.getOrDefault(player.getUUID(), List.of()), random),
                    CardBalance.fixedEnergy(),
                    Math.max(20.0F, player.getMaxHealth()),
                    CardBalance.PLAYER_BASE_SPEED);
            playerStates.add(state);
            byEntityId.put(player.getId(), state);
            byPlayerId.put(player.getUUID(), state);
            locks.put(player.getId(), EntityLock.capture(player));
            total = total.add(player.position());
            totalCount++;
        }
        for (LivingEntity enemy : enemies) {
            DeveloperMonsterDefinition monsterOverride = DeveloperDataManager.monsterOverride(enemy).orElse(null);
            CombatantState state = new CombatantState(
                    enemy,
                    new BattleDeck(enemyCards.getOrDefault(enemy.getId(), List.of()), random),
                    monsterOverride != null && monsterOverride.hasEnergyOverride() ? monsterOverride.energy() : CardBalance.fixedEnergy(),
                    monsterOverride != null && monsterOverride.hasHealthOverride() ? monsterOverride.maxHealth() : Math.max(1.0F, enemy.getMaxHealth()),
                    monsterOverride != null && monsterOverride.hasSpeedOverride() ? monsterOverride.speed() : nonPlayerBaseSpeed(enemy));
            applyInitialEffects(state, monsterOverride);
            enemyStates.add(state);
            byEntityId.put(enemy.getId(), state);
            locks.put(enemy.getId(), EntityLock.capture(enemy));
            total = total.add(enemy.position());
            totalCount++;
        }
        cameraCenter = totalCount <= 0 ? leader.position() : total.scale(1.0D / totalCount);
    }

    private static void applyInitialEffects(CombatantState state, DeveloperMonsterDefinition monsterOverride) {
        if (state == null || monsterOverride == null) {
            return;
        }
        for (DeveloperMonsterInitialEffect effect : monsterOverride.initialEffects()) {
            if (effect == null || !effect.isEffective()) {
                continue;
            }
            effect.effectType().ifPresent(type -> state.addEffect(type, effect.amount()));
        }
    }

    public UUID id() {
        return id;
    }

    public ServerPlayer player() {
        return leader;
    }

    public LivingEntity monster() {
        return enemyStates.isEmpty() ? leader : enemyStates.getFirst().entity();
    }

    public List<ServerPlayer> players() {
        List<ServerPlayer> players = new ArrayList<>();
        for (CombatantState state : playerStates) {
            if (state.entity() instanceof ServerPlayer player) {
                players.add(player);
            }
        }
        return players;
    }

    public List<LivingEntity> entities() {
        List<LivingEntity> entities = new ArrayList<>();
        for (CombatantState state : playerStates) {
            entities.add(state.entity());
        }
        for (CombatantState state : enemyStates) {
            entities.add(state.entity());
        }
        return entities;
    }

    public Vec3 cameraCenter() {
        return cameraCenter;
    }

    public boolean suppressDamageEvent() {
        return suppressDamageEvent;
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        beginBattle();
    }

    public boolean involves(LivingEntity entity) {
        return entity != null && byEntityId.containsKey(entity.getId());
    }

    public CombatantState stateFor(LivingEntity entity) {
        return entity == null ? null : byEntityId.get(entity.getId());
    }

    public boolean tick() {
        if (playerStates.stream().noneMatch(state -> state.entity() instanceof ServerPlayer player && !player.hasDisconnected())) {
            return true;
        }
        tickFakeDeaths();
        if (endingAfterAnimations) {
            lockBattleEntities();
            return allFakeDeathAnimationsDone();
        }
        if (hasWinner()) {
            endingAfterAnimations = true;
            markDirty();
            lockBattleEntities();
            return allFakeDeathAnimationsDone();
        }
        lockBattleEntities();
        pacifyOutsideHostiles();
        protectOpeningHealth();
        tickPendingCardBatches();
        phaseTicks++;
        if (hasPendingCardBatches()) {
            return false;
        }
        if (phase == BattlePhase.MONSTER_TURN) {
            tickMonsterTurn();
        } else if (phase == BattlePhase.ROUND_END && phaseTicks >= ROUND_END_DELAY_TICKS) {
            beginRound();
            beginPlayerTurn();
        }
        if (hasWinner()) {
            endingAfterAnimations = true;
            markDirty();
        }
        return endingAfterAnimations && allFakeDeathAnimationsDone();
    }

    public void finish() {
        for (CombatantState state : allStates()) {
            EntityLock lock = locks.get(state.entity().getId());
            if (lock != null) {
                lock.restoreBeforeDeath(state.entity());
            }
            state.entity().setGlowingTag(false);
        }
        for (CombatantState state : enemyStates) {
            if (state.fakeDead() && state.entity().isAlive()) {
                applyTrueDeath(state);
            }
        }
        for (CombatantState state : playerStates) {
            if (state.fakeDead()) {
                applyTrueDeath(state);
                if (state.entity().isAlive()) {
                    recoverBlockedPlayerDeath(state);
                }
            }
        }
        for (CombatantState state : allStates()) {
            EntityLock lock = locks.get(state.entity().getId());
            if (lock != null && !state.fakeDead() && state.entity().isAlive()) {
                lock.restoreSurvivor(state.entity());
            }
        }
    }

    public void selectTarget(ServerPlayer player, int targetEntityId) {
        CombatantState actor = byPlayerId.get(player.getUUID());
        if (actor == null || actor.fakeDead()) {
            return;
        }
        CombatantState target = byEntityId.get(targetEntityId);
        if (target != null && !target.fakeDead() && enemyStates.contains(target)) {
            selectedTargets.compute(player.getUUID(), (ignored, current) -> current != null && current == targetEntityId ? -1 : targetEntityId);
        } else {
            selectedTargets.put(player.getUUID(), -1);
        }
        markDirty();
    }

    public boolean usePlayerCard(ServerPlayer player, int handIndex, int targetEntityId) {
        CombatantState user = byPlayerId.get(player.getUUID());
        if (user == null || user.fakeDead() || user.endedTurn() || phase != BattlePhase.PLAYER_TURN || hasPendingCardBatches()) {
            return false;
        }
        CardInstance card = user.deck().peekHand(handIndex);
        if (card == null || !canUseCard(user, card)) {
            return false;
        }
        if (card.requiresExplicitTarget() && !validExplicitTarget(user, targetEntityId, card)) {
            return false;
        }
        if (!user.spendEnergy(card.cost())) {
            return false;
        }
        CardInstance used = user.deck().useHand(handIndex);
        if (used == null) {
            return false;
        }
        CombatantState selectedTarget = byEntityId.getOrDefault(targetEntityId, firstAliveEnemy());
        if (selectedTarget == null || selectedTarget.fakeDead()) {
            selectedTarget = firstAliveEnemy();
        }
        queueCard(user, selectedTarget, used);
        markDirty();
        return true;
    }

    public boolean confirmHandSelection(ServerPlayer player, List<UUID> cardIds) {
        CombatantState user = byPlayerId.get(player.getUUID());
        if (pendingHandSelection == null || pendingHandSelection.user() != user || user == null || user.fakeDead()) {
            return false;
        }
        List<UUID> selectedIds = List.copyOf(cardIds == null ? List.of() : cardIds);
        List<UUID> currentCandidateIds = currentPendingHandCandidateIds();
        Set<UUID> uniqueIds = new LinkedHashSet<>(selectedIds);
        boolean validSelection = selectedIds.size() == pendingHandSelection.requiredCount()
                && uniqueIds.size() == selectedIds.size()
                && currentCandidateIds.containsAll(selectedIds);
        if (validSelection) {
            List<CardInstance> selectedCards = pendingHandSelection.user().deck().removeHandByIds(selectedIds);
            if (selectedCards.size() == selectedIds.size()) {
                completePendingHandSelection(selectedCards);
                markDirty();
                return true;
            }
            currentCandidateIds = currentPendingHandCandidateIds();
        }
        if (currentCandidateIds.size() <= pendingHandSelection.requiredCount()) {
            List<CardInstance> selectedCards = pendingHandSelection.user().deck().removeHandByIds(currentCandidateIds);
            completePendingHandSelection(selectedCards);
            markDirty();
            return true;
        }
        return false;
    }

    public void endPlayerTurn(ServerPlayer player) {
        CombatantState state = byPlayerId.get(player.getUUID());
        if (state == null || state.fakeDead() || phase != BattlePhase.PLAYER_TURN || hasPendingCardBatches()) {
            return;
        }
        state.setEndedTurn(true);
        markDirty();
        if (alivePlayers().stream().allMatch(CombatantState::endedTurn)) {
            for (CombatantState playerState : alivePlayers()) {
                applyOwnTurnEndEffects(playerState);
                playerState.reduceRetainedCardCosts();
                playerState.deck().discardHand(true);
            }
            beginMonsterTurn();
        }
    }

    public void handleAttack(LivingEntity attacker, LivingEntity target) {
        // Vanilla combat is canceled while the card battle owns combat resolution.
    }

    public void pacifyOutsideAttacker(LivingEntity attacker) {
        if (attacker instanceof Mob mob && !inSameBattleMob(mob)) {
            pacifyMobAgainstBattle(mob);
        }
    }

    public BattleSnapshot snapshotFor(ServerPlayer viewer) {
        long start = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        CombatantState local = byPlayerId.get(viewer.getUUID());
        if (local == null) {
            local = playerStates.isEmpty() ? null : playerStates.getFirst();
        }
        CombatantState firstEnemy = firstAliveEnemy();
        if (firstEnemy == null && !enemyStates.isEmpty()) {
            firstEnemy = enemyStates.getFirst();
        }
        long visualStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        List<BattleVisualEvent> visualEvents = List.copyOf(pendingVisualEvents);
        long visualNanos = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() - visualStart : 0L;
        long playersStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        List<BattleCombatantSnapshot> players = playerSnapshots();
        long playersNanos = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() - playersStart : 0L;
        long enemiesStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        List<BattleCombatantSnapshot> enemies = enemySnapshots();
        long enemiesNanos = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() - enemiesStart : 0L;
        long handStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        List<CardInstance> localHand = local == null ? List.of() : List.copyOf(local.deck().hand());
        long handNanos = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() - handStart : 0L;
        long monsterStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        List<CardInstance> monsterHand = firstEnemy == null ? List.of() : List.copyOf(firstEnemy.deck().hand());
        List<CardInstance> monsterIntentCards = firstEnemy == null ? List.of() : monsterIntentCards(firstEnemy);
        CardInstance monsterIntent = monsterIntentCards.isEmpty() ? null : monsterIntentCards.getFirst();
        long monsterNanos = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() - monsterStart : 0L;
        long enemyIntentsStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        List<BattleEnemyIntentSnapshot> enemyIntents = enemyIntentSnapshots(firstEnemy);
        long enemyIntentsNanos = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() - enemyIntentsStart : 0L;
        long entityHandsStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        List<BattleEntityCardsSnapshot> entityHands = entityHandSnapshots(local, firstEnemy);
        long entityHandsNanos = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() - entityHandsStart : 0L;
        BattleSnapshot snapshot = new BattleSnapshot(
                id,
                snapshotSequence,
                true,
                phase,
                hasPendingCardBatches(),
                round,
                selectedTargets.getOrDefault(viewer.getUUID(), -1),
                local == null ? -1 : local.entity().getId(),
                local != null && local.endedTurn(),
                players,
                enemies,
                local == null ? 0 : local.deck().drawPile().size(),
                local == null ? 0 : local.deck().discardPile().size(),
                local == null ? 0 : local.deck().exhaustPile().size(),
                local == null ? 0L : local.deck().version(),
                localHand,
                pendingHandSelectionSnapshotFor(local),
                monsterHand,
                monsterIntent,
                monsterIntentCards,
                enemyIntents,
                entityHands,
                visualEvents);
        if (MoonSpirePerfDiagnostics.enabled()) {
            int entityHandCards = entityHands.stream().mapToInt(entityHand -> entityHand.cards().size()).sum();
            int intentCards = monsterIntentCards.size() + enemyIntents.stream().mapToInt(intent -> intent.cards().size()).sum();
            MoonSpirePerfDiagnostics.markOperation("server.battle.snapshotFor.detail", MoonSpirePerfDiagnostics.now() - start,
                    "battleId=" + id
                            + " sequence=" + snapshotSequence
                            + " playerMs=" + MoonSpirePerfDiagnostics.millis(playersNanos)
                            + " enemyMs=" + MoonSpirePerfDiagnostics.millis(enemiesNanos)
                            + " handMs=" + MoonSpirePerfDiagnostics.millis(handNanos)
                            + " monsterMs=" + MoonSpirePerfDiagnostics.millis(monsterNanos)
                            + " enemyIntentMs=" + MoonSpirePerfDiagnostics.millis(enemyIntentsNanos)
                            + " entityHandMs=" + MoonSpirePerfDiagnostics.millis(entityHandsNanos)
                            + " visualMs=" + MoonSpirePerfDiagnostics.millis(visualNanos)
                            + " hand=" + localHand.size()
                            + " entityHands=" + entityHands.size()
                            + " entityHandCards=" + entityHandCards
                            + " intentCards=" + intentCards
                            + " visualEvents=" + visualEvents.size());
        }
        return snapshot;
    }

    public long nextSnapshotSequence() {
        return ++snapshotSequence;
    }

    public boolean matchesId(UUID battleId) {
        return id.equals(battleId);
    }

    public List<CardInstance> pileCardsFor(ServerPlayer viewer, BattlePileSource source) {
        CombatantState local = byPlayerId.get(viewer.getUUID());
        if (local == null || source == null) {
            return List.of();
        }
        return switch (source) {
            case BATTLE_DECK -> battleDeckCards(local);
            case DRAW -> List.copyOf(local.deck().drawPile());
            case DISCARD -> List.copyOf(local.deck().discardPile());
            case EXHAUST -> List.copyOf(local.deck().exhaustPile());
        };
    }

    public int pileCountFor(ServerPlayer viewer, BattlePileSource source) {
        CombatantState local = byPlayerId.get(viewer.getUUID());
        if (local == null || source == null) {
            return 0;
        }
        return switch (source) {
            case BATTLE_DECK -> local.deck().hand().size() + local.deck().drawPile().size() + local.deck().discardPile().size();
            case DRAW -> local.deck().drawPile().size();
            case DISCARD -> local.deck().discardPile().size();
            case EXHAUST -> local.deck().exhaustPile().size();
        };
    }

    public long deckVersionFor(ServerPlayer viewer) {
        CombatantState local = byPlayerId.get(viewer.getUUID());
        return local == null ? 0L : local.deck().version();
    }

    private List<CardInstance> battleDeckCards(CombatantState local) {
        List<CardInstance> cards = new ArrayList<>(local.deck().hand().size() + local.deck().drawPile().size() + local.deck().discardPile().size());
        cards.addAll(local.deck().hand());
        cards.addAll(local.deck().drawPile());
        cards.addAll(local.deck().discardPile());
        return List.copyOf(cards);
    }

    public boolean shouldSyncNow() {
        if (syncDirty) {
            syncDirty = false;
            syncCooldownTicks = 0;
            logSyncReason("dirty");
            return true;
        }
        if (pendingHandSelection != null) {
            if (!pendingVisualEvents.isEmpty()) {
                syncCooldownTicks = 0;
                logSyncReason("pending-visual");
                return true;
            }
            syncCooldownTicks = 0;
            logSyncSuppressed("pending-hand-selection");
            return false;
        }
        if (!pendingCardBatches.isEmpty() || !pendingCardSteps.isEmpty() || pendingUsedCard != null) {
            if (!pendingVisualEvents.isEmpty()) {
                syncCooldownTicks = 0;
                logSyncReason("resolving-visual");
                return true;
            }
            syncCooldownTicks = 0;
            logSyncSuppressed("resolving-effects");
            return false;
        }
        if (!pendingVisualEvents.isEmpty() || endingAfterAnimations) {
            syncCooldownTicks = 0;
            logSyncReason(endingAfterAnimations ? "ending" : "visual");
            return true;
        }
        syncCooldownTicks++;
        if (syncCooldownTicks >= IDLE_SYNC_HEARTBEAT_TICKS) {
            syncCooldownTicks = 0;
            logSyncReason("heartbeat");
            return true;
        }
        return false;
    }

    private void logSyncReason(String reason) {
        lastSuppressedSyncReason = "";
        suppressedSyncLogTicks = 0;
        if (MoonSpirePerfDiagnostics.enabled()) {
            MoonSpirePerfDiagnostics.log("server.battle.shouldSync", "battleId=" + id + " sequence=" + snapshotSequence + " reason=" + reason);
        }
    }

    private void logSyncSuppressed(String reason) {
        if (!MoonSpirePerfDiagnostics.enabled()) {
            return;
        }
        suppressedSyncLogTicks++;
        if (reason.equals(lastSuppressedSyncReason) && suppressedSyncLogTicks < IDLE_SYNC_HEARTBEAT_TICKS) {
            return;
        }
        lastSuppressedSyncReason = reason;
        suppressedSyncLogTicks = 0;
        MoonSpirePerfDiagnostics.log("server.battle.shouldSync", "battleId=" + id + " sequence=" + snapshotSequence + " reason=pending-suppressed detail=" + reason);
    }

    private void markDirty() {
        syncDirty = true;
    }

    public void clearSyncDirty() {
        syncDirty = false;
        syncCooldownTicks = 0;
        lastSuppressedSyncReason = "";
        suppressedSyncLogTicks = 0;
    }

    public void clearPendingVisualEvents() {
        pendingVisualEvents.clear();
    }

    private void beginBattle() {
        freezeAi();
        clearParticipantHandAnimations();
        resetParticipantsToStart();
        openingProtectionTicks = 20;
        for (CombatantState state : allStates()) {
            state.entity().invulnerableTime = Math.max(state.entity().invulnerableTime, openingProtectionTicks);
        }
        beginRound();
        beginPlayerTurn();
    }

    private void clearParticipantHandAnimations() {
        for (CombatantState state : allStates()) {
            LivingEntity entity = state.entity();
            if (entity.isUsingItem()) {
                entity.stopUsingItem();
            }
            entity.swinging = false;
            entity.swingTime = 0;
            entity.attackAnim = 0.0F;
            entity.oAttackAnim = 0.0F;
        }
    }

    private void beginRound() {
        for (CombatantState state : allStates()) {
            if (!state.fakeDead()) {
                state.rollRoundSpeed(leader.getRandom());
            }
        }
        resetParticipantsToStart();
    }

    private void beginPlayerTurn() {
        phase = BattlePhase.PLAYER_TURN;
        phaseTicks = 0;
        markDirty();
        for (CombatantState state : playerStates) {
            if (!state.fakeDead()) {
                state.clearDefense();
                applyOwnTurnStartEffects(state);
                if (state.fakeDead()) {
                    continue;
                }
                state.resetEnergy();
                state.setEndedTurn(false);
                state.deck().startTurn(leader.getRandom());
            }
        }
        for (CombatantState state : enemyStates) {
            if (!state.fakeDead()) {
                state.deck().startTurn(leader.getRandom());
            }
        }
        selectedTargets.clear();
    }

    private void beginMonsterTurn() {
        phase = BattlePhase.MONSTER_TURN;
        phaseTicks = 0;
        markDirty();
        monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
        for (CombatantState state : enemyStates) {
            if (!state.fakeDead()) {
                state.clearDefense();
                applyOwnTurnStartEffects(state);
                if (state.fakeDead()) {
                    continue;
                }
                state.resetEnergy();
            }
        }
        enemyTurnOrder = enemyActionOrder();
        currentEnemyIndex = 0;
        selectedTargets.clear();
    }

    private void beginRoundEnd() {
        phase = BattlePhase.ROUND_END;
        phaseTicks = 0;
        markDirty();
        for (CombatantState state : aliveEnemies()) {
            applyOwnTurnEndEffects(state);
            state.reduceRetainedCardCosts();
            state.deck().discardHand(true);
        }
        resetParticipantsToStart();
        round++;
    }

    private void tickMonsterTurn() {
        if (hasPendingCardBatches()) {
            return;
        }
        if (monsterActionDelay > 0) {
            monsterActionDelay--;
            return;
        }
        while (currentEnemyIndex < enemyTurnOrder.size() && enemyTurnOrder.get(currentEnemyIndex).fakeDead()) {
            currentEnemyIndex++;
        }
        if (currentEnemyIndex >= enemyTurnOrder.size()) {
            beginRoundEnd();
            return;
        }
        CombatantState enemy = enemyTurnOrder.get(currentEnemyIndex);
        int index = chooseMonsterCard(enemy);
        if (index < 0) {
            currentEnemyIndex++;
            monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
            return;
        }
        CardInstance card = enemy.deck().peekHand(index);
        if (card == null || !enemy.spendEnergy(card.cost())) {
            currentEnemyIndex++;
            monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
            return;
        }
        CardInstance used = enemy.deck().useHand(index);
        CombatantState target = randomAlivePlayer();
        if (used == null || target == null) {
            currentEnemyIndex++;
            monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
            return;
        }
        queueCard(enemy, target, used);
        monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
    }

    private List<CombatantState> enemyActionOrder() {
        return enemyStates.stream()
                .sorted(Comparator.comparingInt(CombatantState::roundSpeed).reversed())
                .toList();
    }

    private int chooseMonsterCard(CombatantState monster) {
        return chooseMonsterCard(
                monster.deck().hand(),
                monster.energyLeft(),
                monster.defense(),
                monster.battleHealth(),
                monster.maxBattleHealth());
    }

    private CardInstance monsterIntent(CombatantState monster) {
        List<CardInstance> plannedCards = plannedMonsterCards(monster);
        return plannedCards.isEmpty() ? null : plannedCards.getFirst();
    }

    private List<CardInstance> monsterIntentCards(CombatantState monster) {
        if (phase != BattlePhase.MONSTER_TURN && phase != BattlePhase.PLAYER_TURN) {
            return List.of();
        }
        return plannedMonsterCards(monster);
    }

    private List<CardInstance> plannedMonsterCards(CombatantState monster) {
        if (monster == null || monster.fakeDead() || (phase != BattlePhase.MONSTER_TURN && phase != BattlePhase.PLAYER_TURN)) {
            return List.of();
        }
        List<CardInstance> virtualHand = new ArrayList<>(monster.deck().hand());
        int energyLeft = phase == BattlePhase.PLAYER_TURN ? monster.maxEnergy() : monster.energyLeft();
        int defense = phase == BattlePhase.PLAYER_TURN ? 0 : monster.defense();
        float health = monster.battleHealth();
        List<CardInstance> planned = new ArrayList<>();
        while (!virtualHand.isEmpty() && health > 0.0F) {
            int index = chooseMonsterCard(virtualHand, energyLeft, defense, health, monster.maxBattleHealth());
            if (index < 0) {
                break;
            }
            CardInstance card = virtualHand.remove(index);
            planned.add(card);
            energyLeft -= Math.max(0, card.cost());
            defense += Math.max(0, card.selfEffectAmount(CardEffectKind.BLOCK));
        }
        return planned;
    }

    private int chooseMonsterCard(List<CardInstance> hand, int energyLeft, int defense, float health, float maxHealth) {
        if (defense <= 0 && health < maxHealth * 0.65F) {
            int defenseIndex = firstAffordableDefense(hand, energyLeft);
            if (defenseIndex >= 0) {
                return defenseIndex;
            }
        }
        int attackIndex = firstAffordableAttack(hand, energyLeft);
        if (attackIndex >= 0) {
            return attackIndex;
        }
        int defenseIndex = firstAffordableDefense(hand, energyLeft);
        if (defenseIndex >= 0) {
            return defenseIndex;
        }
        return firstAffordableAction(hand, energyLeft);
    }

    private int firstAffordableAttack(List<CardInstance> hand, int energyLeft) {
        for (int i = 0; i < hand.size(); i++) {
            CardInstance card = hand.get(i);
            if (card.hasAttack() && card.cost() <= energyLeft) {
                return i;
            }
        }
        return -1;
    }

    private int firstAffordableDefense(List<CardInstance> hand, int energyLeft) {
        for (int i = 0; i < hand.size(); i++) {
            CardInstance card = hand.get(i);
            if (card.hasDefense() && card.cost() <= energyLeft) {
                return i;
            }
        }
        return -1;
    }

    private int firstAffordableAction(List<CardInstance> hand, int energyLeft) {
        for (int i = 0; i < hand.size(); i++) {
            CardInstance card = hand.get(i);
            if (card.hasAnyEffect() && card.cost() <= energyLeft) {
                return i;
            }
        }
        return -1;
    }

    private boolean canUseCard(CombatantState user, CardInstance card) {
        return !user.fakeDead() && card.cost() <= user.energyLeft() && card.hasAnyEffect();
    }

    private void queueCard(CombatantState user, CombatantState selectedTarget, CardInstance card) {
        CombatantState opponent = selectedTarget == null ? firstOpponent(user) : selectedTarget;
        if (opponent == null) {
            return;
        }
        faceTarget(user.entity(), opponent.entity());
        pendingCardBatches.clear();
        pendingCardSteps.clear();
        pendingCardBatchDelay = 0;
        pendingHandSelection = null;
        pendingUsedCardOwner = user;
        pendingUsedCard = card;
        if (triggersAttackUse(card, user)) {
            PendingEffect bleed = bleedEffectForAttack(user);
            if (bleed != null) {
                pendingCardSteps.add(new PendingBatchStep(new PendingCardBatch(user, card.sourceStack(), ItemStack.EMPTY, null, List.of(bleed))));
            }
        }
        List<CardEffect> currentEffects = new ArrayList<>();
        for (CardEffect effect : card.effects()) {
            if (effect.kind().isKeyword()) {
                continue;
            }
            if (effect.kind() == CardEffectKind.CONSUME_ARROW) {
                if (!currentEffects.isEmpty()) {
                    addEffectSteps(user, selectedTarget, card, currentEffects);
                    currentEffects = new ArrayList<>();
                }
                addConsumeArrowStep(user, selectedTarget, card, effect);
                continue;
            }
            if (effect.kind().isHandSelection()) {
                if (!currentEffects.isEmpty()) {
                    addEffectSteps(user, selectedTarget, card, currentEffects);
                    currentEffects = new ArrayList<>();
                }
                if (effect.amount() > 0) {
                    for (CombatantState effectTarget : targetsForEffect(effect, user, selectedTarget)) {
                        pendingCardSteps.add(new PendingHandSelectionStep(handSelectionAction(effect.kind()), effect.amount(), effectTarget));
                    }
                }
                continue;
            }
            currentEffects.add(effect);
        }
        if (!currentEffects.isEmpty()) {
            addEffectSteps(user, selectedTarget, card, currentEffects);
        }
        advancePendingCardSteps();
        if (!hasPendingCardBatches()) {
            emitVisual(user.entity(), opponent.entity(), card.sourceStack(), card, new BattleDamageResult(0, 0, 0), 0);
            finishPendingUsedCard();
        }
    }

    private void addEffectSteps(CombatantState user, CombatantState selectedTarget, CardInstance card, List<CardEffect> effects) {
        addEffectSteps(user, selectedTarget, card, effects, ItemStack.EMPTY);
    }

    private void addEffectSteps(CombatantState user, CombatantState selectedTarget, CardInstance card, List<CardEffect> effects, ItemStack projectileStack) {
        boolean remoteDamage = card.hasEffect(CardEffectKind.REMOTE);
        int maxCount = 0;
        for (CardEffect effect : effects) {
            if (effect.amount() > 0 && effect.kind().isResolvedEffect()) {
                maxCount = Math.max(maxCount, effect.count());
            }
        }
        for (int repeat = 0; repeat < maxCount; repeat++) {
            List<PendingEffect> batchEffects = new ArrayList<>();
            for (CardEffect effect : effects) {
                if (!effect.kind().isResolvedEffect() || effect.amount() <= 0 || effect.count() <= repeat) {
                    continue;
                }
                for (CombatantState effectTarget : targetsForEffect(effect, user, selectedTarget)) {
                    batchEffects.add(new PendingEffect(effect.kind(), effect.amount(), effectTarget, false, remoteDamage && effect.kind() == CardEffectKind.DAMAGE));
                }
            }
            if (!batchEffects.isEmpty()) {
                pendingCardSteps.add(new PendingBatchStep(new PendingCardBatch(user, card.sourceStack(), projectileStack, card, batchEffects)));
            }
        }
    }

    private void addConsumeArrowStep(CombatantState user, CombatantState selectedTarget, CardInstance card, CardEffect effect) {
        if (effect.amount() <= 0) {
            return;
        }
        List<UUID> arrowIds = user.deck().hand().stream()
                .filter(candidate -> candidate.hasEffect(CardEffectKind.ARROW))
                .map(CardInstance::id)
                .toList();
        if (arrowIds.isEmpty()) {
            return;
        }
        ArrowResolution resolution = new ArrowResolution(user, selectedTarget, card, effect.amount());
        pendingCardSteps.add(new PendingHandSelectionStep(PendingHandSelectionSnapshot.Action.CONSUME_ARROW, 1, user, arrowIds, resolution));
    }

    private boolean triggersAttackUse(CardInstance card, CombatantState user) {
        if (card.hasEnemyEffect(CardEffectKind.DAMAGE)) {
            return true;
        }
        return card.effects().stream().anyMatch(effect -> effect.kind() == CardEffectKind.CONSUME_ARROW && effect.amount() > 0)
                && user.deck().hand().stream().anyMatch(handCard -> handCard.hasEffect(CardEffectKind.ARROW));
    }

    private List<CombatantState> targetsForEffect(CardEffect effect, CombatantState user, CombatantState selectedTarget) {
        if (!effect.kind().usesTarget()) {
            return List.of();
        }
        List<CombatantState> allies = sideOf(user);
        List<CombatantState> enemies = opposingSideOf(user);
        return switch (effect.target()) {
            case SELF -> List.of(user);
            case SINGLE_ENEMY -> List.of(validTargetOrFirst(selectedTarget, enemies));
            case SINGLE_ALLY -> selectedTarget != null && allies.contains(selectedTarget) && selectedTarget != user && !selectedTarget.fakeDead() ? List.of(selectedTarget) : List.of();
            case ALL_ENEMIES -> alive(enemies);
            case RANDOM_ENEMY -> randomTarget(alive(enemies));
            case ALL_ALLIES -> alive(allies);
            case ALL_UNITS -> alive(allStates());
            case ALL_OTHER_UNITS -> alive(allStates()).stream().filter(state -> state != user).toList();
            case ALL_OTHER_ALLIES -> alive(allies).stream().filter(state -> state != user).toList();
            case RANDOM_ALLY -> randomTarget(alive(allies).stream().filter(state -> state != user).toList());
        };
    }

    private CombatantState validTargetOrFirst(CombatantState selectedTarget, List<CombatantState> candidates) {
        if (selectedTarget != null && candidates.contains(selectedTarget) && !selectedTarget.fakeDead()) {
            return selectedTarget;
        }
        return candidates.stream().filter(state -> !state.fakeDead()).findFirst().orElse(null);
    }

    private List<CombatantState> randomTarget(List<CombatantState> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        int maxGlowing = candidates.stream().mapToInt(state -> state.effectAmount(BattleEffectType.GLOWING)).max().orElse(0);
        List<CombatantState> pool = maxGlowing > 0
                ? candidates.stream().filter(state -> state.effectAmount(BattleEffectType.GLOWING) == maxGlowing).toList()
                : candidates;
        return List.of(pool.get(leader.getRandom().nextInt(pool.size())));
    }

    private boolean validExplicitTarget(CombatantState user, int targetEntityId, CardInstance card) {
        CombatantState target = byEntityId.get(targetEntityId);
        if (target == null || target.fakeDead()) {
            return false;
        }
        boolean needsEnemy = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target() == CardTarget.SINGLE_ENEMY);
        boolean needsAlly = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target() == CardTarget.SINGLE_ALLY);
        if (needsEnemy && !opposingSideOf(user).contains(target)) {
            return false;
        }
        if (needsAlly && (!sideOf(user).contains(target) || target == user)) {
            return false;
        }
        return true;
    }

    private void finishUsedCard(CombatantState user, CardInstance card) {
        if (card.effects().stream().anyMatch(effect -> effect.kind() == CardEffectKind.EXHAUST)) {
            user.deck().exhaust(card);
        } else {
            user.deck().discard(card);
        }
    }

    private void finishPendingUsedCard() {
        if (pendingUsedCardOwner != null && pendingUsedCard != null) {
            finishUsedCard(pendingUsedCardOwner, pendingUsedCard);
        }
        pendingUsedCardOwner = null;
        pendingUsedCard = null;
    }

    private void applyBattleKnockback(LivingEntity attacker, LivingEntity target, BattleDamageResult result) {
        if (result.healthDamage() <= 0) {
            return;
        }
        double dx = attacker.getX() - target.getX();
        double dz = attacker.getZ() - target.getZ();
        double strength = target instanceof ServerPlayer ? 0.42D : 0.28D;
        target.knockback(strength, dx, dz);
        knockbackTicks.put(target.getId(), KNOCKBACK_RELEASE_TICKS);
    }

    private ItemStack projectileStackForArrow(CardInstance arrow) {
        ResourceLocation itemId = itemId(arrow.sourceStack());
        if (itemId != null && itemId.equals(BuiltInRegistries.ITEM.getKey(Items.SPECTRAL_ARROW))) {
            return new ItemStack(Items.SPECTRAL_ARROW);
        }
        return new ItemStack(Items.ARROW);
    }

    private ResourceLocation itemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private int rangedPrepareTicks(CardInstance card) {
        ItemStack stack = card.sourceStack();
        if (!stack.isEmpty() && stack.is(Items.CROSSBOW)) {
            return CROSSBOW_LOAD_TICKS;
        }
        return BOW_DRAW_TICKS;
    }

    private BattleVisualEvent.AnimationType rangedAnimationType(CardInstance card) {
        ItemStack stack = card.sourceStack();
        return !stack.isEmpty() && stack.is(Items.CROSSBOW)
                ? BattleVisualEvent.AnimationType.CROSSBOW_LOAD
                : BattleVisualEvent.AnimationType.BOW_DRAW;
    }

    private PendingEffect bleedEffectForAttack(CombatantState user) {
        int bleed = user.effectAmount(BattleEffectType.BLEED);
        if (bleed <= 0) {
            return null;
        }
        return new PendingEffect(CardEffectKind.DAMAGE, bleed, user, true, false);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, CardInstance playedCard, BattleDamageResult result, int delayTicks) {
        emitVisual(attacker, target, stack, ItemStack.EMPTY, playedCard, result, 0, 0, delayTicks, BattleVisualEvent.AnimationType.NONE, 0);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, CardInstance playedCard, BattleDamageResult result, int gainedBlock, int delayTicks) {
        emitVisual(attacker, target, stack, ItemStack.EMPTY, playedCard, result, gainedBlock, 0, delayTicks, BattleVisualEvent.AnimationType.NONE, 0);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, CardInstance playedCard, BattleDamageResult result, int gainedBlock, int healedHealth, int delayTicks) {
        emitVisual(attacker, target, stack, ItemStack.EMPTY, playedCard, result, gainedBlock, healedHealth, delayTicks, BattleVisualEvent.AnimationType.NONE, 0);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, ItemStack projectileStack, CardInstance playedCard, BattleDamageResult result, int gainedBlock, int healedHealth, int delayTicks, BattleVisualEvent.AnimationType animationType, int animationTicks) {
        pendingVisualEvents.add(new BattleVisualEvent(
                attacker.getId(),
                target.getId(),
                stack.copy(),
                projectileStack.copy(),
                playedCard == null ? null : playedCard.copyForBattle(),
                result.blockedDamage(),
                result.healthDamage(),
                Math.max(0, gainedBlock),
                Math.max(0, healedHealth),
                Math.max(0, delayTicks),
                animationType == null ? BattleVisualEvent.AnimationType.NONE : animationType,
                Math.max(0, animationTicks),
                result.blockedDamage() > 0,
                result.healthDamage() > 0,
                gainedBlock > 0));
        markDirty();
    }

    private boolean hasPendingCardBatches() {
        return pendingAnimation != null || !pendingCardBatches.isEmpty() || !pendingCardSteps.isEmpty() || pendingHandSelection != null || pendingUsedCard != null;
    }

    private void tickPendingCardBatches() {
        if (pendingAnimation != null) {
            if (!pendingAnimation.tick()) {
                return;
            }
            BattleAnimation completed = pendingAnimation;
            pendingAnimation = null;
            completeAnimatedBatch(completed.batch());
            markDirty();
            if (!pendingCardBatches.isEmpty()) {
                pendingCardBatchDelay = REPEATED_EFFECT_VISUAL_INTERVAL_TICKS;
            } else {
                pendingCardBatchDelay = 0;
                advancePendingCardSteps();
                if (pendingCardBatches.isEmpty() && pendingCardSteps.isEmpty() && pendingHandSelection == null) {
                    finishPendingUsedCard();
                }
            }
            return;
        }
        if (pendingHandSelection != null) {
            pendingCardBatchDelay = 0;
            return;
        }
        if (pendingCardBatches.isEmpty() && !pendingCardSteps.isEmpty()) {
            advancePendingCardSteps();
        }
        if (pendingCardBatches.isEmpty()) {
            pendingCardBatchDelay = 0;
            if (pendingCardSteps.isEmpty() && pendingHandSelection == null && pendingUsedCard != null) {
                finishPendingUsedCard();
            }
            return;
        }
        if (pendingCardBatchDelay > 0) {
            pendingCardBatchDelay--;
            return;
        }
        PendingCardBatch batch = pendingCardBatches.remove(0);
        markDirty();
        if (beginBattleAnimation(batch)) {
            return;
        }
        applyPendingCardBatch(batch, false);
        if (pendingCardBatches.isEmpty()) {
            pendingCardBatchDelay = 0;
            advancePendingCardSteps();
            if (pendingCardBatches.isEmpty() && pendingCardSteps.isEmpty() && pendingHandSelection == null) {
                finishPendingUsedCard();
            }
        } else {
            pendingCardBatchDelay = REPEATED_EFFECT_VISUAL_INTERVAL_TICKS;
        }
    }

    private boolean beginBattleAnimation(PendingCardBatch batch) {
        PendingEffect animated = firstAnimatedEffect(batch);
        if (animated == null) {
            return false;
        }
        if (batch.card() != null && batch.card().hasEffect(CardEffectKind.REMOTE)) {
            ItemStack projectileStack = batch.projectileStack().isEmpty() ? new ItemStack(Items.ARROW) : batch.projectileStack();
            int drawTicks = rangedPrepareTicks(batch.card());
            emitVisual(batch.user().entity(), animated.target().entity(), batch.stack(), projectileStack, batch.card(), new BattleDamageResult(0, 0, 0), 0, 0, 0, rangedAnimationType(batch.card()), drawTicks);
            pendingAnimation = new ProjectileAnimation(batch, animated.target(), projectileStack, drawTicks);
            return true;
        }
        return false;
    }

    private PendingEffect firstAnimatedEffect(PendingCardBatch batch) {
        for (PendingEffect effect : batch.effects()) {
            if (effect.kind() == CardEffectKind.DAMAGE && !effect.effectDamage() && effect.target() != null && !effect.target().fakeDead()) {
                return effect;
            }
        }
        return null;
    }

    private void completeAnimatedBatch(PendingCardBatch batch) {
        applyPendingCardBatch(batch, true);
    }

    private void advancePendingCardSteps() {
        while (pendingHandSelection == null && pendingCardBatches.isEmpty() && !pendingCardSteps.isEmpty()) {
            PendingCardStep step = pendingCardSteps.remove(0);
            if (step instanceof PendingBatchStep batchStep) {
                pendingCardBatches.add(batchStep.batch());
                pendingCardBatchDelay = Math.max(pendingCardBatchDelay, CARD_EFFECT_START_DELAY_TICKS);
                markDirty();
                return;
            }
            if (step instanceof PendingHandSelectionStep selectionStep) {
                beginPendingHandSelection(selectionStep);
            }
        }
    }

    private void beginPendingHandSelection(PendingHandSelectionStep step) {
        List<UUID> candidateIds = step.candidateIds().isEmpty()
                ? step.target().deck().hand().stream().map(CardInstance::id).toList()
                : currentCandidateIds(step.target(), step.candidateIds());
        int available = candidateIds.size();
        int required = Math.max(0, step.requiredCount());
        if (required <= 0 || available <= 0 || step.target().fakeDead()) {
            return;
        }
        if (!(step.target().entity() instanceof ServerPlayer) || available <= required) {
            List<UUID> selectedIds = candidateIds.subList(0, Math.min(required, available));
            completeHandSelection(step.target(), step.action(), step.target().deck().removeHandByIds(selectedIds), step.arrowResolution());
            return;
        }
        pendingHandSelection = new PendingHandSelection(step.target(), step.action(), required, candidateIds, step.arrowResolution());
        markDirty();
    }

    private void completePendingHandSelection(List<CardInstance> selectedCards) {
        PendingHandSelection selection = pendingHandSelection;
        pendingHandSelection = null;
        if (selection != null) {
            completeHandSelection(selection.user(), selection.action(), selectedCards, selection.arrowResolution());
        }
        markDirty();
        advancePendingCardSteps();
        if (pendingCardBatches.isEmpty() && pendingCardSteps.isEmpty() && pendingHandSelection == null) {
            finishPendingUsedCard();
        }
    }

    private List<UUID> currentPendingHandCandidateIds() {
        if (pendingHandSelection == null) {
            return List.of();
        }
        Set<UUID> candidateIds = new LinkedHashSet<>(pendingHandSelection.candidateIds());
        List<UUID> currentIds = new ArrayList<>();
        for (CardInstance card : pendingHandSelection.user().deck().hand()) {
            if (candidateIds.contains(card.id())) {
                currentIds.add(card.id());
            }
        }
        return currentIds;
    }

    private void completeHandSelection(CombatantState user, PendingHandSelectionSnapshot.Action action, List<CardInstance> selectedCards, ArrowResolution resolution) {
        if (action == PendingHandSelectionSnapshot.Action.EXHAUST) {
            user.deck().exhaustAll(selectedCards);
        } else if (action == PendingHandSelectionSnapshot.Action.DISCARD) {
            user.deck().discardAll(selectedCards);
        } else if (action == PendingHandSelectionSnapshot.Action.CONSUME_ARROW) {
            user.deck().exhaustAll(selectedCards);
            if (!selectedCards.isEmpty()) {
                resolveConsumedArrow(resolution, selectedCards.getFirst());
            }
        }
    }

    private List<UUID> currentCandidateIds(CombatantState user, List<UUID> candidateIds) {
        Set<UUID> allowed = new LinkedHashSet<>(candidateIds == null ? List.of() : candidateIds);
        List<UUID> currentIds = new ArrayList<>();
        for (CardInstance card : user.deck().hand()) {
            if (allowed.contains(card.id())) {
                currentIds.add(card.id());
            }
        }
        return currentIds;
    }

    private void resolveConsumedArrow(ArrowResolution resolution, CardInstance arrow) {
        if (resolution == null || arrow == null) {
            return;
        }
        List<CardEffect> effects = new ArrayList<>();
        effects.add(new CardEffect(CardEffectKind.DAMAGE, resolution.amount(), CardTarget.SINGLE_ENEMY));
        for (CardEffect arrowEffect : arrow.effects()) {
            if (arrowEffect.kind() == CardEffectKind.DAMAGE && arrowEffect.target().targetsEnemy()) {
                effects.add(new CardEffect(CardEffectKind.DAMAGE, arrowEffect.amount(), arrowEffect.target(), arrowEffect.count()));
            } else if (arrowEffect.kind() == CardEffectKind.GLOWING) {
                effects.add(arrowEffect);
            }
        }
        addEffectSteps(resolution.user(), resolution.selectedTarget(), resolution.card(), effects, projectileStackForArrow(arrow));
    }

    private PendingHandSelectionSnapshot pendingHandSelectionSnapshotFor(CombatantState local) {
        if (pendingHandSelection == null || pendingHandSelection.user() != local) {
            return PendingHandSelectionSnapshot.NONE;
        }
        return new PendingHandSelectionSnapshot(pendingHandSelection.action(), pendingHandSelection.requiredCount(), pendingHandSelection.user().entity().getId(), pendingHandSelection.candidateIds());
    }

    private void applyPendingCardBatch(PendingCardBatch batch, boolean suppressCardVisual) {
        Map<CombatantState, BattleDamageResult> damageResults = new LinkedHashMap<>();
        Map<CombatantState, Integer> blockGains = new LinkedHashMap<>();
        Map<CombatantState, Integer> heals = new LinkedHashMap<>();
        Set<CombatantState> knockbackTargets = new LinkedHashSet<>();
        Set<CombatantState> effectOnlyTargets = new LinkedHashSet<>();
        UUID killCredit = playerKillCredit(batch.user());
        for (PendingEffect effect : batch.effects()) {
            if (effect.target() == null || effect.target().fakeDead()) {
                continue;
            }
            if (effect.kind() == CardEffectKind.DAMAGE) {
                BattleDamageResult result = effect.effectDamage()
                        ? effect.target().applyEffectDamage(effect.amount(), killCredit)
                        : effect.target().applyCardDamage(effect.amount(), batch.user(), killCredit, effect.remoteDamage());
                damageResults.merge(effect.target(), result, BattleState::mergeDamageResult);
                if (!effect.effectDamage() && result.healthDamage() > 0 && !effect.target().fakeDead()) {
                    knockbackTargets.add(effect.target());
                }
            } else if (effect.kind() == CardEffectKind.BLOCK) {
                effect.target().addDefense(effect.amount());
                blockGains.merge(effect.target(), Math.max(0, effect.amount()), Integer::sum);
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.HEAL) {
                int healed = effect.target().heal(effect.amount());
                if (healed > 0) {
                    heals.merge(effect.target(), healed, Integer::sum);
                }
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.BLEED) {
                effect.target().addEffect(BattleEffectType.BLEED, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.GLOWING) {
                effect.target().addEffect(BattleEffectType.GLOWING, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.GUARD) {
                effect.target().addEffect(BattleEffectType.GUARD, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.STRENGTH) {
                effect.target().addEffect(BattleEffectType.STRENGTH, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.LOSE_STRENGTH) {
                effect.target().addEffect(BattleEffectType.STRENGTH, -effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.REGENERATION) {
                effect.target().addEffect(BattleEffectType.REGENERATION, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.HASTE) {
                effect.target().addEffect(BattleEffectType.HASTE, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.POISON) {
                effect.target().addEffect(BattleEffectType.POISON, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.BURN) {
                effect.target().addEffect(BattleEffectType.BURN, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.WEAKNESS) {
                effect.target().addEffect(BattleEffectType.WEAKNESS, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.SLOWNESS) {
                effect.target().addEffect(BattleEffectType.SLOWNESS, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.DRAW_CARDS) {
                effect.target().deck().draw(effect.amount(), leader.getRandom());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.GAIN_ENERGY) {
                effect.target().addEnergy(effect.amount());
                effectOnlyTargets.add(effect.target());
            }
        }
        for (CombatantState target : knockbackTargets) {
            applyBattleKnockback(batch.user().entity(), target.entity(), damageResults.getOrDefault(target, new BattleDamageResult(0, 0, 0)));
        }
        boolean emittedCardVisual = false;
        for (Map.Entry<CombatantState, BattleDamageResult> entry : damageResults.entrySet()) {
            CombatantState target = entry.getKey();
            emitVisual(batch.user().entity(), target.entity(), visualStack(batch, suppressCardVisual), suppressCardVisual || emittedCardVisual ? null : batch.card(), entry.getValue(), blockGains.getOrDefault(target, 0), heals.getOrDefault(target, 0), 0);
            emittedCardVisual = true;
        }
        for (CombatantState target : effectOnlyTargets) {
            if (damageResults.containsKey(target)) {
                continue;
            }
            emitVisual(batch.user().entity(), target.entity(), visualStack(batch, suppressCardVisual), suppressCardVisual || emittedCardVisual ? null : batch.card(), new BattleDamageResult(0, 0, 0), blockGains.getOrDefault(target, 0), heals.getOrDefault(target, 0), 0);
            emittedCardVisual = true;
        }
        if (!suppressCardVisual && !emittedCardVisual && batch.card() != null) {
            emitVisual(batch.user().entity(), batch.user().entity(), batch.stack(), batch.card(), new BattleDamageResult(0, 0, 0), 0);
        }
    }

    private static ItemStack visualStack(PendingCardBatch batch, boolean suppressCardVisual) {
        return suppressCardVisual ? ItemStack.EMPTY : batch.stack();
    }

    private static BattleDamageResult mergeDamageResult(BattleDamageResult first, BattleDamageResult second) {
        return new BattleDamageResult(
                first.blockedDamage() + second.blockedDamage(),
                first.baseHealthDamage() + second.baseHealthDamage(),
                first.healthDamage() + second.healthDamage());
    }

    private void applyOwnTurnStartEffects(CombatantState state) {
        int poison = state.effectAmount(BattleEffectType.POISON);
        if (poison > 0) {
            BattleDamageResult result = state.applyEffectDamage(poison, null);
            emitVisual(state.entity(), state.entity(), ItemStack.EMPTY, null, result, 0, 0, 0);
            state.reduceEffect(BattleEffectType.POISON, 1);
        }
    }

    private void applyOwnTurnEndEffects(CombatantState state) {
        int regeneration = state.effectAmount(BattleEffectType.REGENERATION);
        if (regeneration > 0) {
            int healed = state.heal(regeneration);
            if (healed > 0) {
                emitVisual(state.entity(), state.entity(), ItemStack.EMPTY, null, new BattleDamageResult(0, 0, 0), 0, healed, 0);
            }
            state.reduceEffect(BattleEffectType.REGENERATION, 1);
        }
        int burn = state.effectAmount(BattleEffectType.BURN);
        if (burn > 0) {
            BattleDamageResult result = state.applyEffectDamage(burn, null);
            emitVisual(state.entity(), state.entity(), ItemStack.EMPTY, null, result, 0, 0, 0);
            state.reduceEffect(BattleEffectType.BURN, 1);
        }
        state.reduceEffect(BattleEffectType.WEAKNESS, 1);
        state.reduceEffect(BattleEffectType.GLOWING, 1);
        state.decayEndOfTurnEffects();
    }

    private UUID playerKillCredit(CombatantState user) {
        if (user.entity() instanceof ServerPlayer player) {
            return player.getUUID();
        }
        return null;
    }

    private void faceTarget(LivingEntity actor, LivingEntity target) {
        double dx = target.getX() - actor.getX();
        double dz = target.getZ() - actor.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 57.2957763671875D) - 90.0F;
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
    }

    private void facePosition(LivingEntity actor, Vec3 target) {
        double dx = target.x - actor.getX();
        double dz = target.z - actor.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 57.2957763671875D) - 90.0F;
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
    }

    private void lockBattleEntities() {
        for (CombatantState state : allStates()) {
            LivingEntity entity = state.entity();
            EntityLock lock = locks.get(entity.getId());
            if (lock == null || !entity.isAlive()) {
                continue;
            }
            if (entity instanceof ServerPlayer player) {
                player.getInventory().selected = lock.hotbarSlot();
            }
            if (pendingAnimation != null && pendingAnimation.movesEntity(entity)) {
                entity.xxa = 0.0F;
                entity.yya = 0.0F;
                entity.zza = 0.0F;
                entity.setJumping(false);
                continue;
            }
            int releaseTicks = knockbackTicks.getOrDefault(entity.getId(), 0);
            Vec3 nextLockedPos = freezeEntity(entity, lock.lockedPos(), releaseTicks);
            locks.put(entity.getId(), lock.withLockedPos(nextLockedPos));
            int nextTicks = nextKnockbackTicks(entity, releaseTicks);
            if (nextTicks > 0) {
                knockbackTicks.put(entity.getId(), nextTicks);
            } else {
                knockbackTicks.remove(entity.getId());
            }
        }
        freezeAi();
    }

    private void freezeAi() {
        for (CombatantState state : enemyStates) {
            if (state.entity() instanceof Mob mob && mob.isAlive()) {
                mob.getNavigation().stop();
                mob.setTarget(null);
                mob.setLastHurtMob(null);
                mob.setLastHurtByMob(null);
                mob.setAggressive(false);
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
                if (mob instanceof NeutralMob neutralMob) {
                    neutralMob.setPersistentAngerTarget(null);
                    neutralMob.setRemainingPersistentAngerTime(0);
                }
                mob.setNoAi(true);
            }
        }
    }

    private Vec3 freezeEntity(LivingEntity entity, Vec3 lockedPos, int knockbackTicks) {
        if (byEntityId.get(entity.getId()).fakeDead()) {
            entity.setDeltaMovement(Vec3.ZERO);
            entity.xxa = 0.0F;
            entity.yya = 0.0F;
            entity.zza = 0.0F;
            entity.setJumping(false);
            entity.resetFallDistance();
            if (entity instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            return entity.position();
        }
        if (knockbackTicks > 0) {
            entity.xxa = 0.0F;
            entity.yya = 0.0F;
            entity.zza = 0.0F;
            entity.setJumping(false);
            return entity.position();
        }
        Vec3 movement = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0D, movement.y, 0.0D);
        entity.xxa = 0.0F;
        entity.yya = 0.0F;
        entity.zza = 0.0F;
        entity.setJumping(false);
        Vec3 nextLockedPos = lockedPos;
        if (entity.onGround()) {
            nextLockedPos = new Vec3(lockedPos.x, entity.getY(), lockedPos.z);
        }
        double dx = entity.getX() - nextLockedPos.x;
        double dz = entity.getZ() - nextLockedPos.z;
        if (dx * dx + dz * dz > 0.0004D) {
            entity.teleportTo(nextLockedPos.x, entity.getY(), nextLockedPos.z);
            entity.setOldPosAndRot();
        }
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        return nextLockedPos;
    }

    private int nextKnockbackTicks(LivingEntity entity, int knockbackTicks) {
        if (knockbackTicks <= 0) {
            return 0;
        }
        if (entity.onGround() && knockbackTicks <= KNOCKBACK_RELEASE_TICKS - MIN_KNOCKBACK_RELEASE_TICKS) {
            return 0;
        }
        return knockbackTicks - 1;
    }

    private void pacifyOutsideHostiles() {
        AABB area = battleArea().inflate(32.0D);
        for (Mob mob : leader.level().getEntitiesOfClass(Mob.class, area)) {
            if (!mob.isAlive() || inSameBattleMob(mob)) {
                continue;
            }
            pacifyMobAgainstBattle(mob);
        }
    }

    private void pacifyMobAgainstBattle(Mob mob) {
        boolean pacified = false;
        LivingEntity target = mob.getTarget();
        if (target != null && involves(target)) {
            mob.setTarget(null);
            pacified = true;
        }
        if (mob.getLastHurtMob() != null && involves(mob.getLastHurtMob())) {
            mob.setLastHurtMob(null);
            pacified = true;
        }
        if (mob.getLastHurtByMob() != null && involves(mob.getLastHurtByMob())) {
            mob.setLastHurtByMob(null);
            pacified = true;
        }
        UUID angerTarget = mob instanceof NeutralMob neutralMob ? neutralMob.getPersistentAngerTarget() : null;
        if (mob instanceof NeutralMob neutralMob && angerTarget != null && playerStates.stream().anyMatch(state -> state.entity().getUUID().equals(angerTarget))) {
            neutralMob.setPersistentAngerTarget(null);
            neutralMob.setRemainingPersistentAngerTime(0);
            pacified = true;
        }
        if (pacified) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
            mob.getNavigation().stop();
            mob.setAggressive(false);
        }
    }

    private void resetParticipantsToStart() {
        for (CombatantState state : allStates()) {
            if (state.fakeDead() || !state.entity().isAlive()) {
                continue;
            }
            EntityLock lock = locks.get(state.entity().getId());
            if (lock != null) {
                lock.resetToStart(state.entity());
                locks.put(state.entity().getId(), lock.withLockedPos(lock.startPos()));
            }
        }
        knockbackTicks.clear();
        faceTeams();
    }

    private void faceTeams() {
        for (CombatantState player : alivePlayers()) {
            CombatantState enemy = firstAliveEnemy();
            if (enemy != null) {
                faceTarget(player.entity(), enemy.entity());
            }
        }
        for (CombatantState enemy : aliveEnemies()) {
            CombatantState player = randomAlivePlayer();
            if (player != null) {
                faceTarget(enemy.entity(), player.entity());
            }
        }
    }

    private void protectOpeningHealth() {
        if (openingProtectionTicks <= 0) {
            return;
        }
        openingProtectionTicks--;
        for (CombatantState state : allStates()) {
            EntityLock lock = locks.get(state.entity().getId());
            if (lock != null && state.entity().isAlive() && state.entity().getHealth() < lock.startHealth()) {
                state.entity().setHealth(lock.startHealth());
            }
        }
    }

    private void tickFakeDeaths() {
        for (CombatantState state : allStates()) {
            state.tickFakeDeath();
        }
    }

    private boolean hasWinner() {
        return alivePlayers().isEmpty() || aliveEnemies().isEmpty();
    }

    private boolean allFakeDeathAnimationsDone() {
        return allStates().stream().allMatch(CombatantState::fakeDeathAnimationDone);
    }

    private void applyTrueDeath(CombatantState state) {
        suppressDamageEvent = true;
        try {
            LivingEntity entity = state.entity();
            entity.invulnerableTime = 0;
            entity.setHealth(0.0F);
            if (!(entity instanceof ServerPlayer)) {
                ServerPlayer killer = creditedKiller(state);
                if (killer != null) {
                    entity.die(entity.damageSources().playerAttack(killer));
                } else {
                    entity.die(entity.damageSources().generic());
                }
            } else {
                entity.die(entity.damageSources().generic());
            }
        } finally {
            suppressDamageEvent = false;
        }
    }

    private void recoverBlockedPlayerDeath(CombatantState state) {
        state.clearFakeDeath();
        EntityLock lock = locks.get(state.entity().getId());
        if (lock != null) {
            lock.restoreSurvivor(state.entity());
        }
    }

    private ServerPlayer creditedKiller(CombatantState state) {
        UUID credited = state.creditedPlayerKill();
        if (credited != null) {
            for (CombatantState playerState : playerStates) {
                if (playerState.entity() instanceof ServerPlayer player && player.getUUID().equals(credited)) {
                    return player;
                }
            }
        }
        for (CombatantState playerState : playerStates) {
            if (playerState.entity() instanceof ServerPlayer player) {
                return player;
            }
        }
        return leader;
    }

    private List<BattleCombatantSnapshot> playerSnapshots() {
        return playerStates.stream().map(CombatantState::snapshot).toList();
    }

    private List<BattleCombatantSnapshot> enemySnapshots() {
        return enemyStates.stream().map(CombatantState::snapshot).toList();
    }

    private List<BattleEnemyIntentSnapshot> enemyIntentSnapshots(CombatantState firstEnemy) {
        List<BattleEnemyIntentSnapshot> snapshots = new ArrayList<>();
        for (CombatantState enemy : enemyStates) {
            if (enemy == firstEnemy) {
                continue;
            }
            snapshots.add(new BattleEnemyIntentSnapshot(enemy.entity().getId(), monsterIntentCards(enemy)));
        }
        return snapshots;
    }

    private List<BattleEntityCardsSnapshot> entityHandSnapshots(CombatantState local, CombatantState firstEnemy) {
        List<BattleEntityCardsSnapshot> snapshots = new ArrayList<>();
        for (CombatantState player : playerStates) {
            if (player == local) {
                continue;
            }
            snapshots.add(new BattleEntityCardsSnapshot(player.entity().getId(), List.copyOf(player.deck().hand())));
        }
        for (CombatantState enemy : enemyStates) {
            if (enemy == firstEnemy) {
                continue;
            }
            snapshots.add(new BattleEntityCardsSnapshot(enemy.entity().getId(), List.copyOf(enemy.deck().hand())));
        }
        return snapshots;
    }

    private AABB battleArea() {
        AABB area = null;
        for (CombatantState state : allStates()) {
            area = area == null ? state.entity().getBoundingBox() : area.minmax(state.entity().getBoundingBox());
        }
        return area == null ? leader.getBoundingBox() : area;
    }

    private List<CombatantState> allStates() {
        List<CombatantState> states = new ArrayList<>(playerStates.size() + enemyStates.size());
        states.addAll(playerStates);
        states.addAll(enemyStates);
        return states;
    }

    private List<CombatantState> alivePlayers() {
        return alive(playerStates);
    }

    private List<CombatantState> aliveEnemies() {
        return alive(enemyStates);
    }

    private List<CombatantState> alive(List<CombatantState> states) {
        return states.stream().filter(state -> !state.fakeDead() && state.entity().isAlive()).toList();
    }

    private List<CombatantState> sideOf(CombatantState user) {
        return playerStates.contains(user) ? playerStates : enemyStates;
    }

    private List<CombatantState> opposingSideOf(CombatantState user) {
        return playerStates.contains(user) ? enemyStates : playerStates;
    }

    private CombatantState firstOpponent(CombatantState user) {
        List<CombatantState> opponents = alive(opposingSideOf(user));
        return opponents.isEmpty() ? null : opponents.getFirst();
    }

    private CombatantState firstAliveEnemy() {
        List<CombatantState> enemies = aliveEnemies();
        return enemies.isEmpty() ? null : enemies.getFirst();
    }

    private CombatantState randomAlivePlayer() {
        List<CombatantState> players = alivePlayers();
        return players.isEmpty() ? null : players.get(leader.getRandom().nextInt(players.size()));
    }

    private boolean inSameBattleMob(Mob mob) {
        return byEntityId.containsKey(mob.getId());
    }

    private static PendingHandSelectionSnapshot.Action handSelectionAction(CardEffectKind kind) {
        return kind == CardEffectKind.EXHAUST_HAND ? PendingHandSelectionSnapshot.Action.EXHAUST : PendingHandSelectionSnapshot.Action.DISCARD;
    }

    private static int nonPlayerBaseSpeed(LivingEntity entity) {
        double movementSpeed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return Math.max(1, Math.round((float) (movementSpeed / CardBalance.NON_PLAYER_BASELINE_MOVEMENT_SPEED * CardBalance.PLAYER_BASE_SPEED)));
    }

    private record EntityLock(Vec3 startPos, Vec3 lockedPos, float yRot, float xRot, float startHealth, int hotbarSlot, boolean noAiBeforeBattle) {
        private static EntityLock capture(LivingEntity entity) {
            int slot = entity instanceof ServerPlayer player ? player.getInventory().selected : 0;
            boolean noAi = entity instanceof Mob mob && mob.isNoAi();
            return new EntityLock(entity.position(), entity.position(), entity.getYRot(), entity.getXRot(), entity.getHealth(), slot, noAi);
        }

        private EntityLock withLockedPos(Vec3 lockedPos) {
            return new EntityLock(startPos, lockedPos, yRot, xRot, startHealth, hotbarSlot, noAiBeforeBattle);
        }

        private void resetToStart(LivingEntity entity) {
            entity.teleportTo(startPos.x, startPos.y, startPos.z);
            clearMotion(entity);
        }

        private void restoreBeforeDeath(LivingEntity entity) {
            clearMotion(entity);
            if (entity instanceof Mob mob) {
                mob.setNoAi(noAiBeforeBattle);
            }
        }

        private void restoreSurvivor(LivingEntity entity) {
            entity.teleportTo(startPos.x, startPos.y, startPos.z);
            entity.setYRot(yRot);
            entity.setXRot(xRot);
            if (entity.isAlive()) {
                entity.setHealth(Math.max(1.0F, Math.min(startHealth, entity.getMaxHealth())));
            }
            if (entity instanceof ServerPlayer player) {
                player.getInventory().selected = hotbarSlot;
            }
            if (entity instanceof Mob mob) {
                mob.setNoAi(noAiBeforeBattle);
            }
            clearMotion(entity);
        }

        private static void clearMotion(LivingEntity entity) {
            entity.setDeltaMovement(Vec3.ZERO);
            entity.resetFallDistance();
            entity.xxa = 0.0F;
            entity.yya = 0.0F;
            entity.zza = 0.0F;
            entity.setJumping(false);
            entity.setOldPosAndRot();
        }
    }

    private record PendingEffect(CardEffectKind kind, int amount, CombatantState target, boolean effectDamage, boolean remoteDamage) {
        private PendingEffect(CardEffectKind kind, int amount, CombatantState target) {
            this(kind, amount, target, false, false);
        }
    }

    private record PendingCardBatch(CombatantState user, ItemStack stack, ItemStack projectileStack, CardInstance card, List<PendingEffect> effects) {
        private PendingCardBatch {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            projectileStack = projectileStack == null ? ItemStack.EMPTY : projectileStack.copy();
            effects = List.copyOf(effects);
        }
    }

    private sealed interface PendingCardStep permits PendingBatchStep, PendingHandSelectionStep {
    }

    private record PendingBatchStep(PendingCardBatch batch) implements PendingCardStep {
    }

    private record PendingHandSelectionStep(PendingHandSelectionSnapshot.Action action, int requiredCount, CombatantState target, List<UUID> candidateIds, ArrowResolution arrowResolution) implements PendingCardStep {
        private PendingHandSelectionStep(PendingHandSelectionSnapshot.Action action, int requiredCount, CombatantState target) {
            this(action, requiredCount, target, List.of(), null);
        }
    }

    private record ArrowResolution(CombatantState user, CombatantState selectedTarget, CardInstance card, int amount) {
    }

    private record PendingHandSelection(CombatantState user, PendingHandSelectionSnapshot.Action action, int requiredCount, List<UUID> candidateIds, ArrowResolution arrowResolution) {
        private PendingHandSelection {
            candidateIds = List.copyOf(candidateIds);
        }
    }

    private sealed interface BattleAnimation permits LungeAnimation, ProjectileAnimation {
        PendingCardBatch batch();

        boolean tick();

        boolean movesEntity(LivingEntity entity);
    }

    private final class LungeAnimation implements BattleAnimation {
        private final PendingCardBatch batch;
        private final CombatantState target;
        private final LivingEntity actor;
        private final Vec3 start;
        private final Vec3 strike;
        private final boolean noPhysicsBeforeAnimation;
        private int ticks;
        private boolean returning;

        private LungeAnimation(PendingCardBatch batch, CombatantState target) {
            this.batch = batch;
            this.target = target;
            this.actor = batch.user().entity();
            this.start = actor.position();
            this.noPhysicsBeforeAnimation = actor.noPhysics;
            Vec3 targetPos = target.entity().position();
            Vec3 direction = targetPos.subtract(start);
            Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
            Vec3 normalized = horizontal.lengthSqr() > 0.0001D ? horizontal.normalize() : actor.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
            double distance = Math.max(0.0D, Math.min(LUNGE_REACH, horizontal.length() - LUNGE_STOP_DISTANCE));
            this.strike = start.add(normalized.scale(distance));
        }

        @Override
        public PendingCardBatch batch() {
            return batch;
        }

        @Override
        public boolean tick() {
            if (!actor.isAlive() || target.fakeDead()) {
                actor.noPhysics = noPhysicsBeforeAnimation;
                return true;
            }
            faceTarget(actor, target.entity());
            if (!returning) {
                ticks++;
                moveActor(lerp(start, strike, ticks / (double) MELEE_LUNGE_TICKS));
                if (ticks >= MELEE_LUNGE_TICKS) {
                    ticks = 0;
                    returning = true;
                    return false;
                }
                return false;
            }
            ticks++;
            moveActor(lerp(strike, start, ticks / (double) MELEE_RECOVER_TICKS));
            if (ticks >= MELEE_RECOVER_TICKS) {
                moveActor(start);
                actor.noPhysics = noPhysicsBeforeAnimation;
                EntityLock lock = locks.get(actor.getId());
                if (lock != null) {
                    locks.put(actor.getId(), lock.withLockedPos(actor.position()));
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean movesEntity(LivingEntity entity) {
            return entity == actor;
        }

        private void moveActor(Vec3 pos) {
            Vec3 delta = pos.subtract(actor.position());
            actor.noPhysics = true;
            actor.move(MoverType.SELF, delta);
            actor.setDeltaMovement(Vec3.ZERO);
            actor.hasImpulse = true;
        }
    }

    private final class ProjectileAnimation implements BattleAnimation {
        private final PendingCardBatch batch;
        private final CombatantState target;
        private final ItemStack projectileStack;
        private final int prepareTicks;
        private final int flightTicks;
        private AbstractArrow arrow;
        private int ticks;

        private ProjectileAnimation(PendingCardBatch batch, CombatantState target, ItemStack projectileStack, int prepareTicks) {
            this.batch = batch;
            this.target = target;
            this.projectileStack = projectileStack.copy();
            this.prepareTicks = Math.max(0, prepareTicks);
            this.flightTicks = projectileFlightTicks(batch.user().entity(), target.entity());
        }

        @Override
        public PendingCardBatch batch() {
            return batch;
        }

        @Override
        public boolean tick() {
            LivingEntity actor = batch.user().entity();
            if (!actor.isAlive() || target.fakeDead()) {
                discardArrow();
                return true;
            }
            faceTarget(actor, target.entity());
            if (ticks < prepareTicks) {
                ticks++;
                return false;
            }
            if (arrow == null) {
                spawnArrow(actor, target.entity());
            }
            ticks++;
            guideArrow(target.entity());
            if (ticks >= prepareTicks + flightTicks || arrowCloseToTarget(target.entity())) {
                placeArrowAtTarget(target.entity());
                discardArrow();
                return true;
            }
            return false;
        }

        @Override
        public boolean movesEntity(LivingEntity entity) {
            return false;
        }

        private void spawnArrow(LivingEntity actor, LivingEntity targetEntity) {
            Level level = actor.level();
            ItemStack arrowStack = projectileStack.is(Items.SPECTRAL_ARROW) ? new ItemStack(Items.SPECTRAL_ARROW) : new ItemStack(Items.ARROW);
            ItemStack weaponStack = batch.stack().isEmpty() ? new ItemStack(Items.BOW) : batch.stack().copy();
            arrow = arrowStack.is(Items.SPECTRAL_ARROW)
                    ? new SpectralArrow(level, actor, arrowStack, weaponStack)
                    : new Arrow(level, actor, arrowStack, weaponStack);
            Vec3 startPos = actor.getEyePosition().add(actor.getLookAngle().scale(0.6D));
            arrow.setPos(startPos);
            arrow.setOwner(actor);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setBaseDamage(0.0D);
            arrow.setNoGravity(true);
            arrow.setNoPhysics(true);
            arrow.noPhysics = true;
            Vec3 direction = targetPoint(targetEntity).subtract(startPos).normalize();
            arrow.shoot(direction.x, direction.y, direction.z, 2.4F, 0.0F);
            level.addFreshEntity(arrow);
        }

        private void guideArrow(LivingEntity targetEntity) {
            if (arrow == null || arrow.isRemoved()) {
                return;
            }
            Vec3 targetPoint = targetPoint(targetEntity);
            Vec3 delta = targetPoint.subtract(arrow.position());
            double remainingTicks = Math.max(1.0D, prepareTicks + flightTicks - ticks + 1.0D);
            Vec3 step = delta.scale(1.0D / remainingTicks);
            arrow.setNoGravity(true);
            arrow.setNoPhysics(true);
            arrow.noPhysics = true;
            arrow.setDeltaMovement(step);
            arrow.setPos(arrow.position().add(step));
            arrow.hasImpulse = true;
        }

        private boolean arrowCloseToTarget(LivingEntity targetEntity) {
            return arrow == null || arrow.isRemoved() || arrow.position().distanceToSqr(targetPoint(targetEntity)) <= PROJECTILE_BLOCK_DISTANCE * PROJECTILE_BLOCK_DISTANCE;
        }

        private void placeArrowAtTarget(LivingEntity targetEntity) {
            if (arrow != null && !arrow.isRemoved()) {
                arrow.setPos(targetPoint(targetEntity));
            }
        }

        private void discardArrow() {
            if (arrow != null && !arrow.isRemoved()) {
                arrow.discard();
            }
            arrow = null;
        }
    }

    private int projectileFlightTicks(LivingEntity actor, LivingEntity target) {
        double distance = actor.getEyePosition().distanceTo(targetPoint(target));
        return Math.max(MIN_PROJECTILE_TICKS, Math.min(MAX_PROJECTILE_TICKS, (int) Math.ceil(distance / 1.6D)));
    }

    private Vec3 targetPoint(LivingEntity entity) {
        return entity.getBoundingBox().getCenter();
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double amount) {
        double t = Math.max(0.0D, Math.min(1.0D, amount));
        return from.add(to.subtract(from).scale(t));
    }
}
