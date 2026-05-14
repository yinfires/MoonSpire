package com.yinfires.moonspire.card;

import com.yinfires.moonspire.developer.DeveloperDataManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
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
        int battleCostReduction,
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
        battleCostReduction = Math.max(0, battleCostReduction);
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
        this(id, "", sourceStack, nameKey, descriptionKey, attack, defense, cost, 0, effects, sourceType, "", "", "", 0, 0, 1.0F, "default");
    }

    public static CardInstance simpleMonsterCard(String name, int attack, int defense, int cost) {
        return new CardInstance(UUID.randomUUID(), "", ItemStack.EMPTY, name, "", attack, defense, cost, 0, List.of(), CardSourceType.MONSTER, "", "", "", 0, 0, 1.0F, "default");
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
        return Math.max(0, baseCost() - battleCostReduction);
    }

    public int baseCost() {
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

    public long renderStateHash() {
        long hash = 0xcbf29ce484222325L;
        hash = appendHash(hash, cardId);
        hash = appendHash(hash, nameKey);
        hash = appendHash(hash, descriptionKey);
        hash = appendHash(hash, attack);
        hash = appendHash(hash, defense);
        hash = appendHash(hash, cost);
        hash = appendHash(hash, battleCostReduction);
        hash = appendHash(hash, sourceType.name());
        hash = appendHash(hash, developerCardId);
        hash = appendHash(hash, artPath);
        hash = appendHash(hash, artItemId);
        hash = appendHash(hash, artX);
        hash = appendHash(hash, artY);
        hash = appendHash(hash, Float.floatToIntBits(artScale));
        hash = appendHash(hash, faceId);
        if (!sourceStack.isEmpty()) {
            hash = appendHash(hash, BuiltInRegistries.ITEM.getKey(sourceStack.getItem()).toString());
        }
        for (CardEffect effect : effects) {
            hash = appendHash(hash, effect.kind().name());
            hash = appendHash(hash, effect.amount());
            hash = appendHash(hash, effect.target().name());
            hash = appendHash(hash, effect.count());
        }
        return hash;
    }

    private static long appendHash(long hash, String value) {
        return appendHash(hash, value == null ? 0 : value.hashCode());
    }

    private static long appendHash(long hash, int value) {
        hash ^= value;
        return hash * 0x100000001b3L;
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
        tag.putInt("cost", baseCost());
        tag.putInt("battleCostReduction", battleCostReduction);
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
                Math.max(0, tag.getInt("battleCostReduction")),
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
        return new CardInstance(UUID.randomUUID(), cardId, sourceStack.copy(), nameKey(), descriptionKey(), attack(), defense(), baseCost(), battleCostReduction, List.copyOf(effects()), sourceType(), developerCardId(), artPath(), artItemId(), artX(), artY(), artScale(), faceId());
    }

    public CardInstance withAdditionalBattleCostReduction(int amount) {
        return new CardInstance(id, cardId, sourceStack.copy(), nameKey(), descriptionKey(), attack(), defense(), baseCost(), battleCostReduction + Math.max(0, amount), List.copyOf(effects()), sourceType(), developerCardId(), artPath(), artItemId(), artX(), artY(), artScale(), faceId());
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

    public int enemyDirectDamageAmount() {
        return effects().stream()
                .filter(effect -> (effect.kind() == CardEffectKind.DAMAGE || effect.kind() == CardEffectKind.CONSUME_ARROW) && effect.target().targetsEnemy())
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
        return enemyDirectDamageAmount() > 0;
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
        buf.writeVarInt(card.baseCost());
        buf.writeVarInt(card.battleCostReduction);
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
        int battleCostReduction = buf.readVarInt();
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
                battleCostReduction,
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
        return CardEffectOrder.orderedCardEffects(normalized);
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
            case "card.moonspire.monster.undead_power.name" -> "builtin_monster_undead_power";
            case "card.moonspire.monster.axe_chop.name" -> "builtin_monster_axe_chop";
            case "card.moonspire.monster.heavy_axe_blow.name" -> "builtin_monster_heavy_axe_blow";
            case "card.moonspire.monster.executioners_blow.name" -> "builtin_monster_executioners_blow";
            case "card.moonspire.monster.raised_axe_guard.name" -> "builtin_monster_raised_axe_guard";
            case "card.moonspire.monster.fanatic_might.name" -> "builtin_monster_fanatic_might";
            case "card.moonspire.monster.lunge.name" -> "builtin_monster_lunge";
            case "card.moonspire.monster.bow_strike.name" -> "builtin_monster_bow_strike";
            case "card.moonspire.monster.sidestep.name" -> "builtin_monster_sidestep";
            case "card.moonspire.monster.shoot.name" -> "builtin_monster_shoot";
            case "card.moonspire.monster.poisoned_shot.name" -> "builtin_monster_poisoned_shot";
            case "card.moonspire.monster.slowing_shot.name" -> "builtin_monster_slowing_shot";
            case "card.moonspire.monster.drop_the_hanging_blade.name" -> "builtin_monster_drop_the_hanging_blade";
            case "card.moonspire.monster.grazing_cut.name" -> "builtin_monster_grazing_cut";
            case "card.moonspire.monster.reload_cover.name" -> "builtin_monster_reload_cover";
            case "card.moonspire.monster.hungry_lunge.name" -> "builtin_monster_hungry_lunge";
            case "card.moonspire.monster.pounce.name" -> "builtin_monster_pounce";
            case "card.moonspire.monster.skitter.name" -> "builtin_monster_skitter";
            case "card.moonspire.monster.bite.name" -> "builtin_monster_bite";
            case "card.moonspire.monster.web.name" -> "builtin_monster_web";
            case "card.moonspire.monster.venom_fang.name" -> "builtin_monster_venom_fang";
            case "card.moonspire.monster.light_fuse.name" -> "builtin_monster_light_fuse";
            case "card.moonspire.monster.hissing_advance.name" -> "builtin_monster_hissing_advance";
            case "card.moonspire.monster.powder_shell.name" -> "builtin_monster_powder_shell";
            case "card.moonspire.monster.raking_dive.name" -> "builtin_monster_raking_dive";
            case "card.moonspire.monster.dragging_talons.name" -> "builtin_monster_dragging_talons";
            case "card.moonspire.monster.wingbeat_guard.name" -> "builtin_monster_wingbeat_guard";
            case "card.moonspire.monster.moonlit_glide.name" -> "builtin_monster_moonlit_glide";
            case "card.moonspire.monster.razor_rush.name" -> "builtin_monster_razor_rush";
            case "card.moonspire.monster.flicker_cut.name" -> "builtin_monster_flicker_cut";
            case "card.moonspire.monster.phase_stab.name" -> "builtin_monster_phase_stab";
            case "card.moonspire.monster.evasive_flicker.name" -> "builtin_monster_evasive_flicker";
            case "card.moonspire.monster.frenzied_dive.name" -> "builtin_monster_frenzied_dive";
            case "card.moonspire.monster.guardian_beam.name" -> "builtin_monster_guardian_beam";
            case "card.moonspire.monster.tidal_gaze.name" -> "builtin_monster_tidal_gaze";
            case "card.moonspire.monster.spiked_carapace.name" -> "builtin_monster_spiked_carapace";
            case "card.moonspire.monster.deep_sea_reflux.name" -> "builtin_monster_deep_sea_reflux";
            case "card.moonspire.monster.elder_beam.name" -> "builtin_monster_elder_beam";
            case "card.moonspire.monster.elder_tidal_erosion.name" -> "builtin_monster_elder_tidal_erosion";
            case "card.moonspire.monster.elder_thorn_crown.name" -> "builtin_monster_elder_thorn_crown";
            case "card.moonspire.monster.deep_sea_pressure.name" -> "builtin_monster_deep_sea_pressure";
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
