package com.yinfires.moonspire.battle;

public enum BattleEffectType {
    BLEED("bleed", "effect.moonspire.bleed.name", "effect.moonspire.bleed.description", "effect.moonspire.bleed.active_description", "gui/effects/bleeding.png"),
    GUARD("guard", "effect.moonspire.guard.name", "effect.moonspire.guard.description", "effect.moonspire.guard.active_description", "gui/effects/guard.png");

    private final String id;
    private final String nameKey;
    private final String descriptionKey;
    private final String activeDescriptionKey;
    private final String iconTexturePath;

    BattleEffectType(String id, String nameKey, String descriptionKey, String activeDescriptionKey, String iconTexturePath) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.activeDescriptionKey = activeDescriptionKey;
        this.iconTexturePath = iconTexturePath;
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
}
