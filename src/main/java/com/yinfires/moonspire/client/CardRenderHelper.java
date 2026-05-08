package com.yinfires.moonspire.client;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleCombatantSnapshot;
import com.yinfires.moonspire.battle.BattleDamageCalculator;
import com.yinfires.moonspire.battle.BattleEffectSnapshot;
import com.yinfires.moonspire.battle.BattleEffectType;
import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.CardTarget;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.card.RegisteredCardDefinition;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.developer.DeveloperCardDefinition;
import com.yinfires.moonspire.developer.DeveloperCardFace;
import com.yinfires.moonspire.developer.DeveloperData;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import com.yinfires.moonspire.developer.DeveloperPaths;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CardRenderHelper {
    public static final int CARD_WIDTH = 128;
    public static final int CARD_HEIGHT = 158;
    public static final int SMALL_CARD_WIDTH = 82;
    public static final int SMALL_CARD_HEIGHT = 101;
    public static final int TIP_WIDTH = 122;
    private static final ResourceLocation CARD_BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "textures/gui/cards/card_base.png");
    private static final int KEYWORD_TEXT_COLOR = 0xD49A22;
    private static final int CARD_DESCRIPTION_TEXT_COLOR = 0xFFF2E6D2;
    private static final int INCREASED_VALUE_COLOR = 0x2F9E55;
    private static final int COST_ICON_TEXTURE_SIZE = 32;
    private static final int COST_TEXT_COLOR = 0xFFFFF6E0;
    private static final int COST_UNAFFORDABLE_TEXT_COLOR = 0xFFFF5F63;
    private static final int COST_OUTLINE_COLOR = 0xFF46393B;
    private static final float CARD_COST_BASE_WIDTH = SMALL_CARD_WIDTH;
    private static final float COST_TEXT_VERTICAL_BIAS = 1.0F;
    private static final float ENERGY_COST_SCREEN_SCALE = 2.0F;
    private static final int ENERGY_COST_TEXT_X_OFFSET = 2;
    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 158;
    private static final Map<String, TextureRef> FILE_TEXTURES = new HashMap<>();

    private record TextureRef(ResourceLocation location, int width, int height) {
    }

    public record CardValues(int attack, int defense, List<Integer> damageAmounts, List<Integer> blockAmounts) {
        public CardValues(int attack, int defense) {
            this(attack, defense, List.of(), List.of());
        }

        public static CardValues original(CardInstance card) {
            return new CardValues(card.enemyEffectAmount(CardEffectKind.DAMAGE), card.selfEffectAmount(CardEffectKind.BLOCK));
        }

        public int damageAmount(int effectIndex, int fallback) {
            return effectIndex >= 0 && effectIndex < damageAmounts.size() ? damageAmounts.get(effectIndex) : fallback;
        }

        public int blockAmount(int effectIndex, int fallback) {
            return effectIndex >= 0 && effectIndex < blockAmounts.size() ? blockAmounts.get(effectIndex) : fallback;
        }
    }

    public record CardLocalArea(int x, int y, int width, int height) {
    }

    public static void invalidateFileTexture(Path path) {
        if (path != null) {
            FILE_TEXTURES.remove(path.toAbsolutePath().normalize().toString());
        }
    }

    public static void warmupCard(Font font, CardInstance card, CardValues values) {
        if (font == null || card == null) {
            return;
        }
        DeveloperCardFace face = cardFace(card);
        font.width(card.nameComponent());
        font.width(Component.translatable(card.isAttackType() ? "card.moonspire.type.attack" : "card.moonspire.type.skill"));
        DeveloperCardFace.Area descArea = face.descriptionArea();
        int descWidth = sx(CARD_WIDTH, descArea.width());
        float scale = 0.62F;
        List<Component> lines = descriptionLines(card, values);
        if (lines.isEmpty()) {
            lines = List.of(card.descriptionComponent());
        }
        for (Component line : lines) {
            font.split(line, Math.max(1, (int) (descWidth / scale)));
        }
        customTexture(face.imagePath(), DeveloperPaths.cardFacesDirectory());
        customTexture(card.artPath(), DeveloperPaths.cardArtDirectory());
        artItem(card.artItemId());
    }

    public record BarSegments(int healthWidth, int blockX, int blockWidth) {
    }

    private CardRenderHelper() {
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, false, CardValues.original(card));
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean clipArt) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, false, CardValues.original(card), clipArt);
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean clipArt, DeveloperData data) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, false, CardValues.original(card), clipArt, data);
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, int attackValue, boolean modifiedAttack, boolean unaffordable) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, unaffordable, new CardValues(attackValue, card.selfEffectAmount(CardEffectKind.BLOCK)));
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, int attackValue, boolean modifiedAttack, boolean unaffordable, boolean clipArt) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, unaffordable, new CardValues(attackValue, card.selfEffectAmount(CardEffectKind.BLOCK)), clipArt);
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, CardValues values, boolean unaffordable, boolean clipArt) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, unaffordable, values, clipArt);
    }

    public static void renderDetailedCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, CardValues values, boolean unaffordable, boolean clipArt) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, unaffordable, values, clipArt);
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable) {
        renderCard(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, selected, false, unaffordable, CardValues.original(card));
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable, boolean clipArt) {
        renderSmallCard(graphics, font, card, x, y, selected, unaffordable, CardValues.original(card), clipArt);
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable, boolean clipArt, DeveloperData data) {
        renderCard(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, selected, false, unaffordable, CardValues.original(card), clipArt, data);
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable, CardValues values, boolean clipArt) {
        renderCard(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, selected, false, unaffordable, values, clipArt);
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable, CardValues values, boolean clipArt, boolean showDescription) {
        renderCard(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, selected, false, unaffordable, values, clipArt, null, showDescription);
    }

    public static int previewAttack(CardInstance card, int attackerSpeed, int defenderSpeed, int defenderBlock) {
        return previewAttack(card, attackerSpeed, defenderSpeed, defenderBlock, 0);
    }

    public static int previewAttack(CardInstance card, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard) {
        int incoming = card.effects().stream()
                .filter(effect -> effect.kind() == CardEffectKind.DAMAGE && effect.target().targetsEnemy())
                .mapToInt(effect -> previewDamageAmount(effect.amount(), attackerSpeed, defenderSpeed, defenderBlock, defenderGuard) * effect.count())
                .sum();
        return Math.max(0, incoming);
    }

    public static int previewDamageAmount(int amount, int attackerSpeed, int defenderSpeed) {
        return previewDamageAmount(amount, attackerSpeed, defenderSpeed, 0, 0);
    }

    public static int previewDamageAmount(int amount, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard) {
        return BattleDamageCalculator.directDamage(amount, attackerSpeed, defenderSpeed, defenderBlock, defenderGuard);
    }

    public static List<Component> descriptionLines(CardInstance card, int attackValue, boolean modifiedAttack) {
        return descriptionLines(card, new CardValues(attackValue, card.selfEffectAmount(CardEffectKind.BLOCK)));
    }

    public static List<Component> descriptionLines(CardInstance card, CardValues values) {
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < card.effects().size(); i++) {
            CardEffect effect = card.effects().get(i);
            if (effect.kind() == CardEffectKind.DAMAGE) {
                lines.add(withEffectCount(Component.translatable(damageDescriptionKey(effect.target()), statNumber(effect.amount(), displayedDamageAmount(effect, values, i))), effect.count()));
            } else if (effect.kind() == CardEffectKind.BLOCK) {
                lines.add(withEffectCount(Component.translatable(blockDescriptionKey(effect.target()), statNumber(effect.amount(), displayedBlockAmount(effect, values, i)), keyword(Component.translatable("keyword.moonspire.block.name"))), effect.count()));
            } else if (effect.kind() == CardEffectKind.BLEED) {
                lines.add(withEffectCount(Component.translatable(bleedDescriptionKey(effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.bleed.name"))), effect.count()));
            } else if (effect.kind() == CardEffectKind.GUARD) {
                lines.add(withEffectCount(Component.translatable(guardDescriptionKey(effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.guard.name"))), effect.count()));
            } else if (effect.kind() == CardEffectKind.EXHAUST) {
                lines.add(keyword(Component.translatable("keyword.moonspire.exhaust.name")));
            } else if (effect.kind() == CardEffectKind.EXHAUST_HAND) {
                lines.add(withEffectCount(Component.translatable(handSelectionDescriptionKey("exhaust_hand", effect.target()), effect.amount()), 1));
            } else if (effect.kind() == CardEffectKind.DISCARD_HAND) {
                lines.add(withEffectCount(Component.translatable(handSelectionDescriptionKey("discard_hand", effect.target()), effect.amount()), 1));
            }
        }
        return lines;
    }

    private static int displayedDamageAmount(CardEffect effect, CardValues values, int effectIndex) {
        return values.damageAmount(effectIndex, effect.amount());
    }

    private static int displayedBlockAmount(CardEffect effect, CardValues values, int effectIndex) {
        return values.blockAmount(effectIndex, effect.amount());
    }

    private static Component withEffectCount(Component line, int count) {
        if (count <= 1) {
            return Component.translatable("card.moonspire.effect.period_suffix", line);
        }
        return Component.translatable("card.moonspire.effect.count_suffix", line, count);
    }

    private static String damageDescriptionKey(CardTarget target) {
        return switch (target) {
            case SELF -> "card.moonspire.effect.damage.self";
            case SINGLE_ALLY -> "card.moonspire.effect.damage.single_ally";
            case ALL_ENEMIES -> "card.moonspire.effect.damage.all_enemies";
            case ALL_ALLIES -> "card.moonspire.effect.damage.all_allies";
            case ALL_UNITS -> "card.moonspire.effect.damage.all_units";
            case ALL_OTHER_UNITS -> "card.moonspire.effect.damage.all_other_units";
            case ALL_OTHER_ALLIES -> "card.moonspire.effect.damage.all_other_allies";
            case RANDOM_ENEMY -> "card.moonspire.effect.damage.random_enemy";
            case RANDOM_ALLY -> "card.moonspire.effect.damage.random_ally";
            case SINGLE_ENEMY -> "card.moonspire.effect.damage";
        };
    }

    private static String blockDescriptionKey(CardTarget target) {
        return switch (target) {
            case SELF -> "card.moonspire.effect.block";
            case SINGLE_ALLY -> "card.moonspire.effect.block.single_ally";
            case ALL_ENEMIES -> "card.moonspire.effect.block.all_enemies";
            case ALL_ALLIES -> "card.moonspire.effect.block.all_allies";
            case ALL_UNITS -> "card.moonspire.effect.block.all_units";
            case ALL_OTHER_UNITS -> "card.moonspire.effect.block.all_other_units";
            case ALL_OTHER_ALLIES -> "card.moonspire.effect.block.all_other_allies";
            case RANDOM_ENEMY -> "card.moonspire.effect.block.random_enemy";
            case RANDOM_ALLY -> "card.moonspire.effect.block.random_ally";
            case SINGLE_ENEMY -> "card.moonspire.effect.block.give";
        };
    }

    private static String bleedDescriptionKey(CardTarget target) {
        return switch (target) {
            case SELF -> "card.moonspire.effect.bleed.self";
            case SINGLE_ALLY -> "card.moonspire.effect.bleed.single_ally";
            case ALL_ENEMIES -> "card.moonspire.effect.bleed.all_enemies";
            case ALL_ALLIES -> "card.moonspire.effect.bleed.all_allies";
            case ALL_UNITS -> "card.moonspire.effect.bleed.all_units";
            case ALL_OTHER_UNITS -> "card.moonspire.effect.bleed.all_other_units";
            case ALL_OTHER_ALLIES -> "card.moonspire.effect.bleed.all_other_allies";
            case RANDOM_ENEMY -> "card.moonspire.effect.bleed.random_enemy";
            case RANDOM_ALLY -> "card.moonspire.effect.bleed.random_ally";
            case SINGLE_ENEMY -> "card.moonspire.effect.bleed";
        };
    }

    private static String guardDescriptionKey(CardTarget target) {
        return switch (target) {
            case SELF -> "card.moonspire.effect.guard";
            case SINGLE_ALLY -> "card.moonspire.effect.guard.single_ally";
            case ALL_ENEMIES -> "card.moonspire.effect.guard.all_enemies";
            case ALL_ALLIES -> "card.moonspire.effect.guard.all_allies";
            case ALL_UNITS -> "card.moonspire.effect.guard.all_units";
            case ALL_OTHER_UNITS -> "card.moonspire.effect.guard.all_other_units";
            case ALL_OTHER_ALLIES -> "card.moonspire.effect.guard.all_other_allies";
            case RANDOM_ENEMY -> "card.moonspire.effect.guard.random_enemy";
            case RANDOM_ALLY -> "card.moonspire.effect.guard.random_ally";
            case SINGLE_ENEMY -> "card.moonspire.effect.guard.give";
        };
    }

    private static String handSelectionDescriptionKey(String effect, CardTarget target) {
        return "card.moonspire.effect." + effect + switch (target) {
            case SELF -> "";
            case SINGLE_ENEMY -> ".single_enemy";
            case SINGLE_ALLY -> ".single_ally";
            case ALL_ENEMIES -> ".all_enemies";
            case ALL_ALLIES -> ".all_allies";
            case ALL_UNITS -> ".all_units";
            case ALL_OTHER_UNITS -> ".all_other_units";
            case ALL_OTHER_ALLIES -> ".all_other_allies";
            case RANDOM_ENEMY -> ".random_enemy";
            case RANDOM_ALLY -> ".random_ally";
        };
    }

    public static void renderCombatantBar(GuiGraphics graphics, Font font, BattleCombatantSnapshot entry, int x, int y, int width, int height) {
        MoonSpireUiTextures.drawHealthBar(graphics, x, y, width, height);
        int innerX = x + 2;
        int innerY = y + 2;
        int innerW = Math.max(0, width - 4);
        int innerH = Math.max(1, height - 4);
        BarSegments segments = combatantBarSegments(entry, innerW);
        renderBarFill(graphics, MoonSpireUiTextures.HEALTH_FILL, innerX, innerY, segments.healthWidth(), innerH);
        if (entry.defense() > 0) {
            renderBarFill(graphics, MoonSpireUiTextures.BLOCK_FILL, innerX + segments.blockX(), innerY, segments.blockWidth(), innerH);
        }
        Component value = entry.defense() > 0
                ? Component.translatable("screen.moonspire.health_block_value", Math.round(entry.health()), entry.defense(), Math.round(entry.maxHealth()))
                : Component.translatable("screen.moonspire.health_value", Math.round(entry.health()), Math.round(entry.maxHealth()));
        graphics.drawCenteredString(font, value, x + width / 2, y + 2, 0xFFFFFFFF);
    }

    public static int previewAttack(CardInstance card, BattleCombatantSnapshot attacker, BattleCombatantSnapshot defender) {
        return previewAttack(card, attacker.roundSpeed(), defender.roundSpeed(), defender.defense(), effectAmount(defender, BattleEffectType.GUARD));
    }

    public static CardLocalArea smallDescriptionArea(CardInstance card) {
        return descriptionArea(card, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, null);
    }

    public static void renderSmallCardDescription(GuiGraphics graphics, Font font, CardInstance card, int x, int y, CardValues values) {
        renderCardDescription(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, false, values, null, false);
    }

    public static BarSegments combatantBarSegments(BattleCombatantSnapshot entry, int width) {
        if (width <= 0 || entry.maxHealth() <= 0.0F) {
            return new BarSegments(0, 0, 0);
        }
        float health = Math.max(0.0F, Math.min(entry.health(), entry.maxHealth()));
        int block = Math.max(0, entry.defense());
        float effectiveMax = Math.max(1.0F, entry.maxHealth() + block);
        int healthWidth = Math.min(width, Math.max(0, Math.round(width * (health / effectiveMax))));
        int blockWidth = block <= 0 ? 0 : Math.round(width * (block / effectiveMax));
        blockWidth = Math.min(Math.max(0, width - healthWidth), Math.max(blockWidth, healthWidth < width ? 1 : 0));
        return new BarSegments(healthWidth, healthWidth, blockWidth);
    }

    public static void renderKeywordTips(GuiGraphics graphics, Font font, CardInstance card, int x, int y) {
        int tipY = y;
        if (card.hasSelfEffect(CardEffectKind.BLOCK)) {
            tipY = renderTip(graphics, font, Component.translatable("keyword.moonspire.block.name"), Component.translatable("keyword.moonspire.block.description"), x, tipY);
        }
        for (CardEffect effect : card.effects()) {
            if (effect.kind() == CardEffectKind.BLEED) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.bleed.name"), Component.translatable("effect.moonspire.bleed.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.GUARD) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.guard.name"), Component.translatable("effect.moonspire.guard.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.EXHAUST) {
                tipY = renderTip(graphics, font, Component.translatable("keyword.moonspire.exhaust.name"), Component.translatable("keyword.moonspire.exhaust.description"), x, tipY);
            }
        }
    }

    public static void renderKeywordTipsBeside(GuiGraphics graphics, Font font, CardInstance card, int cardX, int cardY, int screenW) {
        int x = keywordTipsXBeside(cardX, CARD_WIDTH, screenW);
        renderKeywordTips(graphics, font, card, x, cardY);
    }

    public static void renderKeywordTipsBeside(GuiGraphics graphics, Font font, CardInstance card, int cardX, int cardY, int screenW, int screenH) {
        renderKeywordTipsBeside(graphics, font, card, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, screenW, screenH);
    }

    public static void renderKeywordTipsBeside(GuiGraphics graphics, Font font, CardInstance card, int cardX, int cardY, int cardW, int cardH, int screenW, int screenH) {
        int x = keywordTipsXBeside(cardX, cardW, screenW);
        int totalHeight = keywordTipsHeight(font, card);
        int y = cardY + (cardH - totalHeight) / 2;
        y = Math.max(6, Math.min(screenH - totalHeight - 6, y));
        renderKeywordTips(graphics, font, card, x, y);
    }

    private static int keywordTipsXBeside(int cardX, int cardW, int screenW) {
        int rightX = cardX + cardW + 6;
        int preferredX = rightX + TIP_WIDTH <= screenW - 6 ? rightX : cardX - TIP_WIDTH - 6;
        int maxX = Math.max(6, screenW - TIP_WIDTH - 6);
        return Math.max(6, Math.min(maxX, preferredX));
    }

    public static int renderEffectTip(GuiGraphics graphics, Font font, BattleEffectType type, int amount, int x, int y) {
        Object value = type == BattleEffectType.GUARD ? BattleDamageCalculator.guardReductionPercent(amount) : amount;
        return renderTip(graphics, font, Component.translatable("screen.moonspire.effect_tip_title", Component.translatable(type.nameKey()), amount), Component.translatable(type.activeDescriptionKey(), value), x, y);
    }

    public static int effectAmount(BattleCombatantSnapshot snapshot, BattleEffectType type) {
        for (BattleEffectSnapshot effect : snapshot.effects()) {
            if (effect.type() == type) {
                return effect.amount();
            }
        }
        return 0;
    }

    private static int keywordTipsHeight(Font font, CardInstance card) {
        int height = 0;
        if (card.hasSelfEffect(CardEffectKind.BLOCK)) {
            height += tipHeight(font, Component.translatable("keyword.moonspire.block.name"), Component.translatable("keyword.moonspire.block.description")) + 4;
        }
        for (CardEffect effect : card.effects()) {
            if (effect.kind() == CardEffectKind.BLEED) {
                height += tipHeight(font, Component.translatable("effect.moonspire.bleed.name"), Component.translatable("effect.moonspire.bleed.description")) + 4;
            } else if (effect.kind() == CardEffectKind.GUARD) {
                height += tipHeight(font, Component.translatable("effect.moonspire.guard.name"), Component.translatable("effect.moonspire.guard.description")) + 4;
            } else if (effect.kind() == CardEffectKind.EXHAUST) {
                height += tipHeight(font, Component.translatable("keyword.moonspire.exhaust.name"), Component.translatable("keyword.moonspire.exhaust.description")) + 4;
            }
        }
        return Math.max(0, height - 4);
    }

    private static int tipHeight(Font font, Component title, Component description) {
        int pad = 6;
        List<FormattedCharSequence> titleLines = font.split(title, TIP_WIDTH - pad * 2);
        List<FormattedCharSequence> descLines = font.split(description, TIP_WIDTH - pad * 2);
        return pad * 2 + titleLines.size() * 10 + 3 + descLines.size() * 10;
    }

    public static int renderTip(GuiGraphics graphics, Font font, Component title, Component description, int x, int y) {
        int pad = 6;
        List<FormattedCharSequence> titleLines = font.split(title, TIP_WIDTH - pad * 2);
        List<FormattedCharSequence> descLines = font.split(description, TIP_WIDTH - pad * 2);
        int height = tipHeight(font, title, description);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        MoonSpireUiTextures.drawTooltip(graphics, x, y, TIP_WIDTH, height);
        int lineY = y + pad;
        for (FormattedCharSequence line : titleLines) {
            graphics.drawString(font, line, x + pad, lineY, 0xFF000000 | KEYWORD_TEXT_COLOR, false);
            lineY += 10;
        }
        lineY += 3;
        for (FormattedCharSequence line : descLines) {
            graphics.drawString(font, line, x + pad, lineY, 0xFFF2E6D2, false);
            lineY += 10;
        }
        graphics.pose().popPose();
        return y + height + 4;
    }

    private static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean selected, boolean detailed, boolean unaffordable, CardValues values) {
        renderCard(graphics, font, card, x, y, width, height, selected, detailed, unaffordable, values, true);
    }

    private static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean selected, boolean detailed, boolean unaffordable, CardValues values, boolean clipArt) {
        renderCard(graphics, font, card, x, y, width, height, selected, detailed, unaffordable, values, clipArt, null);
    }

    private static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean selected, boolean detailed, boolean unaffordable, CardValues values, boolean clipArt, DeveloperData dataOverride) {
        renderCard(graphics, font, card, x, y, width, height, selected, detailed, unaffordable, values, clipArt, dataOverride, true);
    }

    private static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean selected, boolean detailed, boolean unaffordable, CardValues values, boolean clipArt, DeveloperData dataOverride, boolean showDescription) {
        DeveloperCardFace face = cardFace(card, dataOverride);
        renderCardFaceBase(graphics, face.imagePath(), x, y, width, height);

        DeveloperCardFace.Area costArea = face.costArea();
        drawCardCostNumber(graphics, font, Integer.toString(card.cost()), x + sx(width, costArea.x()) + sx(width, costArea.width()) / 2, y + sy(height, costArea.y()) + sy(height, costArea.height()) / 2, width, unaffordable);

        float nameScale = detailed ? 0.86F : 0.58F;
        DeveloperCardFace.Area nameArea = face.nameArea();
        drawCenteredFit(graphics, font, card.nameComponent().getString(), x + sx(width, nameArea.x()), y + sy(height, nameArea.y()), sx(width, nameArea.width()), sy(height, nameArea.height()), 0xFF3A3025, nameScale);

        DeveloperCardFace.Area artArea = face.artArea();
        int artX = x + sx(width, artArea.x());
        int artY = y + sy(height, artArea.y());
        int artWidth = sx(width, artArea.width());
        int artHeight = sy(height, artArea.height());
        TextureRef artTexture = customTexture(card.artPath(), DeveloperPaths.cardArtDirectory());
        ItemStack artItem = artItem(card.artItemId());
        if (!artItem.isEmpty()) {
            renderCardArtItem(graphics, artItem, artX, artY, artWidth, artHeight, card.artX(), card.artY(), card.artScale(), clipArt);
        } else if (artTexture != null) {
            renderCustomCardArt(graphics, artTexture, artX, artY, artWidth, artHeight, card.artX(), card.artY(), card.artScale(), clipArt);
        } else if (!card.sourceStack().isEmpty()) {
            renderCardArtItem(graphics, card, artX, artY, artWidth, artHeight, clipArt);
        }

        Component type = Component.translatable(card.isAttackType() ? "card.moonspire.type.attack" : "card.moonspire.type.skill");
        DeveloperCardFace.Area typeArea = face.typeArea();
        drawCenteredFit(graphics, font, type.getString(), x + sx(width, typeArea.x()), y + sy(height, typeArea.y()), sx(width, typeArea.width()), sy(height, typeArea.height()), 0xFF514B45, detailed ? 0.62F : 0.48F);

        if (!showDescription) {
            return;
        }
        renderCardDescription(graphics, font, card, x, y, width, height, detailed, values, dataOverride, true);
    }

    private static CardLocalArea descriptionArea(CardInstance card, int width, int height, DeveloperData dataOverride) {
        DeveloperCardFace.Area descArea = cardFace(card, dataOverride).descriptionArea();
        return new CardLocalArea(sx(width, descArea.x()), sy(height, descArea.y()), sx(width, descArea.width()), sy(height, descArea.height()));
    }

    private static void renderCardDescription(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean detailed, CardValues values, DeveloperData dataOverride, boolean clipDescription) {
        CardLocalArea descArea = descriptionArea(card, width, height, dataOverride);
        int descX = x + descArea.x();
        int descY = y + descArea.y();
        int descWidth = descArea.width();
        int descHeight = descArea.height();
        float scale = detailed ? 0.62F : 0.44F;
        int lineHeight = scaledLineHeight(font, scale);
        int maxLines = Math.max(1, Math.min(linesLimit(detailed), Math.max(1, (descHeight + 2) / lineHeight)));
        List<Component> lines = descriptionLines(card, values);
        if (lines.isEmpty()) {
            lines = List.of(card.descriptionComponent());
        }
        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        for (Component line : lines) {
            for (FormattedCharSequence wrapped : font.split(line, (int) (descWidth / scale))) {
                if (wrappedLines.size() >= maxLines) {
                    break;
                }
                wrappedLines.add(wrapped);
            }
            if (wrappedLines.size() >= maxLines) {
                break;
            }
        }
        if (clipDescription) {
            enablePoseScissor(graphics, descX, descY, descWidth, descHeight);
        }
        drawCenteredLines(graphics, font, wrappedLines, descX, descY, descWidth, descHeight, CARD_DESCRIPTION_TEXT_COLOR, scale);
        if (clipDescription) {
            graphics.disableScissor();
        }
    }

    public static void enablePoseScissor(GuiGraphics graphics, int x, int y, int width, int height) {
        Matrix4f pose = graphics.pose().last().pose();
        Vector3f topLeft = pose.transformPosition(x, y, 0.0F, new Vector3f());
        Vector3f topRight = pose.transformPosition(x + width, y, 0.0F, new Vector3f());
        Vector3f bottomLeft = pose.transformPosition(x, y + height, 0.0F, new Vector3f());
        Vector3f bottomRight = pose.transformPosition(x + width, y + height, 0.0F, new Vector3f());
        float minX = Math.min(Math.min(topLeft.x, topRight.x), Math.min(bottomLeft.x, bottomRight.x));
        float minY = Math.min(Math.min(topLeft.y, topRight.y), Math.min(bottomLeft.y, bottomRight.y));
        float maxX = Math.max(Math.max(topLeft.x, topRight.x), Math.max(bottomLeft.x, bottomRight.x));
        float maxY = Math.max(Math.max(topLeft.y, topRight.y), Math.max(bottomLeft.y, bottomRight.y));
        graphics.enableScissor((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY));
    }

    private static int linesLimit(boolean detailed) {
        return detailed ? 8 : 3;
    }

    private static DeveloperCardFace cardFace(CardInstance card) {
        return cardFace(card, null);
    }

    private static DeveloperCardFace cardFace(CardInstance card, DeveloperData dataOverride) {
        DeveloperData data = dataOverride == null ? DeveloperDataManager.load() : dataOverride;
        String faceId = currentRegisteredFaceId(card, data);
        if (faceId.isBlank()) {
            faceId = card.faceId() == null || card.faceId().isBlank() ? data.activeFaceId : card.faceId();
        }
        if (faceId == null || faceId.isBlank()) {
            faceId = "default";
        }
        String resolvedFaceId = faceId;
        return data.cardFaces.stream().filter(face -> face.id().equals(resolvedFaceId)).findFirst().orElse(DeveloperCardFace.defaultFace());
    }

    private static String currentRegisteredFaceId(CardInstance card, DeveloperData data) {
        String registeredId = registeredCardId(card);
        if (registeredId.isBlank()) {
            return "";
        }
        String appliedFaceId = data.cards.stream()
                .filter(definition -> registeredId.equals(MoonSpireCardRegistry.registeredDeveloperId(definition.id())))
                .map(DeveloperCardDefinition::faceId)
                .filter(faceId -> faceId != null && !faceId.isBlank())
                .findFirst()
                .orElse("");
        if (!appliedFaceId.isBlank()) {
            return appliedFaceId;
        }
        return MoonSpireCardRegistry.baseCard(registeredId)
                .map(RegisteredCardDefinition::faceId)
                .filter(faceId -> faceId != null && !faceId.isBlank())
                .orElse("default");
    }

    private static String registeredCardId(CardInstance card) {
        if (card.developerCardId() != null && !card.developerCardId().isBlank()) {
            return MoonSpireCardRegistry.registeredDeveloperId(card.developerCardId());
        }
        if (card.cardId() != null && !card.cardId().isBlank()) {
            return MoonSpireCardRegistry.registeredDeveloperId(card.cardId());
        }
        return "";
    }

    public static void renderCardFaceBase(GuiGraphics graphics, String imagePath, int x, int y, int width, int height) {
        TextureRef baseTexture = customTexture(imagePath, DeveloperPaths.cardFacesDirectory());
        graphics.blit(
                baseTexture == null ? CARD_BASE_TEXTURE : baseTexture.location(),
                x,
                y,
                width,
                height,
                0.0F,
                0.0F,
                baseTexture == null ? TEXTURE_WIDTH : baseTexture.width(),
                baseTexture == null ? TEXTURE_HEIGHT : baseTexture.height(),
                baseTexture == null ? TEXTURE_WIDTH : baseTexture.width(),
                baseTexture == null ? TEXTURE_HEIGHT : baseTexture.height());
    }

    public static void renderCostIconAndNumber(GuiGraphics graphics, Font font, int cost, int x, int y, int width, int height) {
        int iconSize = Math.max(8, Math.min(width, height));
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + (height - iconSize) / 2;
        ResourceLocation texture = cost > 0 ? MoonSpireUiTextures.COST_AVAILABLE : MoonSpireUiTextures.COST_EMPTY;
        graphics.blit(texture, iconX, iconY, iconSize, iconSize, 0.0F, 0.0F, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE);
        drawCostNumber(graphics, font, Integer.toString(cost), iconX + iconSize / 2, iconY + iconSize / 2, 1.0F);
    }

    public static void renderEnergyCostDisplay(GuiGraphics graphics, Font font, int current, int maximum, int x, int y, int width, int height) {
        int iconSize = Math.max(8, Math.min(width, height));
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + (height - iconSize) / 2;
        ResourceLocation texture = current > 0 ? MoonSpireUiTextures.COST_AVAILABLE : MoonSpireUiTextures.COST_EMPTY;
        graphics.blit(texture, iconX, iconY, iconSize, iconSize, 0.0F, 0.0F, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE);
        Component ratio = Component.translatable("screen.moonspire.energy_ratio", current, maximum);
        drawCostNumber(graphics, font, ratio.getString(), iconX + iconSize / 2 + ENERGY_COST_TEXT_X_OFFSET, iconY + iconSize / 2, ENERGY_COST_SCREEN_SCALE);
    }

    public static void drawCostNumber(GuiGraphics graphics, Font font, String text, int centerX, int centerY, int iconSize) {
        drawCostNumber(graphics, font, text, centerX, centerY, 1.0F);
    }

    private static void drawCardCostNumber(GuiGraphics graphics, Font font, String text, int centerX, int centerY, int cardWidth, boolean unaffordable) {
        float inheritedScale = currentPoseScale(graphics);
        float screenScale = Math.max(0.2F, (cardWidth * inheritedScale) / CARD_COST_BASE_WIDTH);
        drawOutlinedScreenText(graphics, font, text, centerX, centerY, screenScale, unaffordable ? COST_UNAFFORDABLE_TEXT_COLOR : COST_TEXT_COLOR, COST_OUTLINE_COLOR);
    }

    public static void drawCostNumber(GuiGraphics graphics, Font font, String text, int centerX, int centerY, float screenScale) {
        drawOutlinedScreenText(graphics, font, text, centerX, centerY, screenScale, COST_TEXT_COLOR, COST_OUTLINE_COLOR);
    }

    public static void drawOutlinedScreenText(GuiGraphics graphics, Font font, Component text, int centerX, int centerY, float screenScale, int textColor, int outlineColor) {
        drawOutlinedScreenText(graphics, font, text.getString(), centerX, centerY, screenScale, textColor, outlineColor);
    }

    public static void drawOutlinedScreenText(GuiGraphics graphics, Font font, String text, int centerX, int centerY, float screenScale, int textColor, int outlineColor) {
        float inheritedScale = currentPoseScale(graphics);
        float localScale = screenScale / inheritedScale;
        float outlineOffset = 1.0F / inheritedScale;
        float yBias = COST_TEXT_VERTICAL_BIAS / inheritedScale;
        int textWidth = font.width(text);
        int textHeight = font.lineHeight;
        float textX = centerX - textWidth * localScale / 2.0F;
        float textY = centerY - textHeight * localScale / 2.0F + yBias;
        drawScaledOutlined(graphics, font, text, textX, textY, localScale, outlineOffset, textColor, outlineColor);
    }

    private static float currentPoseScale(GuiGraphics graphics) {
        Matrix4f pose = graphics.pose().last().pose();
        float xScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
        float yScale = (float) Math.sqrt(pose.m10() * pose.m10() + pose.m11() * pose.m11());
        float scale = Math.max(xScale, yScale);
        return scale > 0.0001F && Float.isFinite(scale) ? scale : 1.0F;
    }

    private static TextureRef customTexture(String path, Path relativeDirectory) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        boolean windowsPath = normalized.length() >= 3 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':' && normalized.charAt(2) == '/';
        if (normalized.contains(":") && !windowsPath) {
            try {
                return new TextureRef(ResourceLocation.parse(normalized), TEXTURE_WIDTH, TEXTURE_HEIGHT);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return fileTexture(normalized, relativeDirectory);
    }

    private static TextureRef fileTexture(String path, Path relativeDirectory) {
        try {
            Path resolved = Path.of(path);
            if (!resolved.isAbsolute()) {
                resolved = relativeDirectory.resolve(path);
            }
            if (!java.nio.file.Files.isRegularFile(resolved)) {
                return null;
            }
            String key = resolved.toAbsolutePath().normalize().toString();
            TextureRef cached = FILE_TEXTURES.get(key);
            if (cached != null) {
                return cached;
            }
            NativeImage image;
            try (FileInputStream input = new FileInputStream(resolved.toFile())) {
                image = NativeImage.read(input);
            }
            int width = Math.max(1, image.getWidth());
            int height = Math.max(1, image.getHeight());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "developer_file/" + Integer.toHexString(key.hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            TextureRef ref = new TextureRef(id, width, height);
            FILE_TEXTURES.put(key, ref);
            return ref;
        } catch (RuntimeException ignored) {
            return null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static ItemStack artItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void renderCardArtItem(GuiGraphics graphics, ItemStack stack, int artX, int artY, int artWidth, int artHeight, int offsetX, int offsetY, float customScale, boolean clipArt) {
        int padding = Math.max(2, Math.min(artWidth, artHeight) / 12);
        int itemSize = Math.max(16, Math.min(artWidth - padding * 2, artHeight - padding * 2));
        itemSize = Math.max(1, Math.round(itemSize * Math.max(0.05F, customScale)));
        float itemScale = itemSize / 16.0F;
        int itemX = artX + (artWidth - itemSize) / 2 + offsetX;
        int itemY = artY + (artHeight - itemSize) / 2 + offsetY;
        if (clipArt) {
            graphics.enableScissor(artX, artY, artX + artWidth, artY + artHeight);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(itemX, itemY, 0.0F);
        graphics.pose().scale(itemScale, itemScale, 1.0F);
        graphics.renderFakeItem(stack, 0, 0);
        graphics.pose().popPose();
        if (clipArt) {
            graphics.disableScissor();
        }
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static void renderCustomCardArt(GuiGraphics graphics, TextureRef texture, int artX, int artY, int artWidth, int artHeight, int offsetX, int offsetY, float scale, boolean clipArt) {
        if (clipArt) {
            graphics.enableScissor(artX, artY, artX + artWidth, artY + artHeight);
        }
        int drawW = Math.max(1, Math.round(artWidth * Math.max(0.05F, scale)));
        int drawH = Math.max(1, Math.round(artHeight * Math.max(0.05F, scale)));
        int drawX = artX + (artWidth - drawW) / 2 + offsetX;
        int drawY = artY + (artHeight - drawH) / 2 + offsetY;
        graphics.blit(texture.location(), drawX, drawY, drawW, drawH, 0.0F, 0.0F, texture.width(), texture.height(), texture.width(), texture.height());
        if (clipArt) {
            graphics.disableScissor();
        }
    }

    private static void renderBarFill(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int tileW = 16;
        int tileH = 16;
        int drawn = 0;
        while (drawn < width) {
            int slice = Math.min(tileW, width - drawn);
            graphics.blit(texture, x + drawn, y, slice, height, 0.0F, 0.0F, slice, tileH, tileW, tileH);
            drawn += slice;
        }
    }

    private static void renderCardArtItem(GuiGraphics graphics, CardInstance card, int artX, int artY, int artWidth, int artHeight, boolean clipArt) {
        int padding = Math.max(2, Math.min(artWidth, artHeight) / 12);
        int itemSize = Math.max(16, Math.min(artWidth - padding * 2, artHeight - padding * 2));
        float itemScale = itemSize / 16.0F;
        int itemX = artX + (artWidth - itemSize) / 2;
        int itemY = artY + (artHeight - itemSize) / 2;
        if (clipArt) {
            graphics.enableScissor(artX, artY, artX + artWidth, artY + artHeight);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(itemX, itemY, 0.0F);
        graphics.pose().scale(itemScale, itemScale, 1.0F);
        graphics.renderFakeItem(card.sourceStack(), 0, 0);
        graphics.pose().popPose();
        if (clipArt) {
            graphics.disableScissor();
        }
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static int sx(int width, int textureX) {
        return Math.round(width * (textureX / (float) TEXTURE_WIDTH));
    }

    private static int sy(int height, int textureY) {
        return Math.round(height * (textureY / (float) TEXTURE_HEIGHT));
    }

    private static Component keyword(Component component) {
        return component.copy().withStyle(style -> style.withColor(TextColor.fromRgb(KEYWORD_TEXT_COLOR)));
    }

    private static Component statNumber(int originalValue, int finalValue) {
        MutableComponent component = Component.translatable("screen.moonspire.value_number", finalValue);
        if (finalValue > originalValue) {
            return component.withStyle(style -> style.withColor(TextColor.fromRgb(INCREASED_VALUE_COLOR)));
        }
        if (finalValue < originalValue) {
            return component.withStyle(ChatFormatting.RED);
        }
        return component;
    }

    private static void drawCenteredFit(GuiGraphics graphics, Font font, String text, int x, int y, int width, int color, float maxScale) {
        drawCenteredFit(graphics, font, text, x, y, width, 10, color, maxScale);
    }

    private static void drawCenteredFit(GuiGraphics graphics, Font font, String text, int x, int y, int width, int height, int color, float maxScale) {
        float scale = Math.min(maxScale, width / (float) Math.max(1, font.width(text)));
        scale = Math.max(0.55F, scale);
        int scaledWidth = (int) (font.width(text) * scale);
        int scaledHeight = (int) (font.lineHeight * scale);
        drawScaled(graphics, font, text, x + (width - scaledWidth) / 2, y + (height - scaledHeight) / 2, color, scale);
    }

    private static void drawCenteredLines(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines, int x, int y, int width, int height, int color, float scale) {
        if (lines.isEmpty()) {
            return;
        }
        int lineHeight = scaledLineHeight(font, scale);
        int totalHeight = lines.size() * lineHeight - 2;
        int lineY = y + (height - totalHeight) / 2;
        for (FormattedCharSequence line : lines) {
            int scaledWidth = (int) (font.width(line) * scale);
            drawScaled(graphics, font, line, x + (width - scaledWidth) / 2, lineY, color, scale);
            lineY += lineHeight;
        }
    }

    private static int scaledLineHeight(Font font, float scale) {
        return Math.max(1, (int) (font.lineHeight * scale + 2));
    }

    private static void drawScaled(GuiGraphics graphics, Font font, String text, int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawScaled(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawScaledOutlined(GuiGraphics graphics, Font font, String text, float x, float y, float scale, float outlineOffset, int textColor, int outlineColor) {
        drawScaledAt(graphics, font, text, x - outlineOffset, y, outlineColor, scale);
        drawScaledAt(graphics, font, text, x + outlineOffset, y, outlineColor, scale);
        drawScaledAt(graphics, font, text, x, y - outlineOffset, outlineColor, scale);
        drawScaledAt(graphics, font, text, x, y + outlineOffset, outlineColor, scale);
        drawScaledAt(graphics, font, text, x, y, textColor, scale);
    }

    private static void drawScaledAt(GuiGraphics graphics, Font font, String text, float x, float y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
