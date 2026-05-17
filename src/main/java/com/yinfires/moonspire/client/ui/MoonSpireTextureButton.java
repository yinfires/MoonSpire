package com.yinfires.moonspire.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class MoonSpireTextureButton extends AbstractButton {
    private final PressAction onPress;

    public MoonSpireTextureButton(int x, int y, int width, int height, Component message, PressAction onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        onPress.onPress(this);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isMouseOver(mouseX, mouseY) || isHoveredOrFocused();
        MoonSpireUiTextures.drawButton(graphics, getX(), getY(), getWidth(), getHeight(), hovered, isActive());
        renderString(graphics, Minecraft.getInstance().font, getFGColor());
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        Component message = getMessage();
        String text = message.getString();
        int availableW = Math.max(1, getWidth() - 8);
        int lineY = getY() + Math.max(0, (getHeight() - font.lineHeight) / 2);
        if (font.width(text) <= availableW) {
            int x = getX() + getWidth() / 2;
            graphics.drawCenteredString(font, message, x, lineY, color);
            return;
        }
        float scale = Math.max(0.55F, availableW / (float) Math.max(1, font.width(text)));
        int clipX = getX() + 4;
        int clipY = getY() + 2;
        int clipRight = getX() + getWidth() - 4;
        int clipBottom = getY() + getHeight() - 2;
        int scaledLineH = Math.max(1, Math.round(font.lineHeight * scale));
        graphics.enableScissor(clipX, clipY, clipRight, clipBottom);
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + getWidth() / 2.0F, getY() + Math.max(0, (getHeight() - scaledLineH) / 2.0F), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @FunctionalInterface
    public interface PressAction {
        void onPress(MoonSpireTextureButton button);
    }
}
