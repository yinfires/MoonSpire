package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import com.yinfires.moonspire.developer.DeveloperMonsterDefinition;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class MonsterDeckProfile {
    private static final List<String> ZOMBIE_DEFAULT_DECK = List.of(
            "builtin_monster_claw",
            "builtin_monster_rotten_guard",
            "builtin_monster_rotten_guard",
            "builtin_monster_lunge",
            "builtin_monster_lunge",
            "builtin_monster_claw",
            "builtin_monster_claw",
            "builtin_monster_claw",
            "builtin_monster_rotten_guard",
            "builtin_monster_undead_power");
    private static final List<String> HUSK_DEFAULT_DECK = List.of(
            "builtin_monster_claw",
            "builtin_monster_rotten_guard",
            "builtin_monster_rotten_guard",
            "builtin_monster_hungry_lunge",
            "builtin_monster_hungry_lunge",
            "builtin_monster_claw",
            "builtin_monster_claw",
            "builtin_monster_claw",
            "builtin_monster_rotten_guard",
            "builtin_monster_undead_power");
    private static final List<String> VINDICATOR_DEFAULT_DECK = List.of(
            "builtin_monster_axe_chop",
            "builtin_monster_axe_chop",
            "builtin_monster_axe_chop",
            "builtin_monster_heavy_axe_blow",
            "builtin_monster_heavy_axe_blow",
            "builtin_monster_executioners_blow",
            "builtin_monster_raised_axe_guard",
            "builtin_monster_raised_axe_guard",
            "builtin_monster_fanatic_might",
            "builtin_monster_fanatic_might");
    private static final List<String> SKELETON_DEFAULT_DECK = List.of(
            "builtin_monster_bow_strike",
            "builtin_monster_sidestep",
            "builtin_monster_shoot",
            "builtin_monster_shoot",
            "builtin_monster_shoot",
            "builtin_monster_shoot",
            "builtin_monster_bow_strike",
            "builtin_monster_bow_strike",
            "builtin_monster_sidestep",
            "builtin_monster_sidestep",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow");
    private static final List<String> STRAY_DEFAULT_DECK = List.of(
            "builtin_monster_bow_strike",
            "builtin_monster_sidestep",
            "builtin_monster_slowing_shot",
            "builtin_monster_slowing_shot",
            "builtin_monster_slowing_shot",
            "builtin_monster_slowing_shot",
            "builtin_monster_bow_strike",
            "builtin_monster_bow_strike",
            "builtin_monster_sidestep",
            "builtin_monster_sidestep",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow");
    private static final List<String> BOGGED_DEFAULT_DECK = List.of(
            "builtin_monster_bow_strike",
            "builtin_monster_sidestep",
            "builtin_monster_poisoned_shot",
            "builtin_monster_poisoned_shot",
            "builtin_monster_poisoned_shot",
            "builtin_monster_poisoned_shot",
            "builtin_monster_bow_strike",
            "builtin_monster_bow_strike",
            "builtin_monster_sidestep",
            "builtin_monster_sidestep",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow");
    private static final List<String> PILLAGER_DEFAULT_DECK = List.of(
            "builtin_monster_drop_the_hanging_blade",
            "builtin_monster_drop_the_hanging_blade",
            "builtin_monster_drop_the_hanging_blade",
            "builtin_monster_drop_the_hanging_blade",
            "builtin_monster_grazing_cut",
            "builtin_monster_grazing_cut",
            "builtin_monster_grazing_cut",
            "builtin_monster_reload_cover",
            "builtin_monster_reload_cover",
            "builtin_monster_reload_cover",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow");
    private static final List<String> SPIDER_DEFAULT_DECK = List.of(
            "builtin_monster_pounce",
            "builtin_monster_skitter",
            "builtin_monster_skitter",
            "builtin_monster_bite",
            "builtin_monster_bite",
            "builtin_monster_pounce",
            "builtin_monster_pounce",
            "builtin_monster_skitter",
            "builtin_monster_web",
            "builtin_monster_web");
    private static final List<String> CAVE_SPIDER_DEFAULT_DECK = List.of(
            "builtin_monster_pounce",
            "builtin_monster_skitter",
            "builtin_monster_skitter",
            "builtin_monster_pounce",
            "builtin_monster_pounce",
            "builtin_monster_skitter",
            "builtin_monster_web",
            "builtin_monster_web",
            "builtin_monster_venom_fang",
            "builtin_monster_venom_fang");
    private static final List<String> CREEPER_DEFAULT_DECK = List.of(
            "builtin_monster_light_fuse",
            "builtin_monster_hissing_advance",
            "builtin_monster_hissing_advance",
            "builtin_monster_hissing_advance",
            "builtin_monster_powder_shell",
            "builtin_monster_powder_shell",
            "builtin_monster_powder_shell");
    private static final List<String> DROWNED_DEFAULT_DECK = List.of(
            "builtin_monster_trident_throw",
            "builtin_monster_trident_throw",
            "builtin_monster_trident_throw",
            "builtin_monster_channeling_throw",
            "builtin_monster_riptide_rush",
            "builtin_monster_riptide_rush",
            "builtin_monster_nautilus_shell",
            "builtin_monster_nautilus_shell",
            "builtin_monster_nautilus_shell",
            "builtin_monster_nautilus_shell");
    private static final List<String> GUARDIAN_DEFAULT_DECK = List.of(
            "builtin_monster_guardian_beam",
            "builtin_monster_guardian_beam",
            "builtin_monster_guardian_beam",
            "builtin_monster_tidal_gaze",
            "builtin_monster_tidal_gaze",
            "builtin_monster_spiked_carapace",
            "builtin_monster_spiked_carapace",
            "builtin_monster_spiked_carapace",
            "builtin_monster_deep_sea_reflux",
            "builtin_monster_deep_sea_reflux");
    private static final List<String> ELDER_GUARDIAN_DEFAULT_DECK = List.of(
            "builtin_monster_elder_beam",
            "builtin_monster_elder_beam",
            "builtin_monster_elder_beam",
            "builtin_monster_elder_tidal_erosion",
            "builtin_monster_elder_tidal_erosion",
            "builtin_monster_elder_thorn_crown",
            "builtin_monster_elder_thorn_crown",
            "builtin_monster_elder_thorn_crown",
            "builtin_monster_deep_sea_pressure",
            "builtin_monster_deep_sea_pressure");
    private static final List<String> FALLBACK_DEFAULT_DECK = List.of(
            "builtin_monster_strike",
            "builtin_monster_guard",
            "builtin_monster_heavy_strike",
            "builtin_monster_strike",
            "builtin_monster_guard");

    private MonsterDeckProfile() {
    }

    public static List<CardInstance> createDeck(LivingEntity monster) {
        Optional<DeveloperMonsterDefinition> override = DeveloperDataManager.monsterOverride(monster);
        if (override.isPresent() && override.get().hasDeckOverride()) {
            List<CardInstance> overrideCards = DeveloperDataManager.cardsByIds(override.get().deckCardIds());
            if (!overrideCards.isEmpty() || override.get().deckCardIds().isEmpty() || !hasDefaultDeck(monster.getType())) {
                return copyAll(overrideCards);
            }
        }
        if (!hasDefaultDeck(monster.getType())) {
            return List.of();
        }
        return createDefaultDeck(monster);
    }

    public static List<String> rewardPoolCardIds(LivingEntity monster, List<CardInstance> battleStartDeck) {
        Optional<DeveloperMonsterDefinition> override = DeveloperDataManager.monsterOverride(monster);
        if (override.isPresent() && override.get().hasRewardOverride()) {
            return uniqueResolvableIds(override.get().rewardCardIds());
        }
        return uniqueCardIds(battleStartDeck);
    }

    public static boolean hasBattleDeck(LivingEntity monster) {
        Optional<DeveloperMonsterDefinition> override = DeveloperDataManager.monsterOverride(monster);
        if (override.isPresent() && override.get().hasDeckOverride()) {
            List<CardInstance> overrideCards = DeveloperDataManager.cardsByIds(override.get().deckCardIds());
            if (!overrideCards.isEmpty() || override.get().deckCardIds().isEmpty() || !hasDefaultDeck(monster.getType())) {
                return !overrideCards.isEmpty();
            }
        }
        return hasDefaultDeck(monster.getType());
    }

    public static boolean hasDefaultDeck(EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER;
    }

    public static List<CardInstance> createDefaultDeck(LivingEntity monster) {
        EntityType<?> type = monster.getType();
        if (type == EntityType.ELDER_GUARDIAN) {
            return cards(ELDER_GUARDIAN_DEFAULT_DECK);
        }
        if (type == EntityType.GUARDIAN) {
            return cards(GUARDIAN_DEFAULT_DECK);
        }
        if (type == EntityType.DROWNED) {
            return cards(DROWNED_DEFAULT_DECK);
        }
        if (type == EntityType.HUSK) {
            return cards(HUSK_DEFAULT_DECK);
        }
        if (type == EntityType.VINDICATOR) {
            return cards(VINDICATOR_DEFAULT_DECK);
        }
        if (isZombieFamily(type)) {
            return cards(ZOMBIE_DEFAULT_DECK);
        }
        if (type == EntityType.STRAY) {
            return cards(STRAY_DEFAULT_DECK);
        }
        if (type == EntityType.BOGGED) {
            return cards(BOGGED_DEFAULT_DECK);
        }
        if (isSkeletonFamily(type)) {
            return cards(SKELETON_DEFAULT_DECK);
        }
        if (type == EntityType.PILLAGER) {
            return cards(PILLAGER_DEFAULT_DECK);
        }
        if (type == EntityType.CAVE_SPIDER) {
            return cards(CAVE_SPIDER_DEFAULT_DECK);
        }
        if (type == EntityType.SPIDER) {
            return cards(SPIDER_DEFAULT_DECK);
        }
        if (type == EntityType.CREEPER) {
            return cards(CREEPER_DEFAULT_DECK);
        }
        return fallback(monster);
    }

    public static List<String> defaultDeckCardIds(EntityType<?> type) {
        if (!hasDefaultDeck(type)) {
            return List.of();
        }
        if (type == EntityType.ELDER_GUARDIAN) {
            return ELDER_GUARDIAN_DEFAULT_DECK;
        }
        if (type == EntityType.GUARDIAN) {
            return GUARDIAN_DEFAULT_DECK;
        }
        if (type == EntityType.DROWNED) {
            return DROWNED_DEFAULT_DECK;
        }
        if (type == EntityType.HUSK) {
            return HUSK_DEFAULT_DECK;
        }
        if (type == EntityType.VINDICATOR) {
            return VINDICATOR_DEFAULT_DECK;
        }
        if (isZombieFamily(type)) {
            return ZOMBIE_DEFAULT_DECK;
        }
        if (type == EntityType.STRAY) {
            return STRAY_DEFAULT_DECK;
        }
        if (type == EntityType.BOGGED) {
            return BOGGED_DEFAULT_DECK;
        }
        if (isSkeletonFamily(type)) {
            return SKELETON_DEFAULT_DECK;
        }
        if (type == EntityType.PILLAGER) {
            return PILLAGER_DEFAULT_DECK;
        }
        if (type == EntityType.CAVE_SPIDER) {
            return CAVE_SPIDER_DEFAULT_DECK;
        }
        if (type == EntityType.SPIDER) {
            return SPIDER_DEFAULT_DECK;
        }
        if (type == EntityType.CREEPER) {
            return CREEPER_DEFAULT_DECK;
        }
        return FALLBACK_DEFAULT_DECK;
    }

    public static boolean isSkeletonFamily(EntityType<?> type) {
        return type == EntityType.SKELETON
                || type == EntityType.STRAY
                || type == EntityType.BOGGED
                || type == EntityType.WITHER_SKELETON;
    }

    public static boolean hasAbundantArrowsByDefault(EntityType<?> type) {
        return isSkeletonFamily(type) || type == EntityType.PILLAGER;
    }

    public static int defaultBaseSpeed(LivingEntity entity) {
        if (entity == null) {
            return CardBalance.PLAYER_BASE_SPEED;
        }
        if (entity != null && entity.getType() == EntityType.CAVE_SPIDER) {
            return 9;
        }
        double movementSpeed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return Math.max(1, Math.round((float) (movementSpeed / CardBalance.NON_PLAYER_BASELINE_MOVEMENT_SPEED * CardBalance.PLAYER_BASE_SPEED)));
    }

    private static boolean isZombieFamily(EntityType<?> type) {
        return type == EntityType.ZOMBIE
                || type == EntityType.HUSK
                || type == EntityType.ZOMBIE_VILLAGER
                || type == EntityType.ZOMBIFIED_PIGLIN;
    }

    private static List<CardInstance> fallback(LivingEntity monster) {
        int attack = Math.max(3, (int) Math.ceil(monster.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        int defense = Math.max(2, monster.getArmorValue());
        return copyAll(List.of(
                card("builtin_monster_strike", attack, 0, attack >= 8 ? 2 : 1),
                card("builtin_monster_guard", 0, defense, 1),
                card("builtin_monster_heavy_strike", attack + 3, 0, 2),
                card("builtin_monster_strike", attack, 0, attack >= 8 ? 2 : 1),
                card("builtin_monster_guard", 0, defense, 1)));
    }

    private static CardInstance card(String id) {
        return MoonSpireCardRegistry.cardInstance(id).orElseThrow(() -> new IllegalStateException("Missing Moon Spire card: " + id));
    }

    private static CardInstance card(String id, int attack, int defense, int cost) {
        return MoonSpireCardRegistry.card(id)
                .map(definition -> definition.withCombatValues(attack, defense, cost).createInstance())
                .orElseThrow(() -> new IllegalStateException("Missing Moon Spire card: " + id));
    }

    private static List<CardInstance> cards(List<String> ids) {
        List<CardInstance> cards = new ArrayList<>();
        for (String id : ids) {
            cards.add(card(id).copyForBattle());
        }
        return cards;
    }

    private static List<CardInstance> copyAll(List<CardInstance> sourceCards) {
        List<CardInstance> cards = new ArrayList<>();
        for (CardInstance card : sourceCards) {
            cards.add(card.copyForBattle());
        }
        return cards;
    }

    private static List<String> uniqueCardIds(List<CardInstance> cards) {
        Set<String> unique = new LinkedHashSet<>();
        if (cards != null) {
            for (CardInstance card : cards) {
                if (card == null || card.cardId().isBlank()) {
                    continue;
                }
                String id = MoonSpireCardRegistry.registeredDeveloperId(card.cardId());
                if (!id.isBlank() && MoonSpireCardRegistry.card(id).isPresent()) {
                    unique.add(id);
                }
            }
        }
        return List.copyOf(unique);
    }

    private static List<String> uniqueResolvableIds(List<String> ids) {
        Set<String> unique = new LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) {
                String registeredId = MoonSpireCardRegistry.registeredDeveloperId(id);
                if (!registeredId.isBlank() && MoonSpireCardRegistry.card(registeredId).isPresent()) {
                    unique.add(registeredId);
                }
            }
        }
        return List.copyOf(unique);
    }
}
