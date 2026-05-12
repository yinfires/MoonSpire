package com.yinfires.moonspire.battle;
import com.yinfires.moonspire.card.CardInstance;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

public class CombatantState {
    public static final int FAKE_DEATH_ANIMATION_TICKS = 20;

    private final LivingEntity entity;
    private final BattleDeck deck;
    private final int maxEnergy;
    private final int baseSpeed;
    private float battleHealth;
    private final float maxBattleHealth;
    private int defense;
    private int currentEnergy;
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
        this.currentEnergy = maxEnergy;
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
        return Math.max(0, maxEnergy - currentEnergy);
    }

    public int energyLeft() {
        return Math.max(0, currentEnergy);
    }

    public void resetEnergy() {
        currentEnergy = maxEnergy;
    }

    public void setEnergySpent(int energySpent) {
        this.currentEnergy = Math.max(0, maxEnergy - Math.max(0, Math.min(maxEnergy, energySpent)));
    }

    public void addEnergy(int amount) {
        currentEnergy += Math.max(0, amount);
    }

    public boolean spendEnergy(int amount) {
        int cost = Math.max(0, amount);
        if (cost > energyLeft()) {
            return false;
        }
        currentEnergy -= cost;
        return true;
    }

    public float battleHealth() {
        return battleHealth;
    }

    public float maxBattleHealth() {
        return maxBattleHealth;
    }

    public float effectiveMaxBattleHealth() {
        return Math.max(1.0F, maxBattleHealth - Math.max(0, effectAmount(BattleEffectType.WITHER)));
    }

    public int defense() {
        return defense;
    }

    public int baseSpeed() {
        return baseSpeed;
    }

    public int roundSpeed() {
        return Math.max(1, roundSpeed + effectAmount(BattleEffectType.HASTE) - effectAmount(BattleEffectType.SLOWNESS));
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
        return !fakeDead || fakeDeathTicks >= FAKE_DEATH_ANIMATION_TICKS;
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
        return applyCardDamage(amount, attacker, creditedPlayerKill, false);
    }

    public BattleDamageResult applyCardDamage(int amount, CombatantState attacker, UUID creditedPlayerKill, boolean ignoreSpeed) {
        int incoming = Math.max(0, amount + attacker.effectAmount(BattleEffectType.STRENGTH));
        int modifiedIncoming = BattleDamageCalculator.directDamage(incoming, attacker.roundSpeed(), this.roundSpeed(), defense, effectAmount(BattleEffectType.GUARD), attacker.effectAmount(BattleEffectType.WEAKNESS) > 0, ignoreSpeed, effectAmount(BattleEffectType.GLOWING) > 0);
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

    public void killForSelfDestruct(UUID creditedPlayerKill) {
        battleHealth = 0.0F;
        markFakeDead(creditedPlayerKill);
    }

    public int heal(int amount) {
        float effectiveMaxHealth = effectiveMaxBattleHealth();
        if (fakeDead || amount <= 0 || battleHealth >= effectiveMaxHealth) {
            return 0;
        }
        float before = battleHealth;
        battleHealth = Math.min(effectiveMaxHealth, battleHealth + amount);
        return Math.round(battleHealth - before);
    }

    public void addEffect(BattleEffectType type, int amount) {
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
        clampBattleHealthToEffectiveMax();
        syncEntityGlowing();
    }

    public int effectAmount(BattleEffectType type) {
        return effects.getOrDefault(type, 0);
    }

    public void reduceEffect(BattleEffectType type, int amount) {
        if (type == null || amount <= 0 || !effects.containsKey(type)) {
            return;
        }
        int next = effects.get(type) - amount;
        if (next > 0 || (type.allowsNegativeStacks() && next != 0)) {
            effects.put(type, next);
        } else {
            effects.remove(type);
        }
        clampBattleHealthToEffectiveMax();
        syncEntityGlowing();
    }

    public void clearEffect(BattleEffectType type) {
        if (type == null || !effects.containsKey(type)) {
            return;
        }
        effects.remove(type);
        clampBattleHealthToEffectiveMax();
        syncEntityGlowing();
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
        reduceEffect(BattleEffectType.WITHER, 1);
        syncEntityGlowing();
    }

    public void reduceRetainedCardCosts() {
        for (int i = 0; i < deck().hand().size(); i++) {
            CardInstance card = deck().hand().get(i);
            if (!card.hasEffect(com.yinfires.moonspire.card.CardEffectKind.RETAIN)) {
                continue;
            }
            int reduction = card.effectAmount(com.yinfires.moonspire.card.CardEffectKind.RETAIN_REDUCE_COST);
            if (reduction > 0 && card.cost() > 0) {
                deck().replaceHandCard(i, card.withAdditionalBattleCostReduction(reduction));
            }
        }
    }

    public BattleCombatantSnapshot snapshot() {
        return snapshot(deck.hand().size() + deck.drawPile().size() + deck.discardPile().size());
    }

    public BattleCombatantSnapshot snapshot(int battleDeckCount) {
        return new BattleCombatantSnapshot(
                entity.getId(),
                Math.min(battleHealth, effectiveMaxBattleHealth()),
                effectiveMaxBattleHealth(),
                defense,
                energyLeft(),
                maxEnergy,
                baseSpeed,
                roundSpeed(),
                endedTurn,
                fakeDead,
                fakeDeathTicks,
                deck.version(),
                Math.max(0, battleDeckCount),
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

    private void clampBattleHealthToEffectiveMax() {
        battleHealth = Math.min(battleHealth, effectiveMaxBattleHealth());
    }

    private void markFakeDead(UUID creditedPlayerKill) {
        if (!fakeDead) {
            fakeDead = true;
            fakeDeathTicks = 0;
            defense = 0;
            currentEnergy = 0;
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
        syncEntityGlowing();
    }

    private void syncEntityGlowing() {
        entity.setGlowingTag(effectAmount(BattleEffectType.GLOWING) > 0);
    }

    private List<BattleEffectSnapshot> effectSnapshots() {
        List<BattleEffectSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<BattleEffectType, Integer> entry : effects.entrySet()) {
            if (entry.getValue() > 0 || (entry.getKey().allowsNegativeStacks() && entry.getValue() < 0)) {
                snapshots.add(BattleEffectSnapshot.of(entry.getKey(), entry.getValue()));
            }
        }
        return snapshots;
    }

}
