package com.yinfires.moonspire.card;

import com.yinfires.moonspire.developer.DeveloperDataManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record CardInstance(
        UUID id,
        String cardId,
        ItemStack sourceStack,
        String nameKey,
        String descriptionKey,
        int attack,
        int defense,
        int cost,
        List<CardEffect> effects,
        CardSourceType sourceType,
        String developerCardId,
        String artPath,
        String artItemId,
        int artX,
        int artY,
        float artScale,
        String faceId) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CardInstance> STREAM_CODEC = StreamCodec.of(
            CardInstance::writeToBuffer,
            CardInstance::readFromBuffer);

    public CardInstance {
        id = id == null ? UUID.randomUUID() : id;
        cardId = cardId == null ? "" : cardId;
        sourceStack = sourceStack == null ? ItemStack.EMPTY : sourceStack;
        nameKey = nameKey == null ? "" : nameKey;
        descriptionKey = descriptionKey == null ? "" : descriptionKey;
        attack = Math.max(0, attack);
        defense = Math.max(0, defense);
        cost = Math.max(0, cost);
        effects = normalizedEffects(attack, defense, effects);
        sourceType = sourceType == null ? CardSourceType.UNKNOWN : sourceType;
        developerCardId = developerCardId == null ? "" : developerCardId;
        artPath = artPath == null ? "" : artPath;
        artItemId = artItemId == null ? "" : artItemId;
        artScale = Math.max(0.05F, artScale);
        faceId = faceId == null || faceId.isBlank() ? "default" : faceId;
    }

    public CardInstance(
            UUID id,
            ItemStack sourceStack,
            String nameKey,
            String descriptionKey,
            int attack,
            int defense,
            int cost,
            List<CardEffect> effects,
            CardSourceType sourceType) {
        this(id, "", sourceStack, nameKey, descriptionKey, attack, defense, cost, effects, sourceType, "", "", "", 0, 0, 1.0F, "default");
    }

    public static CardInstance simpleMonsterCard(String name, int attack, int defense, int cost) {
        return new CardInstance(UUID.randomUUID(), "", ItemStack.EMPTY, name, "", attack, defense, cost, List.of(), CardSourceType.MONSTER, "", "", "", 0, 0, 1.0F, "default");
    }

    public String nameKey() {
        return currentDefinition().map(RegisteredCardDefinition::nameKey).orElse(nameKey);
    }

    public String descriptionKey() {
        return currentDefinition().map(RegisteredCardDefinition::descriptionKey).orElse(descriptionKey);
    }

    public int attack() {
        return currentDefinition().map(RegisteredCardDefinition::attack).orElse(attack);
    }

    public int defense() {
        return currentDefinition().map(RegisteredCardDefinition::defense).orElse(defense);
    }

    public int cost() {
        return currentDefinition().map(RegisteredCardDefinition::cost).orElse(cost);
    }

    public List<CardEffect> effects() {
        return currentDefinition()
                .map(definition -> normalizedEffects(definition.attack(), definition.defense(), definition.effects()))
                .orElse(effects);
    }

    public CardSourceType sourceType() {
        return currentDefinition().map(RegisteredCardDefinition::sourceType).orElse(sourceType);
    }

    public String developerCardId() {
        return currentDefinition().map(RegisteredCardDefinition::developerCardId).orElse(developerCardId);
    }

    public String artPath() {
        return currentDefinition().map(RegisteredCardDefinition::artPath).orElse(artPath);
    }

    public String artItemId() {
        return currentDefinition().map(RegisteredCardDefinition::artItemId).orElse(artItemId);
    }

    public int artX() {
        return currentDefinition().map(RegisteredCardDefinition::artX).orElse(artX);
    }

    public int artY() {
        return currentDefinition().map(RegisteredCardDefinition::artY).orElse(artY);
    }

    public float artScale() {
        return currentDefinition().map(RegisteredCardDefinition::artScale).orElse(artScale);
    }

    public String faceId() {
        return currentDefinition().map(RegisteredCardDefinition::faceId).orElse(faceId);
    }

    private Optional<RegisteredCardDefinition> currentDefinition() {
        String lookupId = !cardId.isBlank() ? cardId : developerCardId;
        if (lookupId.isBlank()) {
            return Optional.empty();
        }
        return MoonSpireCardRegistry.card(lookupId);
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        if (!cardId.isBlank()) {
            tag.putString("cardId", cardId);
        }
        if (!sourceStack.isEmpty()) {
            tag.put("sourceStack", sourceStack.save(provider));
        }
        tag.putString("nameKey", nameKey());
        tag.putString("descriptionKey", descriptionKey());
        tag.putInt("attack", attack());
        tag.putInt("defense", defense());
        tag.putInt("cost", cost());
        ListTag effectTags = new ListTag();
        for (CardEffect effect : effects()) {
            CompoundTag effectTag = new CompoundTag();
            effectTag.putString("type", effect.kind().name());
            effectTag.putInt("amount", effect.amount());
            effectTag.putString("target", effect.target().name());
            effectTag.putInt("count", effect.count());
            effectTags.add(effectTag);
        }
        tag.put("effects", effectTags);
        tag.putString("sourceType", sourceType().name());
        if (!developerCardId().isBlank()) {
            tag.putString("developerCardId", developerCardId());
        }
        if (!artPath().isBlank()) {
            tag.putString("artPath", artPath());
        }
        if (!artItemId().isBlank()) {
            tag.putString("artItemId", artItemId());
        }
        tag.putInt("artX", artX());
        tag.putInt("artY", artY());
        tag.putFloat("artScale", artScale());
        tag.putString("faceId", faceId());
        return tag;
    }

    public static CardInstance load(CompoundTag tag, HolderLookup.Provider provider) {
        ItemStack stack = ItemStack.EMPTY;
        if (tag.contains("sourceStack")) {
            stack = ItemStack.parseOptional(provider, tag.getCompound("sourceStack"));
        }
        CardSourceType sourceType = CardSourceType.byName(tag.getString("sourceType"));
        String nameKey = tag.contains("nameKey") ? tag.getString("nameKey") : tag.getString("name");
        String descriptionKey = tag.contains("descriptionKey") ? tag.getString("descriptionKey") : tag.getString("description");
        int attack = tag.getInt("attack");
        int defense = tag.getInt("defense");
        List<CardEffect> effects = new ArrayList<>();
        ListTag effectTags = tag.getList("effects", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < effectTags.size(); i++) {
            CompoundTag effectTag = effectTags.getCompound(i);
            CardEffectKind kind = CardEffectKind.byName(effectTag.getString("type"));
            effects.add(new CardEffect(
                    kind,
                    Math.max(0, effectTag.getInt("amount")),
                    CardTarget.byName(effectTag.getString("target"), kind.defaultTarget()),
                    effectTag.contains("count") ? effectTag.getInt("count") : 1));
        }
        return new CardInstance(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                normalizeCardId(tag.getString("cardId"), stack, sourceType, tag.getString("developerCardId"), nameKey),
                stack,
                normalizeNameKey(nameKey, stack, sourceType),
                normalizeDescriptionKey(descriptionKey, sourceType),
                attack,
                defense,
                Math.max(0, tag.getInt("cost")),
                effects,
                sourceType,
                tag.getString("developerCardId"),
                tag.getString("artPath"),
                tag.getString("artItemId"),
                tag.getInt("artX"),
                tag.getInt("artY"),
                tag.contains("artScale") ? Math.max(0.05F, tag.getFloat("artScale")) : 1.0F,
                tag.contains("faceId") ? tag.getString("faceId") : "default");
    }

    public CardInstance copyForBattle() {
        return new CardInstance(UUID.randomUUID(), cardId, sourceStack.copy(), nameKey(), descriptionKey(), attack(), defense(), cost(), List.copyOf(effects()), sourceType(), developerCardId(), artPath(), artItemId(), artX(), artY(), artScale(), faceId());
    }

    public Component nameComponent() {
        java.util.Optional<String> displayName = DeveloperDataManager.displayName(nameKey());
        if (displayName.isPresent()) {
            return Component.translatableWithFallback(nameKey(), displayName.get());
        }
        return Component.translatable(nameKey());
    }

    public Component descriptionComponent() {
        if (descriptionKey().isBlank()) {
            return Component.empty();
        }
        return Component.translatable(descriptionKey());
    }

    public int effectAmount(CardEffectKind kind) {
        return effects().stream()
                .filter(effect -> effect.kind() == kind)
                .mapToInt(effect -> effect.amount() * effect.count())
                .sum();
    }

    public int enemyEffectAmount(CardEffectKind kind) {
        return effects().stream()
                .filter(effect -> effect.kind() == kind && effect.target().targetsEnemy())
                .mapToInt(effect -> effect.amount() * effect.count())
                .sum();
    }

    public int selfEffectAmount(CardEffectKind kind) {
        return effects().stream()
                .filter(effect -> effect.kind() == kind && effect.target().targetsSelf())
                .mapToInt(effect -> effect.amount() * effect.count())
                .sum();
    }

    public boolean hasEnemyEffect(CardEffectKind kind) {
        return enemyEffectAmount(kind) > 0;
    }

    public boolean hasSelfEffect(CardEffectKind kind) {
        return selfEffectAmount(kind) > 0;
    }

    public boolean targetsEnemy() {
        return effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target().targetsEnemy());
    }

    public boolean targetsSelf() {
        return effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target().targetsSelf());
    }

    public boolean requiresExplicitTarget() {
        return effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target().requiresExplicitTarget());
    }

    public int targetCountRank() {
        return effects().stream()
                .filter(effect -> effect.amount() > 0)
                .filter(effect -> effect.kind().usesTarget())
                .mapToInt(effect -> effect.target().targetCountRank())
                .max()
                .orElse(0);
    }

    public boolean hasAnyEffect() {
        return effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind().makesCardPlayable());
    }

    public boolean hasEffect(CardEffectKind kind) {
        return effects().stream().anyMatch(effect -> effect.kind() == kind);
    }

    public boolean hasAttack() {
        return hasEnemyEffect(CardEffectKind.DAMAGE);
    }

    public boolean hasDefense() {
        return hasSelfEffect(CardEffectKind.BLOCK);
    }

    public boolean isAttackType() {
        return hasAttack();
    }

    private static void writeToBuffer(RegistryFriendlyByteBuf buf, CardInstance card) {
        buf.writeUUID(card.id);
        buf.writeUtf(card.cardId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, card.sourceStack);
        buf.writeUtf(card.nameKey());
        buf.writeUtf(card.descriptionKey());
        buf.writeVarInt(card.attack());
        buf.writeVarInt(card.defense());
        buf.writeVarInt(card.cost());
        List<CardEffect> currentEffects = card.effects();
        buf.writeVarInt(currentEffects.size());
        for (CardEffect effect : currentEffects) {
            CardEffect.STREAM_CODEC.encode(buf, effect);
        }
        buf.writeEnum(card.sourceType());
        buf.writeUtf(card.developerCardId());
        buf.writeUtf(card.artPath());
        buf.writeUtf(card.artItemId());
        buf.writeVarInt(card.artX());
        buf.writeVarInt(card.artY());
        buf.writeFloat(card.artScale());
        buf.writeUtf(card.faceId());
    }

    private static CardInstance readFromBuffer(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String cardId = buf.readUtf();
        ItemStack sourceStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        String nameKey = buf.readUtf();
        String descriptionKey = buf.readUtf();
        int attack = buf.readVarInt();
        int defense = buf.readVarInt();
        int cost = buf.readVarInt();
        int effectCount = Math.min(32, buf.readVarInt());
        List<CardEffect> effects = new ArrayList<>(effectCount);
        for (int i = 0; i < effectCount; i++) {
            effects.add(CardEffect.STREAM_CODEC.decode(buf));
        }
        return new CardInstance(
                id,
                cardId,
                sourceStack,
                nameKey,
                descriptionKey,
                attack,
                defense,
                cost,
                List.copyOf(effects),
                buf.readEnum(CardSourceType.class),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readUtf());
    }

    private static List<CardEffect> normalizedEffects(int attack, int defense, List<CardEffect> effects) {
        List<CardEffect> normalized = new ArrayList<>();
        if (effects != null) {
            for (CardEffect effect : effects) {
                if (effect != null && (effect.amount() > 0 || effect.kind().isKeyword())) {
                    normalized.add(effect);
                }
            }
        }
        if (attack > 0 && normalized.stream().noneMatch(effect -> effect.kind() == CardEffectKind.DAMAGE && effect.target().targetsEnemy())) {
            normalized.add(0, new CardEffect(CardEffectKind.DAMAGE, attack, CardTarget.SINGLE_ENEMY));
        }
        if (defense > 0 && normalized.stream().noneMatch(effect -> effect.kind() == CardEffectKind.BLOCK && effect.target().targetsSelf())) {
            normalized.add(new CardEffect(CardEffectKind.BLOCK, defense, CardTarget.SELF));
        }
        return List.copyOf(normalized);
    }

    private static String normalizeCardId(String cardId, ItemStack stack, CardSourceType sourceType, String developerCardId, String nameKey) {
        if (cardId != null && !cardId.isBlank()) {
            return cardId;
        }
        if (developerCardId != null && !developerCardId.isBlank()) {
            return MoonSpireCardRegistry.developerCardAlias(developerCardId);
        }
        if (!stack.isEmpty() && (sourceType == CardSourceType.WEAPON || sourceType == CardSourceType.ARMOR || sourceType == CardSourceType.TOOL || sourceType == CardSourceType.UNKNOWN)) {
            return MoonSpireCardRegistry.itemCardId(stack);
        }
        return switch (nameKey) {
            case "card.moonspire.monster.claw.name" -> "builtin_monster_claw";
            case "card.moonspire.monster.rotten_guard.name" -> "builtin_monster_rotten_guard";
            case "card.moonspire.monster.lunge.name" -> "builtin_monster_lunge";
            case "card.moonspire.monster.bone_shot.name" -> "builtin_monster_bone_shot";
            case "card.moonspire.monster.sidestep.name" -> "builtin_monster_sidestep";
            case "card.moonspire.monster.aimed_volley.name" -> "builtin_monster_aimed_volley";
            case "card.moonspire.monster.pounce.name" -> "builtin_monster_pounce";
            case "card.moonspire.monster.skitter.name" -> "builtin_monster_skitter";
            case "card.moonspire.monster.bite.name" -> "builtin_monster_bite";
            case "card.moonspire.monster.strike.name" -> "builtin_monster_strike";
            case "card.moonspire.monster.guard.name" -> "builtin_monster_guard";
            case "card.moonspire.monster.heavy_strike.name" -> "builtin_monster_heavy_strike";
            default -> "";
        };
    }

    private static String normalizeNameKey(String key, ItemStack stack, CardSourceType sourceType) {
        if (isTranslationKey(key)) {
            return key;
        }
        if (!stack.isEmpty()) {
            return stack.getDescriptionId();
        }
        if (sourceType == CardSourceType.MONSTER) {
            return "card.moonspire.monster.strike.name";
        }
        return "card.moonspire.unknown.name";
    }

    private static String normalizeDescriptionKey(String key, CardSourceType sourceType) {
        if (sourceType == CardSourceType.MONSTER && isDefaultMonsterDescriptionKey(key)) {
            return "";
        }
        if (isConvertedDescriptionKey(key)) {
            return "";
        }
        if (isTranslationKey(key)) {
            return key;
        }
        return "";
    }

    private static boolean isTranslationKey(String key) {
        return key != null && (key.startsWith("card.") || key.startsWith("item.") || key.startsWith("block."));
    }

    private static boolean isDefaultMonsterDescriptionKey(String key) {
        return key != null && key.startsWith("card.moonspire.monster.") && key.endsWith(".description");
    }

    private static boolean isConvertedDescriptionKey(String key) {
        return key != null && key.startsWith("card.moonspire.converted.") && key.endsWith(".description");
    }
}
