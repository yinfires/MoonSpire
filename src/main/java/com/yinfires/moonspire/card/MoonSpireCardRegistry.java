package com.yinfires.moonspire.card;

import com.yinfires.moonspire.developer.DeveloperCardDefinition;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class MoonSpireCardRegistry {
    public static final String SELF_DESTRUCT_VIEW_CARD_ID = "custom_self_destruct";

    private MoonSpireCardRegistry() {
    }

    public static Optional<RegisteredCardDefinition> card(String id) {
        String normalized = normalizeId(id);
        if (SELF_DESTRUCT_VIEW_CARD_ID.equals(normalized)) {
            return Optional.empty();
        }
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

    public static ItemStack builtinSourceStack(String id) {
        return switch (normalizeId(id)) {
            case "builtin_monster_poison_splash" -> potionStack(Items.SPLASH_POTION, Potions.POISON);
            case "builtin_monster_weakness_splash" -> potionStack(Items.SPLASH_POTION, Potions.WEAKNESS);
            case "builtin_monster_slowness_splash" -> potionStack(Items.SPLASH_POTION, Potions.SLOWNESS);
            case "builtin_monster_harming_splash" -> potionStack(Items.SPLASH_POTION, Potions.HARMING);
            case "builtin_monster_healing_splash" -> potionStack(Items.SPLASH_POTION, Potions.HEALING);
            case "builtin_monster_healing_draught" -> potionStack(Items.POTION, Potions.HEALING);
            case "builtin_monster_swiftness_draught" -> potionStack(Items.POTION, Potions.SWIFTNESS);
            default -> ItemStack.EMPTY;
        };
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
            if (!registeredId.isBlank() && !SELF_DESTRUCT_VIEW_CARD_ID.equals(registeredId)) {
                cards.put(registeredId, developer.toRegisteredCard(registeredId));
            }
        }
        return List.copyOf(cards.values());
    }

    public static RegisteredCardDefinition selfDestructViewCard() {
        return new RegisteredCardDefinition(
                SELF_DESTRUCT_VIEW_CARD_ID,
                "card.moonspire.custom.self_destruct.name",
                "card.moonspire.custom.self_destruct.description",
                0,
                0,
                0,
                List.of(new CardEffect(CardEffectKind.DAMAGE, CardBalance.SELF_DESTRUCT_DAMAGE, CardTarget.ALL_OTHER_UNITS)),
                CardSourceType.CUSTOM,
                "",
                "minecraft:gunpowder",
                0,
                0,
                1.0F,
                "default",
                "");
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
        return !stack.isEmpty() && (specialConvertedCard(stack) || stack.getItem() instanceof TieredItem || stack.getItem() instanceof ArmorItem || attackFromAttributes(stack) > 0.0D);
    }

    public static RegisteredCardDefinition convertedCard(ItemStack stack) {
        if (!canConvert(stack)) {
            return null;
        }
        RegisteredCardDefinition special = specialConvertedCardDefinition(stack);
        if (special != null) {
            return special;
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

    private static boolean specialConvertedCard(ItemStack stack) {
        return stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW);
    }

    private static RegisteredCardDefinition specialConvertedCardDefinition(ItemStack stack) {
        String id = itemCardId(stack);
        if (id.isBlank()) {
            return null;
        }
        String artItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (stack.is(Items.BOW)) {
            return new RegisteredCardDefinition(id, stack.getDescriptionId(), "", 1, 0, 0, List.of(
                    new CardEffect(CardEffectKind.REMOTE, 1),
                    new CardEffect(CardEffectKind.CONSUME_ARROW, 7, CardTarget.SINGLE_ENEMY)), CardSourceType.WEAPON, "", artItemId, 0, 0, 1.0F, "default", "");
        }
        if (stack.is(Items.CROSSBOW)) {
            return new RegisteredCardDefinition(id, stack.getDescriptionId(), "", 2, 0, 0, List.of(
                    new CardEffect(CardEffectKind.REMOTE, 1),
                    new CardEffect(CardEffectKind.CONSUME_ARROW, 13, CardTarget.SINGLE_ENEMY),
                    new CardEffect(CardEffectKind.RETAIN_REDUCE_COST, 1),
                    new CardEffect(CardEffectKind.RETAIN, 1)), CardSourceType.WEAPON, "", artItemId, 0, 0, 1.0F, "default", "");
        }
        if (stack.is(Items.ARROW)) {
            return new RegisteredCardDefinition(id, stack.getDescriptionId(), "", 1, 0, 0, List.of(
                    new CardEffect(CardEffectKind.ARROW, 1),
                    new CardEffect(CardEffectKind.DAMAGE, 3, CardTarget.SINGLE_ENEMY),
                    new CardEffect(CardEffectKind.EXHAUST, 1)), CardSourceType.UNKNOWN, "", artItemId, 0, 0, 1.0F, "default", "");
        }
        if (stack.is(Items.SPECTRAL_ARROW)) {
            return new RegisteredCardDefinition(id, stack.getDescriptionId(), "", 1, 0, 0, List.of(
                    new CardEffect(CardEffectKind.ARROW, 1),
                    new CardEffect(CardEffectKind.DAMAGE, 3, CardTarget.SINGLE_ENEMY),
                    new CardEffect(CardEffectKind.GLOWING, 1, CardTarget.SINGLE_ENEMY),
                    new CardEffect(CardEffectKind.EXHAUST, 1)), CardSourceType.UNKNOWN, "", artItemId, 0, 0, 1.0F, "default", "");
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
                new RegisteredCardDefinition("builtin_monster_claw", "card.moonspire.monster.claw.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.DAMAGE, 5, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.BLEED, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_rotten_guard", "card.moonspire.monster.rotten_guard.name", "", 1, 0, 4, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_undead_power", "card.moonspire.monster.undead_power.name", "", 3, 0, 0, List.of(
                        new CardEffect(CardEffectKind.STRENGTH, 2, CardTarget.SELF),
                        new CardEffect(CardEffectKind.EXHAUST, 1)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_poison_splash", "card.moonspire.monster.poison_splash.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.POISON, 4, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_weakness_splash", "card.moonspire.monster.weakness_splash.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.WEAKNESS, 2, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_slowness_splash", "card.moonspire.monster.slowness_splash.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.SLOWNESS, 2, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_harming_splash", "card.moonspire.monster.harming_splash.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.DAMAGE, 6, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_healing_draught", "card.moonspire.monster.healing_draught.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.HEAL, 7, CardTarget.SELF)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_swiftness_draught", "card.moonspire.monster.swiftness_draught.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.HASTE, 2, CardTarget.SELF)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_healing_splash", "card.moonspire.monster.healing_splash.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.HEAL, 7, CardTarget.SINGLE_ALLY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_fang_line", "card.moonspire.monster.fang_line.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.EVOKER_FANG_LINE, 6, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:evoker_spawn_egg", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_fang_circle", "card.moonspire.monster.fang_circle.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.EVOKER_FANG_CIRCLE, 5, CardTarget.ALL_ENEMIES)), CardSourceType.MONSTER, "", "minecraft:evoker_spawn_egg", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_summon_vex", "card.moonspire.monster.summon_vex.name", "", 3, 0, 0, List.of(
                        new CardEffect(CardEffectKind.SUMMON_VEX, 3, CardTarget.SELF, 3),
                        new CardEffect(CardEffectKind.EXHAUST, 1)), CardSourceType.MONSTER, "", "minecraft:vex_spawn_egg", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_totem_of_undying", "card.moonspire.monster.totem_of_undying.name", "", 3, 0, 0, List.of(
                        new CardEffect(CardEffectKind.UNDYING, 1, CardTarget.SELF),
                        new CardEffect(CardEffectKind.EXHAUST, 1)), CardSourceType.MONSTER, "", "minecraft:totem_of_undying", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_ritual_ward", "card.moonspire.monster.ritual_ward.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.BLOCK, 6, CardTarget.SELF),
                        new CardEffect(CardEffectKind.GUARD, 1, CardTarget.SELF)), CardSourceType.MONSTER, "", "minecraft:totem_of_undying", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_axe_chop", "card.moonspire.monster.axe_chop.name", "", 1, 6, 0, List.of(
                        new CardEffect(CardEffectKind.BLEED, 2, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:iron_axe", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_heavy_axe_blow", "card.moonspire.monster.heavy_axe_blow.name", "", 2, 10, 0, List.of(), CardSourceType.MONSTER, "", "minecraft:iron_axe", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_executioners_blow", "card.moonspire.monster.executioners_blow.name", "", 3, 14, 0, List.of(
                        new CardEffect(CardEffectKind.BLEED, 5, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:iron_axe", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_raised_axe_guard", "card.moonspire.monster.raised_axe_guard.name", "", 1, 0, 5, List.of(), CardSourceType.MONSTER, "", "minecraft:iron_axe", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_fanatic_might", "card.moonspire.monster.fanatic_might.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.STRENGTH, 1, CardTarget.SELF),
                        new CardEffect(CardEffectKind.EXHAUST, 1)), CardSourceType.MONSTER, "", "minecraft:iron_axe", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_lunge", "card.moonspire.monster.lunge.name", "", 2, 8, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_bow_strike", "card.moonspire.monster.bow_strike.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.DAMAGE, 4, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.HASTE, 1, CardTarget.SELF)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_sidestep", "card.moonspire.monster.sidestep.name", "", 1, 0, 3, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_shoot", "card.moonspire.monster.shoot.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.CONSUME_ARROW, 7, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:bow", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_poisoned_shot", "card.moonspire.monster.poisoned_shot.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.CONSUME_ARROW, 7, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.POISON, 3, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:bow", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_slowing_shot", "card.moonspire.monster.slowing_shot.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.CONSUME_ARROW, 7, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.SLOWNESS, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:bow", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_drop_the_hanging_blade", "card.moonspire.monster.drop_the_hanging_blade.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.CONSUME_ARROW, 10, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:crossbow", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_grazing_cut", "card.moonspire.monster.grazing_cut.name", "", 1, 3, 0, List.of(
                        new CardEffect(CardEffectKind.BLEED, 2, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_reload_cover", "card.moonspire.monster.reload_cover.name", "", 1, 0, 4, List.of(), CardSourceType.MONSTER, "", "minecraft:crossbow", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_hungry_lunge", "card.moonspire.monster.hungry_lunge.name", "", 2, 8, 0, List.of(
                        new CardEffect(CardEffectKind.HUNGER, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_goring_headbutt", "card.moonspire.monster.goring_headbutt.name", "", 1, 8, 0, List.of(
                        new CardEffect(CardEffectKind.WEAKNESS, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:ravager_spawn_egg", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_crushing_charge", "card.moonspire.monster.crushing_charge.name", "", 2, 13, 0, List.of(
                        new CardEffect(CardEffectKind.SLOWNESS, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:ravager_spawn_egg", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_trampling_pressure", "card.moonspire.monster.trampling_pressure.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.DAMAGE, 5, CardTarget.ALL_ENEMIES)), CardSourceType.MONSTER, "", "minecraft:ravager_spawn_egg", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_thick_hide", "card.moonspire.monster.thick_hide.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.BLOCK, 9, CardTarget.SELF),
                        new CardEffect(CardEffectKind.GUARD, 1, CardTarget.SELF),
                        new CardEffect(CardEffectKind.EXHAUST, 1)), CardSourceType.MONSTER, "", "minecraft:leather", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_terrifying_roar", "card.moonspire.monster.terrifying_roar.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.WEAKNESS, 2, CardTarget.ALL_ENEMIES),
                        new CardEffect(CardEffectKind.SLOWNESS, 1, CardTarget.ALL_ENEMIES)), CardSourceType.MONSTER, "", "minecraft:goat_horn", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_pounce", "card.moonspire.monster.pounce.name", "", 1, 5, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_skitter", "card.moonspire.monster.skitter.name", "", 1, 0, 3, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_bite", "card.moonspire.monster.bite.name", "", 2, 7, 0, List.of(), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_web", "card.moonspire.monster.web.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.SLOWNESS, 2, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.EXHAUST, 1)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_venom_fang", "card.moonspire.monster.venom_fang.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.DAMAGE, 3, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.POISON, 5, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_light_fuse", "card.moonspire.monster.light_fuse.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.INNATE, 1),
                        new CardEffect(CardEffectKind.FUSE, 2, CardTarget.SELF),
                        new CardEffect(CardEffectKind.BLOCK, 4, CardTarget.SELF),
                        new CardEffect(CardEffectKind.EXHAUST, 1)), CardSourceType.MONSTER, "", "minecraft:flint_and_steel", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_hissing_advance", "card.moonspire.monster.hissing_advance.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.DAMAGE, 4, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.HASTE, 1, CardTarget.SELF)), CardSourceType.MONSTER, "", "minecraft:gunpowder", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_powder_shell", "card.moonspire.monster.powder_shell.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.BLOCK, 7, CardTarget.SELF)), CardSourceType.MONSTER, "", "minecraft:gunpowder", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_raking_dive", "card.moonspire.monster.raking_dive.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.DAMAGE, 5, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.SLOWNESS, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_dragging_talons", "card.moonspire.monster.dragging_talons.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.DAMAGE, 7, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.SLOWNESS, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_wingbeat_guard", "card.moonspire.monster.wingbeat_guard.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.BLOCK, 3, CardTarget.SELF),
                        new CardEffect(CardEffectKind.HASTE, 1, CardTarget.SELF)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_moonlit_glide", "card.moonspire.monster.moonlit_glide.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.BLOCK, 5, CardTarget.SELF),
                        new CardEffect(CardEffectKind.HASTE, 1, CardTarget.SELF)), CardSourceType.MONSTER, "", "", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_razor_rush", "card.moonspire.monster.razor_rush.name", "", 1, 5, 0, List.of(), CardSourceType.MONSTER, "", "minecraft:iron_sword", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_flicker_cut", "card.moonspire.monster.flicker_cut.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.DAMAGE, 4, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.HASTE, 1, CardTarget.SELF)), CardSourceType.MONSTER, "", "minecraft:iron_sword", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_phase_stab", "card.moonspire.monster.phase_stab.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.DAMAGE, 4, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.SLOWNESS, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:iron_sword", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_evasive_flicker", "card.moonspire.monster.evasive_flicker.name", "", 1, 0, 4, List.of(
                        new CardEffect(CardEffectKind.HASTE, 1, CardTarget.SELF)), CardSourceType.MONSTER, "", "minecraft:iron_sword", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_frenzied_dive", "card.moonspire.monster.frenzied_dive.name", "", 2, 8, 0, List.of(
                        new CardEffect(CardEffectKind.BLEED, 2, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:iron_sword", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_trident_throw", "card.moonspire.monster.trident_throw.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.DAMAGE, 5, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.TIDAL_EROSION, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:trident", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_channeling_throw", "card.moonspire.monster.channeling_throw.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.DAMAGE, 6, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.TIDAL_EROSION, 1, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.PARALYSIS, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:trident", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_riptide_rush", "card.moonspire.monster.riptide_rush.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.DAMAGE, 8, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.TIDAL_EROSION, 2, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:trident", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_nautilus_shell", "card.moonspire.monster.nautilus_shell.name", "", 1, 0, 6, List.of(), CardSourceType.MONSTER, "", "minecraft:nautilus_shell", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_guardian_beam", "card.moonspire.monster.guardian_beam.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.DAMAGE, 5, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.TIDAL_EROSION, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:prismarine_crystals", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_tidal_gaze", "card.moonspire.monster.tidal_gaze.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.DAMAGE, 3, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.TIDAL_EROSION, 2, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:prismarine_crystals", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_spiked_carapace", "card.moonspire.monster.spiked_carapace.name", "", 1, 0, 5, List.of(
                        new CardEffect(CardEffectKind.THORNS, 2, CardTarget.SELF)), CardSourceType.MONSTER, "", "minecraft:prismarine_shard", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_deep_sea_reflux", "card.moonspire.monster.deep_sea_reflux.name", "", 2, 0, 7, List.of(
                        new CardEffect(CardEffectKind.TIDAL_EROSION, 1, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:dark_prismarine", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_elder_beam", "card.moonspire.monster.elder_beam.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.DAMAGE, 9, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.TIDAL_EROSION, 2, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:prismarine_crystals", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_elder_tidal_erosion", "card.moonspire.monster.elder_tidal_erosion.name", "", 1, 0, 0, List.of(
                        new CardEffect(CardEffectKind.REMOTE, 1),
                        new CardEffect(CardEffectKind.DAMAGE, 6, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.TIDAL_EROSION, 3, CardTarget.SINGLE_ENEMY)), CardSourceType.MONSTER, "", "minecraft:prismarine_crystals", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_elder_thorn_crown", "card.moonspire.monster.elder_thorn_crown.name", "", 1, 0, 8, List.of(
                        new CardEffect(CardEffectKind.THORNS, 3, CardTarget.SELF)), CardSourceType.MONSTER, "", "minecraft:prismarine_shard", 0, 0, 1.0F, "default", ""),
                new RegisteredCardDefinition("builtin_monster_deep_sea_pressure", "card.moonspire.monster.deep_sea_pressure.name", "", 2, 0, 0, List.of(
                        new CardEffect(CardEffectKind.TIDAL_EROSION, 5, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.SLOWNESS, 3, CardTarget.SINGLE_ENEMY),
                        new CardEffect(CardEffectKind.EXHAUST, 1)), CardSourceType.MONSTER, "", "minecraft:dark_prismarine", 0, 0, 1.0F, "default", ""),
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

    private static ItemStack potionStack(Item item, Holder<Potion> potion) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.POTION_CONTENTS, PotionContents.EMPTY.withPotion(potion));
        return stack;
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
