package com.yinfires.moonspire.battle;

public enum BattleEffectType {
    BLEED("bleed", "effect.moonspire.bleed.name", "effect.moonspire.bleed.description", "effect.moonspire.bleed.active_description", "gui/effects/bleeding.png"),
    GLOWING("glowing", "effect.moonspire.glowing.name", "effect.moonspire.glowing.description", "effect.moonspire.glowing.active_description", "gui/effects/glowing.png"),
    GUARD("guard", "effect.moonspire.guard.name", "effect.moonspire.guard.description", "effect.moonspire.guard.active_description", "gui/effects/guard.png"),
    STRENGTH("strength", "effect.moonspire.strength.name", "effect.moonspire.strength.description", "effect.moonspire.strength.active_description", "gui/effects/strength.png", true),
    REGENERATION("regeneration", "effect.moonspire.regeneration.name", "effect.moonspire.regeneration.description", "effect.moonspire.regeneration.active_description", "gui/effects/regeneration.png"),
    HASTE("haste", "effect.moonspire.haste.name", "effect.moonspire.haste.description", "effect.moonspire.haste.active_description", "gui/effects/haste.png"),
    POISON("poison", "effect.moonspire.poison.name", "effect.moonspire.poison.description", "effect.moonspire.poison.active_description", "gui/effects/poison.png"),
    BURN("burn", "effect.moonspire.burn.name", "effect.moonspire.burn.description", "effect.moonspire.burn.active_description", "gui/effects/burn.png"),
    WITHER("wither", "effect.moonspire.wither.name", "effect.moonspire.wither.description", "effect.moonspire.wither.active_description", "gui/effects/wither.png"),
    FUSE("fuse", "effect.moonspire.fuse.name", "effect.moonspire.fuse.description", "effect.moonspire.fuse.active_description", "gui/effects/fuse.png"),
    TIDAL_EROSION("tidal_erosion", "effect.moonspire.tidal_erosion.name", "effect.moonspire.tidal_erosion.description", "effect.moonspire.tidal_erosion.active_description", "gui/effects/tidal_erosion.png"),
    PARALYSIS("paralysis", "effect.moonspire.paralysis.name", "effect.moonspire.paralysis.description", "effect.moonspire.paralysis.active_description", "gui/effects/paralysis.png"),
    HUNGER("hunger", "effect.moonspire.hunger.name", "effect.moonspire.hunger.description", "effect.moonspire.hunger.active_description", "gui/effects/hunger.png"),
    THORNS("thorns", "effect.moonspire.thorns.name", "effect.moonspire.thorns.description", "effect.moonspire.thorns.active_description", "gui/effects/thorns.png"),
    WEAKNESS("weakness", "effect.moonspire.weakness.name", "effect.moonspire.weakness.description", "effect.moonspire.weakness.active_description", "gui/effects/weakness.png"),
    SLOWNESS("slowness", "effect.moonspire.slowness.name", "effect.moonspire.slowness.description", "effect.moonspire.slowness.active_description", "gui/effects/slowness.png"),
    ABUNDANT_ARROWS("abundant_arrows", "effect.moonspire.abundant_arrows.name", "effect.moonspire.abundant_arrows.description", "effect.moonspire.abundant_arrows.active_description", "gui/effects/abundant_arrows.png");

    private final String id;
    private final String nameKey;
    private final String descriptionKey;
    private final String activeDescriptionKey;
    private final String iconTexturePath;
    private final boolean allowsNegativeStacks;

    BattleEffectType(String id, String nameKey, String descriptionKey, String activeDescriptionKey, String iconTexturePath) {
        this(id, nameKey, descriptionKey, activeDescriptionKey, iconTexturePath, false);
    }

    BattleEffectType(String id, String nameKey, String descriptionKey, String activeDescriptionKey, String iconTexturePath, boolean allowsNegativeStacks) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.activeDescriptionKey = activeDescriptionKey;
        this.iconTexturePath = iconTexturePath;
        this.allowsNegativeStacks = allowsNegativeStacks;
    }

    public String id() {
        return id;
    }

    public String nameKey() {
        return nameKey;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public String activeDescriptionKey() {
        return activeDescriptionKey;
    }

    public String iconTexturePath() {
        return iconTexturePath;
    }

    public boolean allowsNegativeStacks() {
        return allowsNegativeStacks;
    }
}
