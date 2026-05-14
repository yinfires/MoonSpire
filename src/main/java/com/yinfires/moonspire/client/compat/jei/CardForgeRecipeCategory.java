package com.yinfires.moonspire.client.compat.jei;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.client.CardPreviewAnimation;
import com.yinfires.moonspire.client.CardRenderHelper;
import com.yinfires.moonspire.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class CardForgeRecipeCategory implements IRecipeCategory<CardForgeJeiRecipe> {
    public static final RecipeType<CardForgeJeiRecipe> TYPE = RecipeType.create(MoonSpire.MOD_ID, "card_forge_conversion", CardForgeJeiRecipe.class);
    private static final int WIDTH = 272;
    private static final int HEIGHT = 172;
    private static final int INPUT_X = 13;
    private static final int INPUT_Y = 74;
    private static final int ARROW_X = 47;
    private static final int ARROW_Y = 74;
    private static final int CARD_X = 116;
    private static final int CARD_Y = 38;
    private static final int CARD_PREVIEW_X = 104;
    private static final int CARD_PREVIEW_Y = 14;
    private static final float CARD_PREVIEW_SCALE = CardRenderHelper.CARD_WIDTH / (float) CardRenderHelper.SMALL_CARD_WIDTH;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final CardPreviewAnimation previewAnimation = new CardPreviewAnimation();

    public CardForgeRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.CARD_FORGE.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<CardForgeJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("screen.moonspire.jei.card_forge");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CardForgeJeiRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(INPUT_X, INPUT_Y)
                .setStandardSlotBackground()
                .addItemStack(recipe.input());
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST)
                .addItemStack(new ItemStack(ModItems.CARD_FORGE.get()));
    }

    @Override
    public void draw(CardForgeJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, Component.translatable("screen.moonspire.jei.input"), INPUT_X - 2, INPUT_Y - 13, 0xFF6D5F79, false);
        guiGraphics.drawString(font, Component.translatable("screen.moonspire.jei.output_card"), CARD_X + 5, CARD_Y - 13, 0xFF6D5F79, false);
        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
        try (CardRenderHelper.CardRenderContext ignored = CardRenderHelper.openFrameContext()) {
            String contentKey = CardRenderHelper.contentKey(recipe.card());
            if (previewAnimation.key() != null && !previewAnimation.matches(contentKey)) {
                previewAnimation.clear();
            }
            boolean previewHovered = cardPreviewHovered(mouseX, mouseY);
            if (previewHovered) {
                previewAnimation.setOpenTarget(contentKey,
                        CARD_X + CardRenderHelper.SMALL_CARD_WIDTH / 2.0F,
                        CARD_Y + CardRenderHelper.SMALL_CARD_HEIGHT / 2.0F,
                        CARD_PREVIEW_X + CardRenderHelper.CARD_WIDTH / 2.0F,
                        CARD_PREVIEW_Y + Math.round(CardRenderHelper.SMALL_CARD_HEIGHT * CARD_PREVIEW_SCALE) / 2.0F,
                        1.0F,
                        CARD_PREVIEW_SCALE,
                        CardRenderHelper.SMALL_CARD_WIDTH,
                        CardRenderHelper.SMALL_CARD_HEIGHT);
                previewAnimation.advance();
            } else {
                previewAnimation.setClosingTarget();
                previewAnimation.advance();
                if (previewAnimation.finishedClosing()) {
                    previewAnimation.clear();
                }
            }
            boolean previewAnimatingThisCard = previewAnimation.visible() && previewAnimation.matches(contentKey);
            if (!previewAnimatingThisCard) {
                CardRenderHelper.renderSmallCard(guiGraphics, font, recipe.card(), CARD_X, CARD_Y, false, false, CardRenderHelper.CardValues.original(recipe.card()), false, true, contentKey);
            } else {
                renderAnimatedPreview(guiGraphics, font, recipe, contentKey);
                if (previewAnimation.progress() > 0.86F) {
                    CardPreviewAnimation.Bounds bounds = previewAnimation.bounds();
                    CardRenderHelper.renderKeywordTipsBeside(guiGraphics, font, recipe.card(), bounds.x(), bounds.y(), bounds.width(), bounds.height(), WIDTH, HEIGHT);
                }
            }
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, CardForgeJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (cardPreviewHovered(mouseX, mouseY)) {
            tooltip.add(Component.translatable("screen.moonspire.jei.card_preview"));
        }
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(CardForgeJeiRecipe recipe) {
        return recipe.id();
    }

    private static boolean cardPreviewHovered(double mouseX, double mouseY) {
        return inBounds(mouseX, mouseY, CARD_X, CARD_Y, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
    }

    private void renderAnimatedPreview(GuiGraphics guiGraphics, Font font, CardForgeJeiRecipe recipe, String contentKey) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 120.0F);
        try {
            CardPreviewAnimation.RenderBounds bounds = previewAnimation.renderBounds();
            guiGraphics.pose().translate(bounds.x(), bounds.y(), 0.0F);
            guiGraphics.pose().scale(previewAnimation.scale(), previewAnimation.scale(), 1.0F);
            CardRenderHelper.renderSmallCard(guiGraphics, font, recipe.card(), 0, 0, false, false, CardRenderHelper.CardValues.original(recipe.card()), false, true, contentKey);
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    private static boolean inBounds(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
