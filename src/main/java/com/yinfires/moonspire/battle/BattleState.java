package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.CardTarget;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import com.yinfires.moonspire.developer.DeveloperMonsterDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BattleState {
    private static final int MONSTER_ACTION_DELAY_TICKS = 14;
    private static final int ROUND_END_DELAY_TICKS = 8;
    private static final int KNOCKBACK_RELEASE_TICKS = 24;
    private static final int MIN_KNOCKBACK_RELEASE_TICKS = 4;
    private static final int CARD_EFFECT_START_DELAY_TICKS = 10;
    private static final int REPEATED_EFFECT_VISUAL_INTERVAL_TICKS = 14;

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
    private int openingProtectionTicks;
    private boolean suppressDamageEvent;
    private boolean started;
    private boolean endingAfterAnimations;
    private int syncCooldownTicks;
    private long snapshotSequence;
    private final List<BattleVisualEvent> pendingVisualEvents = new ArrayList<>();
    private final List<PendingCardBatch> pendingCardBatches = new ArrayList<>();
    private final List<PendingCardStep> pendingCardSteps = new ArrayList<>();
    private int pendingCardBatchDelay;
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
            enemyStates.add(state);
            byEntityId.put(enemy.getId(), state);
            locks.put(enemy.getId(), EntityLock.capture(enemy));
            total = total.add(enemy.position());
            totalCount++;
        }
        cameraCenter = totalCount <= 0 ? leader.position() : total.scale(1.0D / totalCount);
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
        }
        return endingAfterAnimations && allFakeDeathAnimationsDone();
    }

    public void finish() {
        for (CombatantState state : allStates()) {
            EntityLock lock = locks.get(state.entity().getId());
            if (lock != null) {
                lock.restoreBeforeDeath(state.entity());
            }
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
                return true;
            }
            currentCandidateIds = currentPendingHandCandidateIds();
        }
        if (currentCandidateIds.size() <= pendingHandSelection.requiredCount()) {
            List<CardInstance> selectedCards = pendingHandSelection.user().deck().removeHandByIds(currentCandidateIds);
            completePendingHandSelection(selectedCards);
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
        if (alivePlayers().stream().allMatch(CombatantState::endedTurn)) {
            for (CombatantState playerState : alivePlayers()) {
                applyOwnTurnEndEffects(playerState);
                playerState.deck().discardHand();
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
        CombatantState local = byPlayerId.get(viewer.getUUID());
        if (local == null) {
            local = playerStates.isEmpty() ? null : playerStates.getFirst();
        }
        CombatantState firstEnemy = firstAliveEnemy();
        if (firstEnemy == null && !enemyStates.isEmpty()) {
            firstEnemy = enemyStates.getFirst();
        }
        List<BattleVisualEvent> visualEvents = List.copyOf(pendingVisualEvents);
        return new BattleSnapshot(
                id,
                snapshotSequence,
                true,
                phase,
                hasPendingCardBatches(),
                round,
                selectedTargets.getOrDefault(viewer.getUUID(), -1),
                local == null ? -1 : local.entity().getId(),
                local != null && local.endedTurn(),
                playerSnapshots(),
                enemySnapshots(),
                local == null ? 0 : local.deck().drawPile().size(),
                local == null ? 0 : local.deck().discardPile().size(),
                local == null ? 0 : local.deck().exhaustPile().size(),
                local == null ? List.of() : List.copyOf(local.deck().hand()),
                local == null ? List.of() : List.copyOf(local.deck().drawPile()),
                local == null ? List.of() : List.copyOf(local.deck().discardPile()),
                local == null ? List.of() : List.copyOf(local.deck().exhaustPile()),
                pendingHandSelectionSnapshotFor(local),
                firstEnemy == null ? List.of() : List.copyOf(firstEnemy.deck().hand()),
                firstEnemy == null ? null : monsterIntent(firstEnemy),
                firstEnemy == null ? List.of() : monsterIntentCards(firstEnemy),
                enemyIntentSnapshots(),
                entityHandSnapshots(),
                visualEvents);
    }

    public long nextSnapshotSequence() {
        return ++snapshotSequence;
    }

    public boolean shouldSyncNow() {
        if (pendingHandSelection != null) {
            if (!pendingVisualEvents.isEmpty()) {
                syncCooldownTicks = 0;
                return true;
            }
            syncCooldownTicks = 0;
            return false;
        }
        if (!pendingCardBatches.isEmpty() || !pendingCardSteps.isEmpty() || pendingUsedCard != null) {
            if (!pendingVisualEvents.isEmpty()) {
                syncCooldownTicks = 0;
                return true;
            }
            syncCooldownTicks = 0;
            return false;
        }
        if (!pendingVisualEvents.isEmpty() || endingAfterAnimations) {
            syncCooldownTicks = 0;
            return true;
        }
        syncCooldownTicks++;
        if (syncCooldownTicks >= 5) {
            syncCooldownTicks = 0;
            return true;
        }
        return false;
    }

    public void clearPendingVisualEvents() {
        pendingVisualEvents.clear();
    }

    private void beginBattle() {
        freezeAi();
        resetParticipantsToStart();
        openingProtectionTicks = 20;
        for (CombatantState state : allStates()) {
            state.entity().invulnerableTime = Math.max(state.entity().invulnerableTime, openingProtectionTicks);
        }
        beginRound();
        beginPlayerTurn();
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
        monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
        currentEnemyIndex = 0;
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
        selectedTargets.clear();
    }

    private void beginRoundEnd() {
        phase = BattlePhase.ROUND_END;
        phaseTicks = 0;
        for (CombatantState state : aliveEnemies()) {
            applyOwnTurnEndEffects(state);
            state.deck().discardHand();
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
        while (currentEnemyIndex < enemyStates.size() && enemyStates.get(currentEnemyIndex).fakeDead()) {
            currentEnemyIndex++;
        }
        if (currentEnemyIndex >= enemyStates.size()) {
            beginRoundEnd();
            return;
        }
        CombatantState enemy = enemyStates.get(currentEnemyIndex);
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
        if (card.hasAttack()) {
            PendingEffect bleed = bleedEffectForAttack(user);
            if (bleed != null) {
                pendingCardSteps.add(new PendingBatchStep(new PendingCardBatch(user, card.sourceStack(), null, List.of(bleed))));
            }
        }
        List<CardEffect> currentEffects = new ArrayList<>();
        for (CardEffect effect : card.effects()) {
            if (effect.kind() == CardEffectKind.EXHAUST) {
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
        int maxCount = 0;
        for (CardEffect effect : effects) {
            if (effect.amount() > 0 && effect.kind() != CardEffectKind.EXHAUST && !effect.kind().isHandSelection()) {
                maxCount = Math.max(maxCount, effect.count());
            }
        }
        for (int repeat = 0; repeat < maxCount; repeat++) {
            List<PendingEffect> batchEffects = new ArrayList<>();
            for (CardEffect effect : effects) {
                if (effect.kind() == CardEffectKind.EXHAUST || effect.kind().isHandSelection() || effect.amount() <= 0 || effect.count() <= repeat) {
                    continue;
                }
                for (CombatantState effectTarget : targetsForEffect(effect, user, selectedTarget)) {
                    batchEffects.add(new PendingEffect(effect.kind(), effect.amount(), effectTarget));
                }
            }
            if (!batchEffects.isEmpty()) {
                pendingCardSteps.add(new PendingBatchStep(new PendingCardBatch(user, card.sourceStack(), card, batchEffects)));
            }
        }
    }

    private List<CombatantState> targetsForEffect(CardEffect effect, CombatantState user, CombatantState selectedTarget) {
        if (effect.kind() == CardEffectKind.EXHAUST) {
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
        return List.of(candidates.get(leader.getRandom().nextInt(candidates.size())));
    }

    private boolean validExplicitTarget(CombatantState user, int targetEntityId, CardInstance card) {
        CombatantState target = byEntityId.get(targetEntityId);
        if (target == null || target.fakeDead()) {
            return false;
        }
        boolean needsEnemy = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind() != CardEffectKind.EXHAUST && effect.target() == CardTarget.SINGLE_ENEMY);
        boolean needsAlly = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind() != CardEffectKind.EXHAUST && effect.target() == CardTarget.SINGLE_ALLY);
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

    private PendingEffect bleedEffectForAttack(CombatantState user) {
        int bleed = user.effectAmount(BattleEffectType.BLEED);
        if (bleed <= 0) {
            return null;
        }
        return new PendingEffect(CardEffectKind.DAMAGE, bleed, user, true);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, CardInstance playedCard, BattleDamageResult result, int delayTicks) {
        emitVisual(attacker, target, stack, playedCard, result, 0, 0, delayTicks);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, CardInstance playedCard, BattleDamageResult result, int gainedBlock, int delayTicks) {
        emitVisual(attacker, target, stack, playedCard, result, gainedBlock, 0, delayTicks);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, CardInstance playedCard, BattleDamageResult result, int gainedBlock, int healedHealth, int delayTicks) {
        pendingVisualEvents.add(new BattleVisualEvent(
                attacker.getId(),
                target.getId(),
                stack.copy(),
                playedCard == null ? null : playedCard.copyForBattle(),
                result.blockedDamage(),
                result.healthDamage(),
                Math.max(0, gainedBlock),
                Math.max(0, healedHealth),
                Math.max(0, delayTicks),
                result.blockedDamage() > 0,
                result.healthDamage() > 0,
                gainedBlock > 0));
    }

    private boolean hasPendingCardBatches() {
        return !pendingCardBatches.isEmpty() || !pendingCardSteps.isEmpty() || pendingHandSelection != null || pendingUsedCard != null;
    }

    private void tickPendingCardBatches() {
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
        applyPendingCardBatch(batch);
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

    private void advancePendingCardSteps() {
        while (pendingHandSelection == null && pendingCardBatches.isEmpty() && !pendingCardSteps.isEmpty()) {
            PendingCardStep step = pendingCardSteps.remove(0);
            if (step instanceof PendingBatchStep batchStep) {
                pendingCardBatches.add(batchStep.batch());
                pendingCardBatchDelay = Math.max(pendingCardBatchDelay, CARD_EFFECT_START_DELAY_TICKS);
                return;
            }
            if (step instanceof PendingHandSelectionStep selectionStep) {
                beginPendingHandSelection(selectionStep);
            }
        }
    }

    private void beginPendingHandSelection(PendingHandSelectionStep step) {
        int available = step.target().deck().hand().size();
        int required = Math.max(0, step.requiredCount());
        if (required <= 0 || available <= 0 || step.target().fakeDead()) {
            return;
        }
        if (!(step.target().entity() instanceof ServerPlayer) || available <= required) {
            completeHandSelection(step.target(), step.action(), step.target().deck().removeFirstHandCards(Math.min(required, available)));
            return;
        }
        List<UUID> candidateIds = step.target().deck().hand().stream().map(CardInstance::id).toList();
        pendingHandSelection = new PendingHandSelection(step.target(), step.action(), required, candidateIds);
    }

    private void completePendingHandSelection(List<CardInstance> selectedCards) {
        PendingHandSelection selection = pendingHandSelection;
        pendingHandSelection = null;
        if (selection != null) {
            completeHandSelection(selection.user(), selection.action(), selectedCards);
        }
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

    private void completeHandSelection(CombatantState user, PendingHandSelectionSnapshot.Action action, List<CardInstance> selectedCards) {
        if (action == PendingHandSelectionSnapshot.Action.EXHAUST) {
            user.deck().exhaustAll(selectedCards);
        } else if (action == PendingHandSelectionSnapshot.Action.DISCARD) {
            user.deck().discardAll(selectedCards);
        }
    }

    private PendingHandSelectionSnapshot pendingHandSelectionSnapshotFor(CombatantState local) {
        if (pendingHandSelection == null || pendingHandSelection.user() != local) {
            return PendingHandSelectionSnapshot.NONE;
        }
        return new PendingHandSelectionSnapshot(pendingHandSelection.action(), pendingHandSelection.requiredCount(), pendingHandSelection.user().entity().getId(), pendingHandSelection.candidateIds());
    }

    private void applyPendingCardBatch(PendingCardBatch batch) {
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
                        : effect.target().applyCardDamage(effect.amount(), batch.user(), killCredit);
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
            }
        }
        for (CombatantState target : knockbackTargets) {
            applyBattleKnockback(batch.user().entity(), target.entity(), damageResults.getOrDefault(target, new BattleDamageResult(0, 0, 0)));
        }
        boolean emittedCardVisual = false;
        for (Map.Entry<CombatantState, BattleDamageResult> entry : damageResults.entrySet()) {
            CombatantState target = entry.getKey();
            emitVisual(batch.user().entity(), target.entity(), batch.stack(), emittedCardVisual ? null : batch.card(), entry.getValue(), blockGains.getOrDefault(target, 0), heals.getOrDefault(target, 0), 0);
            emittedCardVisual = true;
        }
        for (CombatantState target : effectOnlyTargets) {
            if (damageResults.containsKey(target)) {
                continue;
            }
            emitVisual(batch.user().entity(), target.entity(), batch.stack(), emittedCardVisual ? null : batch.card(), new BattleDamageResult(0, 0, 0), blockGains.getOrDefault(target, 0), heals.getOrDefault(target, 0), 0);
            emittedCardVisual = true;
        }
        if (!emittedCardVisual && batch.card() != null) {
            emitVisual(batch.user().entity(), batch.user().entity(), batch.stack(), batch.card(), new BattleDamageResult(0, 0, 0), 0);
        }
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

    private List<BattleEnemyIntentSnapshot> enemyIntentSnapshots() {
        List<BattleEnemyIntentSnapshot> snapshots = new ArrayList<>();
        for (CombatantState enemy : enemyStates) {
            snapshots.add(new BattleEnemyIntentSnapshot(enemy.entity().getId(), monsterIntentCards(enemy)));
        }
        return snapshots;
    }

    private List<BattleEntityCardsSnapshot> entityHandSnapshots() {
        List<BattleEntityCardsSnapshot> snapshots = new ArrayList<>();
        for (CombatantState player : playerStates) {
            snapshots.add(new BattleEntityCardsSnapshot(player.entity().getId(), List.copyOf(player.deck().hand())));
        }
        for (CombatantState enemy : enemyStates) {
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

    private record PendingEffect(CardEffectKind kind, int amount, CombatantState target, boolean effectDamage) {
        private PendingEffect(CardEffectKind kind, int amount, CombatantState target) {
            this(kind, amount, target, false);
        }
    }

    private record PendingCardBatch(CombatantState user, ItemStack stack, CardInstance card, List<PendingEffect> effects) {
        private PendingCardBatch {
            effects = List.copyOf(effects);
        }
    }

    private sealed interface PendingCardStep permits PendingBatchStep, PendingHandSelectionStep {
    }

    private record PendingBatchStep(PendingCardBatch batch) implements PendingCardStep {
    }

    private record PendingHandSelectionStep(PendingHandSelectionSnapshot.Action action, int requiredCount, CombatantState target) implements PendingCardStep {
    }

    private record PendingHandSelection(CombatantState user, PendingHandSelectionSnapshot.Action action, int requiredCount, List<UUID> candidateIds) {
        private PendingHandSelection {
            candidateIds = List.copyOf(candidateIds);
        }
    }
}
