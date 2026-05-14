package com.yinfires.moonspire.client;

import com.yinfires.moonspire.card.CardFactory;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.client.ui.MoonSpireTextureButton;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.network.ConvertSlotPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CardForgeScreen extends NoBlurScreen {
    private static final int SLOT_WIDTH = 232;
    private static final int SLOT_HEIGHT = 26;
    private static final int SLOT_GAP = 4;
    private static final int PREVIEW_GAP = 24;
    private static final int SCREEN_PADDING = 8;
    private static final int VIEW_TOP = 50;
    private static final int VIEW_BOTTOM_RESERVE = 52;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int SCROLLBAR_HIT_WIDTH = 20;
    private static final int SCROLL_STEP = 24;

    private final BlockPos forgePos;
    private double scrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;

    public CardForgeScreen(BlockPos forgePos) {
        super(Component.translatable("screen.moonspire.card_forge"));
        this.forgePos = forgePos;
    }

    @Override
    protected void init() {
        addRenderableWidget(new MoonSpireTextureButton(width / 2 - 50, height - 28, 100, 20, Component.translatable("gui.done"), button -> onClose()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.moonspire.card_forge_hint"), width / 2, 28, 0xFFE7D7FF);
        List<ForgeSlotEntry> entries = forgeSlots();
        ForgeLayout layout = layout(entries.size());
        constrainScroll(layout);
        int hoveredIndex = hoveredIndexAt(layout, entries, mouseX, mouseY);
        graphics.enableScissor(layout.viewX(), layout.viewY(), layout.viewX() + layout.viewW(), layout.viewY() + layout.viewH());
        for (int i = 0; i < entries.size(); i++) {
            int y = rowY(layout, i);
            if (y > layout.viewY() + layout.viewH() || y + SLOT_HEIGHT < layout.viewY()) {
                continue;
            }
            ForgeSlotEntry entry = entries.get(i);
            MoonSpireUiTextures.drawForgeSlot(graphics, layout.contentX(), y, SLOT_WIDTH, SLOT_HEIGHT);
            if (i == hoveredIndex) {
                drawRowHighlight(graphics, layout.contentX(), y);
            }
            graphics.renderFakeItem(entry.stack(), layout.contentX() + 5, y + 5);
            graphics.drawString(font, entry.stack().getHoverName(), layout.contentX() + 28, y + 5, 0xFFEDE8FF, false);
            graphics.drawString(font, Component.translatable("screen.moonspire.slot", entry.slot()), layout.contentX() + 28, y + 16, 0xFFC9C2DD, false);
        }
        graphics.disableScissor();
        renderScrollbar(graphics, layout);
        if (hoveredIndex >= 0 && hoveredIndex < entries.size()) {
            renderCardPreview(graphics, entries.get(hoveredIndex), layout);
        }
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<ForgeSlotEntry> entries = forgeSlots();
            ForgeLayout layout = layout(entries.size());
            constrainScroll(layout);
            if (hasScrollbar(layout) && scrollbarAt(layout, mouseX, mouseY)) {
                ScrollbarThumb thumb = scrollbarThumb(layout);
                draggingScrollbar = true;
                scrollbarGrabOffset = (int) Math.max(0.0D, Math.min(thumb.height(), mouseY - thumb.y()));
                dragScrollbar(layout, mouseY);
                return true;
            }
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                PacketDistributor.sendToServer(new ConvertSlotPayload(slot, forgePos));
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
            dragScrollbar(layout(forgeSlots().size()), mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ForgeLayout layout = layout(forgeSlots().size());
        if (!hasScrollbar(layout) || scrollY == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollOffset -= scrollY * SCROLL_STEP;
        constrainScroll(layout);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int slotAt(double mouseX, double mouseY) {
        List<ForgeSlotEntry> entries = forgeSlots();
        ForgeLayout layout = layout(entries.size());
        int index = hoveredIndexAt(layout, entries, mouseX, mouseY);
        return index >= 0 ? entries.get(index).slot() : -1;
    }

    private List<ForgeSlotEntry> forgeSlots() {
        List<ForgeSlotEntry> entries = new ArrayList<>();
        if (Minecraft.getInstance().player == null) {
            return entries;
        }
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (CardFactory.canConvert(stack)) {
                CardInstance card = CardFactory.fromItem(stack);
                if (card != null) {
                    entries.add(new ForgeSlotEntry(slot, stack, card));
                }
            }
        }
        return entries;
    }

    private ForgeLayout layout(int entryCount) {
        int totalW = SLOT_WIDTH + SCROLLBAR_HIT_WIDTH + 8 + PREVIEW_GAP + CardRenderHelper.CARD_WIDTH;
        int viewX = Math.max(SCREEN_PADDING, (width - totalW) / 2);
        int viewY = VIEW_TOP;
        int viewH = Math.max(SLOT_HEIGHT, height - VIEW_TOP - VIEW_BOTTOM_RESERVE);
        int viewW = SLOT_WIDTH + SCROLLBAR_HIT_WIDTH + 8;
        int contentH = entryCount <= 0 ? 0 : entryCount * (SLOT_HEIGHT + SLOT_GAP) - SLOT_GAP;
        int scrollbarX = viewX + SLOT_WIDTH + 10;
        int previewX = Math.min(width - SCREEN_PADDING - CardRenderHelper.CARD_WIDTH, viewX + viewW + PREVIEW_GAP);
        previewX = Math.max(viewX + SLOT_WIDTH + 4, previewX);
        int previewY = Math.max(viewY, viewY + (viewH - CardRenderHelper.CARD_HEIGHT) / 2);
        previewY = Math.min(height - VIEW_BOTTOM_RESERVE - CardRenderHelper.CARD_HEIGHT, previewY);
        return new ForgeLayout(viewX, viewY, viewW, viewH, viewX, scrollbarX, contentH, previewX, previewY);
    }

    private int rowY(ForgeLayout layout, int index) {
        return layout.viewY() + index * (SLOT_HEIGHT + SLOT_GAP) - (int) Math.round(clampedScrollOffset(layout));
    }

    private void renderScrollbar(GuiGraphics graphics, ForgeLayout layout) {
        if (!hasScrollbar(layout)) {
            return;
        }
        MoonSpireUiTextures.drawScrollbarTrack(graphics, layout.scrollbarX(), layout.viewY(), SCROLLBAR_WIDTH, layout.viewH());
        ScrollbarThumb thumb = scrollbarThumb(layout);
        MoonSpireUiTextures.drawScrollbarThumb(graphics, layout.scrollbarX() - 2, thumb.y(), SCROLLBAR_WIDTH + 4, thumb.height());
    }

    private int hoveredIndexAt(ForgeLayout layout, List<ForgeSlotEntry> entries, double mouseX, double mouseY) {
        if (mouseX < layout.contentX() || mouseX > layout.contentX() + SLOT_WIDTH || mouseY < layout.viewY() || mouseY > layout.viewY() + layout.viewH()) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            int y = rowY(layout, i);
            if (mouseY >= y && mouseY <= y + SLOT_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    private void drawRowHighlight(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 2, y + 2, x + SLOT_WIDTH - 2, y + SLOT_HEIGHT - 2, 0x33FFF2C2);
        graphics.fill(x + 1, y + 1, x + SLOT_WIDTH - 1, y + 2, 0xFFFFD66B);
        graphics.fill(x + 1, y + SLOT_HEIGHT - 2, x + SLOT_WIDTH - 1, y + SLOT_HEIGHT - 1, 0xFFFFD66B);
        graphics.fill(x + 1, y + 1, x + 2, y + SLOT_HEIGHT - 1, 0xFFFFD66B);
        graphics.fill(x + SLOT_WIDTH - 2, y + 1, x + SLOT_WIDTH - 1, y + SLOT_HEIGHT - 1, 0xFFFFD66B);
    }

    private void renderCardPreview(GuiGraphics graphics, ForgeSlotEntry entry, ForgeLayout layout) {
        CardInstance card = entry.card();
        String contentKey = CardRenderHelper.contentKey(card);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 120.0F);
        try {
            CardRenderHelper.renderCard(graphics, font, card, layout.previewX(), layout.previewY(), false, CardRenderHelper.CardValues.original(card), false, false, contentKey);
            CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, layout.previewX(), layout.previewY(), CardRenderHelper.CARD_WIDTH, CardRenderHelper.CARD_HEIGHT, width, height);
        } finally {
            graphics.pose().popPose();
        }
    }

    private boolean scrollbarAt(ForgeLayout layout, double mouseX, double mouseY) {
        return mouseX >= layout.scrollbarX() - (SCROLLBAR_HIT_WIDTH - SCROLLBAR_WIDTH) / 2.0D
                && mouseX <= layout.scrollbarX() + SCROLLBAR_HIT_WIDTH
                && mouseY >= layout.viewY()
                && mouseY <= layout.viewY() + layout.viewH();
    }

    private void dragScrollbar(ForgeLayout layout, double mouseY) {
        if (!hasScrollbar(layout)) {
            return;
        }
        ScrollbarThumb thumb = scrollbarThumb(layout);
        int trackRange = Math.max(1, layout.viewH() - thumb.height());
        int thumbY = (int) Math.max(layout.viewY(), Math.min(layout.viewY() + trackRange, mouseY - scrollbarGrabOffset));
        scrollOffset = (thumbY - layout.viewY()) * maxScroll(layout) / (double) trackRange;
        constrainScroll(layout);
    }

    private ScrollbarThumb scrollbarThumb(ForgeLayout layout) {
        int thumbH = Math.max(22, layout.viewH() * layout.viewH() / Math.max(1, layout.contentH()));
        int maxScroll = Math.max(1, maxScroll(layout));
        int y = layout.viewY() + (int) Math.round((layout.viewH() - thumbH) * clampedScrollOffset(layout) / maxScroll);
        return new ScrollbarThumb(y, thumbH);
    }

    private boolean hasScrollbar(ForgeLayout layout) {
        return maxScroll(layout) > 0;
    }

    private int maxScroll(ForgeLayout layout) {
        return Math.max(0, layout.contentH() - layout.viewH());
    }

    private void constrainScroll(ForgeLayout layout) {
        int maxScroll = maxScroll(layout);
        scrollOffset = Math.max(0.0D, Math.min(maxScroll, scrollOffset));
    }

    private double clampedScrollOffset(ForgeLayout layout) {
        return Math.max(0.0D, Math.min(maxScroll(layout), scrollOffset));
    }

    private record ForgeSlotEntry(int slot, ItemStack stack, CardInstance card) {
    }

    private record ForgeLayout(int viewX, int viewY, int viewW, int viewH, int contentX, int scrollbarX, int contentH, int previewX, int previewY) {
    }

    private record ScrollbarThumb(int y, int height) {
    }
}
