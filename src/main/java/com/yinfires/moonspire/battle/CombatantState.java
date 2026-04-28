package com.yinfires.moonspire.battle;

import net.minecraft.world.entity.LivingEntity;

public class CombatantState {
    private final LivingEntity entity;
    private final BattleDeck deck;
    private final int maxEnergy;
    private float battleHealth;
    private float maxBattleHealth;
    private int defense;
    private int energySpent;

    public CombatantState(LivingEntity entity, BattleDeck deck, int maxEnergy, float maxBattleHealth) {
        this.entity = entity;
        this.deck = deck;
        this.maxEnergy = maxEnergy;
        this.maxBattleHealth = maxBattleHealth;
        this.battleHealth = maxBattleHealth;
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

    public void setEnergySpent(int energySpent) {
        this.energySpent = Math.max(0, Math.min(maxEnergy, energySpent));
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

    public void addDefense(int amount) {
        defense += Math.max(0, amount);
    }

    public void clearDefense() {
        defense = 0;
    }

    public float applyDamage(float amount) {
        float remaining = Math.max(0.0F, amount);
        int blocked = Math.min(defense, (int) Math.floor(remaining));
        defense -= blocked;
        remaining -= blocked;
        if (remaining > 0.0F) {
            battleHealth = Math.max(0.0F, battleHealth - remaining);
        }
        return remaining;
    }

    public boolean isDefeated() {
        return battleHealth <= 0.0F || !entity.isAlive();
    }
}
