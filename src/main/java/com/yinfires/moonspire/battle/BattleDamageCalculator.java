package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardBalance;

public final class BattleDamageCalculator {
    public static final int GUARD_REDUCTION_PERCENT_PER_STACK = 10;
    private static final int MAX_GUARD_REDUCTION_STACKS = 10;

    private BattleDamageCalculator() {
    }

    public static int directDamage(int baseDamage, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard) {
        int incoming = Math.max(0, baseDamage);
        if (incoming <= 0) {
            return 0;
        }
        return Math.round(incoming * speedFactor(attackerSpeed, defenderSpeed, defenderBlock) * guardFactor(defenderGuard));
    }

    public static int guardReductionPercent(int stacks) {
        return Math.min(MAX_GUARD_REDUCTION_STACKS, Math.max(0, stacks)) * GUARD_REDUCTION_PERCENT_PER_STACK;
    }

    private static float speedFactor(int attackerSpeed, int defenderSpeed, int defenderBlock) {
        if (defenderBlock > 0) {
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
}
