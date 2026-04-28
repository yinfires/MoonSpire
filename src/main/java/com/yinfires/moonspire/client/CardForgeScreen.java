package com.yinfires.moonspire.client;

import com.yinfires.moonspire.card.CardFactory;
import com.yinfires.moonspire.network.ConvertSlotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CardForgeScreen extends Screen {
    private final BlockPos forgePos;
    private int scroll;

    public CardForgeScreen(BlockPos forgePos) {
        super(Component.translatable("screen.moonspire.card_forge"));
        this.forgePos = forgePos;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.moonspire.card_forge_hint"), width / 2, 28, 0xFFE7D7FF);
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        int startX = width / 2 - 116;
        int y = 50;
        int visible = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!CardFactory.canConvert(stack)) {
                continue;
            }
            if (visible++ < scroll) {
                continue;
            }
            if (y > height - 52) {
                break;
            }
            graphics.fill(startX, y, startX + 232, y + 26, 0xAA17151F);
            graphics.renderOutline(startX, y, 232, 26, 0xFF6E5A8A);
            graphics.renderFakeItem(stack, startX + 5, y + 5);
            graphics.drawString(font, stack.getHoverName(), startX + 28, y + 5, 0xFFEDE8FF, false);
            graphics.drawString(font, Component.translatable("screen.moonspire.slot", slot), startX + 28, y + 16, 0xFFC9C2DD, false);
            y += 30;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                PacketDistributor.sendToServer(new ConvertSlotPayload(slot, forgePos));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int slotAt(double mouseX, double mouseY) {
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        int startX = width / 2 - 116;
        int y = 50;
        int visible = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!CardFactory.canConvert(stack)) {
                continue;
            }
            if (visible++ < scroll) {
                continue;
            }
            if (mouseX >= startX && mouseX <= startX + 232 && mouseY >= y && mouseY <= y + 26) {
                return slot;
            }
            y += 30;
        }
        return -1;
    }
}
