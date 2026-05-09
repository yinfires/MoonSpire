package com.yinfires.moonspire.developer;

import java.util.ArrayList;
import java.util.List;

public record DeveloperMonsterDefinition(
        String entityTypeId,
        float maxHealth,
        int energy,
        int speed,
        List<String> deckCardIds,
        boolean deckOverride) {
    public DeveloperMonsterDefinition {
        if (deckCardIds == null) {
            deckCardIds = new ArrayList<>();
        }
        deckOverride = deckOverride || !deckCardIds.isEmpty();
    }

    public DeveloperMonsterDefinition(String entityTypeId, float maxHealth, int energy, int speed, List<String> deckCardIds) {
        this(entityTypeId, maxHealth, energy, speed, deckCardIds, deckCardIds != null && !deckCardIds.isEmpty());
    }

    public static DeveloperMonsterDefinition empty(String entityTypeId) {
        return new DeveloperMonsterDefinition(entityTypeId, 0.0F, 0, 0, new ArrayList<>(), false);
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

    public boolean hasDeckOverride() {
        return deckOverride;
    }
}
