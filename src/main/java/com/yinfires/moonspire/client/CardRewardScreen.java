package com.yinfires.moonspire.client;

import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.client.ui.MoonSpireTextureButton;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.network.OpenCardRewardScreenPayload;
import com.yinfires.moonspire.network.SelectCardRewardPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class CardRewardScreen extends NoBlurScreen {
    private static final int TOP_TITLE_Y = 44;
    private static final int BOTTOM_RESERVE = 64;
    private static final int BUTTON_W = 78;
    private static final int BUTTON_H = 22;
    private static final int CARD_GAP_X = 36;
    private static final int CARD_GAP_Y = 16;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int SCROLLBAR_HIT_WIDTH = 20;
    private static final int SCROLL_STEP = 34;
    private static final int MAX_VISIBLE_COLUMNS = 3;
    private static final float CARD_SCALE = 2.0F / 3.0F;
    private static final float PREVIEW_SCALE = 0.9F;

    private final UUID rewardId;
    private final List<OpenCardRewardScreenPayload.RewardPage> pages;
    private MoonSpireTextureButton skipButton;
    private int pageIndex;
    private double scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private int hoveredIndex = -1;
    private final CardPreviewAnimation previewAnimation = new CardPreviewAnimation();

    CardRewardScreen(UUID rewardId, List<OpenCardRewardScreenPayload.RewardPage> pages) {
        super(Component.translatable("screen.moonspire.card_reward"));
        this.rewardId = rewardId;
        this.pages = List.copyOf(pages);
    }

    @Override
    protected void init() {
        clearWidgets();
        int buttonX = (width - BUTTON_W) / 2;
        int buttonY = Math.max(0, height - 48);
        skipButton = new MoonSpireTextureButton(buttonX, buttonY, BUTTON_W, BUTTON_H, Component.translatable("screen.moonspire.card_reward.skip"), button -> submit(null));
        addRenderableWidget(skipButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MoonSpireUiTextures.drawOverlay(graphics, width, height);
        OpenCardRewardScreenPayload.RewardPage page = currentPage();
        if (page == null) {
            onClose();
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 1100.0F);
        try (CardRenderHelper.CardRenderContext ignored = CardRenderHelper.openFrameContext()) {
            graphics.drawCenteredString(font, title, width / 2, TOP_TITLE_Y, 0xFFFFEAC2);
            Layout layout = layout(page.cards().size());
            constrainScroll(layout);
            hoveredIndex = cardIndexAt(layout, mouseX, mouseY);
            updatePreviewAnimation(page, layout, hoveredIndex);
            renderCards(graphics, page, layout, hoveredIndex);
            renderScrollbar(graphics, layout);
            renderWidgets(graphics, mouseX, mouseY, partialTick);
            renderPreview(graphics, page);
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            OpenCardRewardScreenPayload.RewardPage page = currentPage();
            Layout layout = layout(page == null ? 0 : page.cards().size());
            if (clickScrollbar(layout, mouseX, mouseY)) {
                return true;
            }
            int index = cardIndexAt(layout, mouseX, mouseY);
            if (page != null && index >= 0 && index < page.cards().size()) {
                submit(page.cards().get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScrollbar) {
            dragScrollbar(layout(currentCardCount()), mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = layout(currentCardCount());
        if (scrollY != 0.0D && insideGrid(layout, mouseX, mouseY) && hasScrollbar(layout)) {
            scrollOffset = clampScroll(layout, scrollOffset - scrollY * SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            submit(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void submit(CardInstance card) {
        PacketDistributor.sendToServer(new SelectCardRewardPayload(rewardId, pageIndex, card == null ? null : card.id()));
        pageIndex++;
        scrollOffset = 0.0D;
        hoveredIndex = -1;
        previewAnimation.clear();
        draggingScrollbar = false;
        if (pageIndex >= pages.size()) {
            onClose();
        } else {
            init();
        }
    }

    private OpenCardRewardScreenPayload.RewardPage currentPage() {
        return pageIndex >= 0 && pageIndex < pages.size() ? pages.get(pageIndex) : null;
    }

    private int currentCardCount() {
        OpenCardRewardScreenPayload.RewardPage page = currentPage();
        return page == null ? 0 : page.cards().size();
    }

    private void renderCards(GuiGraphics graphics, OpenCardRewardScreenPayload.RewardPage page, Layout layout, int hovered) {
        graphics.enableScissor(layout.viewX(), layout.viewY(), layout.viewX() + layout.viewW(), layout.viewY() + layout.viewH());
        try {
            for (int i = firstVisibleIndex(layout); i < Math.min(page.cards().size(), lastVisibleIndex(layout)); i++) {
                CardBounds bounds = cardBounds(layout, i);
                CardInstance card = page.cards().get(i);
                if (previewAnimation.visible() && previewAnimation.matches(card.id())) {
                    continue;
                }
                renderCardAt(graphics, card, bounds, CARD_SCALE);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderPreview(GuiGraphics graphics, CardInstance card, CardBounds bounds) {
        updatePreviewAnimation(card, bounds);
        renderPreview(graphics, currentPage());
    }

    private void updatePreviewAnimation(OpenCardRewardScreenPayload.RewardPage page, Layout layout, int hoveredIndex) {
        if (hoveredIndex >= 0 && hoveredIndex < page.cards().size()) {
            updatePreviewAnimation(page.cards().get(hoveredIndex), cardBounds(layout, hoveredIndex));
            return;
        }
        previewAnimation.setClosingTarget();
        previewAnimation.advance();
        if (previewAnimation.finishedClosing()) {
            previewAnimation.clear();
        }
    }

    private void updatePreviewAnimation(CardInstance card, CardBounds bounds) {
        float centerX = bounds.x() + cardW() / 2.0F;
        float centerY = bounds.y() + cardH() / 2.0F;
        int previewW = Math.round(CardRenderHelper.CARD_WIDTH * PREVIEW_SCALE);
        int previewH = Math.round(CardRenderHelper.CARD_HEIGHT * PREVIEW_SCALE);
        int previewX = Math.round(centerX - previewW / 2.0F);
        int previewY = Math.round(centerY - previewH / 2.0F);
        previewX = Math.max(8, Math.min(width - previewW - 8, previewX));
        previewY = Math.max(TOP_TITLE_Y + font.lineHeight + 8, Math.min(height - BOTTOM_RESERVE - previewH, previewY));
        previewAnimation.setOpenTarget(card.id(), centerX, centerY, previewX + previewW / 2.0F, previewY + previewH / 2.0F, CARD_SCALE, PREVIEW_SCALE);
        previewAnimation.advance();
    }

    private void renderPreview(GuiGraphics graphics, OpenCardRewardScreenPayload.RewardPage page) {
        CardInstance card = previewAnimationCard(page);
        if (card == null) {
            previewAnimation.clear();
            return;
        }
        if (!previewAnimation.visible()) {
            return;
        }
        renderAnimatedPreview(graphics, card, previewAnimation);
        if (previewAnimation.progress() > 0.86F) {
            CardPreviewAnimation.Bounds bounds = previewAnimation.bounds();
            CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, bounds.x(), bounds.y(), bounds.width(), bounds.height(), width, height);
        }
    }

    private CardInstance previewAnimationCard(OpenCardRewardScreenPayload.RewardPage page) {
        Object key = previewAnimation.key();
        if (key == null || page == null) {
            return null;
        }
        for (CardInstance card : page.cards()) {
            if (previewAnimation.matches(card.id())) {
                return card;
            }
        }
        return null;
    }

    private void renderAnimatedPreview(GuiGraphics graphics, CardInstance card, CardPreviewAnimation animation) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 130.0F);
        CardPreviewAnimation.RenderBounds bounds = animation.renderBounds();
        graphics.pose().translate(bounds.x(), bounds.y(), 0.0F);
        graphics.pose().scale(animation.scale(), animation.scale(), 1.0F);
        CardRenderHelper.renderCard(graphics, font, card, 0, 0, false, false, CardRenderHelper.warmupContentKey(card));
        graphics.pose().popPose();
    }

    private void renderCardAt(GuiGraphics graphics, CardInstance card, CardBounds bounds, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x(), bounds.y(), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        CardRenderHelper.renderCard(graphics, font, card, 0, 0, false, false, CardRenderHelper.warmupContentKey(card));
        graphics.pose().popPose();
    }

    private Layout layout(int cardCount) {
        int columns = Math.max(1, Math.min(MAX_VISIBLE_COLUMNS, Math.max(1, cardCount)));
        int contentW = columns * cardW() + (columns - 1) * CARD_GAP_X;
        int viewW = contentW + (cardCount > MAX_VISIBLE_COLUMNS ? SCROLLBAR_HIT_WIDTH + 8 : 0);
        int viewX = Math.max(8, (width - viewW) / 2);
        int viewY = Math.max(TOP_TITLE_Y + font.lineHeight + 42, (height - BOTTOM_RESERVE - cardH()) / 2);
        int visibleRows = cardCount <= MAX_VISIBLE_COLUMNS ? 1 : 1;
        int viewH = Math.min(cardH() * visibleRows + CARD_GAP_Y * Math.max(0, visibleRows - 1), Math.max(cardH(), height - viewY - BOTTOM_RESERVE));
        int rows = cardCount <= 0 ? 0 : (cardCount + columns - 1) / columns;
        int contentH = rows <= 0 ? 0 : rows * cardH() + (rows - 1) * CARD_GAP_Y;
        int scrollbarX = viewX + contentW + 10;
        return new Layout(viewX, viewY, viewW, viewH, viewX, columns, contentH, scrollbarX);
    }

    private int cardIndexAt(Layout layout, double mouseX, double mouseY) {
        if (!insideGrid(layout, mouseX, mouseY)) {
            return -1;
        }
        for (int i = firstVisibleIndex(layout); i < Math.min(currentCardCount(), lastVisibleIndex(layout)); i++) {
            CardBounds bounds = cardBounds(layout, i);
            if (mouseX >= bounds.x() && mouseX <= bounds.x() + cardW() && mouseY >= bounds.y() && mouseY <= bounds.y() + cardH()) {
                return i;
            }
        }
        return -1;
    }

    private CardBounds cardBounds(Layout layout, int index) {
        int row = index / layout.columns();
        int column = index % layout.columns();
        int x = layout.cardsX() + column * (cardW() + CARD_GAP_X);
        int y = layout.viewY() + row * (cardH() + CARD_GAP_Y) - (int) Math.round(scrollOffset);
        return new CardBounds(x, y);
    }

    private int firstVisibleIndex(Layout layout) {
        int firstRow = Math.max(0, (int) Math.floor(scrollOffset / Math.max(1, cardH() + CARD_GAP_Y)));
        return firstRow * layout.columns();
    }

    private int lastVisibleIndex(Layout layout) {
        int lastRow = Math.max(1, (int) Math.ceil((scrollOffset + layout.viewH()) / Math.max(1, cardH() + CARD_GAP_Y)) + 1);
        return lastRow * layout.columns();
    }

    private boolean insideGrid(Layout layout, double mouseX, double mouseY) {
        return mouseX >= layout.viewX() && mouseX <= layout.viewX() + layout.viewW() && mouseY >= layout.viewY() && mouseY <= layout.viewY() + layout.viewH();
    }

    private boolean clickScrollbar(Layout layout, double mouseX, double mouseY) {
        if (!hasScrollbar(layout) || mouseX < layout.scrollbarX() - 6 || mouseX > layout.scrollbarX() + SCROLLBAR_HIT_WIDTH || mouseY < layout.viewY() || mouseY > layout.viewY() + layout.viewH()) {
            return false;
        }
        ScrollbarThumb thumb = scrollbarThumb(layout);
        draggingScrollbar = true;
        scrollbarGrabOffset = Math.max(0.0D, Math.min(thumb.height(), mouseY - thumb.y()));
        dragScrollbar(layout, mouseY);
        return true;
    }

    private void dragScrollbar(Layout layout, double mouseY) {
        if (!hasScrollbar(layout)) {
            return;
        }
        ScrollbarThumb thumb = scrollbarThumb(layout);
        int trackRange = Math.max(1, layout.viewH() - thumb.height());
        double thumbY = Math.max(layout.viewY(), Math.min(layout.viewY() + trackRange, mouseY - scrollbarGrabOffset));
        scrollOffset = clampScroll(layout, (thumbY - layout.viewY()) * maxScroll(layout) / (double) trackRange);
    }

    private void renderScrollbar(GuiGraphics graphics, Layout layout) {
        if (!hasScrollbar(layout)) {
            return;
        }
        MoonSpireUiTextures.drawScrollbarTrack(graphics, layout.scrollbarX(), layout.viewY(), SCROLLBAR_WIDTH, layout.viewH());
        ScrollbarThumb thumb = scrollbarThumb(layout);
        MoonSpireUiTextures.drawScrollbarThumb(graphics, layout.scrollbarX() - 2, thumb.y(), SCROLLBAR_WIDTH + 4, thumb.height());
    }

    private ScrollbarThumb scrollbarThumb(Layout layout) {
        int thumbH = Math.max(22, layout.viewH() * layout.viewH() / Math.max(1, layout.contentH()));
        int y = layout.viewY() + (int) Math.round((layout.viewH() - thumbH) * scrollOffset / Math.max(1, maxScroll(layout)));
        return new ScrollbarThumb(y, thumbH);
    }

    private boolean hasScrollbar(Layout layout) {
        return maxScroll(layout) > 0;
    }

    private int maxScroll(Layout layout) {
        return Math.max(0, layout.contentH() - layout.viewH());
    }

    private void constrainScroll(Layout layout) {
        scrollOffset = clampScroll(layout, scrollOffset);
    }

    private double clampScroll(Layout layout, double offset) {
        return Math.max(0.0D, Math.min(maxScroll(layout), offset));
    }

    private int cardW() {
        return Math.round(CardRenderHelper.CARD_WIDTH * CARD_SCALE);
    }

    private int cardH() {
        return Math.round(CardRenderHelper.CARD_HEIGHT * CARD_SCALE);
    }

    private record Layout(int viewX, int viewY, int viewW, int viewH, int cardsX, int columns, int contentH, int scrollbarX) {
    }

    private record CardBounds(int x, int y) {
    }

    private record ScrollbarThumb(int y, int height) {
    }
}
