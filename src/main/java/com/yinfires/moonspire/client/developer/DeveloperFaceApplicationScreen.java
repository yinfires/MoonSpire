package com.yinfires.moonspire.client.developer;

import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.client.CardRenderHelper;
import com.yinfires.moonspire.client.NoBlurScreen;
import com.yinfires.moonspire.client.ui.MoonSpireTextureButton;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.developer.DeveloperCardDefinition;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class DeveloperFaceApplicationScreen extends NoBlurScreen {
    private static final int TOP = 18;
    private static final int SEARCH_W = 240;
    private static final int SEARCH_H = 18;
    private static final int VIEW_TOP = 54;
    private static final int BOTTOM_RESERVE = 44;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int SCROLLBAR_HIT_WIDTH = 20;
    private static final int SCROLL_STEP = 34;
    private static final int CARD_GAP_X = 18;
    private static final int CARD_GAP_Y = 18;
    private static final int LABEL_H = 18;
    private static final int GRID_PAD_TOP = 4;
    private static final int GRID_PAD_BOTTOM = 6;

    private final DeveloperCenterScreen parent;
    private final Set<String> selectedCardIds;
    private EditBox searchBox;
    private double scrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    private String cachedQuery = null;
    private List<DeveloperCardDefinition> cachedFilteredCards = List.of();

    DeveloperFaceApplicationScreen(DeveloperCenterScreen parent) {
        super(Component.translatable("screen.moonspire.developer_face_apply"));
        this.parent = parent;
        this.selectedCardIds = new HashSet<>(parent.appliedCardIdsForSelectedFace());
    }

    @Override
    protected void init() {
        clearWidgets();
        int searchX = Math.max(8, (width - SEARCH_W) / 2);
        searchBox = new EditBox(font, searchX, TOP + 18, Math.min(SEARCH_W, width - 16), SEARCH_H, Component.translatable("debug.moonspire.search"));
        searchBox.setHint(Component.translatable("debug.moonspire.search"));
        addRenderableWidget(searchBox);

        ButtonLayout buttons = buttonLayout();
        addRenderableWidget(new MoonSpireTextureButton(buttons.x(), buttons.y(), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.select_all"), button -> selectAll()));
        addRenderableWidget(new MoonSpireTextureButton(buttons.x(1), buttons.y(), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.cancel_all"), button -> clearAll()));
        addRenderableWidget(new MoonSpireTextureButton(buttons.x(2), buttons.y(), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.reset"), button -> resetSelection()));
        addRenderableWidget(new MoonSpireTextureButton(buttons.x(3), buttons.y(), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.apply"), button -> applySelection()));
        addRenderableWidget(new MoonSpireTextureButton(buttons.x(4), buttons.y(), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.done"), button -> done()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, TOP, 0xFFFFD166);
        GridLayout grid = grid(filteredCards().size());
        scrollOffset = clampScroll(scrollOffset, grid);
        MoonSpireUiTextures.drawDarkPanel(graphics, grid.panelX(), grid.panelY(), grid.panelW(), grid.panelH());
        renderCardGrid(graphics, grid);
        renderGridScrollbar(graphics, grid);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            GridLayout grid = grid(filteredCards().size());
            if (clickScrollbar(grid, mouseY, mouseX)) {
                return true;
            }
            int index = indexAt(grid, mouseX, mouseY);
            List<DeveloperCardDefinition> cards = filteredCards();
            if (index >= 0 && index < cards.size()) {
                String id = MoonSpireCardRegistry.registeredDeveloperId(cards.get(index).id());
                if (!selectedCardIds.remove(id)) {
                    selectedCardIds.add(id);
                }
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
            dragScrollbar(grid(filteredCards().size()), mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        GridLayout grid = grid(filteredCards().size());
        if (scrollY != 0.0D && insideGrid(grid, mouseX, mouseY) && hasScrollbar(grid)) {
            scrollOffset = clampScroll(scrollOffset - scrollY * SCROLL_STEP, grid);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        done();
    }

    private void selectAll() {
        selectedCardIds.clear();
        for (DeveloperCardDefinition card : parent.faceApplicationCards()) {
            selectedCardIds.add(MoonSpireCardRegistry.registeredDeveloperId(card.id()));
        }
    }

    private void clearAll() {
        selectedCardIds.clear();
    }

    private void resetSelection() {
        selectedCardIds.clear();
        selectedCardIds.addAll(parent.savedAppliedCardIdsForSelectedFace());
        parent.showStatus(Component.translatable("debug.moonspire.face_apply_reset"));
    }

    private void applySelection() {
        parent.applySelectedFaceToCards(Set.copyOf(selectedCardIds));
        cachedQuery = null;
    }

    private void done() {
        Minecraft.getInstance().setScreen(parent);
    }

    private List<DeveloperCardDefinition> filteredCards() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.equals(cachedQuery)) {
            return cachedFilteredCards;
        }
        cachedQuery = query;
        cachedFilteredCards = parent.faceApplicationCards().stream()
                .filter(card -> query.isBlank()
                        || MoonSpireCardRegistry.registeredDeveloperId(card.id()).toLowerCase(Locale.ROOT).contains(query)
                        || card.nameKey().toLowerCase(Locale.ROOT).contains(query)
                        || card.toCardInstance().nameComponent().getString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        return cachedFilteredCards;
    }

    private void renderCardGrid(GuiGraphics graphics, GridLayout grid) {
        List<DeveloperCardDefinition> cards = filteredCards();
        graphics.enableScissor(grid.viewX(), grid.viewY(), grid.viewX() + grid.viewW(), grid.viewY() + grid.viewH());
        for (int i = firstVisibleIndex(grid); i < Math.min(cards.size(), lastVisibleIndex(grid)); i++) {
            GridCell cell = cell(grid, i);
            DeveloperCardDefinition card = cards.get(i);
            var instance = card.toCardInstance();
            String id = MoonSpireCardRegistry.registeredDeveloperId(card.id());
            boolean selected = selectedCardIds.contains(id);
            CardRenderHelper.renderSmallCard(graphics, font, instance, cell.x(), cell.y(), false, false, true, parent.data());
            if (selected) {
                DeveloperCenterScreen.renderSelectableCardOutline(graphics, cell.x(), cell.y(), CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
            }
            drawCenteredFitted(graphics, instance.nameComponent().getString(), cell.x(), cell.y() + CardRenderHelper.SMALL_CARD_HEIGHT + 2, CardRenderHelper.SMALL_CARD_WIDTH, LABEL_H, selected ? 0xFFB8E6FF : 0xFFEDE8FF);
        }
        graphics.disableScissor();
    }

    private void drawCenteredFitted(GuiGraphics graphics, String text, int x, int y, int w, int h, int color) {
        if (font.width(text) <= w) {
            graphics.drawString(font, text, x + (w - font.width(text)) / 2, y, color, false);
            return;
        }
        float scale = Math.max(0.45F, w / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x + w / 2.0F, y + Math.max(0, (h - Math.round(font.lineHeight * scale)) / 2.0F), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private GridLayout grid(int itemCount) {
        int panelX = 12;
        int panelY = VIEW_TOP - 8;
        int panelW = Math.max(1, width - 24);
        int panelH = Math.max(20, height - VIEW_TOP - BOTTOM_RESERVE);
        int viewX = panelX + 10;
        int viewY = VIEW_TOP;
        int viewW = Math.max(CardRenderHelper.SMALL_CARD_WIDTH, panelW - 20 - SCROLLBAR_HIT_WIDTH);
        int viewH = Math.max(CardRenderHelper.SMALL_CARD_HEIGHT, panelH - 14);
        int columns = Math.max(1, (viewW + CARD_GAP_X) / (CardRenderHelper.SMALL_CARD_WIDTH + CARD_GAP_X));
        int usedW = columns * CardRenderHelper.SMALL_CARD_WIDTH + (columns - 1) * CARD_GAP_X;
        int cellsX = viewX + Math.max(0, (viewW - usedW) / 2);
        int rowH = CardRenderHelper.SMALL_CARD_HEIGHT + LABEL_H + CARD_GAP_Y;
        int rows = itemCount <= 0 ? 0 : (itemCount + columns - 1) / columns;
        int contentH = rows <= 0 ? 0 : GRID_PAD_TOP + rows * rowH - CARD_GAP_Y + GRID_PAD_BOTTOM;
        int scrollbarX = panelX + panelW - SCROLLBAR_HIT_WIDTH / 2 - 5;
        return new GridLayout(panelX, panelY, panelW, panelH, viewX, viewY, viewW, viewH, cellsX, scrollbarX, columns, rowH, contentH);
    }

    private int indexAt(GridLayout grid, double mouseX, double mouseY) {
        if (!insideGrid(grid, mouseX, mouseY)) {
            return -1;
        }
        int column = ((int) mouseX - grid.cellsX()) / (CardRenderHelper.SMALL_CARD_WIDTH + CARD_GAP_X);
        int row = (int) Math.floor((mouseY - grid.viewY() + scrollOffset - GRID_PAD_TOP) / grid.rowH());
        if (column < 0 || column >= grid.columns() || row < 0) {
            return -1;
        }
        GridCell cell = cell(grid, row * grid.columns() + column);
        if (mouseX < cell.x() || mouseX > cell.x() + CardRenderHelper.SMALL_CARD_WIDTH || mouseY < cell.y() || mouseY > cell.y() + CardRenderHelper.SMALL_CARD_HEIGHT) {
            return -1;
        }
        return row * grid.columns() + column;
    }

    private GridCell cell(GridLayout grid, int index) {
        int row = index / grid.columns();
        int column = index % grid.columns();
        int x = grid.cellsX() + column * (CardRenderHelper.SMALL_CARD_WIDTH + CARD_GAP_X);
        int y = grid.viewY() + GRID_PAD_TOP + row * grid.rowH() - (int) Math.round(scrollOffset);
        return new GridCell(x, y);
    }

    private int firstVisibleIndex(GridLayout grid) {
        return Math.max(0, (int) Math.floor(Math.max(0.0D, scrollOffset - GRID_PAD_TOP) / grid.rowH())) * grid.columns();
    }

    private int lastVisibleIndex(GridLayout grid) {
        return Math.max(1, (int) Math.ceil(Math.max(0.0D, scrollOffset + grid.viewH() - GRID_PAD_TOP) / grid.rowH()) + 1) * grid.columns();
    }

    private boolean insideGrid(GridLayout grid, double mouseX, double mouseY) {
        return mouseX >= grid.viewX() && mouseX <= grid.viewX() + grid.viewW() && mouseY >= grid.viewY() && mouseY <= grid.viewY() + grid.viewH();
    }

    private boolean clickScrollbar(GridLayout grid, double mouseY, double mouseX) {
        if (!hasScrollbar(grid) || !scrollbarAt(grid, mouseX, mouseY)) {
            return false;
        }
        ScrollbarThumb thumb = scrollbarThumb(grid);
        draggingScrollbar = true;
        scrollbarGrabOffset = (int) Math.max(0.0D, Math.min(thumb.height(), mouseY - thumb.y()));
        dragScrollbar(grid, mouseY);
        return true;
    }

    private void dragScrollbar(GridLayout grid, double mouseY) {
        if (!hasScrollbar(grid)) {
            return;
        }
        ScrollbarThumb thumb = scrollbarThumb(grid);
        int trackRange = Math.max(1, grid.viewH() - thumb.height());
        int thumbY = (int) Math.max(grid.viewY(), Math.min(grid.viewY() + trackRange, mouseY - scrollbarGrabOffset));
        scrollOffset = clampScroll((thumbY - grid.viewY()) * maxScroll(grid) / (double) trackRange, grid);
    }

    private void renderGridScrollbar(GuiGraphics graphics, GridLayout grid) {
        if (!hasScrollbar(grid)) {
            return;
        }
        MoonSpireUiTextures.drawScrollbarTrack(graphics, grid.scrollbarX(), grid.viewY(), SCROLLBAR_WIDTH, grid.viewH());
        ScrollbarThumb thumb = scrollbarThumb(grid);
        MoonSpireUiTextures.drawScrollbarThumb(graphics, grid.scrollbarX() - 2, thumb.y(), SCROLLBAR_WIDTH + 4, thumb.height());
    }

    private boolean scrollbarAt(GridLayout grid, double mouseX, double mouseY) {
        return mouseX >= grid.scrollbarX() - (SCROLLBAR_HIT_WIDTH - SCROLLBAR_WIDTH) / 2.0D
                && mouseX <= grid.scrollbarX() + SCROLLBAR_HIT_WIDTH
                && mouseY >= grid.viewY()
                && mouseY <= grid.viewY() + grid.viewH();
    }

    private ScrollbarThumb scrollbarThumb(GridLayout grid) {
        int thumbH = Math.max(22, grid.viewH() * grid.viewH() / Math.max(1, grid.contentH()));
        int maxScroll = Math.max(1, maxScroll(grid));
        int y = grid.viewY() + (int) Math.round((grid.viewH() - thumbH) * scrollOffset / maxScroll);
        return new ScrollbarThumb(y, thumbH);
    }

    private boolean hasScrollbar(GridLayout grid) {
        return maxScroll(grid) > 0;
    }

    private int maxScroll(GridLayout grid) {
        return Math.max(0, grid.contentH() - grid.viewH());
    }

    private double clampScroll(double offset, GridLayout grid) {
        return Math.max(0.0D, Math.min(maxScroll(grid), offset));
    }

    private ButtonLayout buttonLayout() {
        int gap = DeveloperCenterScreen.BOTTOM_BUTTON_GAP;
        int w = DeveloperCenterScreen.BOTTOM_BUTTON_W;
        int h = DeveloperCenterScreen.BOTTOM_BUTTON_H;
        int totalW = w * 5 + gap * 4;
        return new ButtonLayout(Math.max(6, (width - totalW) / 2), Math.max(4, height - 28), w, h, gap);
    }

    private record GridLayout(int panelX, int panelY, int panelW, int panelH, int viewX, int viewY, int viewW, int viewH, int cellsX, int scrollbarX, int columns, int rowH, int contentH) {
    }

    private record GridCell(int x, int y) {
    }

    private record ScrollbarThumb(int y, int height) {
    }

    private record ButtonLayout(int x, int y, int w, int h, int gap) {
        int x(int index) {
            return x + index * (w + gap);
        }
    }
}
