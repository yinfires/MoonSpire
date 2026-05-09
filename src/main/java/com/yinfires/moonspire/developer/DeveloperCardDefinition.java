package com.yinfires.moonspire.developer;

import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.CardSourceType;
import com.yinfires.moonspire.card.CardTarget;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.card.RegisteredCardDefinition;
import java.util.ArrayList;
import java.util.List;

public record DeveloperCardDefinition(
        String id,
        String displayName,
        String nameKey,
        String descriptionKey,
        int cost,
        int attack,
        int defense,
        int bleed,
        List<DeveloperCardEffect> cardEffects,
        CardSourceType sourceType,
        String artPath,
        String artItemId,
        int artX,
        int artY,
        float artScale,
        String faceId) {
    public DeveloperCardDefinition {
        id = id == null ? "" : id;
        displayName = displayName == null ? "" : displayName;
        nameKey = nameKey == null ? "" : nameKey;
        descriptionKey = descriptionKey == null ? "" : descriptionKey;
        cardEffects = List.copyOf(cardEffects == null ? List.of() : cardEffects);
        sourceType = sourceType == null ? CardSourceType.CUSTOM : sourceType;
        artPath = artPath == null ? "" : artPath;
        artItemId = artItemId == null ? "" : artItemId;
        faceId = faceId == null || faceId.isBlank() ? "default" : faceId;
        cost = Math.max(0, cost);
        attack = Math.max(0, attack);
        defense = Math.max(0, defense);
        bleed = Math.max(0, bleed);
        artScale = Math.max(0.05F, artScale);
    }

    public static DeveloperCardDefinition defaultCard(String id) {
        return new DeveloperCardDefinition(
                id,
                "",
                "card.moonspire.unknown.name",
                "",
                1,
                0,
                0,
                0,
                List.of(),
                CardSourceType.CUSTOM,
                "",
                "",
                0,
                0,
                1.0F,
                "default");
    }

    public CardInstance toCardInstance() {
        return toRegisteredCard(MoonSpireCardRegistry.developerCardAlias(id)).createInstance();
    }

    public RegisteredCardDefinition toRegisteredCard(String registeredId) {
        List<DeveloperCardEffect> normalizedEffects = normalizedEffects();
        List<CardEffect> effects = normalizedEffects.stream()
                .filter(effect -> effect.amount() > 0 || effect.kind() == DeveloperCardEffect.Kind.EXHAUST)
                .map(effect -> new CardEffect(cardEffectKind(effect.kind()), effect.amount(), effect.target(), effect.count()))
                .toList();
        return new RegisteredCardDefinition(
                registeredId,
                nameKey,
                descriptionKey,
                Math.max(0, cost),
                0,
                0,
                effects,
                sourceType == null ? CardSourceType.CUSTOM : sourceType,
                artPath == null ? "" : artPath,
                artItemId == null ? "" : artItemId,
                artX,
                artY,
                Math.max(0.05F, artScale),
                faceId == null || faceId.isBlank() ? "default" : faceId,
                id);
    }

    private static CardEffectKind cardEffectKind(DeveloperCardEffect.Kind kind) {
        return switch (kind) {
            case DAMAGE -> CardEffectKind.DAMAGE;
            case HEAL -> CardEffectKind.HEAL;
            case BLOCK -> CardEffectKind.BLOCK;
            case BLEED -> CardEffectKind.BLEED;
            case GUARD -> CardEffectKind.GUARD;
            case STRENGTH -> CardEffectKind.STRENGTH;
            case LOSE_STRENGTH -> CardEffectKind.LOSE_STRENGTH;
            case REGENERATION -> CardEffectKind.REGENERATION;
            case HASTE -> CardEffectKind.HASTE;
            case POISON -> CardEffectKind.POISON;
            case BURN -> CardEffectKind.BURN;
            case WEAKNESS -> CardEffectKind.WEAKNESS;
            case SLOWNESS -> CardEffectKind.SLOWNESS;
            case EXHAUST -> CardEffectKind.EXHAUST;
            case EXHAUST_HAND -> CardEffectKind.EXHAUST_HAND;
            case DISCARD_HAND -> CardEffectKind.DISCARD_HAND;
        };
    }

    public List<DeveloperCardEffect> normalizedEffects() {
        if (cardEffects != null && !cardEffects.isEmpty()) {
            return List.copyOf(cardEffects);
        }
        List<DeveloperCardEffect> migrated = new ArrayList<>();
        if (attack > 0) {
            migrated.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.DAMAGE, attack, CardTarget.SINGLE_ENEMY, 1));
        }
        if (defense > 0) {
            migrated.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.BLOCK, defense, CardTarget.SELF, 1));
        }
        if (bleed > 0) {
            migrated.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.BLEED, bleed, CardTarget.SINGLE_ENEMY, 1));
        }
        return List.copyOf(migrated);
    }

    public DeveloperCardDefinition withIdAndKeys(String id, String displayName, String nameKey) {
        return new DeveloperCardDefinition(
                id,
                displayName,
                nameKey,
                descriptionKey,
                cost,
                attack,
                defense,
                bleed,
                normalizedEffects(),
                sourceType,
                artPath,
                artItemId,
                artX,
                artY,
                artScale,
                faceId);
    }
}
