package com.yinfires.moonspire.client;

import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class CardGridPanel {
    private static final int GRID_COLUMNS = 5;
    private static final int CARD_GAP_X = 18;
    private static final int CARD_GAP_Y = 20;
    private static final int PREVIEW_EDGE_MARGIN = 12;
    private static final int PREVIEW_TOP_RESERVE = 54;
    private static final int TITLE_RESERVED_HEIGHT = 42;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int SCROLLBAR_HIT_WIDTH = 20;
    private static final int SCROLL_STEP = 34;
    private static final float GRID_CARD_SCALE = 0.72F;
    private static final float GRID_MIN_CARD_SCALE = 0.54F;
    private static final float GRID_SELECTED_SCALE_BONUS = 0.06F;

    private final Component title;
    private final List<CardInstance> cards = new ArrayList<>();
    private final PreviewAnimation previewAnimation = new PreviewAnimation();
    private double scrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    private int hoveredIndex = -1;
    private long lastAnimationNanos;
    private double previousScrollOffset = Double.NaN;

    CardGridPanel(List<CardInstance> cards, Component title) {
        this.title = title;
        setCards(cards);
    }

    void setCards(List<CardInstance> cards) {
        if (sameCards(cards)) {
            return;
        }
        this.cards.clear();
        this.cards.addAll(cards);
        if (previewAnimation.cardId() != null && this.cards.stream().noneMatch(card -> card.id().equals(previewAnimation.cardId()))) {
            previewAnimation.clear();
        }
    }

    private boolean sameCards(List<CardInstance> nextCards) {
        if (nextCards == null || nextCards.size() != cards.size()) {
            return false;
        }
        for (int i = 0; i < cards.size(); i++) {
            CardInstance current = cards.get(i);
            CardInstance next = nextCards.get(i);
            if (next == null || !current.id().equals(next.id()) || !current.cardId().equals(next.cardId())) {
                return false;
            }
        }
        return true;
    }

    void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY, int bottomReserve, Predicate<CardInstance> selected, PreviewRenderer previewRenderer) {
        render(graphics, font, width, height, mouseX, mouseY, bottomReserve, selected, CardRenderHelper.CardValues::original, previewRenderer);
    }

    void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY, int bottomReserve, Predicate<CardInstance> selected, Function<CardInstance, CardRenderHelper.CardValues> values, PreviewRenderer previewRenderer) {
        Layout layout = layout(width, height, bottomReserve);
        constrainScroll(layout);
        double frameScrollOffset = clampedScrollOffset(layout);
        boolean scrolledSinceLastFrame = Double.isFinite(previousScrollOffset) && Math.abs(frameScrollOffset - previousScrollOffset) > 0.01D;
        previousScrollOffset = frameScrollOffset;
        MoonSpireUiTextures.drawOverlay(graphics, width, height);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFEAC2);
        graphics.drawCenteredString(font, Integer.toString(cards.size()), width / 2, 31, 0xFFE3C48C);

        hoveredIndex = hoveredCardIndex(layout, mouseX, mouseY);
        graphics.enableScissor(layout.viewX(), layout.viewY(), layout.viewX() + layout.viewW(), layout.viewY() + layout.viewH());
        for (int i = firstVisibleIndex(layout); i < Math.min(cards.size(), lastVisibleIndex(layout)); i++) {
            if (i == hoveredIndex) {
                continue;
            }
            CardBounds bounds = cardBounds(layout, i);
            CardInstance card = cards.get(i);
            renderGridCard(graphics, font, card, bounds.x(), bounds.y(), layout.cardW(), layout.cardH(), layout.cardScale(), selected.test(card), values.apply(card));
        }
        graphics.disableScissor();

        renderScrollbar(graphics, layout);
        if (hoveredIndex >= 0) {
            CardInstance card = cards.get(hoveredIndex);
            CardBounds cardBounds = cardBounds(layout, hoveredIndex);
            PreviewBounds preview = previewBounds(layout, hoveredIndex);
            previewAnimation.setOpenTarget(card.id(), cardBounds.centerX(layout), cardBounds.centerY(layout), preview.centerX(), preview.centerY(), layout.cardScale(), 1.0F);
            if (scrolledSinceLastFrame) {
                previewAnimation.snapPositionToTarget();
            }
            previewAnimation.advance(animationFrameTicks());
            renderAnimatedPreview(graphics, font, card, selected.test(card), previewAnimation, previewRenderer);
            if (previewAnimation.progress() > 0.86F) {
                CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, preview.x(), preview.y(), width, height);
            }
        } else {
            previewAnimation.setClosingTarget();
            previewAnimation.advance(animationFrameTicks());
            CardInstance closingCard = previewAnimation.card(cards);
            if (closingCard != null && previewAnimation.visible()) {
                renderAnimatedPreview(graphics, font, closingCard, selected.test(closingCard), previewAnimation, previewRenderer);
            } else {
                previewAnimation.clear();
                lastAnimationNanos = 0L;
            }
        }
    }

    boolean mouseClicked(int width, int height, int bottomReserve, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        Layout layout = layout(width, height, bottomReserve);
        constrainScroll(layout);
        if (!hasScrollbar(layout) || !scrollbarAt(layout, mouseX, mouseY)) {
            return false;
        }
        ScrollbarThumb thumb = scrollbarThumb(layout);
        draggingScrollbar = true;
        scrollbarGrabOffset = (int) Math.max(0.0D, Math.min(thumb.height(), mouseY - thumb.y()));
        dragScrollbar(width, height, bottomReserve, mouseY);
        return true;
    }

    boolean mouseDragged(int width, int height, int bottomReserve, double mouseY) {
        if (!draggingScrollbar) {
            return false;
        }
        dragScrollbar(width, height, bottomReserve, mouseY);
        return true;
    }

    boolean mouseReleased(int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    boolean scroll(int width, int height, int bottomReserve, double scrollY) {
        Layout layout = layout(width, height, bottomReserve);
        if (!hasScrollbar(layout) || scrollY == 0.0D) {
            return false;
        }
        scrollOffset -= scrollY * SCROLL_STEP;
        constrainScroll(layout);
        return true;
    }

    int cardIndexAt(int width, int height, int bottomReserve, double mouseX, double mouseY) {
        Layout layout = layout(width, height, bottomReserve);
        constrainScroll(layout);
        if (mouseX < layout.viewX() || mouseX > layout.viewX() + layout.viewW() || mouseY < layout.viewY() || mouseY > layout.viewY() + layout.viewH()) {
            return -1;
        }
        for (int i = firstVisibleIndex(layout); i < Math.min(cards.size(), lastVisibleIndex(layout)); i++) {
            CardBounds bounds = cardBounds(layout, i);
            if (mouseX >= bounds.x() && mouseX <= bounds.x() + layout.cardW() && mouseY >= bounds.y() && mouseY <= bounds.y() + layout.cardH()) {
                return i;
            }
        }
        return -1;
    }

    boolean inside(int width, int height, int bottomReserve, double mouseX, double mouseY) {
        Layout layout = layout(width, height, bottomReserve);
        return mouseX >= layout.viewX() && mouseX <= layout.viewX() + layout.viewW() && mouseY >= 0 && mouseY <= height;
    }

    boolean previewAt(int width, int height, int bottomReserve, double mouseX, double mouseY) {
        Layout layout = layout(width, height, bottomReserve);
        if (hoveredIndex < 0 || hoveredIndex >= cards.size()) {
            return false;
        }
        PreviewBounds preview = previewBounds(layout, hoveredIndex);
        return mouseX >= preview.x() && mouseX <= preview.x() + CardRenderHelper.CARD_WIDTH
                && mouseY >= preview.y() && mouseY <= preview.y() + CardRenderHelper.CARD_HEIGHT;
    }

    boolean scrollbarAt(int width, int height, int bottomReserve, double mouseX, double mouseY) {
        return scrollbarAt(layout(width, height, bottomReserve), mouseX, mouseY);
    }

    private void renderGridCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int baseW, int baseH, float baseScale, boolean selected, CardRenderHelper.CardValues values) {
        float scale = selected ? Math.min(1.0F, baseScale + GRID_SELECTED_SCALE_BONUS) : baseScale;
        int cardW = Math.round(CardRenderHelper.CARD_WIDTH * scale);
        int cardH = Math.round(CardRenderHelper.CARD_HEIGHT * scale);
        graphics.pose().pushPose();
        graphics.pose().translate(x + (baseW - cardW) / 2.0F, y + (baseH - cardH) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        CardRenderHelper.renderCard(graphics, font, card, 0, 0, false, values, false, false);
        graphics.pose().popPose();
    }

    private void renderAnimatedPreview(GuiGraphics graphics, Font font, CardInstance card, boolean selected, PreviewAnimation animation, PreviewRenderer previewRenderer) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 120.0F);
        graphics.pose().translate(animation.centerX(), animation.centerY(), 0.0F);
        graphics.pose().scale(animation.scale(), animation.scale(), 1.0F);
        previewRenderer.render(graphics, font, card, -CardRenderHelper.CARD_WIDTH / 2, -CardRenderHelper.CARD_HEIGHT / 2, selected);
        graphics.pose().popPose();
        graphics.flush();
    }

    private void renderScrollbar(GuiGraphics graphics, Layout layout) {
        if (!hasScrollbar(layout)) {
            return;
        }
        int x = layout.scrollbarX();
        MoonSpireUiTextures.drawScrollbarTrack(graphics, x, layout.viewY(), SCROLLBAR_WIDTH, layout.viewH());
        ScrollbarThumb thumb = scrollbarThumb(layout);
        MoonSpireUiTextures.drawScrollbarThumb(graphics, x - 2, thumb.y(), SCROLLBAR_WIDTH + 4, thumb.height());
    }

    private boolean scrollbarAt(Layout layout, double mouseX, double mouseY) {
        return mouseX >= layout.scrollbarX() - (SCROLLBAR_HIT_WIDTH - SCROLLBAR_WIDTH) / 2.0D
                && mouseX <= layout.scrollbarX() + SCROLLBAR_HIT_WIDTH
                && mouseY >= layout.viewY()
                && mouseY <= layout.viewY() + layout.viewH();
    }

    private void dragScrollbar(int width, int height, int bottomReserve, double mouseY) {
        Layout layout = layout(width, height, bottomReserve);
        if (!hasScrollbar(layout)) {
            return;
        }
        ScrollbarThumb thumb = scrollbarThumb(layout);
        int trackRange = Math.max(1, layout.viewH() - thumb.height());
        int thumbY = (int) Math.max(layout.viewY(), Math.min(layout.viewY() + trackRange, mouseY - scrollbarGrabOffset));
        scrollOffset = (thumbY - layout.viewY()) * maxScroll(layout) / (double) trackRange;
        constrainScroll(layout);
    }

    private ScrollbarThumb scrollbarThumb(Layout layout) {
        int thumbH = Math.max(22, layout.viewH() * layout.viewH() / Math.max(1, layout.contentH()));
        int maxScroll = Math.max(1, maxScroll(layout));
        int y = layout.viewY() + (int) Math.round((layout.viewH() - thumbH) * clampedScrollOffset(layout) / maxScroll);
        return new ScrollbarThumb(y, thumbH);
    }

    private PreviewBounds previewBounds(Layout layout, int index) {
        CardBounds bounds = cardBounds(layout, index);
        float centerX = bounds.centerX(layout);
        float centerY = bounds.centerY(layout);
        int maxX = Math.max(PREVIEW_EDGE_MARGIN, layout.screenW() - CardRenderHelper.CARD_WIDTH - PREVIEW_EDGE_MARGIN);
        int minY = Math.min(layout.screenH() - CardRenderHelper.CARD_HEIGHT - PREVIEW_EDGE_MARGIN, PREVIEW_TOP_RESERVE);
        int maxY = Math.max(minY, layout.screenH() - layout.bottomReserve() - CardRenderHelper.CARD_HEIGHT - PREVIEW_EDGE_MARGIN);
        int x = Math.max(PREVIEW_EDGE_MARGIN, Math.min(maxX, Math.round(centerX - CardRenderHelper.CARD_WIDTH / 2.0F)));
        int y = Math.max(minY, Math.min(maxY, Math.round(centerY - CardRenderHelper.CARD_HEIGHT / 2.0F)));
        return new PreviewBounds(x, y);
    }

    private CardBounds cardBounds(Layout layout, int index) {
        int row = index / layout.columns();
        int column = index % layout.columns();
        int x = layout.cardsX() + column * (layout.cardW() + CARD_GAP_X);
        int y = layout.viewY() + layout.scrollPadTop() + row * layout.rowH() - (int) Math.round(clampedScrollOffset(layout));
        return new CardBounds(x, y);
    }

    private Layout layout(int width, int height, int bottomReserve) {
        int viewX = 18;
        int viewY = TITLE_RESERVED_HEIGHT;
        int rawViewW = Math.max(CardRenderHelper.CARD_WIDTH + SCROLLBAR_HIT_WIDTH + CARD_GAP_X, width - viewX * 2);
        int contentW = Math.max(CardRenderHelper.CARD_WIDTH, rawViewW - SCROLLBAR_HIT_WIDTH - CARD_GAP_X);
        float scaleForFiveColumns = (contentW - (GRID_COLUMNS - 1) * CARD_GAP_X) / (float) (GRID_COLUMNS * CardRenderHelper.CARD_WIDTH);
        float cardScale = Math.max(GRID_MIN_CARD_SCALE, Math.min(GRID_CARD_SCALE, scaleForFiveColumns));
        int cardW = Math.round(CardRenderHelper.CARD_WIDTH * cardScale);
        int cardH = Math.round(CardRenderHelper.CARD_HEIGHT * cardScale);
        int previewPad = Math.max(0, (CardRenderHelper.CARD_HEIGHT - cardH) / 2 + PREVIEW_EDGE_MARGIN);
        int viewW = Math.max(cardW, rawViewW);
        int reservedBottom = Math.max(0, bottomReserve);
        int pileTop = TITLE_RESERVED_HEIGHT;
        int pileBottom = Math.max(pileTop + CardRenderHelper.CARD_HEIGHT, height - reservedBottom);
        int cardViewY = viewY;
        int availableH = pileBottom - cardViewY;
        int layoutH = availableH;
        int viewH = Math.max(cardH, Math.min(availableH, layoutH));
        int columns = Math.min(GRID_COLUMNS, Math.max(1, (contentW + CARD_GAP_X) / (cardW + CARD_GAP_X)));
        int usedW = columns * cardW + (columns - 1) * CARD_GAP_X;
        int cardsX = viewX + Math.max(0, (contentW - usedW) / 2);
        int rowH = cardH + CARD_GAP_Y;
        int rows = cards.isEmpty() ? 1 : (cards.size() + columns - 1) / columns;
        int scrollPadTop = previewPad;
        int scrollPadBottom = previewPad;
        int cardContentH = cards.isEmpty() ? 0 : rows * rowH - CARD_GAP_Y;
        int contentH = cards.isEmpty() ? 0 : scrollPadTop + cardContentH + scrollPadBottom;
        int scrollbarX = viewX + viewW - SCROLLBAR_HIT_WIDTH / 2;
        return new Layout(width, height, reservedBottom, viewX, cardViewY, viewW, viewH, cardsX, scrollbarX, columns, cardW, cardH, rowH, contentH, scrollPadTop, cardScale);
    }

    private int firstVisibleIndex(Layout layout) {
        int firstRow = Math.max(0, (int) Math.floor((Math.max(0.0D, scrollOffset) - layout.scrollPadTop()) / layout.rowH()));
        return firstRow * layout.columns();
    }

    private int lastVisibleIndex(Layout layout) {
        int lastRow = Math.max(1, (int) Math.ceil((Math.max(0.0D, scrollOffset) - layout.scrollPadTop() + layout.viewH()) / layout.rowH()) + 1);
        return lastRow * layout.columns();
    }

    private int maxScroll(Layout layout) {
        return Math.max(0, layout.contentH() - layout.viewH());
    }

    private boolean hasScrollbar(Layout layout) {
        return maxScroll(layout) > 0;
    }

    private void constrainScroll(Layout layout) {
        int maxScroll = maxScroll(layout);
        scrollOffset = Math.max(0.0D, Math.min(maxScroll, scrollOffset));
    }

    private double clampedScrollOffset(Layout layout) {
        return Math.max(0.0D, Math.min(maxScroll(layout), scrollOffset));
    }

    private int hoveredCardIndex(Layout layout, double mouseX, double mouseY) {
        int direct = cardIndexAt(layout, mouseX, mouseY);
        if (direct >= 0) {
            return direct;
        }
        if (hoveredIndex >= 0 && hoveredIndex < cards.size()) {
            PreviewBounds preview = previewBounds(layout, hoveredIndex);
            if (mouseX >= preview.x() && mouseX <= preview.x() + CardRenderHelper.CARD_WIDTH
                    && mouseY >= preview.y() && mouseY <= preview.y() + CardRenderHelper.CARD_HEIGHT) {
                return hoveredIndex;
            }
        }
        return -1;
    }

    private int cardIndexAt(Layout layout, double mouseX, double mouseY) {
        if (mouseX < layout.viewX() || mouseX > layout.viewX() + layout.viewW() || mouseY < layout.viewY() || mouseY > layout.viewY() + layout.viewH()) {
            return -1;
        }
        for (int i = firstVisibleIndex(layout); i < Math.min(cards.size(), lastVisibleIndex(layout)); i++) {
            CardBounds bounds = cardBounds(layout, i);
            if (mouseX >= bounds.x() && mouseX <= bounds.x() + layout.cardW() && mouseY >= bounds.y() && mouseY <= bounds.y() + layout.cardH()) {
                return i;
            }
        }
        return -1;
    }

    @FunctionalInterface
    interface PreviewRenderer {
        void render(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected);
    }

    private float animationFrameTicks() {
        long now = System.nanoTime();
        if (lastAnimationNanos == 0L) {
            lastAnimationNanos = now;
            return 1.0F;
        }
        float deltaTicks = (now - lastAnimationNanos) / 50_000_000.0F;
        lastAnimationNanos = now;
        if (!Float.isFinite(deltaTicks) || deltaTicks <= 0.0F) {
            return 0.25F;
        }
        return Math.max(0.05F, Math.min(1.5F, deltaTicks));
    }

    private record Layout(int screenW, int screenH, int bottomReserve, int viewX, int viewY, int viewW, int viewH, int cardsX, int scrollbarX, int columns, int cardW, int cardH, int rowH, int contentH, int scrollPadTop, float cardScale) {
    }

    private record CardBounds(int x, int y) {
        private float centerX(Layout layout) {
            return x + layout.cardW() / 2.0F;
        }

        private float centerY(Layout layout) {
            return y + layout.cardH() / 2.0F;
        }
    }

    private record PreviewBounds(int x, int y) {
        private float centerX() {
            return x + CardRenderHelper.CARD_WIDTH / 2.0F;
        }

        private float centerY() {
            return y + CardRenderHelper.CARD_HEIGHT / 2.0F;
        }
    }

    private record ScrollbarThumb(int y, int height) {
    }

    private static final class PreviewAnimation {
        private UUID cardId;
        private float baseCenterX;
        private float baseCenterY;
        private float baseScale;
        private float centerX;
        private float centerY;
        private float scale;
        private float progress;
        private float targetCenterX;
        private float targetCenterY;
        private float targetScale;

        private void setOpenTarget(UUID cardId, float fromX, float fromY, float toX, float toY, float fromScale, float toScale) {
            if (!cardId.equals(this.cardId)) {
                this.cardId = cardId;
                this.baseCenterX = fromX;
                this.baseCenterY = fromY;
                this.baseScale = fromScale;
                this.centerX = fromX;
                this.centerY = fromY;
                this.scale = fromScale;
                this.progress = 0.0F;
            }
            this.baseCenterX = fromX;
            this.baseCenterY = fromY;
            this.baseScale = fromScale;
            this.targetCenterX = toX;
            this.targetCenterY = toY;
            this.targetScale = toScale;
        }

        private void setClosingTarget() {
            if (cardId == null) {
                return;
            }
            targetCenterX = baseCenterX;
            targetCenterY = baseCenterY;
            targetScale = baseScale;
        }

        private void advance(float deltaTicks) {
            float amount = frameAmount(0.52F, deltaTicks);
            centerX = approach(centerX, targetCenterX, amount);
            centerY = approach(centerY, targetCenterY, amount);
            scale = approach(scale, targetScale, amount);
            progress = approach(progress, targetScale <= baseScale + 0.01F ? 0.0F : 1.0F, amount);
        }

        private void snapPositionToTarget() {
            centerX = targetCenterX;
            centerY = targetCenterY;
        }

        private void clear() {
            cardId = null;
            progress = 0.0F;
        }

        private UUID cardId() {
            return cardId;
        }

        private CardInstance card(List<CardInstance> cards) {
            if (cardId == null) {
                return null;
            }
            for (CardInstance card : cards) {
                if (card.id().equals(cardId)) {
                    return card;
                }
            }
            return null;
        }

        private boolean visible() {
            return cardId != null && progress > 0.08F && (Math.abs(scale - baseScale) > 0.025F || Math.abs(centerX - baseCenterX) > 1.0F || Math.abs(centerY - baseCenterY) > 1.0F);
        }

        private float centerX() {
            return centerX;
        }

        private float centerY() {
            return centerY;
        }

        private float scale() {
            return scale;
        }

        private float progress() {
            return progress;
        }

        private static float approach(float current, float target, float amount) {
            if (Math.abs(target - current) < 0.01F) {
                return target;
            }
            return current + (target - current) * amount;
        }

        private static float frameAmount(float perTickAmount, float deltaTicks) {
            return 1.0F - (float) Math.pow(1.0F - perTickAmount, Math.max(0.0F, deltaTicks));
        }
    }
}
