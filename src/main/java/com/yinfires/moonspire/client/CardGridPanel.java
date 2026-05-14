package com.yinfires.moonspire.client;

import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.MoonSpirePerfDiagnostics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    private static final int SCROLL_DIAG_FRAMES = 12;
    private static final int SCROLL_MOTION_FRAMES = 4;
    private static final Comparator<CardInstance> CARD_NAME_ORDER = Comparator
            .comparing((CardInstance card) -> card.nameComponent().getString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(card -> card.cardId(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(card -> card.id().toString());

    private final Component title;
    private final List<CardInstance> cards = new ArrayList<>();
    private final List<String> warmupContentKeys = new ArrayList<>();
    private final List<CardRenderHelper.CardValues> frameValues = new ArrayList<>();
    private final List<GridPose> framePoses = new ArrayList<>();
    private final CardPreviewAnimation previewAnimation = new CardPreviewAnimation();
    private double scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private int hoveredIndex = -1;
    private double previousScrollOffset = Double.NaN;
    private Layout cachedLayout;
    private int cachedFirstVisibleIndex = -1;
    private int cachedLastVisibleIndex = -1;
    private Object contentKey = new Object();
    private int displayCountOverride = -1;
    private int frameValuesStart = -1;
    private int frameValuesEnd = -1;
    private int framePoseScroll = Integer.MIN_VALUE;
    private float framePoseScale = Float.NaN;
    private FrameStats lastFrameStats = FrameStats.EMPTY;
    private int scrollDiagFrames;
    private int scrollMotionFrames;
    private long scrollEventId;
    private String scrollSource = "none";
    private double scrollInput;

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
        this.framePoses.clear();
        if (cards != null && !cards.isEmpty()) {
            for (CardInstance card : cards.stream().filter(Objects::nonNull).sorted(CARD_NAME_ORDER).toList()) {
                this.cards.add(card);
                this.warmupContentKeys.add(CardRenderHelper.warmupContentKey(card));
                this.frameValues.add(null);
                this.framePoses.add(GridPose.EMPTY);
            }
        }
        invalidateLayout();
        frameValuesStart = -1;
        frameValuesEnd = -1;
        framePoseScroll = Integer.MIN_VALUE;
        framePoseScale = Float.NaN;
        scrollMotionFrames = 0;
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
        prepareFramePoses(layout);
        int firstVisible = firstVisibleIndex(layout);
        int endVisible = Math.min(cards.size(), lastVisibleIndex(layout));
        double frameScrollOffset = clampedScrollOffset(layout);
        double previousFrameScrollOffset = previousScrollOffset;
        double scrollDelta = Double.isFinite(previousFrameScrollOffset) ? frameScrollOffset - previousFrameScrollOffset : 0.0D;
        boolean scrolledSinceLastFrame = Math.abs(scrollDelta) > 0.01D;
        boolean scrollMotionActive = scrolledSinceLastFrame || scrollMotionFrames > 0;
        boolean clipGridItemArt = true;
        previousScrollOffset = frameScrollOffset;
        long layoutNanos = elapsedSince(segmentStart);
        segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
        int warmed = 0;
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
        int fullVisibleCards = 0;
        int partialVisibleCards = 0;
        int skippedCostText = 0;
        int skippedNameText = 0;
        int skippedTypeText = 0;
        int skippedDescriptionText = 0;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, MODAL_CONTENT_Z);
        try {
            segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            hoveredIndex = hoveredCardIndex(layout, mouseX, mouseY);
            hoverNanos += elapsedSince(segmentStart);
            segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            updatePreviewAnimation(layout, hoveredIndex, scrolledSinceLastFrame);
            previewNanos += elapsedSince(segmentStart);
            segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            graphics.enableScissor(layout.viewX(), layout.viewY(), layout.viewX() + layout.viewW(), layout.viewY() + layout.viewH());
            scissorNanos += elapsedSince(segmentStart);
            try {
                for (int i = firstVisible; i < endVisible; i++) {
                    CardInstance card = cards.get(i);
                    if (previewAnimationVisibleFor(card)) {
                        continue;
                    }
                    CardBounds bounds = cardBounds(layout, i);
                    if (!cardIntersectsView(layout, bounds)) {
                        continue;
                    }
                    boolean selectedCard = selected.test(card);
                    boolean fullyVisible = cardFullyInsideView(layout, bounds);
                    if (fullyVisible) {
                        fullVisibleCards++;
                    } else {
                        partialVisibleCards++;
                    }
                    CardRenderHelper.SmallCardTextVisibility visibility = textVisibility(layout, bounds, card, selectedCard);
                    if (!visibility.cost()) {
                        skippedCostText++;
                    }
                    if (!visibility.name()) {
                        skippedNameText++;
                    }
                    if (!visibility.type()) {
                        skippedTypeText++;
                    }
                    if (!visibility.description()) {
                        skippedDescriptionText++;
                    }
                    GridPose pose = poseFor(layout, i, selectedCard);
                    graphics.pose().pushPose();
                    graphics.pose().translate(pose.x(), pose.y(), 0.0F);
                    graphics.pose().scale(pose.scale(), pose.scale(), 1.0F);
                    try {
                        segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
                        long beforeBaseNanos = diag ? CardRenderHelper.frameFaceBaseNanos() : 0L;
                        long beforeCustomArtNanos = diag ? CardRenderHelper.frameCustomArtNanos() : 0L;
                        long beforeItemArtNanos = diag ? CardRenderHelper.frameItemArtNanos() : 0L;
                        boolean itemArtRendered = CardRenderHelper.renderGridCardBaseAndArt(graphics, card, 0, 0, clipGridItemArt, warmupContentKeys.get(i));
                        long visualElapsed = elapsedSince(segmentStart);
                        if (diag) {
                            long cardBaseNanos = Math.max(0L, CardRenderHelper.frameFaceBaseNanos() - beforeBaseNanos);
                            long cardArtNanos = Math.max(0L, CardRenderHelper.frameCustomArtNanos() - beforeCustomArtNanos)
                                    + Math.max(0L, CardRenderHelper.frameItemArtNanos() - beforeItemArtNanos);
                            baseNanos += cardBaseNanos;
                            artNanos += cardArtNanos;
                            baseArtOtherNanos += Math.max(0L, visualElapsed - cardBaseNanos - cardArtNanos);
                        }
                        if (itemArtRendered) {
                            segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
                            CardRenderHelper.clearBatchedItemArtDepth(graphics);
                            clearDepthNanos += elapsedSince(segmentStart);
                        }
                        segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
                        CardRenderHelper.renderGridCardText(graphics, font, card, 0, 0, frameValue(i), visibility, warmupContentKeys.get(i));
                        textNanos += elapsedSince(segmentStart);
                    } finally {
                        graphics.pose().popPose();
                    }
                    graphics.flush();
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
            CardInstance previewCard = previewAnimationCard();
            if (previewCard == null) {
                previewAnimation.clear();
            } else if (previewAnimation.visible()) {
                renderAnimatedPreview(graphics, font, previewCard, warmupContentKey(previewCard), selected.test(previewCard), previewAnimation, previewRenderer);
                previewRendered = true;
                if (previewAnimation.progress() > 0.86F) {
                    CardPreviewAnimation.Bounds bounds = previewAnimation.bounds();
                    CardRenderHelper.renderKeywordTipsBeside(graphics, font, previewCard, bounds.x(), bounds.y(), bounds.width(), bounds.height(), width, height);
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
        boolean scrollDiagnosticActive = scrollDiagFrames > 0 || scrolledSinceLastFrame;
        lastFrameStats = new FrameStats(cards.size(), visible, warmed, renderedCards, hoveredIndex, previewRendered, scrollDiagnosticActive, draggingScrollbar, scrollMotionActive, clipGridItemArt, scrollEventId, scrollSource, scrollInput, frameScrollOffset, scrollDelta, maxScroll(layout), firstVisible, endVisible, fullVisibleCards, partialVisibleCards, skippedCostText, skippedNameText, skippedTypeText, skippedDescriptionText, elapsed, layoutNanos, warmupNanos, overlayNanos, hoverNanos, scissorNanos, baseNanos, artNanos, baseArtOtherNanos, clearDepthNanos, textNanos, scrollbarNanos, previewNanos, titleNanos);
        if (scrollMotionFrames > 0) {
            scrollMotionFrames--;
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
        scrollbarGrabOffset = Math.max(0.0D, Math.min(thumb.height(), mouseY - thumb.y()));
        boolean changed = dragScrollbar(width, height, bottomReserve, mouseY);
        markScrollDiagnostics("thumbGrab", mouseY);
        if (changed) {
            scrollMotionFrames = SCROLL_MOTION_FRAMES;
        }
        return true;
    }

    boolean mouseDragged(int width, int height, int bottomReserve, double mouseY) {
        if (!draggingScrollbar) {
            return false;
        }
        if (dragScrollbar(width, height, bottomReserve, mouseY)) {
            markScrollDiagnostics("thumbDrag", mouseY);
        }
        return true;
    }

    boolean mouseReleased(int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            markScrollDiagnostics("thumbRelease", 0.0D);
            return true;
        }
        return false;
    }

    boolean scroll(int width, int height, int bottomReserve, double scrollY) {
        Layout layout = layout(width, height, bottomReserve);
        if (!hasScrollbar(layout) || scrollY == 0.0D) {
            return false;
        }
        double before = clampedScrollOffset(layout);
        scrollOffset -= scrollY * SCROLL_STEP;
        cachedFirstVisibleIndex = -1;
        cachedLastVisibleIndex = -1;
        constrainScroll(layout);
        double after = clampedScrollOffset(layout);
        if (Math.abs(after - before) > 0.01D) {
            scrollMotionFrames = SCROLL_MOTION_FRAMES;
        }
        markScrollDiagnostics(Math.abs(after - before) > 0.01D ? "wheel" : "wheelEdge", scrollY);
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
        if (hoveredIndex < 0 || hoveredIndex >= cards.size()) {
            return false;
        }
        return previewAnimation.matches(cards.get(hoveredIndex).id()) && previewAnimation.contains(mouseX, mouseY);
    }

    boolean scrollbarAt(int width, int height, int bottomReserve, double mouseX, double mouseY) {
        return scrollbarAt(layout(width, height, bottomReserve), mouseX, mouseY);
    }

    void warmup(int width, int height, int bottomReserve, Font font, Function<CardInstance, CardRenderHelper.CardValues> values) {
        Layout layout = layout(width, height, bottomReserve);
        constrainScroll(layout);
        prepareFrameValues(layout, values);
    }

    FrameStats lastFrameStats() {
        return lastFrameStats;
    }

    boolean shouldLogScrollDiagnostics() {
        return MoonSpirePerfDiagnostics.enabled() && (scrollDiagFrames > 0 || lastFrameStats.scrolling());
    }

    void markScrollDiagnosticsLogged() {
        if (scrollDiagFrames > 0) {
            scrollDiagFrames--;
        }
    }

    private void markScrollDiagnostics(String source, double input) {
        if (!MoonSpirePerfDiagnostics.enabled()) {
            return;
        }
        scrollDiagFrames = Math.max(scrollDiagFrames, SCROLL_DIAG_FRAMES);
        scrollEventId++;
        scrollSource = source;
        scrollInput = input;
    }

    private void renderGridCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int baseW, int baseH, float baseScale, boolean selected, CardRenderHelper.CardValues values) {
        boolean itemArtRendered = renderGridCardBaseAndArt(graphics, card, x, y, baseW, baseH, baseScale, selected);
        if (itemArtRendered) {
            CardRenderHelper.clearBatchedItemArtDepth(graphics);
        }
        renderGridCardText(graphics, font, card, x, y, baseW, baseH, baseScale, selected, values);
    }

    private boolean renderGridCardBaseAndArt(GuiGraphics graphics, CardInstance card, int x, int y, int baseW, int baseH, float baseScale, boolean selected) {
        return renderGridCardBaseAndArt(graphics, card, gridPose(x, y, baseW, baseH, baseScale, selected));
    }

    private boolean renderGridCardBaseAndArt(GuiGraphics graphics, CardInstance card, GridPose pose) {
        graphics.pose().pushPose();
        graphics.pose().translate(pose.x(), pose.y(), 0.0F);
        graphics.pose().scale(pose.scale(), pose.scale(), 1.0F);
        boolean itemArtRendered = CardRenderHelper.renderGridCardBaseAndArt(graphics, card, 0, 0);
        graphics.pose().popPose();
        return itemArtRendered;
    }

    private GridPose gridPose(int x, int y, int baseW, int baseH, float baseScale, boolean selected) {
        float scale = selected ? Math.min(1.0F, baseScale + GRID_SELECTED_SCALE_BONUS) : baseScale;
        int cardW = Math.round(CardRenderHelper.CARD_WIDTH * scale);
        int cardH = Math.round(CardRenderHelper.CARD_HEIGHT * scale);
        return new GridPose(x + (baseW - cardW) / 2.0F, y + (baseH - cardH) / 2.0F, scale);
    }

    private void renderGridCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int baseW, int baseH, float baseScale, boolean selected, CardRenderHelper.CardValues values) {
        renderGridCardText(graphics, font, card, CardRenderHelper.warmupContentKey(card), x, y, baseW, baseH, baseScale, selected, values, true);
    }

    private void renderGridCardText(GuiGraphics graphics, Font font, CardInstance card, String contentKey, int x, int y, int baseW, int baseH, float baseScale, boolean selected, CardRenderHelper.CardValues values, boolean showDescription) {
        renderGridCardText(graphics, font, card, contentKey, gridPose(x, y, baseW, baseH, baseScale, selected), values, CardRenderHelper.SmallCardTextVisibility.all(showDescription));
    }

    private void renderGridCardText(GuiGraphics graphics, Font font, CardInstance card, String contentKey, GridPose pose, CardRenderHelper.CardValues values, CardRenderHelper.SmallCardTextVisibility visibility) {
        graphics.pose().pushPose();
        graphics.pose().translate(pose.x(), pose.y(), 0.0F);
        graphics.pose().scale(pose.scale(), pose.scale(), 1.0F);
        CardRenderHelper.renderGridCardText(graphics, font, card, 0, 0, values, visibility, contentKey);
        graphics.pose().popPose();
    }

    private String warmupContentKey(CardInstance card) {
        int index = cards.indexOf(card);
        if (index >= 0 && index < warmupContentKeys.size()) {
            return warmupContentKeys.get(index);
        }
        return CardRenderHelper.warmupContentKey(card);
    }

    private void renderAnimatedPreview(GuiGraphics graphics, Font font, CardInstance card, String contentKey, boolean selected, CardPreviewAnimation animation, PreviewRenderer previewRenderer) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 120.0F);
        CardPreviewAnimation.RenderBounds bounds = animation.renderBounds();
        graphics.pose().translate(bounds.x(), bounds.y(), 0.0F);
        graphics.pose().scale(animation.scale(), animation.scale(), 1.0F);
        previewRenderer.render(graphics, font, card, 0, 0, selected, contentKey);
        graphics.pose().popPose();
    }

    private void updatePreviewAnimation(Layout layout, int hoveredIndex, boolean scrolledSinceLastFrame) {
        if (hoveredIndex >= 0) {
            CardInstance card = cards.get(hoveredIndex);
            CardBounds cardBounds = cardBounds(layout, hoveredIndex);
            PreviewBounds preview = previewBounds(layout, hoveredIndex);
            boolean samePreviewCard = previewAnimation.matches(card.id());
            previewAnimation.setOpenTarget(card.id(), cardBounds.centerX(layout), cardBounds.centerY(layout), preview.centerX(), preview.centerY(), layout.cardScale(), preview.scale());
            if (scrolledSinceLastFrame && samePreviewCard) {
                previewAnimation.snapPositionToTarget();
            }
            previewAnimation.advance();
            return;
        }
        previewAnimation.setClosingTarget();
        previewAnimation.advance();
        if (previewAnimation.finishedClosing()) {
            previewAnimation.clear();
        }
    }

    private boolean previewAnimationVisibleFor(CardInstance card) {
        return previewAnimation.visible() && previewAnimation.matches(card.id());
    }

    private CardInstance previewAnimationCard() {
        Object key = previewAnimation.key();
        if (key == null) {
            return null;
        }
        for (CardInstance card : cards) {
            if (previewAnimation.matches(card.id())) {
                return card;
            }
        }
        return null;
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

    private boolean dragScrollbar(int width, int height, int bottomReserve, double mouseY) {
        Layout layout = layout(width, height, bottomReserve);
        if (!hasScrollbar(layout)) {
            return false;
        }
        ScrollbarThumb thumb = scrollbarThumb(layout);
        int trackRange = Math.max(1, layout.viewH() - thumb.height());
        double before = clampedScrollOffset(layout);
        double thumbY = Math.max(layout.viewY(), Math.min(layout.viewY() + trackRange, mouseY - scrollbarGrabOffset));
        scrollOffset = (thumbY - layout.viewY()) * maxScroll(layout) / (double) trackRange;
        cachedFirstVisibleIndex = -1;
        cachedLastVisibleIndex = -1;
        constrainScroll(layout);
        boolean changed = Math.abs(clampedScrollOffset(layout) - before) > 0.01D;
        if (changed) {
            scrollMotionFrames = SCROLL_MOTION_FRAMES;
        }
        return changed;
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
        return localAreaIntersectsView(layout, bounds, card, selected, CardRenderHelper.gridCardDescriptionArea(card));
    }

    private CardRenderHelper.SmallCardTextVisibility textVisibility(Layout layout, CardBounds bounds, CardInstance card, boolean selected) {
        if (!selected && cardFullyInsideView(layout, bounds)) {
            return CardRenderHelper.SmallCardTextVisibility.all(true);
        }
        return new CardRenderHelper.SmallCardTextVisibility(
                localAreaIntersectsView(layout, bounds, card, selected, CardRenderHelper.gridCostArea(card)),
                localAreaIntersectsView(layout, bounds, card, selected, CardRenderHelper.gridNameArea(card)),
                localAreaIntersectsView(layout, bounds, card, selected, CardRenderHelper.gridTypeArea(card)),
                descriptionIntersectsView(layout, bounds, card, selected));
    }

    private boolean localAreaIntersectsView(Layout layout, CardBounds bounds, CardInstance card, boolean selected, CardRenderHelper.CardLocalArea area) {
        float scale = selected ? Math.min(1.0F, layout.cardScale() + GRID_SELECTED_SCALE_BONUS) : layout.cardScale();
        int cardW = Math.round(CardRenderHelper.CARD_WIDTH * scale);
        int cardH = Math.round(CardRenderHelper.CARD_HEIGHT * scale);
        float cardX = bounds.x() + (layout.cardW() - cardW) / 2.0F;
        float cardY = bounds.y() + (layout.cardH() - cardH) / 2.0F;
        float areaX = cardX + area.x() * scale;
        float areaY = cardY + area.y() * scale;
        float areaW = area.width() * scale;
        float areaH = area.height() * scale;
        return areaX < layout.viewX() + layout.viewW()
                && areaX + areaW > layout.viewX()
                && areaY < layout.viewY() + layout.viewH()
                && areaY + areaH > layout.viewY();
    }

    private boolean cardFullyInsideView(Layout layout, CardBounds bounds) {
        return bounds.x() >= layout.viewX()
                && bounds.x() + layout.cardW() <= layout.viewX() + layout.viewW()
                && bounds.y() >= layout.viewY()
                && bounds.y() + layout.cardH() <= layout.viewY() + layout.viewH();
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

    private void prepareFramePoses(Layout layout) {
        int scrollKey = (int) Math.round(clampedScrollOffset(layout));
        if (framePoseScroll == scrollKey && framePoseScale == layout.cardScale()) {
            return;
        }
        int end = Math.min(cards.size(), lastVisibleIndex(layout));
        for (int i = firstVisibleIndex(layout); i < end; i++) {
            CardBounds bounds = cardBounds(layout, i);
            framePoses.set(i, gridPose(bounds.x(), bounds.y(), layout.cardW(), layout.cardH(), layout.cardScale(), false));
        }
        framePoseScroll = scrollKey;
        framePoseScale = layout.cardScale();
    }

    private GridPose poseFor(Layout layout, int index, boolean selected) {
        if (!selected) {
            return framePoses.get(index);
        }
        CardBounds bounds = cardBounds(layout, index);
        return gridPose(bounds.x(), bounds.y(), layout.cardW(), layout.cardH(), layout.cardScale(), true);
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
        return previewAnimation.matches(cards.get(index).id()) && previewAnimation.contains(mouseX, mouseY);
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
        void render(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, String contentKey);
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

    private record GridPose(float x, float y, float scale) {
        private static final GridPose EMPTY = new GridPose(0.0F, 0.0F, 1.0F);
    }

    record FrameStats(int cards, int visible, int warmup, int rendered, int hoveredIndex, boolean previewRendered, boolean scrolling, boolean draggingScrollbar, boolean scrollMotion, boolean itemArtClip, long scrollEventId, String scrollSource, double scrollInput, double scrollOffset, double scrollDelta, int maxScroll, int firstVisibleIndex, int endVisibleIndex, int fullVisibleCards, int partialVisibleCards, int skippedCostText, int skippedNameText, int skippedTypeText, int skippedDescriptionText, long renderNanos, long layoutNanos, long warmupNanos, long overlayNanos, long hoverNanos, long scissorNanos, long baseNanos, long artNanos, long baseArtOtherNanos, long clearDepthNanos, long textNanos, long scrollbarNanos, long previewNanos, long titleNanos) {
        private static final FrameStats EMPTY = new FrameStats(0, 0, 0, 0, -1, false, false, false, false, true, 0L, "none", 0.0D, 0.0D, 0.0D, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        String summary() {
            return "cards=" + cards
                    + " visible=" + visible
                    + " warmup=" + warmup
                    + " rendered=" + rendered
                    + " hoveredIndex=" + hoveredIndex
                    + " preview=" + previewRendered
                    + " scrolling=" + scrolling
                    + " draggingScrollbar=" + draggingScrollbar
                    + " scrollMotion=" + scrollMotion
                    + " itemArtClip=" + itemArtClip
                    + " scrollEvent=" + scrollEventId
                    + " scrollSource=" + scrollSource
                    + " scrollInput=" + decimal(scrollInput)
                    + " scrollOffset=" + decimal(scrollOffset)
                    + " scrollDelta=" + decimal(scrollDelta)
                    + " maxScroll=" + maxScroll
                    + " visibleRange=" + firstVisibleIndex + "-" + endVisibleIndex
                    + " fullVisible=" + fullVisibleCards
                    + " partialVisible=" + partialVisibleCards
                    + " textSkips=" + skippedCostText + "/" + skippedNameText + "/" + skippedTypeText + "/" + skippedDescriptionText
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

        private static String decimal(double value) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
    }

}
