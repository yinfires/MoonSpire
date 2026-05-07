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
        int x = getX() + getWidth() / 2;
        int y = getY() + (getHeight() - font.lineHeight) / 2;
        graphics.drawCenteredString(font, message, x, y, color);
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
