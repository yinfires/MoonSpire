package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.MoonSpirePerfDiagnostics;
import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.CardTarget;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import com.yinfires.moonspire.developer.DeveloperMonsterDefinition;
import com.yinfires.moonspire.developer.DeveloperMonsterInitialEffect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
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
    private static final double KNOCKBACK_HORIZONTAL_DRAG = 0.82D;
    private static final double KNOCKBACK_VERTICAL_DRAG = 0.98D;
    private static final double KNOCKBACK_GRAVITY = 0.08D;
    private static final double KNOCKBACK_MAX_FALL_SPEED = -3.92D;
    private static final double KNOCKBACK_STOP_SPEED_SQR = 0.0025D;
    private static final int CARD_EFFECT_START_DELAY_TICKS = 10;
    private static final int REPEATED_EFFECT_VISUAL_INTERVAL_TICKS = 14;
    private static final int MELEE_LUNGE_TICKS = 8;
    private static final int MELEE_HIT_PAUSE_TICKS = 6;
    private static final int VINDICATOR_AXE_RAISE_TICKS = 8;
    private static final int VINDICATOR_AXE_APPROACH_TICKS = 8;
    private static final int VINDICATOR_AXE_STRIKE_TICKS = 1;
    private static final int VINDICATOR_AXE_RECOVER_TICKS = 6;
    private static final int VEX_CHARGE_RAISE_TICKS = 8;
    private static final int VEX_CHARGE_APPROACH_TICKS = 7;
    private static final int VEX_CHARGE_HIT_PAUSE_TICKS = 6;
    private static final int BOW_DRAW_TICKS = 20;
    private static final int CROSSBOW_LOAD_TICKS = 25;
    private static final int TRIDENT_DRAW_TICKS = 18;
    private static final int GUARDIAN_BEAM_TICKS = 80;
    private static final int RIPTIDE_CHARGE_TICKS = 16;
    private static final int RIPTIDE_RUSH_TICKS = 10;
    private static final int RIPTIDE_HIT_PAUSE_TICKS = 6;
    private static final int MIN_PROJECTILE_TICKS = 5;
    private static final int MAX_PROJECTILE_TICKS = 18;
    private static final int IDLE_SYNC_HEARTBEAT_TICKS = 100;
    private static final double PROJECTILE_BLOCK_DISTANCE = 0.7D;
    private static final double LUNGE_STOP_DISTANCE = 1.55D;
    private static final double POUNCE_CONTACT_MARGIN = 0.08D;
    private static final double POUNCE_JUMP_HEIGHT = 1.0D;
    private static final double MIN_LUNGE_TRAVEL_DISTANCE = 0.35D;
    private static final double LUNGE_REACH = 10.0D;
    private static final double CARD_FACING_CENTER_MIN_DISTANCE_SQR = 0.0625D;
    private static final double CARD_FACING_DIRECTION_STABILITY_MIN = 0.35D;
    private static final float CARD_FACING_TURN_DEGREES_PER_TICK = 24.0F;
    private static final double MONSTER_AI_MIN_SCORE = 0.01D;
    private static final int MONSTER_AI_HIGH_BLOCK_SOFT_CAP = 12;
    private static final int MONSTER_AI_STATUS_SOFT_CAP = 4;
    private static final int MONSTER_AI_LONG_STATUS_SOFT_CAP = 6;
    private static final double MONSTER_AI_STATUS_SCORE_MULTIPLIER = 10.0D;
    private static final double MONSTER_AI_STATUS_PLAY_BONUS = 40.0D;
    private static final double MONSTER_AI_CATEGORY_STATUS_WEIGHT = 1.25D;
    private static final double MONSTER_AI_CATEGORY_UTILITY_WEIGHT = 1.10D;
    private static final double MONSTER_AI_CATEGORY_ATTACK_WEIGHT = 0.90D;
    private static final double MONSTER_AI_CATEGORY_DEFENSE_WEIGHT = 0.90D;
    private static final int SELF_DESTRUCT_ANIMATION_TICKS = 30;

    private final UUID id = UUID.randomUUID();
    private final ServerPlayer leader;
    private final List<CombatantState> playerStates = new ArrayList<>();
    private final List<CombatantState> enemyStates = new ArrayList<>();
    private final List<MonsterRewardPool> startingEnemyRewardPools = new ArrayList<>();
    private final Map<Integer, CombatantState> byEntityId = new HashMap<>();
    private final Map<UUID, CombatantState> byPlayerId = new HashMap<>();
    private final Map<Integer, MonsterTurnPlan> monsterTurnPlans = new HashMap<>();
    private final Map<Integer, EntityLock> locks = new HashMap<>();
    private final Map<Integer, KnockbackState> knockbackStates = new HashMap<>();
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
    private int finishDelayTicks;
    private boolean enemyHandsPredrawnForCurrentTurn;
    private final List<BattleVisualEvent> pendingVisualEvents = new ArrayList<>();
    private final List<PendingCardBatch> pendingCardBatches = new ArrayList<>();
    private final List<PendingCardStep> pendingCardSteps = new ArrayList<>();
    private int pendingCardBatchDelay;
    private BattleAnimation pendingAnimation;
    private CombatantState pendingUsedCardOwner;
    private CardInstance pendingUsedCard;
    private PendingHandSelection pendingHandSelection;
    private PendingFacing pendingFacing;
    private final List<PendingSelfDestruct> pendingSelfDestructs = new ArrayList<>();

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
            List<CardInstance> enemyStartCards = enemyCards.getOrDefault(enemy.getId(), List.of());
            CombatantState state = new CombatantState(
                    enemy,
                    new BattleDeck(enemyStartCards, random),
                    monsterOverride != null && monsterOverride.hasEnergyOverride() ? monsterOverride.energy() : CardBalance.fixedEnergy(),
                    monsterOverride != null && monsterOverride.hasHealthOverride() ? monsterOverride.maxHealth() : Math.max(1.0F, enemy.getMaxHealth()),
                    monsterOverride != null && monsterOverride.hasSpeedOverride() ? monsterOverride.speed() : MonsterDeckProfile.defaultBaseSpeed(enemy));
            applyInitialEffects(state, monsterOverride);
            applyDefaultInitialEffects(state);
            enemyStates.add(state);
            byEntityId.put(enemy.getId(), state);
            locks.put(enemy.getId(), EntityLock.capture(enemy));
            startingEnemyRewardPools.add(new MonsterRewardPool(BuiltInRegistries.ENTITY_TYPE.getKey(enemy.getType()).toString(), MonsterDeckProfile.rewardPoolCardIds(enemy, enemyStartCards)));
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

    private static void applyDefaultInitialEffects(CombatantState state) {
        if (state != null && MonsterDeckProfile.hasAbundantArrowsByDefault(state.entity().getType())) {
            state.addEffect(BattleEffectType.ABUNDANT_ARROWS, 1);
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

    public List<MonsterRewardPool> startingEnemyRewardPools() {
        return List.copyOf(startingEnemyRewardPools);
    }

    public boolean playerVictory() {
        return !alivePlayers().isEmpty() && aliveEnemies().isEmpty();
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
            if (finishDelayTicks > 0) {
                finishDelayTicks--;
                markDirty();
                lockBattleEntities();
                return false;
            }
            endingAfterAnimations = true;
            markDirty();
            lockBattleEntities();
            return allFakeDeathAnimationsDone();
        }
        lockBattleEntities();
        pacifyOutsideHostiles();
        protectOpeningHealth();
        tickPendingSelfDestructs();
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
            if (finishDelayTicks > 0) {
                finishDelayTicks--;
                markDirty();
                return false;
            }
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
                restoreSurvivor(state, lock);
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
                if (playerState.fakeDead()) {
                    continue;
                }
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
        return pileCardsFor(viewer, source, -1);
    }

    public List<CardInstance> pileCardsFor(ServerPlayer viewer, BattlePileSource source, int entityId) {
        CombatantState state = pileOwner(viewer, entityId);
        if (state == null || source == null) {
            return List.of();
        }
        return switch (source) {
            case BATTLE_DECK -> battleDeckCards(state);
            case DRAW -> List.copyOf(state.deck().drawPile());
            case DISCARD -> List.copyOf(state.deck().discardPile());
            case EXHAUST -> List.copyOf(state.deck().exhaustPile());
        };
    }

    public int pileCountFor(ServerPlayer viewer, BattlePileSource source) {
        return pileCountFor(viewer, source, -1);
    }

    public int pileCountFor(ServerPlayer viewer, BattlePileSource source, int entityId) {
        CombatantState state = pileOwner(viewer, entityId);
        if (state == null || source == null) {
            return 0;
        }
        return switch (source) {
            case BATTLE_DECK -> battleDeckCount(state);
            case DRAW -> state.deck().drawPile().size();
            case DISCARD -> state.deck().discardPile().size();
            case EXHAUST -> state.deck().exhaustPile().size();
        };
    }

    public long deckVersionFor(ServerPlayer viewer) {
        return deckVersionFor(viewer, -1);
    }

    public long deckVersionFor(ServerPlayer viewer, int entityId) {
        CombatantState state = pileOwner(viewer, entityId);
        return state == null ? 0L : state.deck().version();
    }

    private CombatantState pileOwner(ServerPlayer viewer, int entityId) {
        if (viewer == null) {
            return null;
        }
        if (!byPlayerId.containsKey(viewer.getUUID())) {
            return null;
        }
        return entityId >= 0 ? byEntityId.get(entityId) : byPlayerId.get(viewer.getUUID());
    }

    private List<CardInstance> battleDeckCards(CombatantState local) {
        List<CardInstance> cards = new ArrayList<>(battleDeckCount(local));
        cards.addAll(local.deck().hand());
        cards.addAll(local.deck().drawPile());
        cards.addAll(local.deck().discardPile());
        if (pendingUsedCardOwner == local && pendingUsedCard != null) {
            cards.add(pendingUsedCard);
        }
        return List.copyOf(cards);
    }

    private int battleDeckCount(CombatantState state) {
        if (state == null) {
            return 0;
        }
        return state.deck().hand().size()
                + state.deck().drawPile().size()
                + state.deck().discardPile().size()
                + (pendingUsedCardOwner == state && pendingUsedCard != null ? 1 : 0);
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
        if (!pendingCardBatches.isEmpty() || !pendingCardSteps.isEmpty() || pendingUsedCard != null || !pendingSelfDestructs.isEmpty()) {
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
                state.deck().startTurn(leader.getRandom(), consumeHungerDrawReduction(state));
            }
        }
        for (CombatantState state : enemyStates) {
            if (!state.fakeDead()) {
                state.deck().startTurn(leader.getRandom(), hungerDrawReduction(state));
            }
        }
        enemyHandsPredrawnForCurrentTurn = true;
        selectedTargets.clear();
        rebuildMonsterTurnPlans();
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
                if (consumeHungerDrawReduction(state, true) > 0) {
                    markDirty();
                }
                state.resetEnergy();
            }
        }
        enemyHandsPredrawnForCurrentTurn = false;
        enemyTurnOrder = enemyActionOrder();
        currentEnemyIndex = 0;
        selectedTargets.clear();
        ensureMonsterTurnPlans();
    }

    private void beginRoundEnd() {
        phase = BattlePhase.ROUND_END;
        phaseTicks = 0;
        markDirty();
        for (CombatantState state : aliveEnemies()) {
            applyOwnTurnEndEffects(state);
            if (state.fakeDead()) {
                continue;
            }
            state.reduceRetainedCardCosts();
            state.deck().discardHand(true);
        }
        monsterTurnPlans.clear();
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
        MonsterCardChoice choice = nextPlannedMonsterCard(enemy);
        if (choice == null) {
            currentEnemyIndex++;
            monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
            return;
        }
        CardInstance card = enemy.deck().peekHand(choice.handIndex());
        if (card == null || !enemy.spendEnergy(card.cost())) {
            currentEnemyIndex++;
            monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
            return;
        }
        CardInstance used = enemy.deck().useHand(choice.handIndex());
        CombatantState target = choice.selectedTarget() == null || choice.selectedTarget().fakeDead() ? firstOpponent(enemy) : choice.selectedTarget();
        if (used == null || target == null) {
            currentEnemyIndex++;
            monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
            return;
        }
        syncMonsterTurnPlanState(enemy);
        queueCard(enemy, target, used);
        monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
    }

    private List<CombatantState> enemyActionOrder() {
        return enemyStates.stream()
                .sorted(Comparator.comparingInt(CombatantState::roundSpeed).reversed())
                .toList();
    }

    private MonsterCardChoice chooseMonsterCard(CombatantState monster) {
        return chooseMonsterCard(monster, MonsterAiView.live(monster), liveAiViews(alivePlayers()), liveAiViews(aliveEnemies()));
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
        if (phase == BattlePhase.MONSTER_TURN) {
            MonsterTurnPlan plan = monsterTurnPlans.get(monster.entity().getId());
            return plan == null ? List.of() : plan.cards(monster, true);
        }
        MonsterTurnPlan plan = monsterTurnPlans.computeIfAbsent(monster.entity().getId(), ignored -> createMonsterTurnPlan(monster));
        refreshMonsterTurnPlan(monster, plan);
        return plan.cards(monster, false);
    }

    private MonsterCardChoice chooseMonsterCard(CombatantState monster, MonsterAiView self, List<MonsterAiView> players, List<MonsterAiView> enemies) {
        return chooseMonsterCard(monster.deck().hand(), monster.energyLeft(), self, players, enemies);
    }

    private void rebuildMonsterTurnPlans() {
        monsterTurnPlans.clear();
        for (CombatantState enemy : enemyStates) {
            if (!enemy.fakeDead()) {
                monsterTurnPlans.put(enemy.entity().getId(), createMonsterTurnPlan(enemy));
            }
        }
    }

    private void ensureMonsterTurnPlans() {
        monsterTurnPlans.keySet().removeIf(entityId -> {
            CombatantState enemy = byEntityId.get(entityId);
            return enemy == null || enemy.fakeDead();
        });
        for (CombatantState enemy : enemyStates) {
            if (!enemy.fakeDead()) {
                monsterTurnPlans.computeIfAbsent(enemy.entity().getId(), ignored -> createMonsterTurnPlan(enemy));
            }
        }
    }

    private void syncMonsterTurnPlanState(CombatantState monster) {
        MonsterTurnPlan plan = monsterTurnPlans.get(monster.entity().getId());
        if (plan != null) {
            plan.setHandVersion(monster.deck().version());
            plan.setEnergyLeft(planEnergy(monster));
        }
    }

    private MonsterTurnPlan createMonsterTurnPlan(CombatantState monster) {
        MonsterTurnPlan plan = new MonsterTurnPlan(monster.deck().version(), planEnergy(monster));
        refreshMonsterTurnPlan(monster, plan);
        return plan;
    }

    private void refreshMonsterTurnPlan(CombatantState monster, MonsterTurnPlan plan) {
        if (monster == null || monster.fakeDead()) {
            if (plan != null) {
                plan.clear();
            }
            return;
        }
        int energy = planEnergy(monster);
        if (plan.handVersion() == monster.deck().version() && plan.energyLeft() == energy && (plan.exhausted() || plan.hasDisplayableEntry(monster))) {
            return;
        }
        plan.rebuild(plannedMonsterChoices(monster));
        plan.setHandVersion(monster.deck().version());
        plan.setEnergyLeft(energy);
    }

    private int planEnergy(CombatantState monster) {
        return phase == BattlePhase.PLAYER_TURN ? monster.maxEnergy() : monster.energyLeft();
    }

    private List<MonsterCardChoice> plannedMonsterChoices(CombatantState monster) {
        if (monster == null || monster.fakeDead()) {
            return List.of();
        }
        List<CardInstance> virtualHand = new ArrayList<>(monster.deck().hand());
        int energyLeft = phase == BattlePhase.PLAYER_TURN ? monster.maxEnergy() : monster.energyLeft();
        MonsterAiView self = MonsterAiView.live(monster).withDefense(phase == BattlePhase.PLAYER_TURN ? 0 : monster.defense());
        List<MonsterAiView> playerViews = liveAiViews(alivePlayers());
        List<MonsterAiView> enemyViews = liveAiViews(aliveEnemies());
        List<MonsterCardChoice> planned = new ArrayList<>();
        while (!virtualHand.isEmpty() && self.health() > 0.0F) {
            MonsterCardChoice choice = chooseMonsterCard(virtualHand, energyLeft, self, playerViews, enemyViews);
            if (choice == null) {
                break;
            }
            CardInstance card = virtualHand.remove(choice.handIndex());
            planned.add(choice.withCardId(card.id()));
            energyLeft -= Math.max(0, card.cost());
            applyMonsterAiSimulation(card, virtualHand, self, playerViews, enemyViews, choice.selectedEntityId());
        }
        return planned;
    }

    private MonsterCardChoice nextPlannedMonsterCard(CombatantState monster) {
        MonsterTurnPlan plan = monsterTurnPlans.computeIfAbsent(monster.entity().getId(), ignored -> createMonsterTurnPlan(monster));
        boolean hadPlannedEntries = !plan.entries().isEmpty();
        MonsterCardChoice choice = plan.pollUsable(monster);
        if (choice != null) {
            return choice;
        }
        if (!hadPlannedEntries) {
            return null;
        }
        choice = chooseMonsterCard(monster);
        CardInstance card = choice == null ? null : monster.deck().peekHand(choice.handIndex());
        if (card != null) {
            return choice.withCardId(card.id()).withCategory(cardCategory(card));
        }
        return null;
    }

    private MonsterCardChoice chooseMonsterCard(List<CardInstance> hand, int energyLeft, MonsterAiView self, List<MonsterAiView> players, List<MonsterAiView> enemies) {
        List<MonsterCardChoice> choices = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            CardInstance card = hand.get(i);
            if (card.cost() > energyLeft || !card.hasAnyEffect()) {
                continue;
            }
            MonsterCardChoice choice = scoreMonsterCard(i, card, hand, self, players, enemies);
            if (choice == null || choice.score() <= MONSTER_AI_MIN_SCORE) {
                continue;
            }
            choices.add(choice.withCategory(cardCategory(card)));
        }
        MonsterCardChoice priority = priorityMonsterCard(hand, choices, self);
        if (priority != null) {
            return priority;
        }
        return chooseWeightedMonsterCard(choices);
    }

    private MonsterCardChoice priorityMonsterCard(List<CardInstance> hand, List<MonsterCardChoice> choices, MonsterAiView self) {
        if (self == null || self.state() == null || self.state().entity().getType() != EntityType.CREEPER) {
            return null;
        }
        for (MonsterCardChoice choice : choices) {
            CardInstance card = hand.get(choice.handIndex());
            if ("builtin_monster_light_fuse".equals(card.cardId())) {
                return choice;
            }
        }
        return null;
    }

    private MonsterCardChoice chooseWeightedMonsterCard(List<MonsterCardChoice> choices) {
        if (choices.isEmpty()) {
            return null;
        }
        List<MonsterCardCategory> categories = new ArrayList<>();
        double totalCategoryWeight = 0.0D;
        for (MonsterCardCategory category : MonsterCardCategory.values()) {
            if (choices.stream().anyMatch(choice -> choice.category() == category)) {
                categories.add(category);
                totalCategoryWeight += category.weight();
            }
        }
        MonsterCardCategory selectedCategory = categories.getLast();
        double categoryRoll = leader.getRandom().nextDouble() * totalCategoryWeight;
        double categoryCursor = 0.0D;
        for (MonsterCardCategory category : categories) {
            categoryCursor += category.weight();
            if (categoryRoll <= categoryCursor) {
                selectedCategory = category;
                break;
            }
        }
        List<MonsterCardChoice> categoryChoices = new ArrayList<>();
        for (MonsterCardChoice choice : choices) {
            if (choice.category() == selectedCategory) {
                categoryChoices.add(choice);
            }
        }
        double totalChoiceWeight = 0.0D;
        for (MonsterCardChoice choice : categoryChoices) {
            totalChoiceWeight += choiceWeight(choice);
        }
        MonsterCardChoice selected = categoryChoices.getLast();
        double choiceRoll = leader.getRandom().nextDouble() * totalChoiceWeight;
        double choiceCursor = 0.0D;
        for (MonsterCardChoice choice : categoryChoices) {
            choiceCursor += choiceWeight(choice);
            if (choiceRoll <= choiceCursor) {
                selected = choice;
                break;
            }
        }
        return selected;
    }

    private double choiceWeight(MonsterCardChoice choice) {
        return 1.0D + Math.min(12.0D, Math.sqrt(Math.max(0.0D, choice.score())));
    }

    private MonsterCardCategory cardCategory(CardInstance card) {
        boolean attack = false;
        boolean defense = false;
        boolean utility = false;
        boolean status = false;
        for (CardEffect effect : card.effects()) {
            if (effect.amount() <= 0 || !effect.kind().makesCardPlayable()) {
                continue;
            }
            if (effect.kind() == CardEffectKind.DAMAGE || effect.kind() == CardEffectKind.CONSUME_ARROW) {
                attack = true;
            } else if (effect.kind() == CardEffectKind.BLOCK) {
                defense = true;
            } else if (isMonsterAiStatusEffect(effect.kind())) {
                status = true;
            } else {
                utility = true;
            }
        }
        if (status) {
            return MonsterCardCategory.STATUS;
        }
        if (utility) {
            return MonsterCardCategory.UTILITY;
        }
        if (attack) {
            return MonsterCardCategory.ATTACK;
        }
        if (defense) {
            return MonsterCardCategory.DEFENSE;
        }
        return MonsterCardCategory.UTILITY;
    }

    private boolean isMonsterAiStatusEffect(CardEffectKind kind) {
        return kind == CardEffectKind.BLEED
                || kind == CardEffectKind.GLOWING
                || kind == CardEffectKind.GUARD
                || kind == CardEffectKind.STRENGTH
                || kind == CardEffectKind.LOSE_STRENGTH
                || kind == CardEffectKind.REGENERATION
                || kind == CardEffectKind.HASTE
                || kind == CardEffectKind.POISON
                || kind == CardEffectKind.BURN
                || kind == CardEffectKind.WITHER
                || kind == CardEffectKind.TIDAL_EROSION
                || kind == CardEffectKind.PARALYSIS
                || kind == CardEffectKind.HUNGER
                || kind == CardEffectKind.THORNS
                || kind == CardEffectKind.FUSE
                || kind == CardEffectKind.WEAKNESS
                || kind == CardEffectKind.SLOWNESS;
    }

    private MonsterCardChoice scoreMonsterCard(int handIndex, CardInstance card, List<CardInstance> hand, MonsterAiView self, List<MonsterAiView> players, List<MonsterAiView> enemies) {
        boolean paralyzedAttack = self.effectAmount(BattleEffectType.PARALYSIS) > 0 && triggersAttackUse(card, hand);
        MonsterAiView selectedEnemy = bestExplicitEnemyTarget(card, hand, self, players);
        MonsterAiView selectedAlly = bestExplicitAllyTarget(card, self, enemies);
        double score = 0.0D;
        for (CardEffect effect : card.effects()) {
            if (effect.amount() <= 0 || !effect.kind().makesCardPlayable()) {
                continue;
            }
            if (effect.kind() == CardEffectKind.CONSUME_ARROW) {
                score += scoreConsumeArrow(card, hand, effect, self, players, selectedEnemy, paralyzedAttack);
                continue;
            }
            if (isConsumeArrowAttachedEffect(card, effect) && firstArrowInHand(card, hand) == null) {
                continue;
            }
            if (effect.kind().isHandSelection()) {
                score += scoreHandSelection(effect, self);
                continue;
            }
            if (!effect.kind().isResolvedEffect()) {
                continue;
            }
            CardEffect scoringEffect = adjustedMonsterAiEffect(effect, paralyzedAttack);
            if (effect.target() == CardTarget.RANDOM_ENEMY || effect.target() == CardTarget.RANDOM_ALLY) {
                score += scoreRandomTargetEffect(card, scoringEffect, self, players, enemies);
                continue;
            }
            for (MonsterAiView target : aiTargetsForEffect(scoringEffect, self, players, enemies, selectedEnemy, selectedAlly)) {
                score += scoreMonsterEffect(card, scoringEffect, self, target, players.contains(target));
            }
        }
        MonsterAiView selected = selectedEnemy != null ? selectedEnemy : selectedAlly;
        return score <= MONSTER_AI_MIN_SCORE ? null : new MonsterCardChoice(handIndex, selected == null ? -1 : selected.entityId(), selected == null ? null : selected.state(), score);
    }

    private List<MonsterAiView> liveAiViews(List<CombatantState> states) {
        List<MonsterAiView> views = new ArrayList<>();
        for (CombatantState state : states) {
            views.add(MonsterAiView.live(state));
        }
        return views;
    }

    private MonsterAiView bestExplicitEnemyTarget(CardInstance card, List<CardInstance> hand, MonsterAiView self, List<MonsterAiView> players) {
        if (players.isEmpty() || card.effects().stream().noneMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target() == CardTarget.SINGLE_ENEMY)) {
            return null;
        }
        boolean paralyzedAttack = self.effectAmount(BattleEffectType.PARALYSIS) > 0 && triggersAttackUse(card, hand);
        MonsterAiView best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (MonsterAiView target : players) {
            double score = targetPriority(target);
            for (CardEffect effect : card.effects()) {
                if (effect.amount() <= 0 || !effect.kind().usesTarget() || effect.target() != CardTarget.SINGLE_ENEMY) {
                    continue;
                }
                if (effect.kind() == CardEffectKind.CONSUME_ARROW) {
                    score += scoreConsumeArrowTarget(card, hand, effect, self, target, paralyzedAttack);
                } else if (isConsumeArrowAttachedEffect(card, effect) && firstArrowInHand(card, hand) == null) {
                    continue;
                } else {
                    score += effectTargetPriority(card, effect.kind(), adjustedAttackDamageAmount(effect, paralyzedAttack), Math.max(1, effect.count()), self, target);
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best;
    }

    private MonsterAiView bestExplicitAllyTarget(CardInstance card, MonsterAiView self, List<MonsterAiView> enemies) {
        if (card.effects().stream().noneMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target() == CardTarget.SINGLE_ALLY)) {
            return null;
        }
        MonsterAiView best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (MonsterAiView target : enemies) {
            if (target.entityId() == self.entityId()) {
                continue;
            }
            double score = allyPriority(target);
            for (CardEffect effect : card.effects()) {
                if (effect.amount() <= 0 || !effect.kind().usesTarget() || effect.target() != CardTarget.SINGLE_ALLY) {
                    continue;
                }
                score += scoreMonsterEffect(card, effect, self, target, false);
            }
            if (score > bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best;
    }

    private List<MonsterAiView> aiTargetsForEffect(CardEffect effect, MonsterAiView self, List<MonsterAiView> players, List<MonsterAiView> enemies, MonsterAiView selectedEnemy, MonsterAiView selectedAlly) {
        if (!effect.kind().usesTarget()) {
            return List.of();
        }
        return switch (effect.target()) {
            case SELF -> List.of(self);
            case SINGLE_ENEMY -> selectedEnemy == null ? List.of() : List.of(selectedEnemy);
            case SINGLE_ALLY -> selectedAlly == null ? List.of() : List.of(selectedAlly);
            case ALL_ENEMIES -> players;
            case ALL_ALLIES -> enemies;
            case ALL_UNITS -> concatAiViews(players, enemies);
            case ALL_OTHER_UNITS -> concatAiViews(players, enemies).stream().filter(target -> target.entityId() != self.entityId()).toList();
            case ALL_OTHER_ALLIES -> enemies.stream().filter(target -> target.entityId() != self.entityId()).toList();
            case RANDOM_ENEMY -> singletonAiView(bestEnemyTarget(players, self, effect.amount()));
            case RANDOM_ALLY -> singletonAiView(bestRandomAllyTarget(enemies, self, effect));
        };
    }

    private List<MonsterAiView> singletonAiView(MonsterAiView view) {
        return view == null ? List.of() : List.of(view);
    }

    private List<MonsterAiView> concatAiViews(List<MonsterAiView> first, List<MonsterAiView> second) {
        List<MonsterAiView> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private MonsterAiView bestEnemyTarget(List<MonsterAiView> players, MonsterAiView self, int baseDamage) {
        MonsterAiView best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (MonsterAiView target : players) {
            double score = targetPriority(target);
            if (baseDamage > 0) {
                score += directDamageAmount(baseDamage, self, target, false) * 4.0D;
            }
            if (score > bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best;
    }

    private MonsterAiView bestRandomAllyTarget(List<MonsterAiView> enemies, MonsterAiView self, CardEffect effect) {
        MonsterAiView best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (MonsterAiView target : enemies) {
            if (target.entityId() == self.entityId()) {
                continue;
            }
            double score = allyPriority(target) + scoreMonsterEffect(null, effect, self, target, false);
            if (score > bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best;
    }

    private double scoreMonsterEffect(CardInstance card, CardEffect effect, MonsterAiView self, MonsterAiView target) {
        int totalAmount = effect.amount() * Math.max(1, effect.count());
        return switch (effect.kind()) {
            case DAMAGE -> scoreDamage(card, totalAmount, self, target);
            case BLOCK -> scoreBlock(totalAmount, target);
            case HEAL -> scoreHeal(totalAmount, target);
            case BLEED -> scoreNegativeStatus(totalAmount, target, BattleEffectType.BLEED, 2.2D, MONSTER_AI_LONG_STATUS_SOFT_CAP);
            case GLOWING -> scoreNegativeStatus(totalAmount, target, BattleEffectType.GLOWING, 2.8D, MONSTER_AI_STATUS_SOFT_CAP);
            case GUARD -> scorePositiveStatus(totalAmount, target, BattleEffectType.GUARD, 3.0D, MONSTER_AI_STATUS_SOFT_CAP);
            case STRENGTH -> scorePositiveStatus(totalAmount, target, BattleEffectType.STRENGTH, 3.2D, MONSTER_AI_STATUS_SOFT_CAP);
            case LOSE_STRENGTH -> scoreLoseStrength(totalAmount, target);
            case REGENERATION -> scorePositiveStatus(totalAmount, target, BattleEffectType.REGENERATION, 2.6D, MONSTER_AI_LONG_STATUS_SOFT_CAP);
            case HASTE -> scorePositiveStatus(totalAmount, target, BattleEffectType.HASTE, 2.4D, MONSTER_AI_STATUS_SOFT_CAP);
            case POISON -> scoreNegativeStatus(totalAmount, target, BattleEffectType.POISON, 2.8D, MONSTER_AI_LONG_STATUS_SOFT_CAP);
            case BURN -> scoreNegativeStatus(totalAmount, target, BattleEffectType.BURN, 2.6D, MONSTER_AI_LONG_STATUS_SOFT_CAP);
            case WITHER -> scoreNegativeStatus(totalAmount, target, BattleEffectType.WITHER, 3.1D, MONSTER_AI_LONG_STATUS_SOFT_CAP);
            case TIDAL_EROSION -> scoreNegativeStatus(totalAmount, target, BattleEffectType.TIDAL_EROSION, 2.4D, MONSTER_AI_STATUS_SOFT_CAP);
            case PARALYSIS -> scoreNegativeStatus(totalAmount, target, BattleEffectType.PARALYSIS, 3.2D, MONSTER_AI_STATUS_SOFT_CAP);
            case HUNGER -> scoreNegativeStatus(totalAmount, target, BattleEffectType.HUNGER, 3.0D, MONSTER_AI_STATUS_SOFT_CAP);
            case THORNS -> scorePositiveStatus(totalAmount, target, BattleEffectType.THORNS, 3.0D, MONSTER_AI_STATUS_SOFT_CAP);
            case FUSE -> scorePositiveStatus(totalAmount, target, BattleEffectType.FUSE, 8.0D, MONSTER_AI_STATUS_SOFT_CAP);
            case WEAKNESS -> scoreNegativeStatus(totalAmount, target, BattleEffectType.WEAKNESS, 3.4D, MONSTER_AI_STATUS_SOFT_CAP);
            case SLOWNESS -> scoreNegativeStatus(totalAmount, target, BattleEffectType.SLOWNESS, 2.6D, MONSTER_AI_STATUS_SOFT_CAP);
            case DRAW_CARDS -> Math.max(0, totalAmount) * 1.6D;
            case GAIN_ENERGY -> Math.max(0, totalAmount) * 2.0D;
            default -> 0.0D;
        };
    }

    private double scoreMonsterEffect(CardInstance card, CardEffect effect, MonsterAiView self, MonsterAiView target, boolean targetIsPlayer) {
        double score = scoreMonsterEffect(card, effect, self, target);
        if (harmfulMonsterEffect(effect.kind())) {
            return targetIsPlayer ? score : -score;
        }
        if (helpfulMonsterEffect(effect.kind())) {
            return targetIsPlayer ? -score : score;
        }
        return score;
    }

    private double scoreRandomTargetEffect(CardInstance card, CardEffect effect, MonsterAiView self, List<MonsterAiView> players, List<MonsterAiView> enemies) {
        List<MonsterAiView> candidates = effect.target() == CardTarget.RANDOM_ENEMY
                ? players
                : enemies.stream().filter(target -> target.entityId() != self.entityId()).toList();
        if (candidates.isEmpty()) {
            return 0.0D;
        }
        double total = 0.0D;
        for (MonsterAiView target : candidates) {
            total += scoreMonsterEffect(card, effect, self, target, players.contains(target));
        }
        return total / candidates.size();
    }

    private double scoreDamage(CardInstance card, int amount, MonsterAiView self, MonsterAiView target) {
        boolean remote = card != null && card.hasEffect(CardEffectKind.REMOTE);
        int damage = directDamageAmount(amount, self, target, remote);
        int healthDamage = Math.max(0, damage - target.defense());
        double score = damage * 3.0D + healthDamage * 5.0D + targetPriority(target);
        if (healthDamage >= target.health()) {
            score += 120.0D;
        }
        return score;
    }

    private double scoreConsumeArrow(CardInstance card, List<CardInstance> hand, CardEffect effect, MonsterAiView self, List<MonsterAiView> players, MonsterAiView selectedEnemy, boolean paralyzedAttack) {
        int damage = consumeArrowDamage(card, hand, effect, paralyzedAttack);
        if (damage <= 0) {
            return 0.0D;
        }
        MonsterAiView target = selectedEnemy == null ? bestEnemyTarget(players, self, damage) : selectedEnemy;
        return target == null ? 0.0D : scoreConsumeArrowTarget(card, hand, effect, self, target, paralyzedAttack);
    }

    private double scoreConsumeArrowTarget(CardInstance card, List<CardInstance> hand, CardEffect effect, MonsterAiView self, MonsterAiView target, boolean paralyzedAttack) {
        CardInstance arrow = firstArrowInHand(card, hand);
        if (arrow == null) {
            return 0.0D;
        }
        double score = scoreDamage(card, consumeArrowDamage(card, hand, effect, paralyzedAttack), self, target);
        for (CardEffect arrowEffect : arrow.effects()) {
            if (arrowEffect.kind() == CardEffectKind.GLOWING && arrowEffect.amount() > 0) {
                score += scoreMonsterEffect(card, arrowEffect, self, target, true);
            }
        }
        return score;
    }

    private int consumeArrowDamage(CardInstance card, List<CardInstance> hand, CardEffect effect) {
        return consumeArrowDamage(card, hand, effect, false);
    }

    private int consumeArrowDamage(CardInstance card, List<CardInstance> hand, CardEffect effect, boolean paralyzedAttack) {
        CardInstance arrow = firstArrowInHand(card, hand);
        if (arrow == null) {
            return 0;
        }
        int amount = Math.max(0, adjustedAttackDamageAmount(effect, paralyzedAttack));
        for (CardEffect arrowEffect : arrow.effects()) {
            if (arrowEffect.kind() == CardEffectKind.DAMAGE && arrowEffect.target().targetsEnemy()) {
                amount += Math.max(0, arrowEffect.amount()) * Math.max(1, arrowEffect.count());
            }
        }
        return amount;
    }

    private CardInstance firstArrowInHand(CardInstance card, List<CardInstance> hand) {
        if (card == null) {
            return null;
        }
        for (CardInstance candidate : hand) {
            if (candidate != card && candidate.hasEffect(CardEffectKind.ARROW)) {
                return candidate;
            }
        }
        return null;
    }

    private double scoreHandSelection(CardEffect effect, MonsterAiView self) {
        return effect.target().targetsSelf() ? Math.max(0, effect.amount()) * 0.25D + (self.healthRatio() < 0.45D ? -2.0D : 0.0D) : 0.0D;
    }

    private double scoreBlock(int amount, MonsterAiView target) {
        int block = Math.max(0, amount);
        if (block <= 0) {
            return 0.0D;
        }
        double missingHealthRatio = 1.0D - target.healthRatio();
        double urgency = target.defense() <= 0 ? 1.4D : 1.0D;
        if (target.healthRatio() < 0.35D) {
            urgency += 1.0D;
        } else if (target.healthRatio() < 0.65D) {
            urgency += 0.45D;
        }
        double wastePenalty = Math.max(0, target.defense() - MONSTER_AI_HIGH_BLOCK_SOFT_CAP) * 0.7D;
        return Math.max(0.0D, block * (2.2D + missingHealthRatio * 3.0D) * urgency - wastePenalty);
    }

    private double scoreHeal(int amount, MonsterAiView target) {
        float missing = Math.max(0.0F, target.effectiveMaxHealth() - target.health());
        double healed = Math.min(Math.max(0, amount), missing);
        if (healed <= 0.0D) {
            return 0.0D;
        }
        return healed * (3.0D + (1.0D - target.healthRatio()) * 4.0D);
    }

    private double scorePositiveStatus(int amount, MonsterAiView target, BattleEffectType type, double weight, int softCap) {
        int positiveAmount = Math.max(0, amount);
        if (positiveAmount <= 0) {
            return 0.0D;
        }
        int current = Math.max(0, target.effectAmount(type));
        double need = Math.max(0.15D, 1.0D - current / (double) Math.max(1, softCap));
        double healthFactor = type == BattleEffectType.GUARD || type == BattleEffectType.REGENERATION ? 1.0D + (1.0D - target.healthRatio()) : 1.0D;
        return (positiveAmount * weight * MONSTER_AI_STATUS_SCORE_MULTIPLIER + MONSTER_AI_STATUS_PLAY_BONUS) * need * healthFactor;
    }

    private double scoreNegativeStatus(int amount, MonsterAiView target, BattleEffectType type, double weight, int softCap) {
        int negativeAmount = Math.max(0, amount);
        if (negativeAmount <= 0) {
            return 0.0D;
        }
        int current = Math.max(0, target.effectAmount(type));
        double need = Math.max(0.15D, 1.0D - current / (double) Math.max(1, softCap));
        double pressure = 1.0D + (1.0D - target.healthRatio()) * 0.65D + Math.min(4, target.effectAmount(BattleEffectType.GLOWING)) * 0.08D;
        return (negativeAmount * weight * MONSTER_AI_STATUS_SCORE_MULTIPLIER + MONSTER_AI_STATUS_PLAY_BONUS) * need * pressure + targetPriority(target) * 0.2D;
    }

    private double scoreLoseStrength(int amount, MonsterAiView target) {
        int removedAmount = Math.max(0, amount);
        if (removedAmount <= 0) {
            return 0.0D;
        }
        int current = Math.max(0, target.effectAmount(BattleEffectType.STRENGTH));
        if (current <= 0) {
            return removedAmount * 2.0D;
        }
        double pressure = 1.0D + current * 0.2D;
        return (Math.min(removedAmount, current) * 4.0D * MONSTER_AI_STATUS_SCORE_MULTIPLIER + MONSTER_AI_STATUS_PLAY_BONUS) * pressure;
    }

    private double effectTargetPriority(CardInstance card, CardEffectKind kind, int amount, int count, MonsterAiView self, MonsterAiView target) {
        if (kind == CardEffectKind.DAMAGE || kind == CardEffectKind.CONSUME_ARROW) {
            return scoreDamage(card, amount * count, self, target);
        }
        if (kind == CardEffectKind.BLEED) {
            return scoreNegativeStatus(amount * count, target, BattleEffectType.BLEED, 2.2D, MONSTER_AI_LONG_STATUS_SOFT_CAP);
        }
        if (kind == CardEffectKind.GLOWING) {
            return scoreNegativeStatus(amount * count, target, BattleEffectType.GLOWING, 2.8D, MONSTER_AI_STATUS_SOFT_CAP);
        }
        if (kind == CardEffectKind.LOSE_STRENGTH) {
            return scoreLoseStrength(amount * count, target);
        }
        if (kind == CardEffectKind.POISON) {
            return scoreNegativeStatus(amount * count, target, BattleEffectType.POISON, 2.8D, MONSTER_AI_LONG_STATUS_SOFT_CAP);
        }
        if (kind == CardEffectKind.BURN) {
            return scoreNegativeStatus(amount * count, target, BattleEffectType.BURN, 2.6D, MONSTER_AI_LONG_STATUS_SOFT_CAP);
        }
        if (kind == CardEffectKind.WITHER) {
            return scoreNegativeStatus(amount * count, target, BattleEffectType.WITHER, 3.1D, MONSTER_AI_LONG_STATUS_SOFT_CAP);
        }
        if (kind == CardEffectKind.TIDAL_EROSION) {
            return scoreNegativeStatus(amount * count, target, BattleEffectType.TIDAL_EROSION, 2.4D, MONSTER_AI_STATUS_SOFT_CAP);
        }
        if (kind == CardEffectKind.PARALYSIS) {
            return scoreNegativeStatus(amount * count, target, BattleEffectType.PARALYSIS, 3.2D, MONSTER_AI_STATUS_SOFT_CAP);
        }
        if (kind == CardEffectKind.HUNGER) {
            return scoreNegativeStatus(amount * count, target, BattleEffectType.HUNGER, 3.0D, MONSTER_AI_STATUS_SOFT_CAP);
        }
        if (kind == CardEffectKind.THORNS) {
            return scorePositiveStatus(amount * count, target, BattleEffectType.THORNS, 3.0D, MONSTER_AI_STATUS_SOFT_CAP);
        }
        if (kind == CardEffectKind.WEAKNESS) {
            return scoreNegativeStatus(amount * count, target, BattleEffectType.WEAKNESS, 3.4D, MONSTER_AI_STATUS_SOFT_CAP);
        }
        if (kind == CardEffectKind.SLOWNESS) {
            return scoreNegativeStatus(amount * count, target, BattleEffectType.SLOWNESS, 2.6D, MONSTER_AI_STATUS_SOFT_CAP);
        }
        return targetPriority(target);
    }

    private boolean harmfulMonsterEffect(CardEffectKind kind) {
        return kind == CardEffectKind.DAMAGE
                || kind == CardEffectKind.CONSUME_ARROW
                || kind == CardEffectKind.BLEED
                || kind == CardEffectKind.GLOWING
                || kind == CardEffectKind.LOSE_STRENGTH
                || kind == CardEffectKind.POISON
                || kind == CardEffectKind.BURN
                || kind == CardEffectKind.WITHER
                || kind == CardEffectKind.TIDAL_EROSION
                || kind == CardEffectKind.PARALYSIS
                || kind == CardEffectKind.HUNGER
                || kind == CardEffectKind.WEAKNESS
                || kind == CardEffectKind.SLOWNESS;
    }

    private boolean helpfulMonsterEffect(CardEffectKind kind) {
        return kind == CardEffectKind.BLOCK
                || kind == CardEffectKind.HEAL
                || kind == CardEffectKind.GUARD
                || kind == CardEffectKind.STRENGTH
                || kind == CardEffectKind.REGENERATION
                || kind == CardEffectKind.HASTE
                || kind == CardEffectKind.THORNS
                || kind == CardEffectKind.FUSE
                || kind == CardEffectKind.DRAW_CARDS
                || kind == CardEffectKind.GAIN_ENERGY;
    }

    private int directDamageAmount(int amount, MonsterAiView attacker, MonsterAiView defender, boolean remote) {
        int incoming = Math.max(0, amount + attacker.effectAmount(BattleEffectType.STRENGTH));
        return BattleDamageCalculator.directDamage(incoming, attacker.roundSpeed(), defender.roundSpeed(), defender.defense(), defender.effectAmount(BattleEffectType.GUARD), attacker.effectAmount(BattleEffectType.WEAKNESS) > 0, remote, defender.effectAmount(BattleEffectType.GLOWING) > 0);
    }

    private double targetPriority(MonsterAiView target) {
        return Math.max(0, target.effectAmount(BattleEffectType.GLOWING)) * 8.0D
                + (1.0D - target.healthRatio()) * 28.0D
                + Math.max(0, 10 - target.defense()) * 0.7D;
    }

    private double allyPriority(MonsterAiView target) {
        return (1.0D - target.healthRatio()) * 24.0D
                + Math.max(0, MONSTER_AI_HIGH_BLOCK_SOFT_CAP - target.defense()) * 0.5D;
    }

    private void applyMonsterAiSimulation(CardInstance card, List<CardInstance> hand, MonsterAiView self, List<MonsterAiView> players, List<MonsterAiView> enemies, int selectedEntityId) {
        MonsterAiView selectedTarget = selectedEntityId < 0 ? null : findAiView(players, selectedEntityId);
        if (selectedTarget == null && selectedEntityId >= 0) {
            selectedTarget = findAiView(enemies, selectedEntityId);
        }
        boolean paralyzedAttack = self.effectAmount(BattleEffectType.PARALYSIS) > 0 && triggersAttackUse(card, hand);
        if (paralyzedAttack) {
            self.reduceEffect(BattleEffectType.PARALYSIS, 1);
        }
        for (CardEffect effect : card.effects()) {
            if (effect.amount() <= 0 || !effect.kind().makesCardPlayable()) {
                continue;
            }
            if (effect.kind() == CardEffectKind.CONSUME_ARROW) {
                CardInstance arrow = firstArrowInHand(card, hand);
                if (selectedTarget != null) {
                    int arrowDamage = consumeArrowDamage(card, hand, effect, paralyzedAttack);
                    if (arrowDamage > 0) {
                        applyMonsterAiEffect(card, new CardEffect(CardEffectKind.DAMAGE, arrowDamage, CardTarget.SINGLE_ENEMY), self, selectedTarget);
                    }
                    if (arrow != null) {
                        for (CardEffect arrowEffect : arrow.effects()) {
                            if (arrowEffect.kind() == CardEffectKind.GLOWING && arrowEffect.amount() > 0) {
                                applyMonsterAiEffect(card, arrowEffect, self, selectedTarget);
                            }
                        }
                        for (CardEffect attachedEffect : consumeArrowAttachedEffects(card)) {
                            applyMonsterAiEffect(card, attachedEffect, self, selectedTarget);
                        }
                    }
                }
                if (arrow != null) {
                    hand.remove(arrow);
                }
                continue;
            }
            if (!effect.kind().isResolvedEffect()) {
                continue;
            }
            if (isConsumeArrowAttachedEffect(card, effect)) {
                continue;
            }
            CardEffect simulationEffect = adjustedMonsterAiEffect(effect, paralyzedAttack);
            MonsterAiView selectedEnemy = selectedTarget != null && players.contains(selectedTarget) ? selectedTarget : bestExplicitEnemyTarget(card, hand, self, players);
            MonsterAiView selectedAlly = selectedTarget != null && enemies.contains(selectedTarget) ? selectedTarget : bestExplicitAllyTarget(card, self, enemies);
            for (MonsterAiView target : aiTargetsForEffect(simulationEffect, self, players, enemies, selectedEnemy, selectedAlly)) {
                applyMonsterAiEffect(card, simulationEffect, self, target);
            }
        }
    }

    private MonsterAiView findAiView(List<MonsterAiView> views, int entityId) {
        for (MonsterAiView view : views) {
            if (view.entityId() == entityId) {
                return view;
            }
        }
        return null;
    }

    private void applyMonsterAiEffect(CardInstance card, CardEffect effect, MonsterAiView self, MonsterAiView target) {
        int amount = effect.amount() * Math.max(1, effect.count());
        switch (effect.kind()) {
            case DAMAGE -> target.takeDamage(directDamageAmount(amount, self, target, card.hasEffect(CardEffectKind.REMOTE)));
            case BLOCK -> target.addDefense(amount);
            case HEAL -> target.heal(amount);
            case BLEED -> target.addEffect(BattleEffectType.BLEED, amount);
            case GLOWING -> target.addEffect(BattleEffectType.GLOWING, amount);
            case GUARD -> target.addEffect(BattleEffectType.GUARD, amount);
            case STRENGTH -> target.addEffect(BattleEffectType.STRENGTH, amount);
            case LOSE_STRENGTH -> target.addEffect(BattleEffectType.STRENGTH, -amount);
            case REGENERATION -> target.addEffect(BattleEffectType.REGENERATION, amount);
            case HASTE -> target.addEffect(BattleEffectType.HASTE, amount);
            case POISON -> target.addEffect(BattleEffectType.POISON, amount);
            case BURN -> target.addEffect(BattleEffectType.BURN, amount);
            case WITHER -> target.addEffect(BattleEffectType.WITHER, amount);
            case TIDAL_EROSION -> target.addEffect(BattleEffectType.TIDAL_EROSION, amount);
            case PARALYSIS -> target.addEffect(BattleEffectType.PARALYSIS, amount);
            case HUNGER -> target.addEffect(BattleEffectType.HUNGER, amount);
            case THORNS -> target.addEffect(BattleEffectType.THORNS, amount);
            case FUSE -> target.addEffect(BattleEffectType.FUSE, amount);
            case WEAKNESS -> target.addEffect(BattleEffectType.WEAKNESS, amount);
            case SLOWNESS -> target.addEffect(BattleEffectType.SLOWNESS, amount);
            default -> {
            }
        }
    }

    private boolean canUseCard(CombatantState user, CardInstance card) {
        return !user.fakeDead() && card.cost() <= user.energyLeft() && card.hasAnyEffect();
    }

    private void queueCard(CombatantState user, CombatantState selectedTarget, CardInstance card) {
        CombatantState visualTarget = selectedTarget == null || selectedTarget.fakeDead() ? firstOpponent(user) : selectedTarget;
        if (visualTarget == null) {
            visualTarget = user;
        }
        pendingCardBatches.clear();
        pendingCardSteps.clear();
        pendingCardBatchDelay = 0;
        pendingHandSelection = null;
        pendingFacing = null;
        pendingUsedCardOwner = user;
        pendingUsedCard = card;
        boolean attackUse = triggersAttackUse(card, user);
        boolean paralyzedAttack = attackUse && user.effectAmount(BattleEffectType.PARALYSIS) > 0;
        if (attackUse) {
            PendingEffect bleed = bleedEffectForAttack(user);
            if (bleed != null) {
                pendingCardSteps.add(new PendingBatchStep(new PendingCardBatch(user, card.sourceStack(), ItemStack.EMPTY, null, List.of(bleed))));
            }
            user.reduceEffect(BattleEffectType.PARALYSIS, 1);
        }
        List<CardEffect> currentEffects = new ArrayList<>();
        for (CardEffect effect : card.effects()) {
            if (effect.kind().isKeyword()) {
                continue;
            }
            if (effect.kind() == CardEffectKind.CONSUME_ARROW) {
                if (!currentEffects.isEmpty()) {
                    addEffectSteps(user, selectedTarget, card, currentEffects, paralyzedAttack);
                    currentEffects = new ArrayList<>();
                }
                addConsumeArrowStep(user, selectedTarget, card, effect, paralyzedAttack);
                continue;
            }
            if (isConsumeArrowAttachedEffect(card, effect)) {
                continue;
            }
            if (effect.kind().isHandSelection()) {
                if (!currentEffects.isEmpty()) {
                    addEffectSteps(user, selectedTarget, card, currentEffects, paralyzedAttack);
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
            addEffectSteps(user, selectedTarget, card, currentEffects, paralyzedAttack);
        }
        advancePendingCardSteps();
        if (!hasPendingCardBatches()) {
            emitVisual(user.entity(), visualTarget.entity(), card.sourceStack(), card, new BattleDamageResult(0, 0, 0), 0);
            finishPendingUsedCard();
        }
    }

    private void addEffectSteps(CombatantState user, CombatantState selectedTarget, CardInstance card, List<CardEffect> effects) {
        addEffectSteps(user, selectedTarget, card, effects, ItemStack.EMPTY);
    }

    private void addEffectSteps(CombatantState user, CombatantState selectedTarget, CardInstance card, List<CardEffect> effects, ItemStack projectileStack) {
        addEffectSteps(user, selectedTarget, card, effects, projectileStack, false);
    }

    private void addEffectSteps(CombatantState user, CombatantState selectedTarget, CardInstance card, List<CardEffect> effects, boolean paralyzedAttack) {
        addEffectSteps(user, selectedTarget, card, effects, ItemStack.EMPTY, paralyzedAttack);
    }

    private void addEffectSteps(CombatantState user, CombatantState selectedTarget, CardInstance card, List<CardEffect> effects, ItemStack projectileStack, boolean paralyzedAttack) {
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
                    int amount = adjustedAttackDamageAmount(effect, paralyzedAttack);
                    batchEffects.add(new PendingEffect(effect.kind(), amount, effectTarget, false, remoteDamage && effect.kind() == CardEffectKind.DAMAGE));
                }
            }
            if (!batchEffects.isEmpty()) {
                pendingCardSteps.add(new PendingBatchStep(new PendingCardBatch(user, card.sourceStack(), projectileStack, card, batchEffects)));
            }
        }
    }

    private int adjustedAttackDamageAmount(CardEffect effect, boolean paralyzedAttack) {
        if ((effect.kind() == CardEffectKind.DAMAGE || effect.kind() == CardEffectKind.CONSUME_ARROW) && paralyzedAttack) {
            return Math.max(0, effect.amount() - CardBalance.PARALYSIS_ATTACK_DAMAGE_REDUCTION);
        }
        return effect.amount();
    }

    private CardEffect adjustedMonsterAiEffect(CardEffect effect, boolean paralyzedAttack) {
        int amount = adjustedAttackDamageAmount(effect, paralyzedAttack);
        return amount == effect.amount() ? effect : new CardEffect(effect.kind(), amount, effect.target(), effect.count());
    }

    private void addConsumeArrowStep(CombatantState user, CombatantState selectedTarget, CardInstance card, CardEffect effect, boolean paralyzedAttack) {
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
        ArrowResolution resolution = new ArrowResolution(user, selectedTarget, card, adjustedAttackDamageAmount(effect, paralyzedAttack), consumeArrowAttachedEffects(card));
        pendingCardSteps.add(new PendingHandSelectionStep(PendingHandSelectionSnapshot.Action.CONSUME_ARROW, 1, user, arrowIds, resolution));
    }

    private List<CardEffect> consumeArrowAttachedEffects(CardInstance card) {
        if (!hasConsumeArrowAttachedEffects(card)) {
            return List.of();
        }
        return card.effects().stream()
                .filter(effect -> isConsumeArrowAttachedEffect(card, effect))
                .toList();
    }

    private boolean hasConsumeArrowAttachedEffects(CardInstance card) {
        return card != null
                && ("builtin_monster_poisoned_shot".equals(card.cardId())
                || "builtin_monster_slowing_shot".equals(card.cardId()));
    }

    private boolean isConsumeArrowAttachedEffect(CardInstance card, CardEffect effect) {
        if (!hasConsumeArrowAttachedEffects(card) || effect == null) {
            return false;
        }
        return effect.target() == CardTarget.SINGLE_ENEMY
                && (effect.kind() == CardEffectKind.POISON || effect.kind() == CardEffectKind.SLOWNESS);
    }

    private boolean triggersAttackUse(CardInstance card, CombatantState user) {
        if (card.hasEnemyEffect(CardEffectKind.DAMAGE)) {
            return true;
        }
        return card.effects().stream().anyMatch(effect -> effect.kind() == CardEffectKind.CONSUME_ARROW && effect.amount() > 0)
                && user.deck().hand().stream().anyMatch(handCard -> handCard.hasEffect(CardEffectKind.ARROW));
    }

    private boolean triggersAttackUse(CardInstance card, List<CardInstance> hand) {
        if (card.hasEnemyEffect(CardEffectKind.DAMAGE)) {
            return true;
        }
        return card.effects().stream().anyMatch(effect -> effect.kind() == CardEffectKind.CONSUME_ARROW && effect.amount() > 0)
                && hand.stream().anyMatch(handCard -> handCard.hasEffect(CardEffectKind.ARROW));
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
            markDirty();
        }
        pendingFacing = null;
        pendingUsedCardOwner = null;
        pendingUsedCard = null;
    }

    private Vec3 applyBattleKnockback(LivingEntity attacker, LivingEntity target, BattleDamageResult result) {
        if (result.healthDamage() <= 0 || attacker == null || target == null) {
            return Vec3.ZERO;
        }
        Vec3 before = target.position();
        double dx = attacker.getX() - target.getX();
        double dz = attacker.getZ() - target.getZ();
        double strength = target instanceof ServerPlayer ? 0.42D : 0.28D;
        target.knockback(strength, dx, dz);
        target.hasImpulse = true;
        Vec3 motion = target.getDeltaMovement();
        Vec3 knockbackVelocity = sanitizeKnockbackVelocity(motion);
        target.setDeltaMovement(Vec3.ZERO);
        if (knockbackVelocity.lengthSqr() > 0.0001D) {
            knockbackStates.put(target.getId(), new KnockbackState(KNOCKBACK_RELEASE_TICKS, MIN_KNOCKBACK_RELEASE_TICKS, knockbackVelocity));
        } else {
            knockbackStates.remove(target.getId());
        }
        EntityLock lock = locks.get(target.getId());
        if (lock != null) {
            locks.put(target.getId(), lock.withLockedPos(target.position()));
        }
        if (knockbackVelocity.lengthSqr() > 0.0001D) {
            return knockbackVelocity;
        }
        Vec3 away = target.position().subtract(attacker.position()).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() > 0.0001D) {
            return away.normalize().scale(strength);
        }
        Vec3 moved = target.position().subtract(before).multiply(1.0D, 0.0D, 1.0D);
        return moved.lengthSqr() > 0.0001D ? moved : Vec3.ZERO;
    }

    private Vec3 sanitizeKnockbackVelocity(Vec3 motion) {
        if (motion == null || motion.lengthSqr() <= 0.0001D) {
            return Vec3.ZERO;
        }
        double y = Math.max(0.0D, motion.y);
        return new Vec3(motion.x, y, motion.z);
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
        if (isGuardianBeamCard(card)) {
            return GUARDIAN_BEAM_TICKS;
        }
        if (isTridentCard(card)) {
            return TRIDENT_DRAW_TICKS;
        }
        ItemStack stack = card.sourceStack();
        if (!stack.isEmpty() && stack.is(Items.CROSSBOW)) {
            return CROSSBOW_LOAD_TICKS;
        }
        return BOW_DRAW_TICKS;
    }

    private BattleVisualEvent.AnimationType rangedAnimationType(CardInstance card) {
        if (isGuardianBeamCard(card)) {
            return BattleVisualEvent.AnimationType.GUARDIAN_BEAM;
        }
        if (isChannelingThrowCard(card)) {
            return BattleVisualEvent.AnimationType.CHANNELING_TRIDENT_THROW;
        }
        if (isTridentCard(card)) {
            return BattleVisualEvent.AnimationType.TRIDENT_THROW;
        }
        ItemStack stack = card.sourceStack();
        return !stack.isEmpty() && stack.is(Items.CROSSBOW)
                ? BattleVisualEvent.AnimationType.CROSSBOW_LOAD
                : BattleVisualEvent.AnimationType.BOW_DRAW;
    }

    private boolean isTridentCard(CardInstance card) {
        if (card == null) {
            return false;
        }
        ItemStack stack = card.sourceStack();
        return (!stack.isEmpty() && stack.is(Items.TRIDENT))
                || "builtin_monster_trident_throw".equals(card.cardId())
                || "builtin_monster_channeling_throw".equals(card.cardId())
                || "builtin_monster_riptide_rush".equals(card.cardId());
    }

    private boolean isGuardianBeamCard(CardInstance card) {
        if (card == null) {
            return false;
        }
        return "builtin_monster_guardian_beam".equals(card.cardId())
                || "builtin_monster_tidal_gaze".equals(card.cardId())
                || "builtin_monster_elder_beam".equals(card.cardId())
                || "builtin_monster_elder_tidal_erosion".equals(card.cardId());
    }

    private boolean isChannelingThrowCard(CardInstance card) {
        return card != null && "builtin_monster_channeling_throw".equals(card.cardId());
    }

    private boolean isRiptideRushCard(CardInstance card) {
        return card != null && "builtin_monster_riptide_rush".equals(card.cardId());
    }

    private boolean isVindicatorAxeAttack(PendingCardBatch batch) {
        if (batch == null || batch.card() == null || batch.user() == null || batch.user().entity().getType() != EntityType.VINDICATOR) {
            return false;
        }
        return "builtin_monster_axe_chop".equals(batch.card().cardId())
                || "builtin_monster_heavy_axe_blow".equals(batch.card().cardId())
                || "builtin_monster_executioners_blow".equals(batch.card().cardId());
    }

    private boolean isVexChargeAttack(PendingCardBatch batch) {
        if (batch == null || batch.card() == null || batch.user() == null || batch.user().entity().getType() != EntityType.VEX) {
            return false;
        }
        return "builtin_monster_razor_rush".equals(batch.card().cardId())
                || "builtin_monster_flicker_cut".equals(batch.card().cardId())
                || "builtin_monster_phase_stab".equals(batch.card().cardId())
                || "builtin_monster_frenzied_dive".equals(batch.card().cardId());
    }

    private LungeStyle lungeStyle(PendingCardBatch batch) {
        if (isVindicatorAxeAttack(batch)) {
            return LungeStyle.VINDICATOR_AXE;
        }
        if (isVexChargeAttack(batch)) {
            return LungeStyle.VEX_CHARGE;
        }
        return LungeStyle.NORMAL;
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
        emitVisual(attacker, target, stack, projectileStack, playedCard, result, gainedBlock, healedHealth, delayTicks, animationType, animationTicks, null, null);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, ItemStack projectileStack, CardInstance playedCard, BattleDamageResult result, int gainedBlock, int healedHealth, int delayTicks, BattleVisualEvent.AnimationType animationType, int animationTicks, Vec3 animationStart, Vec3 animationStrike) {
        emitVisual(attacker, target, stack, projectileStack, playedCard, result, gainedBlock, healedHealth, delayTicks, animationType, animationTicks, animationStart, animationStrike, null);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, ItemStack projectileStack, CardInstance playedCard, BattleDamageResult result, int gainedBlock, int healedHealth, int delayTicks, BattleVisualEvent.AnimationType animationType, int animationTicks, Vec3 animationStart, Vec3 animationStrike, Vec3 knockbackDelta) {
        Vec3 lookTarget = attacker == target ? null : visualFacingPoint(target);
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
                gainedBlock > 0,
                animationStart,
                animationStrike,
                knockbackDelta,
                lookTarget));
        markDirty();
    }

    private boolean hasPendingCardBatches() {
        return pendingAnimation != null || !pendingCardBatches.isEmpty() || !pendingCardSteps.isEmpty() || pendingHandSelection != null || pendingUsedCard != null || !pendingSelfDestructs.isEmpty();
    }

    private void tickPendingCardBatches() {
        if (pendingAnimation != null) {
            if (pendingAnimation.readyToApplyEffects()) {
                completeAnimatedBatch(pendingAnimation);
                pendingAnimation.markEffectsApplied();
                markDirty();
            }
            if (!pendingAnimation.tick()) {
                return;
            }
            BattleAnimation completed = pendingAnimation;
            pendingAnimation = null;
            if (!completed.effectsApplied()) {
                completeAnimatedBatch(completed);
                completed.markEffectsApplied();
                markDirty();
            }
            if (!pendingCardBatches.isEmpty()) {
                pendingCardBatchDelay = REPEATED_EFFECT_VISUAL_INTERVAL_TICKS;
                prepareFacingForNextBatch();
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
            pendingFacing = null;
            return;
        }
        if (pendingCardBatches.isEmpty() && !pendingCardSteps.isEmpty()) {
            advancePendingCardSteps();
        }
        if (pendingCardBatches.isEmpty()) {
            pendingCardBatchDelay = 0;
            pendingFacing = null;
            if (pendingCardSteps.isEmpty() && pendingHandSelection == null && pendingUsedCard != null) {
                finishPendingUsedCard();
            }
            return;
        }
        if (pendingCardBatchDelay > 0) {
            tickPendingFacing();
            pendingCardBatchDelay--;
            return;
        }
        PendingCardBatch batch = pendingCardBatches.getFirst();
        finishPendingFacing(batch);
        pendingCardBatches.remove(0);
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
            prepareFacingForNextBatch();
        }
    }

    private boolean beginBattleAnimation(PendingCardBatch batch) {
        PendingEffect animated = firstAnimatedEffect(batch);
        if (animated == null) {
            return false;
        }
        if (isRiptideRushCard(batch.card())) {
            RiptideRushAnimation animation = new RiptideRushAnimation(batch, animated.target());
            ItemStack visualStack = batch.stack().isEmpty() ? new ItemStack(Items.TRIDENT) : batch.stack();
            emitVisual(batch.user().entity(), animated.target().entity(), visualStack, ItemStack.EMPTY, batch.card(), new BattleDamageResult(0, 0, 0), 0, 0, 0, BattleVisualEvent.AnimationType.RIPTIDE_RUSH, RIPTIDE_CHARGE_TICKS + RIPTIDE_RUSH_TICKS + RIPTIDE_HIT_PAUSE_TICKS, animation.start(), animation.strike());
            pendingAnimation = animation;
            return true;
        }
        if (batch.card() != null && batch.card().hasEffect(CardEffectKind.REMOTE)) {
            if (isGuardianBeamCard(batch.card())) {
                emitVisual(batch.user().entity(), animated.target().entity(), batch.stack(), ItemStack.EMPTY, batch.card(), new BattleDamageResult(0, 0, 0), 0, 0, 0, BattleVisualEvent.AnimationType.GUARDIAN_BEAM, GUARDIAN_BEAM_TICKS);
                pendingAnimation = new GuardianBeamAnimation(batch, animated.target(), GUARDIAN_BEAM_TICKS);
                return true;
            }
            boolean trident = isTridentCard(batch.card());
            ItemStack projectileStack = trident ? new ItemStack(Items.TRIDENT) : batch.projectileStack().isEmpty() ? new ItemStack(Items.ARROW) : batch.projectileStack();
            ItemStack visualStack = trident ? new ItemStack(Items.TRIDENT) : batch.stack().isEmpty() ? new ItemStack(Items.BOW) : batch.stack();
            int drawTicks = rangedPrepareTicks(batch.card());
            emitVisual(batch.user().entity(), animated.target().entity(), visualStack, projectileStack, batch.card(), new BattleDamageResult(0, 0, 0), 0, 0, 0, rangedAnimationType(batch.card()), drawTicks);
            pendingAnimation = new ProjectileAnimation(batch, animated.target(), projectileStack, drawTicks);
            return true;
        }
        LungeAnimation animation = new LungeAnimation(batch, animated.target(), lungeStyle(batch));
        emitVisual(batch.user().entity(), animated.target().entity(), visualStack(batch), ItemStack.EMPTY, batch.card(), new BattleDamageResult(0, 0, 0), 0, 0, 0, animation.animationType(), animation.visualTicks(), animation.start(), animation.strike());
        pendingAnimation = animation;
        return true;
    }

    private PendingEffect firstAnimatedEffect(PendingCardBatch batch) {
        for (PendingEffect effect : batch.effects()) {
            if (effect.kind() == CardEffectKind.DAMAGE && !effect.effectDamage() && effect.target() != null && !effect.target().fakeDead()) {
                return effect;
            }
        }
        return null;
    }

    private void completeAnimatedBatch(BattleAnimation animation) {
        BattleVisualEvent.AnimationType hitAnimationType = animation instanceof LungeAnimation lunge
                ? lunge.hitAnimationType()
                : BattleVisualEvent.AnimationType.NONE;
        applyPendingCardBatch(animation.batch(), true, hitAnimationType);
    }

    private void advancePendingCardSteps() {
        while (pendingHandSelection == null && pendingCardBatches.isEmpty() && !pendingCardSteps.isEmpty()) {
            PendingCardStep step = pendingCardSteps.remove(0);
            if (step instanceof PendingBatchStep batchStep) {
                pendingCardBatches.add(batchStep.batch());
                pendingCardBatchDelay = Math.max(pendingCardBatchDelay, CARD_EFFECT_START_DELAY_TICKS);
                prepareFacingForBatch(batchStep.batch());
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
            } else if (arrowEffect.kind() == CardEffectKind.GLOWING || arrowEffect.kind() == CardEffectKind.WITHER) {
                effects.add(arrowEffect);
            }
        }
        effects.addAll(resolution.attachedEffects());
        addEffectSteps(resolution.user(), resolution.selectedTarget(), resolution.card(), effects, projectileStackForArrow(arrow));
    }

    private PendingHandSelectionSnapshot pendingHandSelectionSnapshotFor(CombatantState local) {
        if (pendingHandSelection == null || pendingHandSelection.user() != local) {
            return PendingHandSelectionSnapshot.NONE;
        }
        return new PendingHandSelectionSnapshot(pendingHandSelection.action(), pendingHandSelection.requiredCount(), pendingHandSelection.user().entity().getId(), pendingHandSelection.candidateIds());
    }

    private void applyPendingCardBatch(PendingCardBatch batch, boolean suppressCardVisual) {
        applyPendingCardBatch(batch, suppressCardVisual, BattleVisualEvent.AnimationType.NONE);
    }

    private void applyPendingCardBatch(PendingCardBatch batch, boolean suppressCardVisual, BattleVisualEvent.AnimationType hitAnimationType) {
        Map<CombatantState, BattleDamageResult> damageResults = new LinkedHashMap<>();
        Map<CombatantState, BattleDamageResult> thornsResults = new LinkedHashMap<>();
        Map<CombatantState, Integer> blockGains = new LinkedHashMap<>();
        Map<CombatantState, Integer> heals = new LinkedHashMap<>();
        Set<CombatantState> effectDamageTargets = new LinkedHashSet<>();
        Set<CombatantState> cardDamageTargets = new LinkedHashSet<>();
        Map<CombatantState, Vec3> knockbackDeltas = new LinkedHashMap<>();
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
                if (effect.effectDamage()) {
                    effectDamageTargets.add(effect.target());
                } else {
                    cardDamageTargets.add(effect.target());
                }
                if (triggersThorns(effect, batch.user())) {
                    BattleDamageResult thornsResult = applyThornsDamage(effect.target(), batch.user());
                    if (thornsResult.blockedDamage() > 0 || thornsResult.healthDamage() > 0) {
                        thornsResults.merge(effect.target(), thornsResult, BattleState::mergeDamageResult);
                    }
                }
                if (!effect.effectDamage() && result.healthDamage() > 0 && !effect.target().fakeDead()) {
                    knockbackTargets.add(effect.target());
                }
            } else if (effect.kind() == CardEffectKind.BLOCK) {
                int gainedBlock = effect.target().addDefense(effect.amount());
                if (gainedBlock > 0) {
                    blockGains.merge(effect.target(), gainedBlock, Integer::sum);
                }
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
            } else if (effect.kind() == CardEffectKind.WITHER) {
                effect.target().addEffect(BattleEffectType.WITHER, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.TIDAL_EROSION) {
                effect.target().addEffect(BattleEffectType.TIDAL_EROSION, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.PARALYSIS) {
                effect.target().addEffect(BattleEffectType.PARALYSIS, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.HUNGER) {
                applyHunger(effect.target(), effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.THORNS) {
                effect.target().addEffect(BattleEffectType.THORNS, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.FUSE) {
                effect.target().addEffect(BattleEffectType.FUSE, effect.amount());
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
            Vec3 delta = applyBattleKnockback(batch.user().entity(), target.entity(), damageResults.getOrDefault(target, new BattleDamageResult(0, 0, 0)));
            if (delta.lengthSqr() > 0.0001D) {
                knockbackDeltas.put(target, delta);
            }
        }
        boolean emittedCardVisual = false;
        for (Map.Entry<CombatantState, BattleDamageResult> entry : damageResults.entrySet()) {
            CombatantState target = entry.getKey();
            boolean effectDamageOnly = effectDamageTargets.contains(target)
                    && !cardDamageTargets.contains(target)
                    && blockGains.getOrDefault(target, 0) <= 0
                    && heals.getOrDefault(target, 0) <= 0;
            ItemStack visualStack = effectDamageOnly ? ItemStack.EMPTY : visualStack(batch, suppressCardVisual);
            CardInstance visualCard = effectDamageOnly || suppressCardVisual || emittedCardVisual ? null : batch.card();
            emitVisual(batch.user().entity(), target.entity(), visualStack, ItemStack.EMPTY, visualCard, entry.getValue(), blockGains.getOrDefault(target, 0), heals.getOrDefault(target, 0), 0, effectDamageOnly ? BattleVisualEvent.AnimationType.NONE : hitAnimationType, 0, null, null, knockbackDeltas.get(target));
            if (!effectDamageOnly) {
                emittedCardVisual = true;
            }
        }
        for (Map.Entry<CombatantState, BattleDamageResult> entry : thornsResults.entrySet()) {
            emitVisual(entry.getKey().entity(), batch.user().entity(), ItemStack.EMPTY, ItemStack.EMPTY, null, entry.getValue(), 0, 0, 0, BattleVisualEvent.AnimationType.NONE, 0);
        }
        for (CombatantState target : effectOnlyTargets) {
            if (damageResults.containsKey(target)) {
                continue;
            }
            emitVisual(batch.user().entity(), target.entity(), visualStack(batch, suppressCardVisual), ItemStack.EMPTY, suppressCardVisual || emittedCardVisual ? null : batch.card(), new BattleDamageResult(0, 0, 0), blockGains.getOrDefault(target, 0), heals.getOrDefault(target, 0), 0, hitAnimationType, 0);
            emittedCardVisual = true;
        }
        if (!suppressCardVisual && !emittedCardVisual && batch.card() != null) {
            emitVisual(batch.user().entity(), batch.user().entity(), batch.stack(), batch.card(), new BattleDamageResult(0, 0, 0), 0);
        }
    }

    private boolean triggersThorns(PendingEffect effect, CombatantState attacker) {
        return effect.kind() == CardEffectKind.DAMAGE
                && !effect.effectDamage()
                && effect.amount() > 0
                && attacker != null
                && effect.target() != null
                && attacker != effect.target()
                && !attacker.fakeDead()
                && effect.target().effectAmount(BattleEffectType.THORNS) > 0;
    }

    private BattleDamageResult applyThornsDamage(CombatantState thornsOwner, CombatantState attacker) {
        if (thornsOwner == null || attacker == null || attacker.fakeDead()) {
            return new BattleDamageResult(0, 0, 0);
        }
        int damage = thornsOwner.effectAmount(BattleEffectType.THORNS);
        if (damage <= 0) {
            return new BattleDamageResult(0, 0, 0);
        }
        return attacker.applyEffectDamage(damage, playerKillCredit(thornsOwner));
    }

    private static ItemStack visualStack(PendingCardBatch batch, boolean suppressCardVisual) {
        return suppressCardVisual ? ItemStack.EMPTY : batch.stack();
    }

    private static ItemStack visualStack(PendingCardBatch batch) {
        if (batch.card() != null && isVindicatorAxeCard(batch.card())) {
            return new ItemStack(Items.IRON_AXE);
        }
        if (batch.card() != null && isVexCard(batch.card())) {
            return new ItemStack(Items.IRON_SWORD);
        }
        if (batch.card() != null && "builtin_monster_bow_strike".equals(batch.card().cardId())) {
            return new ItemStack(Items.BOW);
        }
        return batch.stack();
    }

    private static boolean isVindicatorAxeCard(CardInstance card) {
        if (card == null) {
            return false;
        }
        return "builtin_monster_axe_chop".equals(card.cardId())
                || "builtin_monster_heavy_axe_blow".equals(card.cardId())
                || "builtin_monster_executioners_blow".equals(card.cardId())
                || "builtin_monster_raised_axe_guard".equals(card.cardId())
                || "builtin_monster_fanatic_might".equals(card.cardId());
    }

    private static boolean isVexCard(CardInstance card) {
        if (card == null) {
            return false;
        }
        return "builtin_monster_razor_rush".equals(card.cardId())
                || "builtin_monster_flicker_cut".equals(card.cardId())
                || "builtin_monster_phase_stab".equals(card.cardId())
                || "builtin_monster_evasive_flicker".equals(card.cardId())
                || "builtin_monster_frenzied_dive".equals(card.cardId());
    }

    private static BattleDamageResult mergeDamageResult(BattleDamageResult first, BattleDamageResult second) {
        return new BattleDamageResult(
                first.blockedDamage() + second.blockedDamage(),
                first.baseHealthDamage() + second.baseHealthDamage(),
                first.healthDamage() + second.healthDamage());
    }

    private void applyOwnTurnStartEffects(CombatantState state) {
        int abundantArrows = state.effectAmount(BattleEffectType.ABUNDANT_ARROWS);
        for (int i = 0; i < abundantArrows; i++) {
            MoonSpireCardRegistry.cardInstance("item_minecraft_arrow")
                    .ifPresent(card -> state.deck().addGeneratedToHandOrDiscard(card.copyForBattle()));
        }
        int poison = state.effectAmount(BattleEffectType.POISON);
        if (poison > 0) {
            BattleDamageResult result = state.applyEffectDamage(poison, null);
            emitVisual(state.entity(), state.entity(), ItemStack.EMPTY, null, result, 0, 0, 0);
            state.reduceEffect(BattleEffectType.POISON, 1);
        }
    }

    private int hungerDrawReduction(CombatantState state) {
        return state != null && state.effectAmount(BattleEffectType.HUNGER) > 0 ? 1 : 0;
    }

    private int consumeHungerDrawReduction(CombatantState state) {
        return consumeHungerDrawReduction(state, false);
    }

    private int consumeHungerDrawReduction(CombatantState state, boolean adjustPredrawnHand) {
        int reduction = hungerDrawReduction(state);
        if (reduction > 0) {
            if (adjustPredrawnHand && state.deck().applyAdditionalStartTurnDrawReduction(reduction)) {
                markDirty();
            }
            state.reduceEffect(BattleEffectType.HUNGER, 1);
        }
        return reduction;
    }

    private void applyHunger(CombatantState target, int amount) {
        if (target == null || amount <= 0) {
            return;
        }
        target.addEffect(BattleEffectType.HUNGER, amount);
        if (enemyHandsPredrawnForCurrentTurn && phase == BattlePhase.PLAYER_TURN && enemyStates.contains(target) && target.deck().applyAdditionalStartTurnDrawReduction(hungerDrawReduction(target))) {
            markDirty();
        }
    }

    private void applyOwnTurnEndEffects(CombatantState state) {
        if (state == null) {
            return;
        }
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
            boolean triggerFuse = state.effectAmount(BattleEffectType.FUSE) > 0;
            BattleDamageResult result = state.applyEffectDamage(burn, null);
            emitVisual(state.entity(), state.entity(), ItemStack.EMPTY, null, result, 0, 0, 0);
            state.reduceEffect(BattleEffectType.BURN, 1);
            if (triggerFuse) {
                if (state.fakeDead()) {
                    state.clearFakeDeath();
                }
                triggerSelfDestruct(state);
                return;
            }
        }
        int fuse = state.effectAmount(BattleEffectType.FUSE);
        if (fuse > 0) {
            state.reduceEffect(BattleEffectType.FUSE, 1);
            if (fuse <= 1) {
                triggerSelfDestruct(state);
                return;
            }
        }
        state.reduceEffect(BattleEffectType.WEAKNESS, 1);
        state.reduceEffect(BattleEffectType.GLOWING, 1);
        state.decayEndOfTurnEffects();
    }

    private void triggerSelfDestruct(CombatantState user) {
        if (user == null) {
            return;
        }
        if (user.fakeDead()) {
            return;
        }
        if (pendingSelfDestructs.stream().anyMatch(pending -> pending.user() == user)) {
            return;
        }
        user.clearEffect(BattleEffectType.FUSE);
        emitVisual(user.entity(), user.entity(), ItemStack.EMPTY, ItemStack.EMPTY, MoonSpireCardRegistry.selfDestructViewCard().createInstance(), new BattleDamageResult(0, 0, 0), 0, 0, 0, BattleVisualEvent.AnimationType.SELF_DESTRUCT, SELF_DESTRUCT_ANIMATION_TICKS);
        pendingSelfDestructs.add(new PendingSelfDestruct(user, SELF_DESTRUCT_ANIMATION_TICKS));
        markDirty();
    }

    private void tickPendingSelfDestructs() {
        if (pendingSelfDestructs.isEmpty()) {
            return;
        }
        Iterator<PendingSelfDestruct> iterator = pendingSelfDestructs.iterator();
        List<PendingSelfDestruct> nextPending = new ArrayList<>();
        while (iterator.hasNext()) {
            PendingSelfDestruct pending = iterator.next();
            PendingSelfDestruct next = pending.next();
            if (next.ticks() > 0) {
                nextPending.add(next);
            } else {
                resolveSelfDestruct(pending.user());
            }
            iterator.remove();
        }
        pendingSelfDestructs.addAll(nextPending);
    }

    private void resolveSelfDestruct(CombatantState user) {
        if (user == null || user.fakeDead()) {
            return;
        }
        Map<CombatantState, BattleDamageResult> damageResults = new LinkedHashMap<>();
        UUID killCredit = playerKillCredit(user);
        for (CombatantState target : alive(allStates())) {
            if (target == user || target.fakeDead()) {
                continue;
            }
            BattleDamageResult result = target.applyEffectDamage(CardBalance.SELF_DESTRUCT_DAMAGE, killCredit);
            damageResults.put(target, result);
        }
        for (Map.Entry<CombatantState, BattleDamageResult> entry : damageResults.entrySet()) {
            emitVisual(user.entity(), entry.getKey().entity(), ItemStack.EMPTY, ItemStack.EMPTY, null, entry.getValue(), 0, 0, 0, BattleVisualEvent.AnimationType.NONE, 0);
        }
        user.killForSelfDestruct(null);
        finishDelayTicks = Math.max(finishDelayTicks, CombatantState.FAKE_DEATH_ANIMATION_TICKS);
        markDirty();
    }

    private UUID playerKillCredit(CombatantState user) {
        if (user.entity() instanceof ServerPlayer player) {
            return player.getUUID();
        }
        return null;
    }

    private void faceTarget(LivingEntity actor, LivingEntity target) {
        facePosition(actor, visualFacingPoint(target));
    }

    private void facePosition(LivingEntity actor, Vec3 target) {
        applyFacing(actor, yawTo(actor, target), pitchTo(actor, target));
    }

    private float yawTo(LivingEntity actor, Vec3 target) {
        double dx = target.x - actor.getX();
        double dz = target.z - actor.getZ();
        if (dx * dx + dz * dz <= 0.0001D) {
            return actor.getYRot();
        }
        return (float) (Math.atan2(dz, dx) * 57.2957763671875D) - 90.0F;
    }

    private float pitchTo(LivingEntity actor, Vec3 target) {
        double dx = target.x - actor.getX();
        double dy = target.y - actor.getEyeY();
        double dz = target.z - actor.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= 0.0001D) {
            return actor.getXRot();
        }
        return (float) (-(Math.atan2(dy, horizontal) * 57.2957763671875D));
    }

    private void turnTowardPosition(LivingEntity actor, Vec3 target, float maxDegrees) {
        float yaw = yawTo(actor, target);
        float delta = Mth.wrapDegrees(yaw - actor.getYRot());
        applyFacingYaw(actor, actor.getYRot() + Mth.clamp(delta, -maxDegrees, maxDegrees));
    }

    private void applyFacingYaw(LivingEntity actor, float yaw) {
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
        actor.yHeadRotO = yaw;
        actor.yBodyRotO = yaw;
    }

    private void applyFacing(LivingEntity actor, float yaw, float pitch) {
        applyFacingYaw(actor, yaw);
        actor.setXRot(Mth.clamp(pitch, -80.0F, 80.0F));
        actor.setOldPosAndRot();
    }

    private void prepareFacingForNextBatch() {
        if (pendingCardBatches.isEmpty()) {
            pendingFacing = null;
            return;
        }
        prepareFacingForBatch(pendingCardBatches.getFirst());
    }

    private void prepareFacingForBatch(PendingCardBatch batch) {
        Vec3 point = facingPointForBatch(batch);
        pendingFacing = point == null ? null : new PendingFacing(batch);
    }

    private void tickPendingFacing() {
        if (pendingFacing == null || pendingCardBatches.isEmpty() || pendingCardBatches.getFirst() != pendingFacing.batch()) {
            pendingFacing = null;
            return;
        }
        PendingCardBatch batch = pendingFacing.batch();
        Vec3 point = facingPointForBatch(batch);
        if (point == null || !batch.user().entity().isAlive()) {
            pendingFacing = null;
            return;
        }
        turnTowardPosition(batch.user().entity(), point, CARD_FACING_TURN_DEGREES_PER_TICK);
    }

    private void finishPendingFacing(PendingCardBatch batch) {
        if (pendingFacing != null && pendingFacing.batch() != batch) {
            pendingFacing = null;
        }
        Vec3 point = facingPointForBatch(batch);
        if (point != null && batch.user().entity().isAlive()) {
            facePosition(batch.user().entity(), point);
        }
        pendingFacing = null;
    }

    private Vec3 facingPointForBatch(PendingCardBatch batch) {
        LinkedHashSet<CombatantState> targets = new LinkedHashSet<>();
        for (PendingEffect effect : batch.effects()) {
            CombatantState target = effect.target();
            if (target != null && target != batch.user() && !target.fakeDead() && target.entity().isAlive()) {
                targets.add(target);
            }
        }
        return facingPointForTargets(batch.user().entity(), List.copyOf(targets));
    }

    private Vec3 facingPointForTargets(LivingEntity actor, List<CombatantState> targets) {
        if (targets.isEmpty()) {
            return null;
        }
        if (targets.size() == 1) {
            return visualFacingPoint(targets.getFirst().entity());
        }
        Vec3 actorPos = actor.position();
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        Vec3 directionSum = Vec3.ZERO;
        int count = 0;
        for (CombatantState target : targets) {
            Vec3 targetPos = visualFacingPoint(target.entity());
            x += targetPos.x;
            y += targetPos.y;
            z += targetPos.z;
            Vec3 horizontal = targetPos.subtract(actorPos).multiply(1.0D, 0.0D, 1.0D);
            if (horizontal.lengthSqr() > 0.0001D) {
                directionSum = directionSum.add(horizontal.normalize());
            }
            count++;
        }
        Vec3 center = new Vec3(x / count, y / count, z / count);
        Vec3 toCenter = center.subtract(actorPos).multiply(1.0D, 0.0D, 1.0D);
        double directionStability = directionSum.length() / count;
        if (toCenter.lengthSqr() >= CARD_FACING_CENTER_MIN_DISTANCE_SQR && directionStability >= CARD_FACING_DIRECTION_STABILITY_MIN) {
            return center;
        }
        CombatantState stableTarget = stableFacingTarget(actor, targets);
        return stableTarget == null ? null : visualFacingPoint(stableTarget.entity());
    }

    private CombatantState stableFacingTarget(LivingEntity actor, List<CombatantState> targets) {
        CombatantState bestTarget = null;
        float bestYawDelta = Float.MAX_VALUE;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (CombatantState target : targets) {
            Vec3 targetPos = visualFacingPoint(target.entity());
            Vec3 horizontal = targetPos.subtract(actor.position()).multiply(1.0D, 0.0D, 1.0D);
            double distanceSqr = horizontal.lengthSqr();
            if (distanceSqr <= 0.0001D) {
                continue;
            }
            float yawDelta = Math.abs(Mth.wrapDegrees(yawTo(actor, targetPos) - actor.getYRot()));
            if (yawDelta + 0.1F < bestYawDelta || (Math.abs(yawDelta - bestYawDelta) <= 0.1F && distanceSqr < bestDistanceSqr)) {
                bestTarget = target;
                bestYawDelta = yawDelta;
                bestDistanceSqr = distanceSqr;
            }
        }
        return bestTarget;
    }

    private Vec3 visualFacingPoint(LivingEntity target) {
        if (target == null) {
            return Vec3.ZERO;
        }
        return new Vec3(target.getX(), target.getEyeY(), target.getZ());
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
            KnockbackState knockbackState = knockbackStates.get(entity.getId());
            Vec3 nextLockedPos = freezeEntity(entity, lock.lockedPos(), knockbackState);
            locks.put(entity.getId(), lock.withLockedPos(nextLockedPos));
            if (knockbackState != null && knockbackState.active()) {
                knockbackStates.put(entity.getId(), knockbackState);
            } else {
                knockbackStates.remove(entity.getId());
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

    private Vec3 freezeEntity(LivingEntity entity, Vec3 lockedPos, KnockbackState knockbackState) {
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
            if (knockbackState != null) {
                knockbackState.stop();
            }
            return entity.position();
        }
        if (knockbackState != null && knockbackState.active()) {
            Vec3 nextLockedPos = advanceBattleKnockback(entity, knockbackState);
            entity.xxa = 0.0F;
            entity.yya = 0.0F;
            entity.zza = 0.0F;
            entity.setJumping(false);
            if (!knockbackState.active()) {
                Vec3 safePosition = safeReturnPosition(entity, entity.position());
                if (safePosition.distanceToSqr(entity.position()) > 0.0004D) {
                    entity.teleportTo(safePosition.x, safePosition.y, safePosition.z);
                    entity.setOldPosAndRot();
                }
                syncFinalPlayerPosition(entity);
                return safePosition;
            }
            return nextLockedPos;
        }
        Vec3 movement = applyNoAiBattleFall(entity, entity.getDeltaMovement());
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

    private Vec3 applyNoAiBattleFall(LivingEntity entity, Vec3 movement) {
        if (!(entity instanceof Mob mob) || !mob.isNoAi() || entity.onGround() || entity.isNoGravity()) {
            return movement;
        }
        double fallY = movement.y;
        if (fallY > -3.92D) {
            fallY -= 0.08D;
        }
        fallY *= 0.98D;
        entity.move(MoverType.SELF, new Vec3(0.0D, fallY, 0.0D));
        if (entity.onGround() && fallY < 0.0D) {
            fallY = 0.0D;
        }
        entity.hasImpulse = true;
        return new Vec3(movement.x, fallY, movement.z);
    }

    private Vec3 advanceBattleKnockback(LivingEntity entity, KnockbackState state) {
        Vec3 velocity = state.velocity();
        if (!entity.isNoGravity() && (!entity.onGround() || velocity.y > 0.0D)) {
            velocity = new Vec3(velocity.x, Math.max(KNOCKBACK_MAX_FALL_SPEED, velocity.y - KNOCKBACK_GRAVITY), velocity.z);
        }
        entity.move(MoverType.SELF, velocity);
        if (entity.onGround() && velocity.y < 0.0D) {
            velocity = new Vec3(velocity.x, 0.0D, velocity.z);
        }
        Vec3 nextVelocity = new Vec3(velocity.x * KNOCKBACK_HORIZONTAL_DRAG, velocity.y * KNOCKBACK_VERTICAL_DRAG, velocity.z * KNOCKBACK_HORIZONTAL_DRAG);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true;
        state.advance(nextVelocity, entity.onGround());
        return entity.position();
    }

    private void syncFinalPlayerPosition(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
        }
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
                Vec3 safeStart = safeReturnPosition(state.entity(), lock.startPos());
                lock.resetToPosition(state.entity(), safeStart);
                locks.put(state.entity().getId(), lock.withLockedPos(safeStart));
            }
        }
        knockbackStates.clear();
        faceTeams();
    }

    private Vec3 safeReturnPosition(LivingEntity entity, Vec3 preferred) {
        AABB boxAtPreferred = entity.getBoundingBox().move(preferred.subtract(entity.position()));
        for (int i = 0; i <= 12; i++) {
            double offset = i * 0.25D;
            AABB candidateBox = boxAtPreferred.move(0.0D, offset, 0.0D);
            if (entity.level().noCollision(entity, candidateBox)) {
                return preferred.add(0.0D, offset, 0.0D);
            }
        }
        return preferred.add(0.0D, 3.0D, 0.0D);
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
            restoreSurvivor(state, lock);
        }
    }

    private void restoreSurvivor(CombatantState state, EntityLock lock) {
        LivingEntity entity = state.entity();
        Vec3 safeStart = safeReturnPosition(entity, lock.startPos());
        lock.restoreSurvivor(entity, safeStart);
        locks.put(entity.getId(), lock.withLockedPos(safeStart));
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
        return playerStates.stream().map(state -> state.snapshot(battleDeckCount(state))).toList();
    }

    private List<BattleCombatantSnapshot> enemySnapshots() {
        return enemyStates.stream().map(state -> state.snapshot(battleDeckCount(state))).toList();
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

    private record PendingFacing(PendingCardBatch batch) {
    }

    private record PendingSelfDestruct(CombatantState user, int ticks) {
        private PendingSelfDestruct next() {
            return new PendingSelfDestruct(user, ticks - 1);
        }
    }

    private record EntityLock(Vec3 startPos, Vec3 lockedPos, float yRot, float xRot, float startHealth, int hotbarSlot, boolean noAiBeforeBattle, boolean noPhysicsBeforeBattle) {
        private static EntityLock capture(LivingEntity entity) {
            int slot = entity instanceof ServerPlayer player ? player.getInventory().selected : 0;
            boolean noAi = entity instanceof Mob mob && mob.isNoAi();
            return new EntityLock(entity.position(), entity.position(), entity.getYRot(), entity.getXRot(), entity.getHealth(), slot, noAi, entity.noPhysics);
        }

        private EntityLock withLockedPos(Vec3 lockedPos) {
            return new EntityLock(startPos, lockedPos, yRot, xRot, startHealth, hotbarSlot, noAiBeforeBattle, noPhysicsBeforeBattle);
        }

        private void resetToPosition(LivingEntity entity, Vec3 pos) {
            entity.teleportTo(pos.x, pos.y, pos.z);
            clearMotion(entity);
        }

        private void restoreBeforeDeath(LivingEntity entity) {
            clearMotion(entity);
            entity.noPhysics = noPhysicsBeforeBattle;
            if (entity instanceof Mob mob) {
                mob.setNoAi(noAiBeforeBattle);
            }
        }

        private void restoreSurvivor(LivingEntity entity, Vec3 pos) {
            entity.noPhysics = noPhysicsBeforeBattle;
            entity.teleportTo(pos.x, pos.y, pos.z);
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
            entity.noPhysics = noPhysicsBeforeBattle;
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

    private static final class KnockbackState {
        private int remainingTicks;
        private int minimumTicks;
        private Vec3 velocity;

        private KnockbackState(int remainingTicks, int minimumTicks, Vec3 velocity) {
            this.remainingTicks = Math.max(0, remainingTicks);
            this.minimumTicks = Math.max(0, minimumTicks);
            this.velocity = velocity == null ? Vec3.ZERO : velocity;
        }

        private boolean active() {
            return remainingTicks > 0 && (minimumTicks > 0 || velocity.lengthSqr() > KNOCKBACK_STOP_SPEED_SQR);
        }

        private Vec3 velocity() {
            return velocity;
        }

        private void advance(Vec3 nextVelocity, boolean onGround) {
            remainingTicks = Math.max(0, remainingTicks - 1);
            minimumTicks = Math.max(0, minimumTicks - 1);
            velocity = nextVelocity == null ? Vec3.ZERO : nextVelocity;
            if (onGround && minimumTicks <= 0 && velocity.lengthSqr() <= KNOCKBACK_STOP_SPEED_SQR) {
                stop();
            }
            if (remainingTicks <= 0) {
                stop();
            }
        }

        private void stop() {
            remainingTicks = 0;
            minimumTicks = 0;
            velocity = Vec3.ZERO;
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

    private record ArrowResolution(CombatantState user, CombatantState selectedTarget, CardInstance card, int amount, List<CardEffect> attachedEffects) {
        private ArrowResolution {
            attachedEffects = List.copyOf(attachedEffects == null ? List.of() : attachedEffects);
        }
    }

    private record PendingHandSelection(CombatantState user, PendingHandSelectionSnapshot.Action action, int requiredCount, List<UUID> candidateIds, ArrowResolution arrowResolution) {
        private PendingHandSelection {
            candidateIds = List.copyOf(candidateIds);
        }
    }

    private record MonsterCardChoice(UUID cardId, int handIndex, int selectedEntityId, CombatantState selectedTarget, double score, MonsterCardCategory category) {
        private MonsterCardChoice(int handIndex, int selectedEntityId, CombatantState selectedTarget, double score) {
            this(null, handIndex, selectedEntityId, selectedTarget, score, MonsterCardCategory.UTILITY);
        }

        private MonsterCardChoice withCardId(UUID cardId) {
            return new MonsterCardChoice(cardId, handIndex, selectedEntityId, selectedTarget, score, category);
        }

        private MonsterCardChoice withCategory(MonsterCardCategory category) {
            return new MonsterCardChoice(cardId, handIndex, selectedEntityId, selectedTarget, score, category);
        }

        private MonsterCardChoice withHandIndex(int handIndex) {
            return new MonsterCardChoice(cardId, handIndex, selectedEntityId, selectedTarget, score, category);
        }
    }

    private enum MonsterCardCategory {
        STATUS(MONSTER_AI_CATEGORY_STATUS_WEIGHT),
        UTILITY(MONSTER_AI_CATEGORY_UTILITY_WEIGHT),
        ATTACK(MONSTER_AI_CATEGORY_ATTACK_WEIGHT),
        DEFENSE(MONSTER_AI_CATEGORY_DEFENSE_WEIGHT);

        private final double weight;

        MonsterCardCategory(double weight) {
            this.weight = weight;
        }

        private double weight() {
            return weight;
        }
    }

    private final class MonsterTurnPlan {
        private final List<MonsterCardChoice> entries = new ArrayList<>();
        private long handVersion;
        private int energyLeft;
        private boolean exhausted;

        private MonsterTurnPlan(long handVersion, int energyLeft) {
            this.handVersion = handVersion;
            this.energyLeft = energyLeft;
        }

        private List<MonsterCardChoice> entries() {
            return entries;
        }

        private long handVersion() {
            return handVersion;
        }

        private void setHandVersion(long handVersion) {
            this.handVersion = handVersion;
        }

        private int energyLeft() {
            return energyLeft;
        }

        private void setEnergyLeft(int energyLeft) {
            this.energyLeft = energyLeft;
        }

        private void rebuild(List<MonsterCardChoice> choices) {
            entries.clear();
            entries.addAll(choices);
            exhausted = choices.isEmpty();
        }

        private void clear() {
            entries.clear();
            exhausted = true;
        }

        private boolean exhausted() {
            return exhausted;
        }

        private boolean hasDisplayableEntry(CombatantState monster) {
            return !displayableCards(monster, phase == BattlePhase.MONSTER_TURN, true).isEmpty();
        }

        private List<CardInstance> cards(CombatantState monster, boolean useCurrentEnergy) {
            return displayableCards(monster, useCurrentEnergy, false);
        }

        private List<CardInstance> displayableCards(CombatantState monster, boolean useCurrentEnergy, boolean stopAfterFirst) {
            if (monster == null) {
                return List.of();
            }
            List<CardInstance> cards = new ArrayList<>();
            int remainingEnergy = useCurrentEnergy ? monster.energyLeft() : energyLeft;
            for (MonsterCardChoice entry : entries) {
                int handIndex = entry == null || entry.cardId() == null ? -1 : handIndexById(monster.deck().hand(), entry.cardId());
                CardInstance card = handIndex < 0 ? null : monster.deck().peekHand(handIndex);
                if (card == null || !card.hasAnyEffect()) {
                    continue;
                }
                if (card.cost() > remainingEnergy) {
                    continue;
                }
                if (card.requiresExplicitTarget() && (entry.selectedTarget() == null || entry.selectedTarget().fakeDead())) {
                    continue;
                }
                remainingEnergy -= Math.max(0, card.cost());
                cards.add(card);
                if (stopAfterFirst) {
                    return cards;
                }
            }
            return cards;
        }

        private MonsterCardChoice pollUsable(CombatantState monster) {
            while (!entries.isEmpty()) {
                MonsterCardChoice entry = entries.removeFirst();
                MonsterCardChoice usable = usableChoice(monster, entry);
                if (usable != null) {
                    if (entries.isEmpty()) {
                        exhausted = true;
                    }
                    return usable;
                }
            }
            exhausted = true;
            return null;
        }

        private MonsterCardChoice usableChoice(CombatantState monster, MonsterCardChoice entry) {
            if (monster == null || entry == null || entry.cardId() == null) {
                return null;
            }
            int handIndex = handIndexById(monster.deck().hand(), entry.cardId());
            if (handIndex < 0) {
                return null;
            }
            CardInstance card = monster.deck().peekHand(handIndex);
            if (card == null || card.cost() > monster.energyLeft() || !card.hasAnyEffect()) {
                return null;
            }
            if (card.requiresExplicitTarget() && (entry.selectedTarget() == null || entry.selectedTarget().fakeDead())) {
                return null;
            }
            return entry.withHandIndex(handIndex);
        }

        private int handIndexById(List<CardInstance> hand, UUID id) {
            for (int i = 0; i < hand.size(); i++) {
                if (hand.get(i).id().equals(id)) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static final class MonsterAiView {
        private final CombatantState state;
        private final int entityId;
        private final float maxHealth;
        private float health;
        private int defense;
        private int roundSpeed;
        private final Map<BattleEffectType, Integer> effects;

        private MonsterAiView(CombatantState state, int entityId, float health, float maxHealth, int defense, int roundSpeed, Map<BattleEffectType, Integer> effects) {
            this.state = state;
            this.entityId = entityId;
            this.health = Math.max(0.0F, health);
            this.maxHealth = Math.max(1.0F, maxHealth);
            this.defense = Math.max(0, defense);
            this.roundSpeed = Math.max(1, roundSpeed);
            this.effects = new EnumMap<>(BattleEffectType.class);
            this.effects.putAll(effects);
        }

        private static MonsterAiView live(CombatantState state) {
            Map<BattleEffectType, Integer> effects = new EnumMap<>(BattleEffectType.class);
            for (BattleEffectType type : BattleEffectType.values()) {
                int amount = state.effectAmount(type);
                if (amount != 0) {
                    effects.put(type, amount);
                }
            }
            return new MonsterAiView(state, state.entity().getId(), state.battleHealth(), state.maxBattleHealth(), state.defense(), state.roundSpeed(), effects);
        }

        private MonsterAiView withDefense(int defense) {
            return new MonsterAiView(state, entityId, health, maxHealth, defense, roundSpeed, effects);
        }

        private CombatantState state() {
            return state;
        }

        private int entityId() {
            return entityId;
        }

        private float health() {
            return health;
        }

        private float maxHealth() {
            return effectiveMaxHealth();
        }

        private double healthRatio() {
            return Math.max(0.0D, Math.min(1.0D, health / effectiveMaxHealth()));
        }

        private int defense() {
            return defense;
        }

        private int roundSpeed() {
            return Math.max(1, roundSpeed + effectAmount(BattleEffectType.HASTE) - effectAmount(BattleEffectType.SLOWNESS));
        }

        private int effectAmount(BattleEffectType type) {
            return effects.getOrDefault(type, 0);
        }

        private void addDefense(int amount) {
            int gain = Math.max(0, amount);
            if (gain <= 0) {
                return;
            }
            int eroded = Math.min(gain, Math.max(0, effectAmount(BattleEffectType.TIDAL_EROSION)));
            if (eroded > 0) {
                reduceEffect(BattleEffectType.TIDAL_EROSION, eroded);
            }
            defense += Math.max(0, gain - eroded);
        }

        private void heal(int amount) {
            health = Math.min(effectiveMaxHealth(), health + Math.max(0, amount));
        }

        private void takeDamage(int amount) {
            int incoming = Math.max(0, amount);
            int blocked = Math.min(defense, incoming);
            defense -= blocked;
            health = Math.max(0.0F, health - Math.max(0, incoming - blocked));
        }

        private void addEffect(BattleEffectType type, int amount) {
            if (type == null || (!type.allowsNegativeStacks() && amount <= 0) || (type.allowsNegativeStacks() && amount == 0)) {
                return;
            }
            int next = effects.getOrDefault(type, 0) + amount;
            if (next == 0) {
                effects.remove(type);
            } else if (type.allowsNegativeStacks() || next > 0) {
                effects.put(type, next);
            } else {
                effects.remove(type);
            }
            health = Math.min(health, effectiveMaxHealth());
        }

        private void reduceEffect(BattleEffectType type, int amount) {
            if (type == null || amount <= 0 || !effects.containsKey(type)) {
                return;
            }
            int next = effects.get(type) - amount;
            if (next > 0 || (type.allowsNegativeStacks() && next != 0)) {
                effects.put(type, next);
            } else {
                effects.remove(type);
            }
            health = Math.min(health, effectiveMaxHealth());
        }

        private float effectiveMaxHealth() {
            return Math.max(1.0F, maxHealth - Math.max(0, effectAmount(BattleEffectType.WITHER)));
        }
    }

    private sealed interface BattleAnimation permits LungeAnimation, ProjectileAnimation, GuardianBeamAnimation, RiptideRushAnimation {
        PendingCardBatch batch();

        boolean tick();

        boolean movesEntity(LivingEntity entity);

        default boolean readyToApplyEffects() {
            return false;
        }

        boolean effectsApplied();

        void markEffectsApplied();
    }

    private final class LungeAnimation implements BattleAnimation {
        private final PendingCardBatch batch;
        private final CombatantState target;
        private final LivingEntity actor;
        private final Vec3 start;
        private final Vec3 strike;
        private final boolean pounce;
        private final LungeStyle style;
        private final boolean noPhysicsBeforeAnimation;
        private int ticks;
        private Phase phase = Phase.LUNGE;
        private boolean effectsApplied;

        private LungeAnimation(PendingCardBatch batch, CombatantState target, LungeStyle style) {
            this.batch = batch;
            this.target = target;
            this.actor = batch.user().entity();
            this.start = actor.position();
            this.pounce = batch.card() != null && "builtin_monster_pounce".equals(batch.card().cardId());
            this.style = style == null ? LungeStyle.NORMAL : style;
            this.phase = this.style.hasPrepare() ? Phase.PREPARE : Phase.LUNGE;
            this.noPhysicsBeforeAnimation = actor.noPhysics;
            Vec3 targetPos = target.entity().position();
            Vec3 direction = targetPos.subtract(start);
            Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
            Vec3 normalized = horizontal.lengthSqr() > 0.0001D ? horizontal.normalize() : actor.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
            double stopDistance = pounce ? pounceStopDistance(actor, target.entity()) : LUNGE_STOP_DISTANCE;
            double distance = Math.max(0.0D, Math.min(LUNGE_REACH, horizontal.length() - stopDistance));
            if (!pounce && distance < MIN_LUNGE_TRAVEL_DISTANCE) {
                distance = 0.0D;
            }
            this.strike = start.add(normalized.scale(distance));
        }

        @Override
        public PendingCardBatch batch() {
            return batch;
        }

        @Override
        public boolean tick() {
            if (!actor.isAlive()) {
                actor.noPhysics = noPhysicsBeforeAnimation;
                return true;
            }
            if (target.fakeDead() && !effectsApplied) {
                finishAtCurrentPosition();
                return true;
            }
            finishPendingFacing(batch);
            if (phase == Phase.PREPARE) {
                ticks++;
                moveActor(start);
                if (ticks >= style.prepareTicks()) {
                    ticks = 0;
                    phase = Phase.LUNGE;
                }
                return false;
            }
            if (phase == Phase.LUNGE) {
                ticks++;
                moveActor(lungePosition(ticks / (double) lungeTicks()));
                if (ticks >= lungeTicks()) {
                    ticks = 0;
                    phase = style.hasStrike() ? Phase.STRIKE : Phase.HIT_PAUSE;
                    moveActor(strike);
                    return false;
                }
                return false;
            }
            if (phase == Phase.STRIKE) {
                ticks++;
                moveActor(strike);
                if (ticks >= style.strikeTicks()) {
                    phase = Phase.HIT_PAUSE;
                    return false;
                }
                return false;
            }
            if (phase == Phase.HIT_PAUSE) {
                ticks++;
                moveActor(strike);
                if (ticks >= hitPauseTicks()) {
                    finishAtStrike();
                    return true;
                }
                return false;
            }
            finishAtStrike();
            return true;
        }

        @Override
        public boolean movesEntity(LivingEntity entity) {
            return entity == actor;
        }

        @Override
        public boolean readyToApplyEffects() {
            return phase == (style.hasStrike() ? Phase.STRIKE : Phase.HIT_PAUSE) && !effectsApplied;
        }

        @Override
        public boolean effectsApplied() {
            return effectsApplied;
        }

        @Override
        public void markEffectsApplied() {
            effectsApplied = true;
        }

        private Vec3 start() {
            return start;
        }

        private Vec3 strike() {
            return strike;
        }

        private int visualTicks() {
            return style.visualTicks();
        }

        private BattleVisualEvent.AnimationType hitAnimationType() {
            return style.animationType();
        }

        private BattleVisualEvent.AnimationType animationType() {
            return style.animationType();
        }

        private int lungeTicks() {
            return style.approachTicks();
        }

        private int hitPauseTicks() {
            return style.recoverTicks();
        }

        private Vec3 lungePosition(double progress) {
            Vec3 position = lerp(start, strike, progress);
            if (!pounce) {
                return position;
            }
            double t = Math.max(0.0D, Math.min(1.0D, progress));
            double jump = Math.sin(Math.PI * t) * POUNCE_JUMP_HEIGHT;
            return position.add(0.0D, jump, 0.0D);
        }

        private void moveActor(Vec3 pos) {
            actor.noPhysics = true;
            Vec3 delta = pos.subtract(actor.position());
            actor.move(MoverType.SELF, delta);
            actor.setDeltaMovement(Vec3.ZERO);
            actor.hasImpulse = true;
        }

        private void finishAtStrike() {
            Vec3 safeStrike = safeReturnPosition(actor, strike);
            moveActor(safeStrike);
            syncFinalPlayerPosition(actor);
            actor.noPhysics = noPhysicsBeforeAnimation;
            EntityLock lock = locks.get(actor.getId());
            if (lock != null) {
                locks.put(actor.getId(), lock.withLockedPos(safeStrike));
            }
        }

        private double pounceStopDistance(LivingEntity actor, LivingEntity target) {
            double actorRadius = actor.getBbWidth() * 0.5D;
            double targetRadius = target.getBbWidth() * 0.5D;
            return Math.max(0.12D, actorRadius + targetRadius + POUNCE_CONTACT_MARGIN);
        }

        private void finishAtCurrentPosition() {
            Vec3 safePosition = safeReturnPosition(actor, actor.position());
            moveActor(safePosition);
            syncFinalPlayerPosition(actor);
            actor.noPhysics = noPhysicsBeforeAnimation;
            EntityLock lock = locks.get(actor.getId());
            if (lock != null) {
                locks.put(actor.getId(), lock.withLockedPos(safePosition));
            }
        }

        private enum Phase {
            PREPARE,
            LUNGE,
            STRIKE,
            HIT_PAUSE
        }
    }

    private enum LungeStyle {
        NORMAL,
        VINDICATOR_AXE,
        VEX_CHARGE;

        private boolean hasPrepare() {
            return this == VINDICATOR_AXE || this == VEX_CHARGE;
        }

        private int prepareTicks() {
            return switch (this) {
                case VINDICATOR_AXE -> VINDICATOR_AXE_RAISE_TICKS;
                case VEX_CHARGE -> VEX_CHARGE_RAISE_TICKS;
                case NORMAL -> 0;
            };
        }

        private int approachTicks() {
            return switch (this) {
                case VINDICATOR_AXE -> VINDICATOR_AXE_APPROACH_TICKS;
                case VEX_CHARGE -> VEX_CHARGE_APPROACH_TICKS;
                case NORMAL -> MELEE_LUNGE_TICKS;
            };
        }

        private boolean hasStrike() {
            return this == VINDICATOR_AXE;
        }

        private int strikeTicks() {
            return this == VINDICATOR_AXE ? VINDICATOR_AXE_STRIKE_TICKS : 0;
        }

        private int recoverTicks() {
            return switch (this) {
                case VINDICATOR_AXE -> VINDICATOR_AXE_RECOVER_TICKS;
                case VEX_CHARGE -> VEX_CHARGE_HIT_PAUSE_TICKS;
                case NORMAL -> MELEE_HIT_PAUSE_TICKS;
            };
        }

        private int visualTicks() {
            return prepareTicks() + approachTicks() + strikeTicks() + recoverTicks();
        }

        private BattleVisualEvent.AnimationType animationType() {
            return switch (this) {
                case VINDICATOR_AXE -> BattleVisualEvent.AnimationType.VINDICATOR_AXE_SWING;
                case VEX_CHARGE -> BattleVisualEvent.AnimationType.VEX_CHARGE_LUNGE;
                case NORMAL -> BattleVisualEvent.AnimationType.MELEE_LUNGE;
            };
        }
    }

    private final class ProjectileAnimation implements BattleAnimation {
        private final PendingCardBatch batch;
        private final CombatantState target;
        private final ItemStack projectileStack;
        private final int prepareTicks;
        private final int flightTicks;
        private final boolean channeling;
        private AbstractArrow arrow;
        private int ticks;
        private boolean effectsApplied;
        private boolean lightningTriggered;

        private ProjectileAnimation(PendingCardBatch batch, CombatantState target, ItemStack projectileStack, int prepareTicks) {
            this.batch = batch;
            this.target = target;
            this.projectileStack = projectileStack.copy();
            this.prepareTicks = Math.max(0, prepareTicks);
            this.flightTicks = projectileFlightTicks(batch.user().entity(), target.entity());
            this.channeling = isChannelingThrowCard(batch.card());
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
            finishPendingFacing(batch);
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
                triggerChannelingLightning(target.entity());
                discardArrow();
                return true;
            }
            return false;
        }

        @Override
        public boolean movesEntity(LivingEntity entity) {
            return false;
        }

        @Override
        public boolean effectsApplied() {
            return effectsApplied;
        }

        @Override
        public void markEffectsApplied() {
            effectsApplied = true;
        }

        private void spawnArrow(LivingEntity actor, LivingEntity targetEntity) {
            Level level = actor.level();
            ItemStack arrowStack = projectileStack.is(Items.TRIDENT) ? new ItemStack(Items.TRIDENT) : projectileStack.is(Items.SPECTRAL_ARROW) ? new ItemStack(Items.SPECTRAL_ARROW) : new ItemStack(Items.ARROW);
            ItemStack weaponStack = batch.stack().isEmpty() ? new ItemStack(Items.BOW) : batch.stack().copy();
            arrow = arrowStack.is(Items.TRIDENT)
                    ? new ThrownTrident(level, actor, arrowStack)
                    : arrowStack.is(Items.SPECTRAL_ARROW)
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

        private void triggerChannelingLightning(LivingEntity targetEntity) {
            if (!channeling || lightningTriggered || targetEntity == null) {
                return;
            }
            lightningTriggered = true;
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(targetEntity.level());
            if (lightning == null) {
                return;
            }
            Vec3 point = targetPoint(targetEntity);
            lightning.moveTo(point.x, point.y, point.z);
            lightning.setVisualOnly(true);
            lightning.setDamage(0.0F);
            targetEntity.level().addFreshEntity(lightning);
        }
    }

    private final class GuardianBeamAnimation implements BattleAnimation {
        private final PendingCardBatch batch;
        private final CombatantState target;
        private final LivingEntity actor;
        private final int durationTicks;
        private int ticks;
        private boolean effectsApplied;

        private GuardianBeamAnimation(PendingCardBatch batch, CombatantState target, int durationTicks) {
            this.batch = batch;
            this.target = target;
            this.actor = batch.user().entity();
            this.durationTicks = Math.max(1, durationTicks);
        }

        @Override
        public PendingCardBatch batch() {
            return batch;
        }

        @Override
        public boolean tick() {
            if (!actor.isAlive() || target.fakeDead()) {
                return true;
            }
            finishPendingFacing(batch);
            ticks++;
            return ticks >= durationTicks;
        }

        @Override
        public boolean movesEntity(LivingEntity entity) {
            return false;
        }

        @Override
        public boolean effectsApplied() {
            return effectsApplied;
        }

        @Override
        public void markEffectsApplied() {
            effectsApplied = true;
        }
    }

    private final class RiptideRushAnimation implements BattleAnimation {
        private final PendingCardBatch batch;
        private final CombatantState target;
        private final LivingEntity actor;
        private final Vec3 start;
        private final Vec3 strike;
        private int ticks;
        private boolean effectsApplied;

        private RiptideRushAnimation(PendingCardBatch batch, CombatantState target) {
            this.batch = batch;
            this.target = target;
            this.actor = batch.user().entity();
            this.start = actor.position();
            this.strike = riptideStrikePosition(actor, target.entity());
        }

        @Override
        public PendingCardBatch batch() {
            return batch;
        }

        @Override
        public boolean tick() {
            if (!actor.isAlive() || target.fakeDead()) {
                return true;
            }
            finishPendingFacing(batch);
            ticks++;
            return ticks >= RIPTIDE_CHARGE_TICKS + RIPTIDE_RUSH_TICKS + RIPTIDE_HIT_PAUSE_TICKS;
        }

        @Override
        public boolean movesEntity(LivingEntity entity) {
            return false;
        }

        @Override
        public boolean readyToApplyEffects() {
            return ticks >= RIPTIDE_CHARGE_TICKS + RIPTIDE_RUSH_TICKS && !effectsApplied;
        }

        @Override
        public boolean effectsApplied() {
            return effectsApplied;
        }

        @Override
        public void markEffectsApplied() {
            effectsApplied = true;
        }

        private Vec3 start() {
            return start;
        }

        private Vec3 strike() {
            return strike;
        }

        private Vec3 riptideStrikePosition(LivingEntity actor, LivingEntity targetEntity) {
            Vec3 targetPos = targetEntity.position();
            Vec3 direction = targetPos.subtract(start);
            Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
            Vec3 normalized = horizontal.lengthSqr() > 0.0001D ? horizontal.normalize() : actor.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
            double stopDistance = Math.max(0.75D, actor.getBbWidth() * 0.5D + targetEntity.getBbWidth() * 0.5D + POUNCE_CONTACT_MARGIN);
            double distance = Math.max(0.0D, Math.min(LUNGE_REACH, horizontal.length() - stopDistance));
            Vec3 rawStrike = start.add(normalized.scale(distance));
            return safeReturnPosition(actor, rawStrike);
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
