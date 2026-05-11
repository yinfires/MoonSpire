package com.yinfires.moonspire.developer;

import com.yinfires.moonspire.battle.BattleEffectType;
import java.util.Locale;
import java.util.Optional;

public record DeveloperMonsterInitialEffect(String effectId, int amount) {
    public DeveloperMonsterInitialEffect {
        effectId = normalizeEffectId(effectId);
        BattleEffectType type = typeById(effectId).orElse(null);
        if (type == null) {
            amount = 0;
        } else if (!type.allowsNegativeStacks()) {
            amount = Math.max(0, amount);
        }
    }

    public static DeveloperMonsterInitialEffect of(BattleEffectType type, int amount) {
        return new DeveloperMonsterInitialEffect(type == null ? "" : type.id(), amount);
    }

    public Optional<BattleEffectType> effectType() {
        return typeById(effectId);
    }

    public boolean isEffective() {
        return effectType()
                .map(type -> type.allowsNegativeStacks() ? amount != 0 : amount > 0)
                .orElse(false);
    }

    private static String normalizeEffectId(String effectId) {
        String cleaned = effectId == null ? "" : effectId.trim().toLowerCase(Locale.ROOT);
        return typeById(cleaned)
                .map(BattleEffectType::id)
                .orElse(cleaned);
    }

    private static Optional<BattleEffectType> typeById(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return Optional.empty();
        }
        String cleaned = effectId.trim().toLowerCase(Locale.ROOT);
        for (BattleEffectType type : BattleEffectType.values()) {
            if (type.id().equals(cleaned) || type.name().toLowerCase(Locale.ROOT).equals(cleaned)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
