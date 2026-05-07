package com.yinfires.moonspire.developer;

import java.util.ArrayList;
import java.util.List;

public record DeveloperMonsterDefinition(
        String entityTypeId,
        float maxHealth,
        int energy,
        int speed,
        List<String> deckCardIds) {
    public static DeveloperMonsterDefinition empty(String entityTypeId) {
        return new DeveloperMonsterDefinition(entityTypeId, 0.0F, 0, 0, new ArrayList<>());
    }

    public boolean hasHealthOverride() {
        return maxHealth > 0.0F;
    }

    public boolean hasEnergyOverride() {
        return energy > 0;
    }

    public boolean hasSpeedOverride() {
        return speed > 0;
    }
}
