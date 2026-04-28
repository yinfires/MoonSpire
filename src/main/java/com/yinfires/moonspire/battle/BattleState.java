package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardInstance;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public class BattleState {
    private final UUID id = UUID.randomUUID();
    private final ServerPlayer player;
    private final LivingEntity monster;
    private final CombatantState playerState;
    private final CombatantState monsterState;
    private BattlePhase phase = BattlePhase.PREPARE;
    private int phaseTicks = CardBalance.PREPARE_TICKS;
    private int executeTicks;
    private boolean playerReady;
    private boolean monsterNoAiBeforeBattle;
    private boolean suppressDamageEvent;

    public BattleState(ServerPlayer player, LivingEntity monster, List<CardInstance> playerCards, List<CardInstance> monsterCards) {
        RandomSource random = player.getRandom();
        this.player = player;
        this.monster = monster;
        this.playerState = new CombatantState(
                player,
                new BattleDeck(playerCards, random),
                CardBalance.playerEnergy(player.experienceLevel),
                Math.max(20.0F, player.getMaxHealth()));
        this.monsterState = new CombatantState(
                monster,
                new BattleDeck(monsterCards, random),
                CardBalance.monsterEnergy(monster.getMaxHealth()),
                Math.max(1.0F, monster.getMaxHealth()));
        if (monster instanceof Mob mob) {
            monsterNoAiBeforeBattle = mob.isNoAi();
        }
        beginPrepare();
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

    public int phaseTicks() {
        return phaseTicks;
    }

    public boolean suppressDamageEvent() {
        return suppressDamageEvent;
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

    public CombatantState opponentOf(LivingEntity entity) {
        if (entity == player) {
            return monsterState;
        }
        if (entity == monster) {
            return playerState;
        }
        return null;
    }

    public boolean tick() {
        if (!player.isAlive() || player.hasDisconnected() || !monster.isAlive() || monster.isRemoved()) {
            return true;
        }
        if (phase == BattlePhase.PREPARE) {
            freeze(player);
            freeze(monster);
            if (--phaseTicks <= 0 || playerReady) {
                beginExecute();
            }
        } else {
            executeTicks++;
            tickMonsterAi();
            if (executeTicks >= CardBalance.ATTACK_AUTO_HIT_TICKS) {
                resolveAutomaticHits();
            }
            if (!playerState.deck().hasPreparedActions() && !monsterState.deck().hasPreparedActions()) {
                beginPrepare();
            }
        }
        return playerState.isDefeated() || monsterState.isDefeated();
    }

    public void finish() {
        if (monster instanceof Mob mob) {
            mob.setNoAi(monsterNoAiBeforeBattle);
        }
        player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
        monster.setDeltaMovement(0.0D, monster.getDeltaMovement().y, 0.0D);
        if (playerState.isDefeated() && player.isAlive()) {
            applyEntityDamage(player, player.getMaxHealth() * 2.0F);
        }
        if (monsterState.isDefeated() && monster.isAlive()) {
            applyEntityDamage(monster, monster.getMaxHealth() * 2.0F);
        }
    }

    public void preparePlayerCards(List<Integer> handIndexes) {
        if (phase != BattlePhase.PREPARE) {
            return;
        }
        playerState.setEnergySpent(playerState.deck().prepare(handIndexes, playerState.maxEnergy()));
        playerReady = true;
    }

    public boolean usePlayerPreparedCard(int targetEntityId) {
        if (phase != BattlePhase.EXECUTE) {
            return false;
        }
        CardInstance next = playerState.deck().peekNextAction();
        if (next == null) {
            return false;
        }
        if (next.hasDefense() && !next.hasAttack()) {
            CardInstance card = playerState.deck().popNextAction();
            playerState.addDefense(card.defense());
            player.swing(InteractionHand.MAIN_HAND, true);
            return true;
        }
        if (next.hasAttack()) {
            if (targetEntityId != monster.getId() || !player.canInteractWithEntity(monster, 0.0D)) {
                return false;
            }
            CardInstance card = playerState.deck().popNextAction();
            monsterState.applyDamage(card.attack());
            if (card.defense() > 0) {
                playerState.addDefense(card.defense());
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            return true;
        }
        return false;
    }

    public void handleAttack(LivingEntity attacker, LivingEntity target) {
        if (phase != BattlePhase.EXECUTE) {
            return;
        }
        CombatantState attackerState = stateFor(attacker);
        CombatantState targetState = stateFor(target);
        if (attackerState == null || targetState == null) {
            return;
        }
        CardInstance card = attackerState.deck().popNextAttack();
        if (card != null) {
            targetState.applyDamage(card.attack());
            attacker.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    public BattleSnapshot snapshot() {
        return new BattleSnapshot(
                true,
                phase,
                phaseTicks,
                playerState.battleHealth(),
                playerState.maxBattleHealth(),
                playerState.defense(),
                playerState.energyLeft(),
                playerState.maxEnergy(),
                monsterState.battleHealth(),
                monsterState.maxBattleHealth(),
                monsterState.defense(),
                playerState.deck().drawPile().size(),
                playerState.deck().discardPile().size(),
                List.copyOf(playerState.deck().hand()),
                List.copyOf(playerState.deck().prepared()));
    }

    private void beginPrepare() {
        phase = BattlePhase.PREPARE;
        phaseTicks = CardBalance.PREPARE_TICKS;
        executeTicks = 0;
        playerReady = false;
        playerState.clearDefense();
        monsterState.clearDefense();
        playerState.setEnergySpent(0);
        monsterState.setEnergySpent(0);
        playerState.deck().startRound(player.getRandom());
        monsterState.deck().startRound(player.getRandom());
        prepareMonsterCards();
        if (monster instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setNoAi(true);
        }
    }

    private void beginExecute() {
        phase = BattlePhase.EXECUTE;
        phaseTicks = 0;
        executeTicks = 0;
        if (monster instanceof Mob mob) {
            mob.setNoAi(monsterNoAiBeforeBattle);
            mob.setTarget(player);
        }
    }

    private void prepareMonsterCards() {
        int spent = 0;
        java.util.ArrayList<Integer> indexes = new java.util.ArrayList<>();
        List<CardInstance> hand = monsterState.deck().hand();
        for (int i = 0; i < hand.size(); i++) {
            CardInstance card = hand.get(i);
            if (spent + card.cost() <= monsterState.maxEnergy()) {
                indexes.add(i);
                spent += card.cost();
            }
        }
        monsterState.setEnergySpent(monsterState.deck().prepare(indexes, monsterState.maxEnergy()));
    }

    private void tickMonsterAi() {
        if (!(monster instanceof Mob mob)) {
            return;
        }
        if (monster.distanceToSqr(player) > 6.0D) {
            mob.getNavigation().moveTo(player, 1.1D);
        } else if (mob.getSensing().hasLineOfSight(player)) {
            handleAttack(monster, player);
        }
        if (monsterState.defense() <= 0 && monsterState.battleHealth() < monsterState.maxBattleHealth() * 0.65F) {
            CardInstance defense = monsterState.deck().popNextDefense();
            if (defense != null) {
                monsterState.addDefense(defense.defense());
            }
        }
    }

    private void resolveAutomaticHits() {
        resolveAutomaticHits(playerState, monsterState);
        resolveAutomaticHits(monsterState, playerState);
        beginPrepare();
    }

    private void resolveAutomaticHits(CombatantState attacker, CombatantState defender) {
        CardInstance card;
        int defenderSpeed = defender.deck().bestPreparedSpeed();
        while ((card = attacker.deck().popNextAttack()) != null) {
            defender.applyDamage(CardBalance.autoHitDamage(card, defenderSpeed));
        }
    }

    private void freeze(LivingEntity entity) {
        entity.setDeltaMovement(0.0D, entity.getDeltaMovement().y, 0.0D);
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    private void applyEntityDamage(LivingEntity entity, float amount) {
        suppressDamageEvent = true;
        entity.hurt(entity.damageSources().generic(), amount);
        suppressDamageEvent = false;
    }
}
