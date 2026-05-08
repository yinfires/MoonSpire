package com.yinfires.moonspire.battle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

public class CombatantState {
    private final LivingEntity entity;
    private final BattleDeck deck;
    private final int maxEnergy;
    private final int baseSpeed;
    private float battleHealth;
    private final float maxBattleHealth;
    private int defense;
    private int energySpent;
    private int roundSpeed;
    private boolean endedTurn;
    private boolean fakeDead;
    private int fakeDeathTicks;
    private UUID creditedPlayerKill;
    private final Map<BattleEffectType, Integer> effects = new EnumMap<>(BattleEffectType.class);

    public CombatantState(LivingEntity entity, BattleDeck deck, int maxEnergy, float maxBattleHealth, int baseSpeed) {
        this.entity = entity;
        this.deck = deck;
        this.maxEnergy = maxEnergy;
        this.maxBattleHealth = maxBattleHealth;
        this.battleHealth = maxBattleHealth;
        this.baseSpeed = Math.max(1, baseSpeed);
        this.roundSpeed = this.baseSpeed;
    }

    public LivingEntity entity() {
        return entity;
    }

    public BattleDeck deck() {
        return deck;
    }

    public int maxEnergy() {
        return maxEnergy;
    }

    public int energySpent() {
        return energySpent;
    }

    public int energyLeft() {
        return Math.max(0, maxEnergy - energySpent);
    }

    public void resetEnergy() {
        energySpent = 0;
    }

    public void setEnergySpent(int energySpent) {
        this.energySpent = Math.max(0, Math.min(maxEnergy, energySpent));
    }

    public boolean spendEnergy(int amount) {
        int cost = Math.max(0, amount);
        if (cost > energyLeft()) {
            return false;
        }
        setEnergySpent(energySpent + cost);
        return true;
    }

    public float battleHealth() {
        return battleHealth;
    }

    public float maxBattleHealth() {
        return maxBattleHealth;
    }

    public int defense() {
        return defense;
    }

    public int baseSpeed() {
        return baseSpeed;
    }

    public int roundSpeed() {
        return roundSpeed;
    }

    public boolean endedTurn() {
        return endedTurn;
    }

    public void setEndedTurn(boolean endedTurn) {
        this.endedTurn = endedTurn;
    }

    public boolean fakeDead() {
        return fakeDead;
    }

    public int fakeDeathTicks() {
        return fakeDeathTicks;
    }

    public boolean fakeDeathAnimationDone() {
        return !fakeDead || fakeDeathTicks >= 24;
    }

    public UUID creditedPlayerKill() {
        return creditedPlayerKill;
    }

    public void tickFakeDeath() {
        if (fakeDead) {
            fakeDeathTicks++;
            if (entity.isAlive()) {
                entity.setHealth(Math.max(1.0F, entity.getHealth()));
            }
        }
    }

    public void rollRoundSpeed(RandomSource random) {
        roundSpeed = Math.max(1, baseSpeed + random.nextInt(5) - 2);
    }

    public void addDefense(int amount) {
        defense += Math.max(0, amount);
    }

    public void clearDefense() {
        defense = 0;
    }

    public BattleDamageResult applyCardDamage(int amount, CombatantState attacker, UUID creditedPlayerKill) {
        int incoming = Math.max(0, amount);
        int modifiedIncoming = BattleDamageCalculator.directDamage(incoming, attacker.roundSpeed(), roundSpeed, defense, effectAmount(BattleEffectType.GUARD));
        return applyBlockableDamage(incoming, modifiedIncoming, creditedPlayerKill);
    }

    public BattleDamageResult applyEffectDamage(int amount, UUID creditedPlayerKill) {
        int damage = Math.max(0, amount);
        return applyBlockableDamage(damage, damage, creditedPlayerKill);
    }

    public BattleDamageResult applyDirectHealthDamage(int amount, UUID creditedPlayerKill) {
        int damage = Math.max(0, amount);
        applyHealthDamage(damage, creditedPlayerKill);
        return new BattleDamageResult(0, damage, damage);
    }

    public void addEffect(BattleEffectType type, int amount) {
        if (amount <= 0) {
            return;
        }
        effects.merge(type, amount, Integer::sum);
    }

    public int effectAmount(BattleEffectType type) {
        return effects.getOrDefault(type, 0);
    }

    public void decayEndOfTurnEffects() {
        int bleed = effectAmount(BattleEffectType.BLEED);
        if (bleed > 0) {
            int next = bleed / 3;
            if (next > 0) {
                effects.put(BattleEffectType.BLEED, next);
            } else {
                effects.remove(BattleEffectType.BLEED);
            }
        }
    }

    public BattleCombatantSnapshot snapshot() {
        return new BattleCombatantSnapshot(
                entity.getId(),
                battleHealth,
                maxBattleHealth,
                defense,
                energyLeft(),
                maxEnergy,
                baseSpeed,
                roundSpeed,
                endedTurn,
                fakeDead,
                fakeDeathTicks,
                effectSnapshots());
    }

    public boolean isDefeated() {
        return fakeDead || battleHealth <= 0.0F;
    }

    private BattleDamageResult applyBlockableDamage(int baseDamage, int damage, UUID creditedPlayerKill) {
        if (fakeDead) {
            return new BattleDamageResult(0, baseDamage, 0);
        }
        int blocked = Math.min(defense, damage);
        defense -= blocked;
        int finalHealthDamage = damage - blocked;
        applyHealthDamage(finalHealthDamage, creditedPlayerKill);
        return new BattleDamageResult(blocked, baseDamage, finalHealthDamage);
    }

    private void applyHealthDamage(int amount, UUID creditedPlayerKill) {
        if (amount > 0) {
            battleHealth = Math.max(0.0F, battleHealth - amount);
            if (battleHealth <= 0.0F) {
                markFakeDead(creditedPlayerKill);
            }
        }
    }

    private void markFakeDead(UUID creditedPlayerKill) {
        if (!fakeDead) {
            fakeDead = true;
            fakeDeathTicks = 0;
            defense = 0;
            energySpent = maxEnergy;
            endedTurn = true;
            deck().discardHand();
        }
        if (creditedPlayerKill != null) {
            this.creditedPlayerKill = creditedPlayerKill;
        }
    }

    public void clearFakeDeath() {
        fakeDead = false;
        fakeDeathTicks = 0;
        creditedPlayerKill = null;
        endedTurn = false;
        defense = 0;
        battleHealth = Math.max(1.0F, battleHealth);
        if (entity.isAlive()) {
            entity.setHealth(Math.max(1.0F, entity.getHealth()));
        }
    }

    private List<BattleEffectSnapshot> effectSnapshots() {
        List<BattleEffectSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<BattleEffectType, Integer> entry : effects.entrySet()) {
            if (entry.getValue() > 0) {
                snapshots.add(BattleEffectSnapshot.of(entry.getKey(), entry.getValue()));
            }
        }
        return snapshots;
    }

}
