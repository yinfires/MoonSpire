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
            return cards(
                    card("builtin_monster_claw"),
                    card("builtin_monster_rotten_guard"),
                    card("builtin_monster_lunge"),
                    card("builtin_monster_claw"),
                    card("builtin_monster_rotten_guard"));
        }
        if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED) {
            return cards(
                    card("builtin_monster_bone_shot"),
                    card("builtin_monster_sidestep"),
                    card("builtin_monster_aimed_volley"),
                    card("builtin_monster_bone_shot"),
                    card("builtin_monster_sidestep"));
        }
        if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) {
            return cards(
                    card("builtin_monster_pounce"),
                    card("builtin_monster_skitter"),
                    card("builtin_monster_bite"),
                    card("builtin_monster_pounce"),
                    card("builtin_monster_skitter"));
        }
        return fallback(monster);
    }

    public static List<String> defaultDeckCardIds(EntityType<?> type) {
        if (!hasDefaultDeck(type)) {
            return List.of();
        }
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
            return List.of("builtin_monster_claw", "builtin_monster_rotten_guard", "builtin_monster_lunge", "builtin_monster_claw", "builtin_monster_rotten_guard");
        }
        if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED) {
            return List.of("builtin_monster_bone_shot", "builtin_monster_sidestep", "builtin_monster_aimed_volley", "builtin_monster_bone_shot", "builtin_monster_sidestep");
        }
        if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) {
            return List.of("builtin_monster_pounce", "builtin_monster_skitter", "builtin_monster_bite", "builtin_monster_pounce", "builtin_monster_skitter");
        }
        return List.of("builtin_monster_strike", "builtin_monster_guard", "builtin_monster_heavy_strike", "builtin_monster_strike", "builtin_monster_guard");
    }

    private static List<CardInstance> fallback(LivingEntity monster) {
        int attack = Math.max(3, (int) Math.ceil(monster.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        int defense = Math.max(2, monster.getArmorValue());
        return cards(
                card("builtin_monster_strike", attack, 0, attack >= 8 ? 2 : 1),
                card("builtin_monster_guard", 0, defense, 1),
                card("builtin_monster_heavy_strike", attack + 3, 0, 2),
                card("builtin_monster_strike", attack, 0, attack >= 8 ? 2 : 1),
                card("builtin_monster_guard", 0, defense, 1));
    }

    private static CardInstance card(String id) {
        return MoonSpireCardRegistry.cardInstance(id).orElseThrow(() -> new IllegalStateException("Missing Moon Spire card: " + id));
    }

    private static CardInstance card(String id, int attack, int defense, int cost) {
        return MoonSpireCardRegistry.card(id)
                .map(definition -> definition.withCombatValues(attack, defense, cost).createInstance())
                .orElseThrow(() -> new IllegalStateException("Missing Moon Spire card: " + id));
    }

    private static List<CardInstance> cards(CardInstance first, CardInstance second, CardInstance third, CardInstance fourth, CardInstance fifth) {
        List<CardInstance> cards = new ArrayList<>();
        cards.add(first.copyForBattle());
        cards.add(second.copyForBattle());
        cards.add(third.copyForBattle());
        cards.add(fourth.copyForBattle());
        cards.add(fifth.copyForBattle());
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
