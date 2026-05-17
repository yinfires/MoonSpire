package com.yinfires.moonspire.client.developer;

import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.client.CardRenderHelper;
import com.yinfires.moonspire.client.NoBlurScreen;
import com.yinfires.moonspire.client.ui.MoonSpireScreenLayout;
import com.yinfires.moonspire.client.ui.MoonSpireTextureButton;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.developer.DeveloperCardDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class DeveloperMonsterDeckScreen extends NoBlurScreen {
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
    private static final Comparator<DeveloperCardDefinition> CARD_NAME_ORDER = Comparator
            .comparing((DeveloperCardDefinition card) -> card.toCardInstance().nameComponent().getString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(card -> MoonSpireCardRegistry.registeredDeveloperId(card.id()), String.CASE_INSENSITIVE_ORDER);
    private static final Comparator<DeckEntry> DECK_ENTRY_ORDER = Comparator
            .comparing(DeckEntry::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(DeckEntry::id, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(DeckEntry::index);

    private final DeveloperCenterScreen parent;
    private final List<String> deckCardIds;
    private final boolean rewardMode;
    private final Set<String> pendingAddCardIds = new HashSet<>();
    private EditBox searchBox;
    private int selectedDeckIndex = -1;
    private double scrollOffset;
    private boolean addMode;
    private boolean dirty;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    private List<DeveloperCardDefinition> cachedAllCards;
    private Map<String, DeveloperCardDefinition> cachedCardsById = Map.of();
    private String cachedQuery = null;
    private List<DeveloperCardDefinition> cachedFilteredCards = List.of();

    DeveloperMonsterDeckScreen(DeveloperCenterScreen parent) {
        this(parent, false);
    }

    DeveloperMonsterDeckScreen(DeveloperCenterScreen parent, boolean rewardMode) {
        super(Component.translatable("screen.moonspire.developer_monster_deck"));
        this.parent = parent;
        this.rewardMode = rewardMode;
        this.deckCardIds = new ArrayList<>(rewardMode ? parent.selectedMonsterRewardCardIds() : parent.selectedMonsterDeckCardIds());
    }

    @Override
    protected void init() {
        clearWidgets();
        int searchX = Math.max(8, (width - SEARCH_W) / 2);
        searchBox = new EditBox(font, searchX, TOP + 18, Math.min(SEARCH_W, width - 16), SEARCH_H, Component.translatable("debug.moonspire.search"));
        searchBox.setHint(Component.translatable("debug.moonspire.search"));
        searchBox.visible = addMode;
        searchBox.active = addMode;
        addRenderableWidget(searchBox);

        ButtonLayout buttons = buttonLayout(addMode ? 2 : rewardMode ? 4 : 5);
        if (addMode) {
            addRenderableWidget(new MoonSpireTextureButton(buttons.x(0), buttons.y(0), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.add"), button -> confirmAdd()));
            addRenderableWidget(new MoonSpireTextureButton(buttons.x(1), buttons.y(1), buttons.w(), buttons.h(), Component.translatable("gui.cancel"), button -> cancelAdd()));
        } else {
            addRenderableWidget(new MoonSpireTextureButton(buttons.x(0), buttons.y(0), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.add"), button -> openAddMode()));
            addRenderableWidget(new MoonSpireTextureButton(buttons.x(1), buttons.y(1), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.delete"), button -> deleteSelected()));
            if (rewardMode) {
                addRenderableWidget(new MoonSpireTextureButton(buttons.x(2), buttons.y(2), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.reset"), button -> resetDeck()));
                addRenderableWidget(new MoonSpireTextureButton(buttons.x(3), buttons.y(3), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.done"), button -> done()));
            } else {
                addRenderableWidget(new MoonSpireTextureButton(buttons.x(2), buttons.y(2), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.copy"), button -> copySelected()));
                addRenderableWidget(new MoonSpireTextureButton(buttons.x(3), buttons.y(3), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.reset"), button -> resetDeck()));
                addRenderableWidget(new MoonSpireTextureButton(buttons.x(4), buttons.y(4), buttons.w(), buttons.h(), Component.translatable("debug.moonspire.done"), button -> done()));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Component header = addMode
                ? Component.translatable(rewardMode ? "screen.moonspire.developer_monster_rewards_add" : "screen.moonspire.developer_monster_deck_add")
                : Component.translatable(rewardMode ? "screen.moonspire.developer_monster_rewards_for" : "screen.moonspire.developer_monster_deck_for", parent.selectedMonsterId());
        graphics.drawCenteredString(font, header, width / 2, TOP, 0xFFFFD166);
        GridLayout grid = grid(cardCount());
        scrollOffset = clampScroll(scrollOffset, grid);
        MoonSpireUiTextures.drawDarkPanel(graphics, grid.panelX(), grid.panelY(), grid.panelW(), grid.panelH());
        renderCardGrid(graphics, grid);
        renderGridScrollbar(graphics, grid);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            GridLayout grid = grid(cardCount());
            if (clickScrollbar(grid, mouseY, mouseX)) {
                return true;
            }
            int index = indexAt(grid, mouseX, mouseY);
            if (addMode) {
                List<DeveloperCardDefinition> cards = filteredCards();
                if (index >= 0 && index < cards.size()) {
                    String id = MoonSpireCardRegistry.registeredDeveloperId(cards.get(index).id());
                    if (!pendingAddCardIds.remove(id)) {
                        pendingAddCardIds.add(id);
                    }
                    return true;
                }
            } else if (index >= 0 && index < deckCardIds.size()) {
                selectedDeckIndex = visibleDeckEntries().get(index).index();
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
            dragScrollbar(grid(cardCount()), mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        GridLayout grid = grid(cardCount());
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
        if (addMode) {
            cancelAdd();
        } else {
            done();
        }
    }

    private void openAddMode() {
        addMode = true;
        pendingAddCardIds.clear();
        scrollOffset = 0.0D;
        init();
    }

    private void confirmAdd() {
        for (DeveloperCardDefinition card : filteredCards()) {
            String id = MoonSpireCardRegistry.registeredDeveloperId(card.id());
            if (pendingAddCardIds.contains(id)) {
                addCardId(id);
            }
        }
        pendingAddCardIds.clear();
        addMode = false;
        scrollOffset = 0.0D;
        if (dirty) {
            applyToParent();
        }
        init();
    }

    private void cancelAdd() {
        pendingAddCardIds.clear();
        addMode = false;
        scrollOffset = 0.0D;
        init();
    }

    private void deleteSelected() {
        if (selectedDeckIndex >= 0 && selectedDeckIndex < deckCardIds.size()) {
            deckCardIds.remove(selectedDeckIndex);
            selectedDeckIndex = Math.min(selectedDeckIndex, deckCardIds.size() - 1);
            dirty = true;
            applyToParent();
        }
    }

    private void copySelected() {
        if (!rewardMode && selectedDeckIndex >= 0 && selectedDeckIndex < deckCardIds.size()) {
            deckCardIds.add(selectedDeckIndex + 1, deckCardIds.get(selectedDeckIndex));
            selectedDeckIndex++;
            dirty = true;
            applyToParent();
        }
    }

    private void resetDeck() {
        deckCardIds.clear();
        deckCardIds.addAll(rewardMode ? parent.defaultSelectedMonsterRewardCardIds() : parent.savedSelectedMonsterDeckCardIds());
        selectedDeckIndex = deckCardIds.isEmpty() ? -1 : Math.min(Math.max(0, selectedDeckIndex), deckCardIds.size() - 1);
        scrollOffset = 0.0D;
        dirty = true;
        if (rewardMode) {
            parent.resetSelectedMonsterRewardCardIdsToDefault();
            dirty = false;
        } else {
            applyToParent();
        }
    }

    private void done() {
        if (dirty) {
            applyToParent();
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void applyToParent() {
        if (rewardMode) {
            parent.setSelectedMonsterRewardCardIds(deckCardIds);
        } else {
            parent.setSelectedMonsterDeckCardIds(deckCardIds);
        }
    }

    private void addCardId(String id) {
        if (!rewardMode || !deckCardIds.contains(id)) {
            deckCardIds.add(id);
            dirty = true;
        }
    }

    private int cardCount() {
        return addMode ? filteredCards().size() : deckCardIds.size();
    }

    private List<DeveloperCardDefinition> filteredCards() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.equals(cachedQuery)) {
            return cachedFilteredCards;
        }
        cachedQuery = query;
        cachedFilteredCards = allCards().stream()
                .filter(card -> !MoonSpireCardRegistry.SELF_DESTRUCT_VIEW_CARD_ID.equals(MoonSpireCardRegistry.registeredDeveloperId(card.id())))
                .filter(card -> query.isBlank()
                        || MoonSpireCardRegistry.registeredDeveloperId(card.id()).toLowerCase(Locale.ROOT).contains(query)
                        || card.nameKey().toLowerCase(Locale.ROOT).contains(query)
                        || card.toCardInstance().nameComponent().getString().toLowerCase(Locale.ROOT).contains(query))
                .sorted(CARD_NAME_ORDER)
                .toList();
        return cachedFilteredCards;
    }

    private List<DeveloperCardDefinition> allCards() {
        if (cachedAllCards == null) {
            cachedAllCards = parent.deckSelectionCards();
            cachedCardsById = cachedAllCards.stream()
                    .collect(Collectors.toMap(card -> MoonSpireCardRegistry.registeredDeveloperId(card.id()), card -> card, (first, second) -> second));
        }
        return cachedAllCards;
    }

    private DeveloperCardDefinition cardByDeckId(String id) {
        allCards();
        return cachedCardsById.get(MoonSpireCardRegistry.registeredDeveloperId(id));
    }

    private List<DeckEntry> visibleDeckEntries() {
        allCards();
        List<DeckEntry> entries = new ArrayList<>(deckCardIds.size());
        for (int i = 0; i < deckCardIds.size(); i++) {
            String id = deckCardIds.get(i);
            entries.add(new DeckEntry(i, id, cardByDeckId(id)));
        }
        entries.sort(DECK_ENTRY_ORDER);
        return entries;
    }

    private void renderCardGrid(GuiGraphics graphics, GridLayout grid) {
        List<DeveloperCardDefinition> addCards = addMode ? filteredCards() : List.of();
        List<DeckEntry> deckEntries = addMode ? List.of() : visibleDeckEntries();
        graphics.enableScissor(grid.viewX(), grid.viewY(), grid.viewX() + grid.viewW(), grid.viewY() + grid.viewH());
        for (int i = firstVisibleIndex(grid); i < Math.min(cardCount(), lastVisibleIndex(grid)); i++) {
            GridCell cell = cell(grid, i);
            DeckEntry entry = addMode ? null : deckEntries.get(i);
            DeveloperCardDefinition card = addMode ? addCards.get(i) : entry.card();
            String id = addMode ? MoonSpireCardRegistry.registeredDeveloperId(card.id()) : entry.id();
            boolean selected = addMode ? pendingAddCardIds.contains(id) : entry.index() == selectedDeckIndex;
            if (card != null) {
                CardRenderHelper.renderSmallCard(graphics, font, card.toCardInstance(), cell.x(), cell.y(), false, false, true, parent.data());
            } else {
                graphics.fill(cell.x(), cell.y(), cell.x() + CardRenderHelper.SMALL_CARD_WIDTH, cell.y() + CardRenderHelper.SMALL_CARD_HEIGHT, 0x66402020);
                drawCenteredFitted(graphics, id, cell.x() + 4, cell.y() + 32, CardRenderHelper.SMALL_CARD_WIDTH - 8, LABEL_H, 0xFFFFB8B8);
            }
            if (selected) {
                DeveloperCenterScreen.renderSelectableCardOutline(graphics, cell.x(), cell.y(), CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
            }
            String label = addMode ? card.toCardInstance().nameComponent().getString() : entry.displayName();
            drawCenteredFitted(graphics, label, cell.x(), cell.y() + CardRenderHelper.SMALL_CARD_HEIGHT + 2, CardRenderHelper.SMALL_CARD_WIDTH, LABEL_H, selected ? 0xFFB8E6FF : 0xFFEDE8FF);
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
        int gapX = Math.max(4, Math.min(CARD_GAP_X, viewW / 12));
        int columns = Math.max(1, (viewW + gapX) / (CardRenderHelper.SMALL_CARD_WIDTH + gapX));
        int usedW = columns * CardRenderHelper.SMALL_CARD_WIDTH + (columns - 1) * gapX;
        int cellsX = viewX + Math.max(0, (viewW - usedW) / 2);
        int rowH = CardRenderHelper.SMALL_CARD_HEIGHT + LABEL_H + CARD_GAP_Y;
        int rows = itemCount <= 0 ? 0 : (itemCount + columns - 1) / columns;
        int contentH = rows <= 0 ? 0 : GRID_PAD_TOP + rows * rowH - CARD_GAP_Y + GRID_PAD_BOTTOM;
        int scrollbarX = panelX + panelW - SCROLLBAR_HIT_WIDTH / 2 - 5;
        return new GridLayout(panelX, panelY, panelW, panelH, viewX, viewY, viewW, viewH, cellsX, scrollbarX, columns, rowH, contentH, gapX);
    }

    private int indexAt(GridLayout grid, double mouseX, double mouseY) {
        if (!insideGrid(grid, mouseX, mouseY)) {
            return -1;
        }
        int column = ((int) mouseX - grid.cellsX()) / (CardRenderHelper.SMALL_CARD_WIDTH + grid.gapX());
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
        int x = grid.cellsX() + column * (CardRenderHelper.SMALL_CARD_WIDTH + grid.gapX());
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

    private ButtonLayout buttonLayout(int count) {
        int preferredTotal = count * DeveloperCenterScreen.BOTTOM_BUTTON_W + Math.max(0, count - 1) * DeveloperCenterScreen.BOTTOM_BUTTON_GAP;
        if (width >= preferredTotal + 12) {
            return new ButtonLayout((width - preferredTotal) / 2, height - 28, DeveloperCenterScreen.BOTTOM_BUTTON_W, DeveloperCenterScreen.BOTTOM_BUTTON_H, DeveloperCenterScreen.BOTTOM_BUTTON_GAP, count);
        }
        MoonSpireScreenLayout.ButtonRows rows = MoonSpireScreenLayout.buttonRows(width, Math.max(4, height - 28), count, DeveloperCenterScreen.BOTTOM_BUTTON_W, 52, DeveloperCenterScreen.BOTTOM_BUTTON_H, DeveloperCenterScreen.BOTTOM_BUTTON_GAP, 4, 6);
        return new ButtonLayout(rows.x(), Math.max(4, height - rows.height() - 8), rows.w(), rows.h(), rows.gap(), rows.columns());
    }

    private record ButtonLayout(int x, int y, int w, int h, int gap, int columns) {
        int x(int index) {
            return x + (index % columns) * (w + gap);
        }

        int y(int index) {
            return y + (index / columns) * (h + gap);
        }
    }

    private record GridLayout(int panelX, int panelY, int panelW, int panelH, int viewX, int viewY, int viewW, int viewH, int cellsX, int scrollbarX, int columns, int rowH, int contentH, int gapX) {
    }

    private record GridCell(int x, int y) {
    }

    private record DeckEntry(int index, String id, DeveloperCardDefinition card) {
        private String displayName() {
            return card == null ? id : card.toCardInstance().nameComponent().getString();
        }
    }

    private record ScrollbarThumb(int y, int height) {
    }
}
