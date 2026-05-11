package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class MonsterDeckProfile {
    private static final List<String> ZOMBIE_DEFAULT_DECK = List.of(
            "builtin_monster_claw",
            "builtin_monster_rotten_guard",
            "builtin_monster_lunge",
            "builtin_monster_claw",
            "builtin_monster_rotten_guard");
    private static final List<String> SKELETON_DEFAULT_DECK = List.of(
            "builtin_monster_bone_shot",
            "builtin_monster_sidestep",
            "builtin_monster_aimed_volley",
            "builtin_monster_bone_shot",
            "builtin_monster_sidestep");
    private static final List<String> SPIDER_DEFAULT_DECK = List.of(
            "builtin_monster_pounce",
            "builtin_monster_skitter",
            "builtin_monster_bite",
            "builtin_monster_pounce",
            "builtin_monster_skitter");
    private static final List<String> FALLBACK_DEFAULT_DECK = List.of(
            "builtin_monster_strike",
            "builtin_monster_guard",
            "builtin_monster_heavy_strike",
            "builtin_monster_strike",
            "builtin_monster_guard");

    private MonsterDeckProfile() {
    }

    public static List<CardInstance> createDeck(LivingEntity monster) {
        Optional<List<CardInstance>> overrideCards = DeveloperDataManager.monsterOverride(monster)
                .filter(definition -> definition.hasDeckOverride())
                .map(definition -> DeveloperDataManager.cardsByIds(definition.deckCardIds()));
        if (overrideCards.isPresent()) {
            return copyAll(overrideCards.get());
        }
        if (!hasDefaultDeck(monster.getType())) {
            return List.of();
        }
        return createDefaultDeck(monster);
    }

    public static boolean hasBattleDeck(LivingEntity monster) {
        return DeveloperDataManager.monsterOverride(monster)
                .filter(definition -> definition.hasDeckOverride())
                .map(definition -> !DeveloperDataManager.cardsByIds(definition.deckCardIds()).isEmpty())
                .orElseGet(() -> hasDefaultDeck(monster.getType()));
    }

    public static boolean hasDefaultDeck(EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER;
    }

    public static List<CardInstance> createDefaultDeck(LivingEntity monster) {
        EntityType<?> type = monster.getType();
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
            return cards(ZOMBIE_DEFAULT_DECK);
        }
        if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED) {
            return cards(SKELETON_DEFAULT_DECK);
        }
        if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) {
            return cards(SPIDER_DEFAULT_DECK);
        }
        return fallback(monster);
    }

    public static List<String> defaultDeckCardIds(EntityType<?> type) {
        if (!hasDefaultDeck(type)) {
            return List.of();
        }
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
            return ZOMBIE_DEFAULT_DECK;
        }
        if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED) {
            return SKELETON_DEFAULT_DECK;
        }
        if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) {
            return SPIDER_DEFAULT_DECK;
        }
        return FALLBACK_DEFAULT_DECK;
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
}
