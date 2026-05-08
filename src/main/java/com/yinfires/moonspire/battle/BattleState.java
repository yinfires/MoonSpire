package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.CardTarget;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import com.yinfires.moonspire.developer.DeveloperMonsterDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
    private final ServerPlayer player;
    private final LivingEntity monster;
    private final CombatantState playerState;
    private final CombatantState monsterState;
    private final int playerHotbarSlotBeforeBattle;
    private final Vec3 playerStartPos;
    private final Vec3 monsterStartPos;
    private final float playerStartYRot;
    private final float playerStartXRot;
    private final float monsterStartYRot;
    private final float monsterStartXRot;
    private final float playerHealthAtBattleStart;
    private final float monsterHealthAtBattleStart;
    private final Vec3 cameraCenter;
    private Vec3 playerLockedPos;
    private Vec3 monsterLockedPos;
    private int playerKnockbackTicks;
    private int monsterKnockbackTicks;
    private BattlePhase phase = BattlePhase.PLAYER_TURN;
    private int round = 1;
    private int phaseTicks;
    private int selectedTargetId = -1;
    private int monsterActionDelay;
    private int openingProtectionTicks;
    private boolean monsterNoAiBeforeBattle;
    private boolean suppressDamageEvent;
    private boolean started;
    private final List<BattleVisualEvent> pendingVisualEvents = new ArrayList<>();
    private final List<PendingCardBatch> pendingCardBatches = new ArrayList<>();
    private final List<PendingCardStep> pendingCardSteps = new ArrayList<>();
    private int pendingCardBatchDelay;
    private CombatantState pendingUsedCardOwner;
    private CardInstance pendingUsedCard;
    private PendingHandSelection pendingHandSelection;

    public BattleState(ServerPlayer player, LivingEntity monster, List<CardInstance> playerCards, List<CardInstance> monsterCards) {
        RandomSource random = player.getRandom();
        DeveloperMonsterDefinition monsterOverride = DeveloperDataManager.monsterOverride(monster).orElse(null);
        this.player = player;
        this.monster = monster;
        this.playerState = new CombatantState(
                player,
                new BattleDeck(playerCards, random),
                CardBalance.fixedEnergy(),
                Math.max(20.0F, player.getMaxHealth()),
                CardBalance.PLAYER_BASE_SPEED);
        this.monsterState = new CombatantState(
                monster,
                new BattleDeck(monsterCards, random),
                monsterOverride != null && monsterOverride.hasEnergyOverride() ? monsterOverride.energy() : CardBalance.fixedEnergy(),
                monsterOverride != null && monsterOverride.hasHealthOverride() ? monsterOverride.maxHealth() : Math.max(1.0F, monster.getMaxHealth()),
                monsterOverride != null && monsterOverride.hasSpeedOverride() ? monsterOverride.speed() : nonPlayerBaseSpeed(monster));
        if (monster instanceof Mob mob) {
            monsterNoAiBeforeBattle = mob.isNoAi();
        }
        playerHotbarSlotBeforeBattle = player.getInventory().selected;
        playerStartPos = findStandingStartPos(player);
        monsterStartPos = monster.position();
        playerStartYRot = player.getYRot();
        playerStartXRot = player.getXRot();
        monsterStartYRot = monster.getYRot();
        monsterStartXRot = monster.getXRot();
        playerHealthAtBattleStart = player.getHealth();
        monsterHealthAtBattleStart = monster.getHealth();
        cameraCenter = playerStartPos.add(monsterStartPos).scale(0.5D);
        playerLockedPos = playerStartPos;
        monsterLockedPos = monsterStartPos;
    }

    public UUID id() {
        return id;
    }

    public ServerPlayer player() {
        return player;
    }

    public LivingEntity monster() {
        return monster;
    }

    public CombatantState playerState() {
        return playerState;
    }

    public CombatantState monsterState() {
        return monsterState;
    }

    public BattlePhase phase() {
        return phase;
    }

    public int round() {
        return round;
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
        return entity == player || entity == monster;
    }

    public CombatantState stateFor(LivingEntity entity) {
        if (entity == player) {
            return playerState;
        }
        if (entity == monster) {
            return monsterState;
        }
        return null;
    }

    public boolean tick() {
        if (!player.isAlive() || player.hasDisconnected() || !monster.isAlive() || monster.isRemoved()) {
            return true;
        }
        lockBattleEntities();
        pacifyOutsideHostiles();
        protectOpeningHealth();
        tickPendingCardBatches();
        phaseTicks++;
        if (hasPendingCardBatches()) {
            return playerState.isDefeated() || monsterState.isDefeated();
        }
        if (phase == BattlePhase.MONSTER_TURN) {
            tickMonsterTurn();
        } else if (phase == BattlePhase.ROUND_END && phaseTicks >= ROUND_END_DELAY_TICKS) {
            beginRound();
            beginPlayerTurn();
        }
        return playerState.isDefeated() || monsterState.isDefeated();
    }

    public void finish() {
        if (monster instanceof Mob mob) {
            mob.setNoAi(monsterNoAiBeforeBattle);
        }
        player.getInventory().selected = playerHotbarSlotBeforeBattle;
        player.setDeltaMovement(Vec3.ZERO);
        monster.setDeltaMovement(Vec3.ZERO);
        player.invulnerableTime = 0;
        monster.invulnerableTime = 0;
        restoreParticipantsToStart();
        if (playerState.isDefeated() && player.isAlive()) {
            applyEntityDamage(player, player.getMaxHealth() * 2.0F);
        }
        if (monsterState.isDefeated() && monster.isAlive()) {
            applyEntityDamage(monster, monster.getMaxHealth() * 2.0F);
        }
    }

    public void selectTarget(int targetEntityId) {
        if (targetEntityId == monster.getId()) {
            selectedTargetId = selectedTargetId == targetEntityId ? -1 : targetEntityId;
        } else {
            selectedTargetId = -1;
        }
    }

    public boolean usePlayerCard(int handIndex, int targetEntityId) {
        if (phase != BattlePhase.PLAYER_TURN || hasPendingCardBatches()) {
            return false;
        }
        CardInstance card = playerState.deck().peekHand(handIndex);
        if (card == null || !canUseCard(playerState, card)) {
            return false;
        }
        if (card.requiresExplicitTarget() && !validExplicitTarget(playerState, targetEntityId, card)) {
            return false;
        }
        if (!playerState.spendEnergy(card.cost())) {
            return false;
        }
        CardInstance used = playerState.deck().useHand(handIndex);
        if (used == null) {
            return false;
        }
        CombatantState selectedTarget = targetEntityId == player.getId() ? playerState : monsterState;
        queueCard(playerState, selectedTarget, used);
        return true;
    }

    public boolean confirmHandSelection(List<UUID> cardIds) {
        if (pendingHandSelection == null || pendingHandSelection.user() != playerState) {
            return false;
        }
        List<UUID> selectedIds = List.copyOf(cardIds == null ? List.of() : cardIds);
        if (selectedIds.size() != pendingHandSelection.requiredCount()) {
            return false;
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(selectedIds);
        if (uniqueIds.size() != selectedIds.size() || !pendingHandSelection.candidateIds().containsAll(selectedIds)) {
            return false;
        }
        List<CardInstance> selectedCards = pendingHandSelection.user().deck().removeHandByIds(selectedIds);
        if (selectedCards.size() != selectedIds.size()) {
            return false;
        }
        completePendingHandSelection(selectedCards);
        return true;
    }

    public void endPlayerTurn() {
        if (phase != BattlePhase.PLAYER_TURN || hasPendingCardBatches()) {
            return;
        }
        playerState.decayEndOfTurnEffects();
        playerState.deck().discardHand();
        beginMonsterTurn();
    }

    public void handleAttack(LivingEntity attacker, LivingEntity target) {
        // Vanilla combat is canceled while the card battle owns combat resolution.
    }

    public void pacifyOutsideAttacker(LivingEntity attacker) {
        if (attacker instanceof Mob mob && mob != monster) {
            pacifyMobAgainstBattle(mob);
        }
    }

    public BattleSnapshot snapshot() {
        List<BattleVisualEvent> visualEvents = List.copyOf(pendingVisualEvents);
        pendingVisualEvents.clear();
        return new BattleSnapshot(
                true,
                phase,
                hasPendingCardBatches(),
                round,
                selectedTargetId,
                playerState.snapshot(),
                monsterState.snapshot(),
                playerState.deck().drawPile().size(),
                playerState.deck().discardPile().size(),
                playerState.deck().exhaustPile().size(),
                List.copyOf(playerState.deck().hand()),
                List.copyOf(playerState.deck().drawPile()),
                List.copyOf(playerState.deck().discardPile()),
                List.copyOf(playerState.deck().exhaustPile()),
                pendingHandSelectionSnapshot(),
                List.copyOf(monsterState.deck().hand()),
                monsterIntent(),
                monsterIntentCards(),
                visualEvents);
    }

    private void beginBattle() {
        freezeAi();
        resetParticipantsToStart();
        faceParticipants();
        openingProtectionTicks = 20;
        player.invulnerableTime = Math.max(player.invulnerableTime, openingProtectionTicks);
        monster.invulnerableTime = Math.max(monster.invulnerableTime, openingProtectionTicks);
        beginRound();
        beginPlayerTurn();
    }

    private void beginRound() {
        playerState.rollRoundSpeed(player.getRandom());
        monsterState.rollRoundSpeed(player.getRandom());
        resetParticipantsToStart();
    }

    private void beginPlayerTurn() {
        playerState.clearDefense();
        phase = BattlePhase.PLAYER_TURN;
        phaseTicks = 0;
        playerState.resetEnergy();
        playerState.deck().startTurn(player.getRandom());
        monsterState.deck().startTurn(player.getRandom());
        selectedTargetId = -1;
    }

    private void beginMonsterTurn() {
        monsterState.clearDefense();
        phase = BattlePhase.MONSTER_TURN;
        phaseTicks = 0;
        monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
        monsterState.resetEnergy();
        selectedTargetId = -1;
    }

    private void beginRoundEnd() {
        phase = BattlePhase.ROUND_END;
        phaseTicks = 0;
        monsterState.decayEndOfTurnEffects();
        monsterState.deck().discardHand();
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
        int index = chooseMonsterCard();
        if (index < 0) {
            beginRoundEnd();
            return;
        }
        CardInstance card = monsterState.deck().peekHand(index);
        if (card == null || !monsterState.spendEnergy(card.cost())) {
            beginRoundEnd();
            return;
        }
        CardInstance used = monsterState.deck().useHand(index);
        if (used == null) {
            beginRoundEnd();
            return;
        }
        queueCard(monsterState, playerState, used);
        monsterActionDelay = MONSTER_ACTION_DELAY_TICKS;
    }

    private int chooseMonsterCard() {
        return chooseMonsterCard(
                monsterState.deck().hand(),
                monsterState.energyLeft(),
                monsterState.defense(),
                monsterState.battleHealth(),
                monsterState.maxBattleHealth());
    }

    private CardInstance monsterIntent() {
        List<CardInstance> plannedCards = plannedMonsterCards();
        return plannedCards.isEmpty() ? null : plannedCards.getFirst();
    }

    private List<CardInstance> monsterIntentCards() {
        if (phase != BattlePhase.MONSTER_TURN && phase != BattlePhase.PLAYER_TURN) {
            return List.of();
        }
        return plannedMonsterCards();
    }

    private List<CardInstance> plannedMonsterCards() {
        if (phase != BattlePhase.MONSTER_TURN && phase != BattlePhase.PLAYER_TURN) {
            return List.of();
        }
        List<CardInstance> virtualHand = new ArrayList<>(monsterState.deck().hand());
        int energyLeft = phase == BattlePhase.PLAYER_TURN ? monsterState.maxEnergy() : monsterState.energyLeft();
        int defense = phase == BattlePhase.PLAYER_TURN ? 0 : monsterState.defense();
        float health = monsterState.battleHealth();
        List<CardInstance> planned = new ArrayList<>();
        while (!virtualHand.isEmpty() && health > 0.0F) {
            int index = chooseMonsterCard(virtualHand, energyLeft, defense, health, monsterState.maxBattleHealth());
            if (index < 0) {
                break;
            }
            CardInstance card = virtualHand.remove(index);
            planned.add(card);
            energyLeft -= Math.max(0, card.cost());
            defense += Math.max(0, card.selfEffectAmount(CardEffectKind.BLOCK));
            if (card.hasAttack()) {
                BattleDamageResult bleed = simulateEffectDamage(monsterState.effectAmount(BattleEffectType.BLEED), defense);
                health = Math.max(0.0F, health - bleed.healthDamage());
                defense = Math.max(0, defense - bleed.blockedDamage());
            }
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

    private boolean canUseCard(CombatantState user, CardInstance card) {
        return card.cost() <= user.energyLeft() && card.hasAnyEffect();
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

    private void queueCard(CombatantState user, CombatantState selectedTarget, CardInstance card) {
        CombatantState opponent = opponentOf(user);
        faceTarget(user.entity(), selectedTarget.entity());
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
        // Gameplay contract: card effects resolve in their stored top-to-bottom order.
        // Hand-selection effects split the sequence and must not be moved across neighbors.
        List<CardEffect> currentEffects = new ArrayList<>();
        for (CardEffect effect : card.effects()) {
            if (effect.kind() == CardEffectKind.EXHAUST) {
                continue;
            }
            if (effect.kind().isHandSelection()) {
                if (!currentEffects.isEmpty()) {
                    addEffectSteps(user, opponent, selectedTarget, card, currentEffects);
                    currentEffects = new ArrayList<>();
                }
                if (effect.amount() > 0) {
                    for (CombatantState effectTarget : targetsForEffect(effect, user, opponent, selectedTarget)) {
                        pendingCardSteps.add(new PendingHandSelectionStep(handSelectionAction(effect.kind()), effect.amount(), effectTarget));
                    }
                }
                continue;
            }
            currentEffects.add(effect);
        }
        if (!currentEffects.isEmpty()) {
            addEffectSteps(user, opponent, selectedTarget, card, currentEffects);
        }
        advancePendingCardSteps();
        if (!hasPendingCardBatches()) {
            emitVisual(user.entity(), selectedTarget.entity(), card.sourceStack(), card, new BattleDamageResult(0, 0, 0), 0);
            finishPendingUsedCard();
        }
    }

    private void addEffectSteps(CombatantState user, CombatantState opponent, CombatantState selectedTarget, CardInstance card, List<CardEffect> effects) {
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
                for (CombatantState effectTarget : targetsForEffect(effect, user, opponent, selectedTarget)) {
                    batchEffects.add(new PendingEffect(effect.kind(), effect.amount(), effectTarget));
                }
            }
            if (!batchEffects.isEmpty()) {
                pendingCardSteps.add(new PendingBatchStep(new PendingCardBatch(user, card.sourceStack(), card, batchEffects)));
            }
        }
    }

    private CombatantState opponentOf(CombatantState user) {
        return user == playerState ? monsterState : playerState;
    }

    private List<CombatantState> targetsForEffect(CardEffect effect, CombatantState user, CombatantState opponent, CombatantState selectedTarget) {
        if (effect.kind() == CardEffectKind.EXHAUST) {
            return List.of();
        }
        return switch (effect.target()) {
            case SELF -> List.of(user);
            case SINGLE_ENEMY -> List.of(selectedTarget == opponent ? selectedTarget : opponent);
            case SINGLE_ALLY -> List.of();
            case ALL_ENEMIES -> List.of(opponent);
            case RANDOM_ENEMY -> randomTarget(List.of(opponent));
            case ALL_ALLIES -> List.of(user);
            case ALL_UNITS -> List.of(user, opponent);
            case ALL_OTHER_UNITS -> List.of(opponent);
            case ALL_OTHER_ALLIES -> List.of();
            case RANDOM_ALLY -> randomTarget(List.of());
        };
    }

    private List<CombatantState> randomTarget(List<CombatantState> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        return List.of(candidates.get(player.getRandom().nextInt(candidates.size())));
    }

    private boolean validExplicitTarget(CombatantState user, int targetEntityId, CardInstance card) {
        boolean needsEnemy = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind() != CardEffectKind.EXHAUST && effect.target() == CardTarget.SINGLE_ENEMY);
        boolean needsAlly = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind() != CardEffectKind.EXHAUST && effect.target() == CardTarget.SINGLE_ALLY);
        if (needsEnemy && targetEntityId != opponentOf(user).entity().getId()) {
            return false;
        }
        if (needsAlly) {
            return false;
        }
        return !needsEnemy || !needsAlly;
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
        double strength = target == player ? 0.42D : 0.28D;
        target.knockback(strength, dx, dz);
        if (target == player) {
            playerKnockbackTicks = KNOCKBACK_RELEASE_TICKS;
        } else if (target == monster) {
            monsterKnockbackTicks = KNOCKBACK_RELEASE_TICKS;
        }
    }

    private PendingEffect bleedEffectForAttack(CombatantState user) {
        int bleed = user.effectAmount(BattleEffectType.BLEED);
        if (bleed <= 0) {
            return null;
        }
        return new PendingEffect(CardEffectKind.DAMAGE, bleed, user, true);
    }

    private BattleDamageResult simulateEffectDamage(int amount, int defense) {
        int damage = Math.max(0, amount);
        int blocked = Math.min(Math.max(0, defense), damage);
        int finalHealthDamage = damage - blocked;
        return new BattleDamageResult(blocked, damage, finalHealthDamage);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, CardInstance playedCard, BattleDamageResult result, int delayTicks) {
        emitVisual(attacker, target, stack, playedCard, result, 0, delayTicks);
    }

    private void emitVisual(LivingEntity attacker, LivingEntity target, ItemStack stack, CardInstance playedCard, BattleDamageResult result, int gainedBlock, int delayTicks) {
        pendingVisualEvents.add(new BattleVisualEvent(
                attacker.getId(),
                target.getId(),
                stack.copy(),
                playedCard == null ? null : playedCard.copyForBattle(),
                result.blockedDamage(),
                result.healthDamage(),
                Math.max(0, gainedBlock),
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
        if (required <= 0 || available <= 0) {
            return;
        }
        if (step.target() != playerState || available <= required) {
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

    private void completeHandSelection(CombatantState user, PendingHandSelectionSnapshot.Action action, List<CardInstance> selectedCards) {
        if (action == PendingHandSelectionSnapshot.Action.EXHAUST) {
            user.deck().exhaustAll(selectedCards);
        } else if (action == PendingHandSelectionSnapshot.Action.DISCARD) {
            user.deck().discardAll(selectedCards);
        }
    }

    private PendingHandSelectionSnapshot pendingHandSelectionSnapshot() {
        if (pendingHandSelection == null) {
            return PendingHandSelectionSnapshot.NONE;
        }
        return new PendingHandSelectionSnapshot(pendingHandSelection.action(), pendingHandSelection.requiredCount(), pendingHandSelection.user().entity().getId(), pendingHandSelection.candidateIds());
    }

    private PendingHandSelectionSnapshot.Action handSelectionAction(CardEffectKind kind) {
        return kind == CardEffectKind.EXHAUST_HAND ? PendingHandSelectionSnapshot.Action.EXHAUST : PendingHandSelectionSnapshot.Action.DISCARD;
    }

    private void applyPendingCardBatch(PendingCardBatch batch) {
        Map<CombatantState, BattleDamageResult> damageResults = new LinkedHashMap<>();
        Map<CombatantState, Integer> blockGains = new LinkedHashMap<>();
        Set<CombatantState> knockbackTargets = new LinkedHashSet<>();
        Set<CombatantState> effectOnlyTargets = new LinkedHashSet<>();
        for (PendingEffect effect : batch.effects()) {
            if (effect.kind() == CardEffectKind.DAMAGE) {
                BattleDamageResult result = effect.effectDamage()
                        ? effect.target().applyEffectDamage(effect.amount())
                        : effect.target().applyCardDamage(effect.amount(), batch.user());
                damageResults.merge(effect.target(), result, BattleState::mergeDamageResult);
                if (!effect.effectDamage() && result.healthDamage() > 0) {
                    knockbackTargets.add(effect.target());
                }
            } else if (effect.kind() == CardEffectKind.BLOCK) {
                effect.target().addDefense(effect.amount());
                blockGains.merge(effect.target(), Math.max(0, effect.amount()), Integer::sum);
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.BLEED) {
                effect.target().addEffect(BattleEffectType.BLEED, effect.amount());
                effectOnlyTargets.add(effect.target());
            } else if (effect.kind() == CardEffectKind.GUARD) {
                effect.target().addEffect(BattleEffectType.GUARD, effect.amount());
                effectOnlyTargets.add(effect.target());
            }
        }
        for (CombatantState target : knockbackTargets) {
            applyBattleKnockback(batch.user().entity(), target.entity(), damageResults.getOrDefault(target, new BattleDamageResult(0, 0, 0)));
        }
        boolean emittedCardVisual = false;
        for (Map.Entry<CombatantState, BattleDamageResult> entry : damageResults.entrySet()) {
            CombatantState target = entry.getKey();
            emitVisual(batch.user().entity(), target.entity(), batch.stack(), emittedCardVisual ? null : batch.card(), entry.getValue(), blockGains.getOrDefault(target, 0), 0);
            emittedCardVisual = true;
        }
        for (CombatantState target : effectOnlyTargets) {
            if (damageResults.containsKey(target)) {
                continue;
            }
            emitVisual(batch.user().entity(), target.entity(), batch.stack(), emittedCardVisual ? null : batch.card(), new BattleDamageResult(0, 0, 0), blockGains.getOrDefault(target, 0), 0);
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

    private void faceTarget(LivingEntity actor, LivingEntity target) {
        double dx = target.getX() - actor.getX();
        double dz = target.getZ() - actor.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 57.2957763671875D) - 90.0F;
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
    }

    private void lockBattleEntities() {
        lockPlayerHotbarSlot();
        playerLockedPos = freezeEntity(player, playerLockedPos, playerKnockbackTicks);
        monsterLockedPos = freezeEntity(monster, monsterLockedPos, monsterKnockbackTicks);
        playerKnockbackTicks = nextKnockbackTicks(player, playerKnockbackTicks);
        monsterKnockbackTicks = nextKnockbackTicks(monster, monsterKnockbackTicks);
        freezeAi();
    }

    private void freezeAi() {
        if (monster instanceof Mob mob) {
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

    private Vec3 freezeEntity(LivingEntity entity, Vec3 lockedPos, int knockbackTicks) {
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

    private Vec3 findStandingStartPos(LivingEntity entity) {
        Vec3 current = entity.position();
        if (entity.onGround()) {
            return current;
        }
        AABB box = entity.getBoundingBox();
        AABB searchBox = new AABB(box.minX, entity.level().getMinBuildHeight(), box.minZ, box.maxX, box.maxY, box.maxZ);
        double bestY = Double.NEGATIVE_INFINITY;
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, searchBox)) {
            for (AABB collisionBox : shape.toAabbs()) {
                double candidateY = collisionBox.maxY;
                if (candidateY <= current.y + 1.0E-6D
                        && candidateY > bestY
                        && canStandAt(entity, current.x, candidateY, current.z)) {
                    bestY = candidateY;
                }
            }
        }
        return bestY == Double.NEGATIVE_INFINITY ? current : new Vec3(current.x, bestY, current.z);
    }

    private boolean canStandAt(LivingEntity entity, double x, double y, double z) {
        AABB movedBox = entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
        return entity.level().noCollision(entity, movedBox);
    }

    private void pacifyOutsideHostiles() {
        AABB area = player.getBoundingBox().minmax(monster.getBoundingBox()).inflate(32.0D);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area)) {
            if (mob == monster || !mob.isAlive()) {
                continue;
            }
            pacifyMobAgainstBattle(mob);
        }
    }

    private void pacifyMobAgainstBattle(Mob mob) {
        boolean pacified = false;
        LivingEntity target = mob.getTarget();
        if (target == player || target == monster) {
            mob.setTarget(null);
            pacified = true;
        }
        if (mob.getLastHurtMob() == player || mob.getLastHurtMob() == monster) {
            mob.setLastHurtMob(null);
            pacified = true;
        }
        if (mob.getLastHurtByMob() == player || mob.getLastHurtByMob() == monster) {
            mob.setLastHurtByMob(null);
            pacified = true;
        }
        UUID angerTarget = mob instanceof NeutralMob neutralMob ? neutralMob.getPersistentAngerTarget() : null;
        if (mob instanceof NeutralMob neutralMob && (player.getUUID().equals(angerTarget) || monster.getUUID().equals(angerTarget))) {
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

    private void lockPlayerHotbarSlot() {
        player.getInventory().selected = playerHotbarSlotBeforeBattle;
    }

    private void resetParticipantsToStart() {
        player.teleportTo(playerStartPos.x, playerStartPos.y, playerStartPos.z);
        monster.teleportTo(monsterStartPos.x, monsterStartPos.y, monsterStartPos.z);
        playerLockedPos = playerStartPos;
        monsterLockedPos = monsterStartPos;
        playerKnockbackTicks = 0;
        monsterKnockbackTicks = 0;
        clearHorizontalMotion(player);
        clearHorizontalMotion(monster);
        faceParticipants();
    }

    private void restoreParticipantsToStart() {
        player.teleportTo(playerStartPos.x, playerStartPos.y, playerStartPos.z);
        player.setYRot(playerStartYRot);
        player.setXRot(playerStartXRot);
        monster.teleportTo(monsterStartPos.x, monsterStartPos.y, monsterStartPos.z);
        monster.setYRot(monsterStartYRot);
        monster.setXRot(monsterStartXRot);
        clearMotion(player);
        clearMotion(monster);
    }

    private void clearHorizontalMotion(LivingEntity entity) {
        Vec3 movement = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0D, movement.y, 0.0D);
        entity.xxa = 0.0F;
        entity.yya = 0.0F;
        entity.zza = 0.0F;
        entity.setJumping(false);
    }

    private void clearMotion(LivingEntity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        entity.xxa = 0.0F;
        entity.yya = 0.0F;
        entity.zza = 0.0F;
        entity.setJumping(false);
        entity.setOldPosAndRot();
    }

    private void faceParticipants() {
        faceTarget(player, monster);
        faceTarget(monster, player);
    }

    private void protectOpeningHealth() {
        if (openingProtectionTicks <= 0) {
            return;
        }
        openingProtectionTicks--;
        if (player.isAlive() && player.getHealth() < playerHealthAtBattleStart) {
            player.setHealth(playerHealthAtBattleStart);
        }
        if (monster.isAlive() && monster.getHealth() < monsterHealthAtBattleStart) {
            monster.setHealth(monsterHealthAtBattleStart);
        }
    }

    private void applyEntityDamage(LivingEntity entity, float amount) {
        suppressDamageEvent = true;
        entity.invulnerableTime = 0;
        entity.hurt(entity.damageSources().generic(), amount);
        suppressDamageEvent = false;
    }

    private static int nonPlayerBaseSpeed(LivingEntity entity) {
        double movementSpeed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return Math.max(1, Math.round((float) (movementSpeed / CardBalance.NON_PLAYER_BASELINE_MOVEMENT_SPEED * CardBalance.PLAYER_BASE_SPEED)));
    }

    private record PendingCardBatch(CombatantState user, ItemStack stack, CardInstance card, List<PendingEffect> effects) {
        private PendingCardBatch {
            stack = stack.copy();
            card = card == null ? null : card.copyForBattle();
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

    private record PendingEffect(CardEffectKind kind, int amount, CombatantState target, boolean effectDamage) {
        private PendingEffect(CardEffectKind kind, int amount, CombatantState target) {
            this(kind, amount, target, false);
        }
    }
}
