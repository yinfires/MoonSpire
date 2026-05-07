package com.yinfires.moonspire.card;

public final class CardBalance {
    public static final int STARTING_HAND_SIZE = 5;
    public static final int BASE_ENERGY = 3;
    public static final int PLAYER_BASE_SPEED = 5;
    public static final double PLAYER_DEFAULT_MOVEMENT_SPEED = 0.1D;
    public static final double NON_PLAYER_BASELINE_MOVEMENT_SPEED = 0.23D;
    public static final float SPEED_DAMAGE_CHANGE_PER_POINT = 0.05F;
    public static final float MAX_SPEED_DAMAGE_BONUS = 1.0F;
    public static final float MAX_SPEED_DAMAGE_REDUCTION = 0.5F;

    private CardBalance() {
    }

    public static int fixedEnergy() {
        return BASE_ENERGY;
    }
}
