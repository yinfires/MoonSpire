package com.yinfires.moonspire.battle;

import java.util.List;

public record MonsterRewardPool(String entityTypeId, List<String> cardIds) {
    public MonsterRewardPool {
        entityTypeId = entityTypeId == null ? "" : entityTypeId;
        cardIds = List.copyOf(cardIds == null ? List.of() : cardIds);
    }
}
