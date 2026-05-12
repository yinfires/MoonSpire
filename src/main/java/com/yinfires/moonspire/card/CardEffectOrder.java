package com.yinfires.moonspire.card;

import com.yinfires.moonspire.developer.DeveloperCardEffect;
import java.util.ArrayList;
import java.util.List;

public final class CardEffectOrder {
    private CardEffectOrder() {
    }

    public static List<CardEffect> orderedCardEffects(List<CardEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return List.of();
        }
        List<CardEffect> innate = new ArrayList<>();
        List<CardEffect> middle = new ArrayList<>();
        List<CardEffect> exhaust = new ArrayList<>();
        for (CardEffect effect : effects) {
            if (effect == null) {
                continue;
            }
            if (effect.kind() == CardEffectKind.INNATE) {
                innate.add(effect);
            } else if (effect.kind() == CardEffectKind.EXHAUST) {
                exhaust.add(effect);
            } else {
                middle.add(effect);
            }
        }
        List<CardEffect> ordered = new ArrayList<>(innate.size() + middle.size() + exhaust.size());
        ordered.addAll(innate);
        ordered.addAll(middle);
        ordered.addAll(exhaust);
        return List.copyOf(ordered);
    }

    public static List<DeveloperCardEffect> orderedDeveloperEffects(List<DeveloperCardEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return List.of();
        }
        List<DeveloperCardEffect> innate = new ArrayList<>();
        List<DeveloperCardEffect> middle = new ArrayList<>();
        List<DeveloperCardEffect> exhaust = new ArrayList<>();
        for (DeveloperCardEffect effect : effects) {
            if (effect == null) {
                continue;
            }
            if (effect.kind() == DeveloperCardEffect.Kind.INNATE) {
                innate.add(effect);
            } else if (effect.kind() == DeveloperCardEffect.Kind.EXHAUST) {
                exhaust.add(effect);
            } else {
                middle.add(effect);
            }
        }
        List<DeveloperCardEffect> ordered = new ArrayList<>(innate.size() + middle.size() + exhaust.size());
        ordered.addAll(innate);
        ordered.addAll(middle);
        ordered.addAll(exhaust);
        return List.copyOf(ordered);
    }
}
