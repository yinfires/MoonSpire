package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardBalance;

public final class BattleDamageCalculator {
    public static final int GUARD_REDUCTION_PERCENT_PER_STACK = 10;
    private static final int MAX_GUARD_REDUCTION_STACKS = 9;

    private BattleDamageCalculator() {
    }

    public static int directDamage(int baseDamage, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard) {
        return directDamage(baseDamage, attackerSpeed, defenderSpeed, defenderBlock, defenderGuard, false);
    }

    public static int directDamage(int baseDamage, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard, boolean weakened) {
        return directDamage(baseDamage, attackerSpeed, defenderSpeed, defenderBlock, defenderGuard, weakened, false, false);
    }

    public static int directDamage(int baseDamage, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard, boolean weakened, boolean ignoreSpeed, boolean glowingTarget) {
        int incoming = Math.max(0, baseDamage);
        if (incoming <= 0) {
            return 0;
        }
        return Math.round(incoming * weaknessFactor(weakened) * speedFactor(attackerSpeed, defenderSpeed, defenderBlock, ignoreSpeed) * guardFactor(defenderGuard) * glowingFactor(glowingTarget));
    }

    public static int guardReductionPercent(int stacks) {
        return Math.min(MAX_GUARD_REDUCTION_STACKS, Math.max(0, stacks)) * GUARD_REDUCTION_PERCENT_PER_STACK;
    }

    private static float speedFactor(int attackerSpeed, int defenderSpeed, int defenderBlock, boolean ignoreSpeed) {
        if (ignoreSpeed || defenderBlock > 0) {
            return 1.0F;
        }
        int advantage = attackerSpeed - defenderSpeed;
        if (advantage > 0) {
            float bonus = Math.min(CardBalance.MAX_SPEED_DAMAGE_BONUS, advantage * CardBalance.SPEED_DAMAGE_CHANGE_PER_POINT);
            return 1.0F + bonus;
        }
        if (advantage < 0) {
            float reduction = Math.min(CardBalance.MAX_SPEED_DAMAGE_REDUCTION, -advantage * CardBalance.SPEED_DAMAGE_CHANGE_PER_POINT);
            return 1.0F - reduction;
        }
        return 1.0F;
    }

    private static float guardFactor(int stacks) {
        int reduction = guardReductionPercent(stacks);
        return Math.max(0.0F, 1.0F - reduction / 100.0F);
    }

    private static float weaknessFactor(boolean weakened) {
        return weakened ? 0.75F : 1.0F;
    }

    private static float glowingFactor(boolean glowingTarget) {
        return glowingTarget ? 1.1F : 1.0F;
    }
}
