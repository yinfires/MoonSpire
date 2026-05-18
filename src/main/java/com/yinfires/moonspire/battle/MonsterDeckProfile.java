package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import com.yinfires.moonspire.developer.DeveloperMonsterDefinition;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;

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
    private static final List<String> WITCH_DEFAULT_DECK = List.of(
            "builtin_monster_poison_splash",
            "builtin_monster_poison_splash",
            "builtin_monster_weakness_splash",
            "builtin_monster_slowness_splash",
            "builtin_monster_harming_splash",
            "builtin_monster_harming_splash",
            "builtin_monster_healing_draught",
            "builtin_monster_healing_draught",
            "builtin_monster_swiftness_draught",
            "builtin_monster_healing_splash");
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
    private static final List<String> WITHER_SKELETON_DEFAULT_DECK = List.of(
            "builtin_monster_wither_blade",
            "builtin_monster_wither_blade",
            "builtin_monster_wither_blade",
            "builtin_monster_charred_guard",
            "builtin_monster_charred_guard",
            "builtin_monster_soul_cleave",
            "builtin_monster_soul_cleave",
            "builtin_monster_black_fortress_stance",
            "builtin_monster_bone_rend",
            "builtin_monster_bone_rend");
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
    private static final List<String> PIGLIN_DEFAULT_DECK = List.of(
            "builtin_monster_piglin_bolt",
            "builtin_monster_piglin_bolt",
            "builtin_monster_piglin_bolt",
            "builtin_monster_piglin_bolt",
            "builtin_monster_gilded_cut",
            "builtin_monster_gilded_cut",
            "builtin_monster_gilded_cut",
            "builtin_monster_gold_guard",
            "builtin_monster_gold_guard",
            "builtin_monster_gold_guard",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow",
            "item_minecraft_arrow");
    private static final List<String> ZOMBIFIED_PIGLIN_DEFAULT_DECK = List.of(
            "builtin_monster_vengeful_gold_cut",
            "builtin_monster_vengeful_gold_cut",
            "builtin_monster_vengeful_gold_cut",
            "builtin_monster_rotten_gold_guard",
            "builtin_monster_rotten_gold_guard",
            "builtin_monster_zombified_lunge",
            "builtin_monster_zombified_lunge",
            "builtin_monster_cursed_gold_stance",
            "builtin_monster_restless_revenge",
            "builtin_monster_restless_revenge");
    private static final List<String> PIGLIN_BRUTE_DEFAULT_DECK = List.of(
            "builtin_monster_brute_chop",
            "builtin_monster_brute_chop",
            "builtin_monster_brute_chop",
            "builtin_monster_brute_cleave",
            "builtin_monster_brute_cleave",
            "builtin_monster_brute_pressure",
            "builtin_monster_brute_pressure",
            "builtin_monster_brute_gold_plate",
            "builtin_monster_brute_gold_plate",
            "builtin_monster_brute_fury");
    private static final List<String> HOGLIN_DEFAULT_DECK = List.of(
            "builtin_monster_hoglin_gore",
            "builtin_monster_hoglin_gore",
            "builtin_monster_hoglin_gore",
            "builtin_monster_crimson_headbutt",
            "builtin_monster_crimson_headbutt",
            "builtin_monster_tusks_up",
            "builtin_monster_tusks_up",
            "builtin_monster_crimson_hide",
            "builtin_monster_crimson_hide",
            "builtin_monster_herd_fury");
    private static final List<String> ZOGLIN_DEFAULT_DECK = List.of(
            "builtin_monster_zoglin_gore",
            "builtin_monster_zoglin_gore",
            "builtin_monster_zoglin_gore",
            "builtin_monster_rotten_headbutt",
            "builtin_monster_rotten_headbutt",
            "builtin_monster_maddened_charge",
            "builtin_monster_maddened_charge",
            "builtin_monster_dead_hide",
            "builtin_monster_dead_hide",
            "builtin_monster_rotting_trample");
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
    private static final List<String> SILVERFISH_DEFAULT_DECK = List.of(
            "builtin_monster_nipping_bite",
            "builtin_monster_nipping_bite",
            "builtin_monster_nipping_bite",
            "builtin_monster_crackling_mandibles",
            "builtin_monster_crackling_mandibles",
            "builtin_monster_stone_scuttle",
            "builtin_monster_stone_scuttle",
            "builtin_monster_swarm_alarm",
            "builtin_monster_swarm_alarm",
            "builtin_monster_infested_call");
    private static final List<String> CREEPER_DEFAULT_DECK = List.of(
            "builtin_monster_light_fuse",
            "builtin_monster_hissing_advance",
            "builtin_monster_hissing_advance",
            "builtin_monster_hissing_advance",
            "builtin_monster_powder_shell",
            "builtin_monster_powder_shell",
            "builtin_monster_powder_shell");
    private static final List<String> PHANTOM_DEFAULT_DECK = List.of(
            "builtin_monster_raking_dive",
            "builtin_monster_wingbeat_guard",
            "builtin_monster_raking_dive",
            "builtin_monster_moonlit_glide",
            "builtin_monster_dragging_talons",
            "builtin_monster_wingbeat_guard",
            "builtin_monster_raking_dive",
            "builtin_monster_dragging_talons",
            "builtin_monster_wingbeat_guard",
            "builtin_monster_moonlit_glide");
    private static final List<String> VEX_DEFAULT_DECK = List.of(
            "builtin_monster_razor_rush",
            "builtin_monster_razor_rush",
            "builtin_monster_razor_rush",
            "builtin_monster_flicker_cut",
            "builtin_monster_flicker_cut",
            "builtin_monster_phase_stab",
            "builtin_monster_phase_stab",
            "builtin_monster_evasive_flicker",
            "builtin_monster_evasive_flicker",
            "builtin_monster_frenzied_dive");
    private static final List<String> EVOKER_DEFAULT_DECK = List.of(
            "builtin_monster_fang_line",
            "builtin_monster_fang_line",
            "builtin_monster_fang_line",
            "builtin_monster_fang_circle",
            "builtin_monster_fang_circle",
            "builtin_monster_summon_vex",
            "builtin_monster_summon_vex",
            "builtin_monster_totem_of_undying",
            "builtin_monster_ritual_ward",
            "builtin_monster_ritual_ward");
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
    private static final List<String> RAVAGER_DEFAULT_DECK = List.of(
            "builtin_monster_goring_headbutt",
            "builtin_monster_goring_headbutt",
            "builtin_monster_goring_headbutt",
            "builtin_monster_crushing_charge",
            "builtin_monster_crushing_charge",
            "builtin_monster_trampling_pressure",
            "builtin_monster_thick_hide",
            "builtin_monster_thick_hide",
            "builtin_monster_thick_hide",
            "builtin_monster_terrifying_roar");
    private static final List<String> SLIME_DEFAULT_DECK = List.of(
            "builtin_monster_slime_bump",
            "builtin_monster_slime_bump",
            "builtin_monster_slime_bump",
            "builtin_monster_sticky_slap",
            "builtin_monster_sticky_slap",
            "builtin_monster_viscous_snare",
            "builtin_monster_viscous_snare",
            "builtin_monster_gelatinous_body",
            "builtin_monster_gelatinous_body",
            "builtin_monster_splattering_pressure");
    private static final List<String> MAGMA_CUBE_DEFAULT_DECK = List.of(
            "builtin_monster_magma_bump",
            "builtin_monster_magma_bump",
            "builtin_monster_magma_bump",
            "builtin_monster_scorching_slap",
            "builtin_monster_scorching_slap",
            "builtin_monster_cinder_cling",
            "builtin_monster_cinder_cling",
            "builtin_monster_igneous_body",
            "builtin_monster_igneous_body",
            "builtin_monster_eruptive_pressure");
    private static final List<String> BLAZE_DEFAULT_DECK = List.of(
            "builtin_monster_blaze_fireball",
            "builtin_monster_blaze_fireball",
            "builtin_monster_blaze_fireball",
            "builtin_monster_blazing_barrage",
            "builtin_monster_blazing_barrage",
            "builtin_monster_smoldering_guard",
            "builtin_monster_smoldering_guard",
            "builtin_monster_heat_haze",
            "builtin_monster_heat_haze",
            "builtin_monster_flame_pressure");
    private static final List<String> GHAST_DEFAULT_DECK = List.of(
            "builtin_monster_ghast_fireball",
            "builtin_monster_ghast_fireball",
            "builtin_monster_ghast_fireball",
            "builtin_monster_explosive_wail",
            "builtin_monster_explosive_wail",
            "builtin_monster_sulfur_drift",
            "builtin_monster_sulfur_drift",
            "builtin_monster_tearful_ward",
            "builtin_monster_tearful_ward",
            "builtin_monster_infernal_shriek");
    private static final List<String> ENDERMAN_DEFAULT_DECK = List.of(
            "builtin_monster_ender_stare",
            "builtin_monster_ender_stare",
            "builtin_monster_blink_step",
            "builtin_monster_blink_step",
            "builtin_monster_void_claw",
            "builtin_monster_void_claw",
            "builtin_monster_void_claw",
            "builtin_monster_pearl_shift",
            "builtin_monster_rending_gaze",
            "builtin_monster_rending_gaze");
    private static final List<String> BREEZE_DEFAULT_DECK = List.of(
            "builtin_monster_wind_charge",
            "builtin_monster_wind_charge",
            "builtin_monster_wind_charge",
            "builtin_monster_gale_burst",
            "builtin_monster_gale_burst",
            "builtin_monster_sweeping_gust",
            "builtin_monster_whirling_guard",
            "builtin_monster_whirling_guard",
            "builtin_monster_unsteady_air",
            "builtin_monster_unsteady_air");
    private static final float SLIME_LARGE_BASE_HEALTH = 16.0F;
    private static final float SPLIT_CHILD_VALUE_MULTIPLIER = 0.5F;
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
        if (monster != null && monster.getType() == EntityType.SLIME && (override.isEmpty() || !override.get().hasDeckOverride())) {
            return uniqueResolvableIds(SLIME_DEFAULT_DECK);
        }
        if (monster != null && monster.getType() == EntityType.MAGMA_CUBE && (override.isEmpty() || !override.get().hasDeckOverride())) {
            return uniqueResolvableIds(MAGMA_CUBE_DEFAULT_DECK);
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
        if (type == EntityType.RAVAGER) {
            return cards(RAVAGER_DEFAULT_DECK);
        }
        if (type == EntityType.SLIME) {
            return slimeCards(monster);
        }
        if (type == EntityType.MAGMA_CUBE) {
            return magmaCubeCards(monster);
        }
        if (type == EntityType.BLAZE) {
            return cards(BLAZE_DEFAULT_DECK);
        }
        if (type == EntityType.GHAST) {
            return cards(GHAST_DEFAULT_DECK);
        }
        if (type == EntityType.ENDERMAN) {
            return cards(ENDERMAN_DEFAULT_DECK);
        }
        if (type == EntityType.BREEZE) {
            return cards(BREEZE_DEFAULT_DECK);
        }
        if (type == EntityType.GUARDIAN) {
            return cards(GUARDIAN_DEFAULT_DECK);
        }
        if (type == EntityType.DROWNED) {
            return cards(DROWNED_DEFAULT_DECK);
        }
        if (type == EntityType.WITCH) {
            return cards(WITCH_DEFAULT_DECK);
        }
        if (type == EntityType.HUSK) {
            return cards(HUSK_DEFAULT_DECK);
        }
        if (type == EntityType.VINDICATOR) {
            return cards(VINDICATOR_DEFAULT_DECK);
        }
        if (type == EntityType.ZOMBIFIED_PIGLIN) {
            return cards(ZOMBIFIED_PIGLIN_DEFAULT_DECK);
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
        if (type == EntityType.WITHER_SKELETON) {
            return cards(WITHER_SKELETON_DEFAULT_DECK);
        }
        if (isSkeletonFamily(type)) {
            return cards(SKELETON_DEFAULT_DECK);
        }
        if (type == EntityType.PILLAGER) {
            return cards(PILLAGER_DEFAULT_DECK);
        }
        if (type == EntityType.PIGLIN) {
            return cards(PIGLIN_DEFAULT_DECK);
        }
        if (type == EntityType.PIGLIN_BRUTE) {
            return cards(PIGLIN_BRUTE_DEFAULT_DECK);
        }
        if (type == EntityType.HOGLIN) {
            return cards(HOGLIN_DEFAULT_DECK);
        }
        if (type == EntityType.ZOGLIN) {
            return cards(ZOGLIN_DEFAULT_DECK);
        }
        if (type == EntityType.CAVE_SPIDER) {
            return cards(CAVE_SPIDER_DEFAULT_DECK);
        }
        if (type == EntityType.SPIDER) {
            return cards(SPIDER_DEFAULT_DECK);
        }
        if (type == EntityType.SILVERFISH) {
            return cards(SILVERFISH_DEFAULT_DECK);
        }
        if (type == EntityType.CREEPER) {
            return cards(CREEPER_DEFAULT_DECK);
        }
        if (type == EntityType.PHANTOM) {
            return cards(PHANTOM_DEFAULT_DECK);
        }
        if (type == EntityType.VEX) {
            return cards(VEX_DEFAULT_DECK);
        }
        if (type == EntityType.EVOKER) {
            return cards(EVOKER_DEFAULT_DECK);
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
        if (type == EntityType.RAVAGER) {
            return RAVAGER_DEFAULT_DECK;
        }
        if (type == EntityType.SLIME) {
            return SLIME_DEFAULT_DECK;
        }
        if (type == EntityType.MAGMA_CUBE) {
            return MAGMA_CUBE_DEFAULT_DECK;
        }
        if (type == EntityType.BLAZE) {
            return BLAZE_DEFAULT_DECK;
        }
        if (type == EntityType.GHAST) {
            return GHAST_DEFAULT_DECK;
        }
        if (type == EntityType.ENDERMAN) {
            return ENDERMAN_DEFAULT_DECK;
        }
        if (type == EntityType.BREEZE) {
            return BREEZE_DEFAULT_DECK;
        }
        if (type == EntityType.GUARDIAN) {
            return GUARDIAN_DEFAULT_DECK;
        }
        if (type == EntityType.DROWNED) {
            return DROWNED_DEFAULT_DECK;
        }
        if (type == EntityType.WITCH) {
            return WITCH_DEFAULT_DECK;
        }
        if (type == EntityType.HUSK) {
            return HUSK_DEFAULT_DECK;
        }
        if (type == EntityType.VINDICATOR) {
            return VINDICATOR_DEFAULT_DECK;
        }
        if (type == EntityType.ZOMBIFIED_PIGLIN) {
            return ZOMBIFIED_PIGLIN_DEFAULT_DECK;
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
        if (type == EntityType.WITHER_SKELETON) {
            return WITHER_SKELETON_DEFAULT_DECK;
        }
        if (isSkeletonFamily(type)) {
            return SKELETON_DEFAULT_DECK;
        }
        if (type == EntityType.PILLAGER) {
            return PILLAGER_DEFAULT_DECK;
        }
        if (type == EntityType.PIGLIN) {
            return PIGLIN_DEFAULT_DECK;
        }
        if (type == EntityType.PIGLIN_BRUTE) {
            return PIGLIN_BRUTE_DEFAULT_DECK;
        }
        if (type == EntityType.HOGLIN) {
            return HOGLIN_DEFAULT_DECK;
        }
        if (type == EntityType.ZOGLIN) {
            return ZOGLIN_DEFAULT_DECK;
        }
        if (type == EntityType.CAVE_SPIDER) {
            return CAVE_SPIDER_DEFAULT_DECK;
        }
        if (type == EntityType.SPIDER) {
            return SPIDER_DEFAULT_DECK;
        }
        if (type == EntityType.SILVERFISH) {
            return SILVERFISH_DEFAULT_DECK;
        }
        if (type == EntityType.CREEPER) {
            return CREEPER_DEFAULT_DECK;
        }
        if (type == EntityType.PHANTOM) {
            return PHANTOM_DEFAULT_DECK;
        }
        if (type == EntityType.VEX) {
            return VEX_DEFAULT_DECK;
        }
        if (type == EntityType.EVOKER) {
            return EVOKER_DEFAULT_DECK;
        }
        return FALLBACK_DEFAULT_DECK;
    }

    public static boolean isSkeletonFamily(EntityType<?> type) {
        return type == EntityType.SKELETON
                || type == EntityType.STRAY
                || type == EntityType.BOGGED;
    }

    public static boolean hasAbundantArrowsByDefault(EntityType<?> type) {
        return isSkeletonFamily(type) || type == EntityType.PILLAGER || type == EntityType.PIGLIN;
    }

    public static int defaultSlimeSplitStacks(LivingEntity entity) {
        if (entity instanceof Slime slime && isSlimeFamily(entity.getType())) {
            int size = slime.getSize();
            if (size >= 4) {
                return 2;
            }
            if (size >= 2) {
                return 1;
            }
        }
        return 0;
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

    public static float defaultMaxBattleHealth(LivingEntity entity) {
        if (entity == null) {
            return 1.0F;
        }
        if (entity.getType() == EntityType.EVOKER) {
            return 60.0F;
        }
        if (entity instanceof Slime slime && isSlimeFamily(entity.getType())) {
            int reductions = slimeWeakeningSteps(slime);
            if (reductions > 0) {
                return weakenedPositiveFloat(SLIME_LARGE_BASE_HEALTH, reductions);
            }
        }
        return Math.max(1.0F, entity.getMaxHealth());
    }

    private static boolean isZombieFamily(EntityType<?> type) {
        return type == EntityType.ZOMBIE
                || type == EntityType.HUSK
                || type == EntityType.ZOMBIE_VILLAGER;
    }

    private static boolean isSlimeFamily(EntityType<?> type) {
        return type == EntityType.SLIME || type == EntityType.MAGMA_CUBE;
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

    private static List<CardInstance> slimeCards(LivingEntity monster) {
        return sizedSlimeFamilyCards(SLIME_DEFAULT_DECK, monster, "dynamic_slime_weakened_");
    }

    private static List<CardInstance> magmaCubeCards(LivingEntity monster) {
        return sizedSlimeFamilyCards(MAGMA_CUBE_DEFAULT_DECK, monster, "dynamic_magma_cube_weakened_");
    }

    private static List<CardInstance> sizedSlimeFamilyCards(List<String> defaultDeck, LivingEntity monster, String weakenedIdPrefix) {
        List<CardInstance> cards = cards(defaultDeck);
        if (monster instanceof Slime slime) {
            int reductions = slimeWeakeningSteps(slime);
            for (int i = 0; i < reductions; i++) {
                cards = weakenedCards(cards, weakenedIdPrefix);
            }
        }
        return cards;
    }

    private static int slimeWeakeningSteps(Slime slime) {
        int size = slime.getSize();
        if (size >= 4) {
            return 0;
        }
        if (size >= 2) {
            return 1;
        }
        return 2;
    }

    private static List<CardInstance> weakenedCards(List<CardInstance> sourceCards, String weakenedIdPrefix) {
        List<CardInstance> cards = new ArrayList<>();
        for (CardInstance card : sourceCards) {
            cards.add(weakenedCard(card, weakenedIdPrefix));
        }
        return cards;
    }

    private static CardInstance weakenedCard(CardInstance card, String weakenedIdPrefix) {
        List<CardEffect> effects = new ArrayList<>();
        for (CardEffect effect : card.effects()) {
            int amount = effect.kind().usesAmount() ? weakenedNonZero(effect.amount()) : effect.amount();
            effects.add(new CardEffect(effect.kind(), amount, effect.target(), effect.count(), effect.entityTypeId()));
        }
        return new CardInstance(
                UUID.randomUUID(),
                weakenedCardId(card, weakenedIdPrefix),
                card.sourceStack().copy(),
                card.nameKey(),
                card.descriptionKey(),
                weakenedNonZero(card.attack()),
                weakenedNonZero(card.defense()),
                card.baseCost(),
                card.battleCostReduction(),
                effects,
                card.sourceType(),
                "",
                card.artPath(),
                card.artItemId(),
                card.artX(),
                card.artY(),
                card.artScale(),
                card.faceId());
    }

    private static String weakenedCardId(CardInstance card, String prefix) {
        String sourceId = card == null ? "" : card.cardId();
        if (sourceId == null || sourceId.isBlank()) {
            sourceId = "card";
        }
        return prefix + dynamicCardIdSuffix(sourceId);
    }

    private static String dynamicCardIdSuffix(String sourceId) {
        String normalized = MoonSpireCardRegistry.normalizeId(sourceId);
        if (normalized.isBlank()) {
            return "card";
        }
        return normalized.replace(':', '_');
    }

    private static int weakenedNonZero(int value) {
        return value <= 0 ? 0 : Math.max(1, (int) Math.ceil(value * SPLIT_CHILD_VALUE_MULTIPLIER));
    }

    private static float weakenedPositiveFloat(float value, int reductions) {
        float result = Math.max(1.0F, value);
        for (int i = 0; i < reductions; i++) {
            result = Math.max(1.0F, (float) Math.ceil(result * SPLIT_CHILD_VALUE_MULTIPLIER));
        }
        return result;
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
