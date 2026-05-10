package com.yinfires.moonspire.client;

import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.MoonSpirePerfDiagnostics;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class CardGridPanel {
    private static final int GRID_VISIBLE_COLUMNS = 5;
    private static final int GRID_SCALE_COLUMNS = 7;
    private static final int CARD_GAP_X = 18;
    private static final int CARD_GAP_Y = 20;
    private static final int PREVIEW_EDGE_MARGIN = 12;
    private static final int PREVIEW_TOP_RESERVE = 54;
    private static final int TITLE_RESERVED_HEIGHT = 42;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int SCROLLBAR_HIT_WIDTH = 20;
    private static final int SCROLL_STEP = 34;
    private static final float GRID_CARD_SCALE = 0.60F;
    private static final float GRID_MIN_CARD_SCALE = 0.45F;
    private static final float PREVIEW_SCALE_REFERENCE = 0.72F;
    private static final float PREVIEW_MIN_SCALE = 0.58F;
    private static final float GRID_SELECTED_SCALE_BONUS = 0.06F;
    private static final float MODAL_CONTENT_Z = 1100.0F;
    private static final int WARMUP_CARDS_PER_FRAME = 3;

    private final Component title;
    private final List<CardInstance> cards = new ArrayList<>();
    private final List<String> warmupContentKeys = new ArrayList<>();
    private final List<CardRenderHelper.CardValues> frameValues = new ArrayList<>();
    private final PreviewAnimation previewAnimation = new PreviewAnimation();
    private double scrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    private int hoveredIndex = -1;
    private long lastAnimationNanos;
    private double previousScrollOffset = Double.NaN;
    private Layout cachedLayout;
    private int cachedFirstVisibleIndex = -1;
    private int cachedLastVisibleIndex = -1;
    private Object contentKey = new Object();
    private int displayCountOverride = -1;
    private final Set<String> warmedCardKeys = new HashSet<>();
    private int warmupCompleteStart = -1;
    private int warmupCompleteEnd = -1;
    private int warmupCompleteScroll = Integer.MIN_VALUE;
    private int frameValuesStart = -1;
    private int frameValuesEnd = -1;
    private FrameStats lastFrameStats = FrameStats.EMPTY;

    CardGridPanel(List<CardInstance> cards, Component title, Object contentKey) {
        this.title = title;
        setCards(cards, contentKey);
    }

    void setCards(List<CardInstance> cards, Object contentKey) {
        if (Objects.equals(this.contentKey, contentKey)) {
            return;
        }
        this.contentKey = contentKey;
        this.cards.clear();
        this.warmupContentKeys.clear();
        this.frameValues.clear();
        if (cards != null && !cards.isEmpty()) {
            for (CardInstance card : cards) {
                this.cards.add(card);
                this.warmupContentKeys.add(CardRenderHelper.warmupContentKey(card));
                this.frameValues.add(null);
            }
        }
        invalidateLayout();
        warmedCardKeys.clear();
        warmupCompleteStart = -1;
        warmupCompleteEnd = -1;
        warmupCompleteScroll = Integer.MIN_VALUE;
        frameValuesStart = -1;
        frameValuesEnd = -1;
        previewAnimation.clear();
        if (MoonSpirePerfDiagnostics.enabled()) {
            MoonSpirePerfDiagnostics.log("client.cardGrid.setCards", "cards=" + this.cards.size() + " contentKey=" + contentKey);
        }
    }

    void setDisplayCountOverride(int displayCountOverride) {
        this.displayCountOverride = displayCountOverride;
    }

    void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY, int bottomReserve, Predicate<CardInstance> selected, PreviewRenderer previewRenderer) {
        render(graphics, font, width, height, mouseX, mouseY, bottomReserve, selected, CardRenderHelper.CardValues::original, previewRenderer);
    }

    void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY, int bottomReserve, Predicate<CardInstance> selected, Function<CardInstance, CardRenderHelper.CardValues> values, PreviewRenderer previewRenderer) {
        boolean diag = MoonSpirePerfDiagnostics.enabled();
        long start = diag ? MoonSpirePerfDiagnostics.now() : 0L;
        long segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
        Layout layout = layout(width, height, bottomReserve);
        constrainScroll(layout);
        prepareFrameValues(layout, values);
        double frameScrollOffset = clampedScrollOffset(layout);
        boolean scrolledSinceLastFrame = Double.isFinite(previousScrollOffset) && Math.abs(frameScrollOffset - previousScrollOffset) > 0.01D;
        previousScrollOffset = frameScrollOffset;
        long layoutNanos = elapsedSince(segmentStart);
        segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
        int warmed = warmupVisibleCards(font, layout, values);
        long warmupNanos = elapsedSince(segmentStart);
        segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
        MoonSpireUiTextures.drawOverlay(graphics, width, height);
        long overlayNanos = elapsedSince(segmentStart);

        int renderedCards = 0;
        boolean previewRendered = false;
        long hoverNanos = 0L;
        long scissorNanos = 0L;
        long baseNanos = 0L;
        long artNanos = 0L;
        long baseArtOtherNanos = 0L;
        long clearDepthNanos = 0L;
        long textNanos = 0L;
        long scrollbarNanos = 0L;
        long previewNanos = 0L;
        long titleNanos = 0L;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, MODAL_CONTENT_Z);
        try {
            segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            hoveredIndex = hoveredCardIndex(layout, mouseX, mouseY);
            hoverNanos += elapsedSince(segmentStart);
            segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            graphics.enableScissor(layout.viewX(), layout.viewY(), layout.viewX() + layout.viewW(), layout.viewY() + layout.viewH());
            scissorNanos += elapsedSince(segmentStart);
            try {
                boolean itemArtRendered = false;
                for (int i = firstVisibleIndex(layout); i < Math.min(cards.size(), lastVisibleIndex(layout)); i++) {
                    if (i == hoveredIndex) {
                        continue;
                    }
                    CardBounds bounds = cardBounds(layout, i);
                    if (!cardIntersectsView(layout, bounds)) {
                        continue;
                    }
                    CardInstance card = cards.get(i);
                    segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
                    long beforeBaseNanos = diag ? CardRenderHelper.frameFaceBaseNanos() : 0L;
                    long beforeCustomArtNanos = diag ? CardRenderHelper.frameCustomArtNanos() : 0L;
                    long beforeItemArtNanos = diag ? CardRenderHelper.frameItemArtNanos() : 0L;
                    itemArtRendered |= renderGridCardBaseAndArt(graphics, card, bounds.x(), bounds.y(), layout.cardW(), layout.cardH(), layout.cardScale(), selected.test(card));
                    long visualElapsed = elapsedSince(segmentStart);
                    if (diag) {
                        long cardBaseNanos = Math.max(0L, CardRenderHelper.frameFaceBaseNanos() - beforeBaseNanos);
                        long cardArtNanos = Math.max(0L, CardRenderHelper.frameCustomArtNanos() - beforeCustomArtNanos)
                                + Math.max(0L, CardRenderHelper.frameItemArtNanos() - beforeItemArtNanos);
                        baseNanos += cardBaseNanos;
                        artNanos += cardArtNanos;
                        baseArtOtherNanos += Math.max(0L, visualElapsed - cardBaseNanos - cardArtNanos);
                    }
                }
                if (itemArtRendered) {
                    segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
                    CardRenderHelper.clearBatchedItemArtDepth(graphics);
                    clearDepthNanos += elapsedSince(segmentStart);
                }
                for (int i = firstVisibleIndex(layout); i < Math.min(cards.size(), lastVisibleIndex(layout)); i++) {
                    if (i == hoveredIndex) {
                        continue;
                    }
                    CardBounds bounds = cardBounds(layout, i);
                    if (!cardIntersectsView(layout, bounds)) {
                        continue;
                    }
                    CardInstance card = cards.get(i);
                    boolean selectedCard = selected.test(card);
                    segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
                    renderGridCardText(graphics, font, card, warmupContentKeys.get(i), bounds.x(), bounds.y(), layout.cardW(), layout.cardH(), layout.cardScale(), selectedCard, frameValue(i), shouldRenderGridDescription(layout, bounds, card, selectedCard));
                    textNanos += elapsedSince(segmentStart);
                    renderedCards++;
                }
            } finally {
                segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
                graphics.disableScissor();
                scissorNanos += elapsedSince(segmentStart);
            }

            segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            renderScrollbar(graphics, layout);
            scrollbarNanos += elapsedSince(segmentStart);
            segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            if (hoveredIndex >= 0) {
                CardInstance card = cards.get(hoveredIndex);
                CardBounds cardBounds = cardBounds(layout, hoveredIndex);
                PreviewBounds preview = previewBounds(layout, hoveredIndex);
                previewAnimation.setOpenTarget(card.id(), cardBounds.centerX(layout), cardBounds.centerY(layout), preview.centerX(), preview.centerY(), layout.cardScale(), preview.scale());
                if (scrolledSinceLastFrame) {
                    previewAnimation.snapPositionToTarget();
                }
                previewAnimation.advance(animationFrameTicks());
                renderAnimatedPreview(graphics, font, card, selected.test(card), previewAnimation, previewRenderer);
                previewRendered = true;
                if (previewAnimation.progress() > 0.86F) {
                    int previewW = Math.round(CardRenderHelper.CARD_WIDTH * previewAnimation.scale());
                    int previewH = Math.round(CardRenderHelper.CARD_HEIGHT * previewAnimation.scale());
                    int previewX = Math.round(previewAnimation.centerX() - previewW / 2.0F);
                    int previewY = Math.round(previewAnimation.centerY() - previewH / 2.0F);
                    CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, previewX, previewY, previewW, previewH, width, height);
                }
            } else {
                previewAnimation.setClosingTarget();
                previewAnimation.advance(animationFrameTicks());
                CardInstance closingCard = previewAnimation.card(cards);
                if (closingCard != null && previewAnimation.visible()) {
                    renderAnimatedPreview(graphics, font, closingCard, selected.test(closingCard), previewAnimation, previewRenderer);
                    previewRendered = true;
                } else {
                    previewAnimation.clear();
                    lastAnimationNanos = 0L;
                }
            }
            previewNanos += elapsedSince(segmentStart);
            segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            renderTitleBand(graphics, font, layout);
            titleNanos += elapsedSince(segmentStart);
        } finally {
            graphics.pose().popPose();
        }
        int visible = visibleCardCount(layout);
        long elapsed = diag ? MoonSpirePerfDiagnostics.now() - start : 0L;
        lastFrameStats = new FrameStats(cards.size(), visible, warmed, renderedCards, hoveredIndex, previewRendered, elapsed, layoutNanos, warmupNanos, overlayNanos, hoverNanos, scissorNanos, baseNanos, artNanos, baseArtOtherNanos, clearDepthNanos, textNanos, scrollbarNanos, previewNanos, titleNanos);
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
        cachedFirstVisibleIndex = -1;
        cachedLastVisibleIndex = -1;
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
        return mouseX >= preview.x() && mouseX <= preview.x() + preview.width()
                && mouseY >= preview.y() && mouseY <= preview.y() + preview.height();
    }

    boolean scrollbarAt(int width, int height, int bottomReserve, double mouseX, double mouseY) {
        return scrollbarAt(layout(width, height, bottomReserve), mouseX, mouseY);
    }

    void warmup(int width, int height, int bottomReserve, Font font, Function<CardInstance, CardRenderHelper.CardValues> values) {
        Layout layout = layout(width, height, bottomReserve);
        constrainScroll(layout);
        prepareFrameValues(layout, values);
        warmupVisibleCards(font, layout, values);
    }

    FrameStats lastFrameStats() {
        return lastFrameStats;
    }

    private int warmupVisibleCards(Font font, Layout layout, Function<CardInstance, CardRenderHelper.CardValues> values) {
        int start = firstVisibleIndex(layout);
        int end = Math.min(cards.size(), lastVisibleIndex(layout));
        int scrollKey = (int) Math.round(clampedScrollOffset(layout));
        if (warmupCompleteStart == start && warmupCompleteEnd == end && warmupCompleteScroll == scrollKey) {
            return 0;
        }
        int warmedThisFrame = 0;
        for (int i = start; i < end; i++) {
            if (!cardIntersectsView(layout, cardBounds(layout, i))) {
                continue;
            }
            CardInstance card = cards.get(i);
            CardRenderHelper.CardValues cardValues = frameValue(i);
            String key = CardRenderHelper.warmupKey(warmupContentKeys.get(i), cardValues);
            if (!warmedCardKeys.add(key)) {
                continue;
            }
            CardRenderHelper.warmupCard(font, card, cardValues);
            warmedThisFrame++;
            if (warmedThisFrame >= WARMUP_CARDS_PER_FRAME) {
                return warmedThisFrame;
            }
        }
        warmupCompleteStart = start;
        warmupCompleteEnd = end;
        warmupCompleteScroll = scrollKey;
        return warmedThisFrame;
    }

    private void renderGridCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int baseW, int baseH, float baseScale, boolean selected, CardRenderHelper.CardValues values) {
        boolean itemArtRendered = renderGridCardBaseAndArt(graphics, card, x, y, baseW, baseH, baseScale, selected);
        if (itemArtRendered) {
            CardRenderHelper.clearBatchedItemArtDepth(graphics);
        }
        renderGridCardText(graphics, font, card, x, y, baseW, baseH, baseScale, selected, values);
    }

    private boolean renderGridCardBaseAndArt(GuiGraphics graphics, CardInstance card, int x, int y, int baseW, int baseH, float baseScale, boolean selected) {
        float scale = selected ? Math.min(1.0F, baseScale + GRID_SELECTED_SCALE_BONUS) : baseScale;
        int cardW = Math.round(CardRenderHelper.CARD_WIDTH * scale);
        int cardH = Math.round(CardRenderHelper.CARD_HEIGHT * scale);
        graphics.pose().pushPose();
        graphics.pose().translate(x + (baseW - cardW) / 2.0F, y + (baseH - cardH) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        boolean itemArtRendered = CardRenderHelper.renderGridCardBaseAndArt(graphics, card, 0, 0);
        graphics.pose().popPose();
        return itemArtRendered;
    }

    private void renderGridCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int baseW, int baseH, float baseScale, boolean selected, CardRenderHelper.CardValues values) {
        renderGridCardText(graphics, font, card, CardRenderHelper.warmupContentKey(card), x, y, baseW, baseH, baseScale, selected, values, true);
    }

    private void renderGridCardText(GuiGraphics graphics, Font font, CardInstance card, String contentKey, int x, int y, int baseW, int baseH, float baseScale, boolean selected, CardRenderHelper.CardValues values, boolean showDescription) {
        float scale = selected ? Math.min(1.0F, baseScale + GRID_SELECTED_SCALE_BONUS) : baseScale;
        int cardW = Math.round(CardRenderHelper.CARD_WIDTH * scale);
        int cardH = Math.round(CardRenderHelper.CARD_HEIGHT * scale);
        graphics.pose().pushPose();
        graphics.pose().translate(x + (baseW - cardW) / 2.0F, y + (baseH - cardH) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        CardRenderHelper.renderGridCardText(graphics, font, card, 0, 0, values, showDescription, contentKey);
        graphics.pose().popPose();
    }

    private void renderAnimatedPreview(GuiGraphics graphics, Font font, CardInstance card, boolean selected, PreviewAnimation animation, PreviewRenderer previewRenderer) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 120.0F);
        graphics.pose().translate(animation.centerX(), animation.centerY(), 0.0F);
        graphics.pose().scale(animation.scale(), animation.scale(), 1.0F);
        previewRenderer.render(graphics, font, card, -CardRenderHelper.CARD_WIDTH / 2, -CardRenderHelper.CARD_HEIGHT / 2, selected);
        graphics.pose().popPose();
    }

    private void renderTitleBand(GuiGraphics graphics, Font font, Layout layout) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 240.0F);
        graphics.fillGradient(0, 0, layout.screenW(), layout.viewY(), MoonSpireUiTextures.CHEST_OVERLAY_TOP, MoonSpireUiTextures.CHEST_OVERLAY_TOP);
        graphics.drawCenteredString(font, title, layout.screenW() / 2, 18, 0xFFFFEAC2);
        int displayCount = displayCountOverride >= 0 ? displayCountOverride : cards.size();
        graphics.drawCenteredString(font, Integer.toString(displayCount), layout.screenW() / 2, 31, 0xFFE3C48C);
        graphics.pose().popPose();
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
        cachedFirstVisibleIndex = -1;
        cachedLastVisibleIndex = -1;
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
        float scale = previewScale(layout);
        int width = Math.round(CardRenderHelper.CARD_WIDTH * scale);
        int height = Math.round(CardRenderHelper.CARD_HEIGHT * scale);
        int x = Math.round(centerX - width / 2.0F);
        int y = Math.round(centerY - height / 2.0F);
        return new PreviewBounds(x, y, width, height, scale);
    }

    private CardBounds cardBounds(Layout layout, int index) {
        int row = index / layout.columns();
        int column = index % layout.columns();
        int x = layout.cardsX() + column * (layout.cardW() + CARD_GAP_X);
        int y = layout.viewY() + layout.scrollPadTop() + row * layout.rowH() - (int) Math.round(clampedScrollOffset(layout));
        return new CardBounds(x, y);
    }

    private boolean cardIntersectsView(Layout layout, CardBounds bounds) {
        return bounds.x() < layout.viewX() + layout.viewW()
                && bounds.x() + layout.cardW() > layout.viewX()
                && bounds.y() < layout.viewY() + layout.viewH()
                && bounds.y() + layout.cardH() > layout.viewY();
    }

    private boolean descriptionIntersectsView(Layout layout, CardBounds bounds, CardInstance card, boolean selected) {
        float scale = selected ? Math.min(1.0F, layout.cardScale() + GRID_SELECTED_SCALE_BONUS) : layout.cardScale();
        int cardW = Math.round(CardRenderHelper.CARD_WIDTH * scale);
        int cardH = Math.round(CardRenderHelper.CARD_HEIGHT * scale);
        float cardX = bounds.x() + (layout.cardW() - cardW) / 2.0F;
        float cardY = bounds.y() + (layout.cardH() - cardH) / 2.0F;
        CardRenderHelper.CardLocalArea area = CardRenderHelper.gridCardDescriptionArea(card);
        float descX = cardX + area.x() * scale;
        float descY = cardY + area.y() * scale;
        float descW = area.width() * scale;
        float descH = area.height() * scale;
        return descX < layout.viewX() + layout.viewW()
                && descX + descW > layout.viewX()
                && descY < layout.viewY() + layout.viewH()
                && descY + descH > layout.viewY();
    }

    private boolean shouldRenderGridDescription(Layout layout, CardBounds bounds, CardInstance card, boolean selected) {
        return descriptionIntersectsView(layout, bounds, card, selected);
    }

    private void prepareFrameValues(Layout layout, Function<CardInstance, CardRenderHelper.CardValues> values) {
        int start = firstVisibleIndex(layout);
        int end = Math.min(cards.size(), lastVisibleIndex(layout));
        if (frameValuesStart != start || frameValuesEnd != end) {
            for (int i = Math.max(0, frameValuesStart); i < Math.min(frameValuesEnd, frameValues.size()); i++) {
                frameValues.set(i, null);
            }
            frameValuesStart = start;
            frameValuesEnd = end;
        }
        for (int i = start; i < end; i++) {
            if (cardIntersectsView(layout, cardBounds(layout, i)) && frameValues.get(i) == null) {
                frameValues.set(i, values.apply(cards.get(i)));
            }
        }
    }

    private CardRenderHelper.CardValues frameValue(int index) {
        CardRenderHelper.CardValues cached = frameValues.get(index);
        if (cached != null) {
            return cached;
        }
        CardRenderHelper.CardValues fallback = CardRenderHelper.CardValues.original(cards.get(index));
        frameValues.set(index, fallback);
        return fallback;
    }

    private int visibleCardCount(Layout layout) {
        int count = 0;
        int end = Math.min(cards.size(), lastVisibleIndex(layout));
        for (int i = firstVisibleIndex(layout); i < end; i++) {
            if (cardIntersectsView(layout, cardBounds(layout, i))) {
                count++;
            }
        }
        return count;
    }

    private Layout layout(int width, int height, int bottomReserve) {
        if (cachedLayout != null
                && cachedLayout.screenW() == width
                && cachedLayout.screenH() == height
                && cachedLayout.bottomReserve() == Math.max(0, bottomReserve)) {
            return cachedLayout;
        }
        int viewX = 18;
        int viewY = TITLE_RESERVED_HEIGHT;
        int rawViewW = Math.max(CardRenderHelper.CARD_WIDTH + SCROLLBAR_HIT_WIDTH + CARD_GAP_X, width - viewX * 2);
        int contentW = Math.max(CardRenderHelper.CARD_WIDTH, rawViewW - SCROLLBAR_HIT_WIDTH - CARD_GAP_X);
        float scaleForSevenColumns = (contentW - (GRID_SCALE_COLUMNS - 1) * CARD_GAP_X) / (float) (GRID_SCALE_COLUMNS * CardRenderHelper.CARD_WIDTH);
        float cardScale = Math.max(GRID_MIN_CARD_SCALE, Math.min(GRID_CARD_SCALE, scaleForSevenColumns));
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
        int columns = Math.min(GRID_VISIBLE_COLUMNS, Math.max(1, (contentW + CARD_GAP_X) / (cardW + CARD_GAP_X)));
        int usedW = columns * cardW + (columns - 1) * CARD_GAP_X;
        int cardsX = viewX + Math.max(0, (contentW - usedW) / 2);
        int rowH = cardH + CARD_GAP_Y;
        int rows = cards.isEmpty() ? 1 : (cards.size() + columns - 1) / columns;
        int scrollPadTop = previewPad;
        int scrollPadBottom = previewPad;
        int cardContentH = cards.isEmpty() ? 0 : rows * rowH - CARD_GAP_Y;
        int contentH = cards.isEmpty() ? 0 : scrollPadTop + cardContentH + scrollPadBottom;
        int scrollbarX = viewX + viewW - SCROLLBAR_HIT_WIDTH / 2;
        cachedLayout = new Layout(width, height, reservedBottom, viewX, cardViewY, viewW, viewH, cardsX, scrollbarX, columns, cardW, cardH, rowH, contentH, scrollPadTop, cardScale);
        cachedFirstVisibleIndex = -1;
        cachedLastVisibleIndex = -1;
        return cachedLayout;
    }

    private float previewScale(Layout layout) {
        return Math.max(PREVIEW_MIN_SCALE, Math.min(1.0F, layout.cardScale() / PREVIEW_SCALE_REFERENCE));
    }

    private int firstVisibleIndex(Layout layout) {
        if (layout == cachedLayout && cachedFirstVisibleIndex >= 0) {
            return cachedFirstVisibleIndex;
        }
        int firstRow = Math.max(0, (int) Math.floor((Math.max(0.0D, scrollOffset) - layout.scrollPadTop()) / layout.rowH()));
        int index = firstRow * layout.columns();
        if (layout == cachedLayout) {
            cachedFirstVisibleIndex = index;
        }
        return index;
    }

    private int lastVisibleIndex(Layout layout) {
        if (layout == cachedLayout && cachedLastVisibleIndex >= 0) {
            return cachedLastVisibleIndex;
        }
        int lastRow = Math.max(1, (int) Math.ceil((Math.max(0.0D, scrollOffset) - layout.scrollPadTop() + layout.viewH()) / layout.rowH()) + 1);
        int index = lastRow * layout.columns();
        if (layout == cachedLayout) {
            cachedLastVisibleIndex = index;
        }
        return index;
    }

    private int maxScroll(Layout layout) {
        return Math.max(0, layout.contentH() - layout.viewH());
    }

    private boolean hasScrollbar(Layout layout) {
        return maxScroll(layout) > 0;
    }

    private void constrainScroll(Layout layout) {
        int maxScroll = maxScroll(layout);
        double next = Math.max(0.0D, Math.min(maxScroll, scrollOffset));
        if (Math.abs(next - scrollOffset) > 0.01D) {
            cachedFirstVisibleIndex = -1;
            cachedLastVisibleIndex = -1;
        }
        scrollOffset = next;
    }

    private double clampedScrollOffset(Layout layout) {
        return Math.max(0.0D, Math.min(maxScroll(layout), scrollOffset));
    }

    private int hoveredCardIndex(Layout layout, double mouseX, double mouseY) {
        int direct = cardIndexAt(layout, mouseX, mouseY);
        if (hoveredIndex >= 0 && hoveredIndex < cards.size()) {
            int previousHover = hoveredIndex;
            boolean currentSticky = currentPreviewContains(previousHover, mouseX, mouseY);
            if (currentSticky) {
                return previousHover;
            }
            if (direct >= 0 && direct != previousHover) {
                return direct;
            }
            return -1;
        }
        if (direct >= 0) {
            return direct;
        }
        return -1;
    }

    private boolean currentPreviewContains(int index, double mouseX, double mouseY) {
        if (index < 0 || index >= cards.size()) {
            return false;
        }
        if (!cards.get(index).id().equals(previewAnimation.cardId())) {
            return false;
        }
        return previewAnimation.contains(mouseX, mouseY);
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

    private void invalidateLayout() {
        cachedLayout = null;
        cachedFirstVisibleIndex = -1;
        cachedLastVisibleIndex = -1;
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

    private static long elapsedSince(long startNanos) {
        return startNanos == 0L ? 0L : MoonSpirePerfDiagnostics.now() - startNanos;
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

    private record PreviewBounds(int x, int y, int width, int height, float scale) {
        private float centerX() {
            return x + width / 2.0F;
        }

        private float centerY() {
            return y + height / 2.0F;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

    }

    private record ScrollbarThumb(int y, int height) {
    }

    record FrameStats(int cards, int visible, int warmup, int rendered, int hoveredIndex, boolean previewRendered, long renderNanos, long layoutNanos, long warmupNanos, long overlayNanos, long hoverNanos, long scissorNanos, long baseNanos, long artNanos, long baseArtOtherNanos, long clearDepthNanos, long textNanos, long scrollbarNanos, long previewNanos, long titleNanos) {
        private static final FrameStats EMPTY = new FrameStats(0, 0, 0, 0, -1, false, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        String summary() {
            return "cards=" + cards
                    + " visible=" + visible
                    + " warmup=" + warmup
                    + " rendered=" + rendered
                    + " hoveredIndex=" + hoveredIndex
                    + " preview=" + previewRendered
                    + " gridMs=" + MoonSpirePerfDiagnostics.millis(renderNanos)
                    + " layoutMs=" + MoonSpirePerfDiagnostics.millis(layoutNanos)
                    + " warmupMs=" + MoonSpirePerfDiagnostics.millis(warmupNanos)
                    + " overlayMs=" + MoonSpirePerfDiagnostics.millis(overlayNanos)
                    + " hoverMs=" + MoonSpirePerfDiagnostics.millis(hoverNanos)
                    + " scissorMs=" + MoonSpirePerfDiagnostics.millis(scissorNanos)
                    + " baseMs=" + MoonSpirePerfDiagnostics.millis(baseNanos)
                    + " artMs=" + MoonSpirePerfDiagnostics.millis(artNanos)
                    + " baseArtOtherMs=" + MoonSpirePerfDiagnostics.millis(baseArtOtherNanos)
                    + " clearDepthMs=" + MoonSpirePerfDiagnostics.millis(clearDepthNanos)
                    + " textMs=" + MoonSpirePerfDiagnostics.millis(textNanos)
                    + " scrollbarMs=" + MoonSpirePerfDiagnostics.millis(scrollbarNanos)
                    + " previewMs=" + MoonSpirePerfDiagnostics.millis(previewNanos)
                    + " titleMs=" + MoonSpirePerfDiagnostics.millis(titleNanos);
        }
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

        private boolean contains(double mouseX, double mouseY) {
            if (cardId == null) {
                return false;
            }
            float halfW = CardRenderHelper.CARD_WIDTH * scale / 2.0F;
            float halfH = CardRenderHelper.CARD_HEIGHT * scale / 2.0F;
            return mouseX >= centerX - halfW && mouseX <= centerX + halfW && mouseY >= centerY - halfH && mouseY <= centerY + halfH;
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
