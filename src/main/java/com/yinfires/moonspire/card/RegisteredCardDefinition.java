package com.yinfires.moonspire.card;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record RegisteredCardDefinition(
        String id,
        String nameKey,
        String descriptionKey,
        int cost,
        int attack,
        int defense,
        List<CardEffect> effects,
        CardSourceType sourceType,
        String artPath,
        String artItemId,
        int artX,
        int artY,
        float artScale,
        String faceId,
        String developerCardId) {
    public RegisteredCardDefinition {
        effects = CardEffectOrder.orderedCardEffects(effects == null ? List.of() : effects);
        sourceType = sourceType == null ? CardSourceType.UNKNOWN : sourceType;
        id = safe(id);
        nameKey = safe(nameKey);
        descriptionKey = safe(descriptionKey);
        artPath = safe(artPath);
        artItemId = safe(artItemId);
        faceId = faceId == null || faceId.isBlank() ? "default" : faceId;
        developerCardId = safe(developerCardId);
        cost = Math.max(0, cost);
        attack = Math.max(0, attack);
        defense = Math.max(0, defense);
        artScale = Math.max(0.05F, artScale);
    }

    public CardInstance createInstance() {
        return createInstance(ItemStack.EMPTY);
    }

    public CardInstance createInstance(ItemStack sourceStack) {
        ItemStack displayStack = sourceStack.isEmpty() ? artItemStack() : sourceStack.copy();
        if (!displayStack.isEmpty()) {
            displayStack.setCount(1);
        }
        return new CardInstance(
                UUID.randomUUID(),
                id,
                displayStack,
                nameKey,
                descriptionKey,
                attack,
                defense,
                cost,
                0,
                effects,
                sourceType,
                developerCardId,
                artPath,
                artItemId,
                artX,
                artY,
                artScale,
                faceId);
    }

    public RegisteredCardDefinition withCombatValues(int attack, int defense, int cost) {
        return new RegisteredCardDefinition(id, nameKey, descriptionKey, cost, attack, defense, effects, sourceType, artPath, artItemId, artX, artY, artScale, faceId, developerCardId);
    }

    private ItemStack artItemStack() {
        ItemStack special = MoonSpireCardRegistry.builtinSourceStack(id);
        if (!special.isEmpty()) {
            return special;
        }
        if (artItemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(artItemId));
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
