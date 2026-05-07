package com.yinfires.moonspire.battle;

public record BattleDamageResult(int blockedDamage, int baseHealthDamage, int healthDamage) {
    public boolean fullyBlocked() {
        return blockedDamage > 0 && healthDamage <= 0;
    }

    public boolean partiallyBlocked() {
        return blockedDamage > 0 && healthDamage > 0;
    }

    public boolean modifiedBySpeed() {
        return blockedDamage + healthDamage != baseHealthDamage;
    }
}
