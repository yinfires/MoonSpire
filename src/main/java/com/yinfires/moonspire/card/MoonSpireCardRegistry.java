package com.yinfires.moonspire.card;

import com.yinfires.moonspire.developer.DeveloperCardDefinition;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;

public final class MoonSpireCardRegistry {
    private MoonSpireCardRegistry() {
    }

    public static Optional<RegisteredCardDefinition> card(String id) {
        String normalized = normalizeId(id);
        Optional<DeveloperCardDefinition> developer = developerByRegisteredId(normalized);
        if (developer.isPresent()) {
            return Optional.of(developer.get().toRegisteredCard(normalized));
        }
        return baseCard(normalized);
    }

    public static Optional<RegisteredCardDefinition> baseCard(String id) {
        String normalized = normalizeId(id);
        RegisteredCardDefinition builtin = builtinCards().stream().filter(card -> card.id().equals(normalized)).findFirst().orElse(null);
        if (builtin != null) {
            return Optional.of(builtin);
        }
        RegisteredCardDefinition converted = convertedCardTemplate(normalized);
        if (converted != null) {
            return Optional.of(converted);
        }
        return Optional.empty();
    }

    public static Optional<CardInstance> cardInstance(String id) {
        return card(id).map(RegisteredCardDefinition::createInstance);
    }

    public static List<CardInstance> cardsByIds(List<String> ids) {
        List<CardInstance> cards = new ArrayList<>();
        for (String id : ids) {
            cardInstance(id).ifPresent(cards::add);
        }
        return cards;
    }

    public static List<RegisteredCardDefinition> allCards() {
        Map<String, RegisteredCardDefinition> cards = new LinkedHashMap<>();
        for (RegisteredCardDefinition card : baseCards()) {
            cards.put(card.id(), card);
        }
        for (DeveloperCardDefinition developer : DeveloperDataManager.load().cards) {
            String registeredId = registeredDeveloperId(developer.id());
            if (!registeredId.isBlank()) {
                cards.put(registeredId, developer.toRegisteredCard(registeredId));
            }
        }
        return List.copyOf(cards.values());
    }

    public static List<RegisteredCardDefinition> baseCards() {
        Map<String, RegisteredCardDefinition> cards = new LinkedHashMap<>();
        for (RegisteredCardDefinition card : builtinCards()) {
            cards.put(card.id(), card);
        }
        for (Item item : BuiltInRegistries.ITEM) {
            RegisteredCardDefinition converted = convertedCard(new ItemStack(item));
            if (converted != null) {
                cards.put(converted.id(), converted);
            }
        }
        return List.copyOf(cards.values());
    }

    public static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String cleaned = id.trim().toLowerCase(Locale.ROOT);
        if (cleaned.contains(":")) {
            return cleaned;
        }
        return cleaned.replace('/', '_').replace('.', '_').replace('-', '_');
    }

    public static String itemCardId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "item_" + itemId.getNamespace().toLowerCase(Locale.ROOT) + "_" + sanitizePath(itemId.getPath());
    }

    public static String developerCardAlias(String id) {
        String normalized = normalizeId(id);
        return normalized.startsWith("custom_") ? normalized : "custom_" + normalized;
    }

    public static String registeredDeveloperId(String id) {
        String normalized = normalizeId(id);
        if (normalized.startsWith("builtin_") || normalized.startsWith("item_") || normalized.startsWith("custom_")) {
            return normalized;
        }
        return developerCardAlias(normalized);
    }

    public static boolean canConvert(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof TieredItem || stack.getItem() instanceof ArmorItem || attackFromAttributes(stack) > 0.0D);
    }

    public static RegisteredCardDefinition convertedCard(ItemStack stack) {
        if (!canConvert(stack)) {
            return null;
        }
        String id = itemCardId(stack);
        if (id.isBlank()) {
            return null;
        }
        CardSourceType type = sourceType(stack);
        int attack = switch (type) {
            case WEAPON, TOOL -> Math.max(1, (int) Math.ceil(attackFromAttributes(stack)));
            case ARMOR -> 0;
            default -> Math.max(0, (int) Math.ceil(attackFromAttributes(stack)));
        };
        int defense = switch (type) {
            case ARMOR -> Math.max(1, armorDefense(stack));
            case TOOL -> Math.max(0, attack / 3);
            default -> 0;
        };
        int rarityBonus = rarityBonus(stack.getRarity());
        int cost = Math.max(0, Math.min(3, (attack + defense + 3) / 5 - rarityBonus));
        List<CardEffect> effects = isIronSword(stack) ? List.of(new CardEffect(CardEffectKind.BLEED, 1, CardTarget.SINGLE_ENEMY)) : List.of();
        return new RegisteredCardDefinition(
                id,
                stack.getDescriptionId(),
                "",
                cost,
                attack,
                defense,
                effects,
                type,
                "",
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                0,
                0,
                1.0F,
                "default",
                "");
    }

    private static RegisteredCardDefinition convertedCardTemplate(String normalizedId) {
        if (!normalizedId.startsWith("item_")) {
            return null;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (normalizedId.equals(itemCardId(stack)) && canConvert(stack)) {
                return convertedCard(stack);
            }
        }
        return null;
    }

    private static Optional<DeveloperCardDefinition> developerById(String id) {
        return DeveloperDataManager.load().cards.stream().filter(card -> id.equals(card.id())).findFirst();
    }

    private static Optional<DeveloperCardDefinition> developerByRegisteredId(String id) {
        return DeveloperDataManager.load().cards.stream().filter(card -> id.equals(registeredDeveloperId(card.id()))).findFirst();
    }

    private static List<RegisteredCardDefinition> builtinCards() {
        List<RegisteredCardDefinition> cards = new ArrayList<>();
        cards.addAll(builtinModCards());
        cards.addAll(builtinMonsterCards());
        return List.copyOf(cards);
    }

    private static List<RegisteredCardDefinition> builtinModCards() {
        return List.of();
    }

    private static List<RegisteredCardDefinition> builtinMonsterCards() {
        return List.of(
                new RegisteredCardDefinition("builtin_monster_claw", "card.moonspire.monster.claw.name", "", 1, 5, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_rotten_guard", "card.moonspire.monster.rotten_guard.name", "", 1, 0, 4, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_lunge", "card.moonspire.monster.lunge.name", "", 2, 8, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_bone_shot", "card.moonspire.monster.bone_shot.name", "", 1, 6, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_sidestep", "card.moonspire.monster.sidestep.name", "", 1, 0, 3, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_aimed_volley", "card.moonspire.monster.aimed_volley.name", "", 2, 9, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_pounce", "card.moonspire.monster.pounce.name", "", 1, 5, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_skitter", "card.moonspire.monster.skitter.name", "", 1, 0, 3, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_bite", "card.moonspire.monster.bite.name", "", 2, 7, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_strike", "card.moonspire.monster.strike.name", "", 1, 3, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_guard", "card.moonspire.monster.guard.name", "", 1, 0, 2, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_heavy_strike", "card.moonspire.monster.heavy_strike.name", "", 2, 6, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""));
    }

    private static CardSourceType sourceType(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem) {
            return CardSourceType.ARMOR;
        }
        if (stack.getItem() instanceof TieredItem) {
            return attackFromAttributes(stack) >= 4.0D ? CardSourceType.WEAPON : CardSourceType.TOOL;
        }
        return CardSourceType.UNKNOWN;
    }

    private static int armorDefense(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return Math.max(1, armorItem.getDefense());
        }
        return (int) Math.ceil(attributeValue(stack, Attributes.ARMOR));
    }

    private static double attackFromAttributes(ItemStack stack) {
        return attributeValue(stack, Attributes.ATTACK_DAMAGE);
    }

    private static double attributeValue(ItemStack stack, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        final double[] value = {0.0D};
        modifiers.modifiers().forEach(entry -> {
            if (entry.attribute().equals(attribute) && entry.slot().test(EquipmentSlot.MAINHAND)) {
                AttributeModifier modifier = entry.modifier();
                if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    value[0] += modifier.amount();
                }
            }
        });
        return value[0];
    }

    private static int rarityBonus(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 0;
            case UNCOMMON -> 1;
            case RARE -> 2;
            case EPIC -> 3;
        };
    }

    private static boolean isIronSword(ItemStack stack) {
        return stack.is(Items.IRON_SWORD);
    }

    private static String sanitizePath(String path) {
        return path.toLowerCase(Locale.ROOT).replace('/', '_').replace('.', '_').replace('-', '_');
    }
}
