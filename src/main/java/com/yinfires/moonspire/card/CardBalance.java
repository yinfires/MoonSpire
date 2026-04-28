package com.yinfires.moonspire.card;

public final class CardBalance {
    public static final int STARTING_HAND_SIZE = 5;
    public static final int PREPARE_TICKS = 20 * 30;
    public static final int ATTACK_AUTO_HIT_TICKS = 20 * 15;
    public static final int MAX_ENERGY = 6;
    public static final int BASE_ENERGY = 3;
    public static final float AUTO_HIT_DAMAGE_RATIO = 2.0F / 3.0F;
    public static final float SPEED_AUTO_HIT_REDUCTION_PER_POINT = 0.05F;
    public static final float MAX_SPEED_AUTO_HIT_REDUCTION = 0.50F;

    private CardBalance() {
    }

    public static int playerEnergy(int experienceLevel) {
        return Math.min(MAX_ENERGY, BASE_ENERGY + Math.max(0, experienceLevel / 10));
    }

    public static int monsterEnergy(float maxHealth) {
        if (maxHealth >= 80.0F) {
            return 6;
        }
        if (maxHealth >= 40.0F) {
            return 5;
        }
        if (maxHealth >= 25.0F) {
            return 4;
        }
        return 3;
    }

    public static float autoHitDamage(CardInstance card, int defenderSpeed) {
        int speedAdvantage = Math.max(0, defenderSpeed - card.speed());
        float reduction = Math.min(MAX_SPEED_AUTO_HIT_REDUCTION, speedAdvantage * SPEED_AUTO_HIT_REDUCTION_PER_POINT);
        return card.attack() * AUTO_HIT_DAMAGE_RATIO * (1.0F - reduction);
    }
}
