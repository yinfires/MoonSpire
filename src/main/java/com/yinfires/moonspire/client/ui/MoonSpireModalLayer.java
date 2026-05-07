package com.yinfires.moonspire.client.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class MoonSpireModalLayer {
    public static final int BLOCKED_MOUSE = Integer.MIN_VALUE;

    private MoonSpireModalLayer() {
    }

    public static int widgetMouse(int mouse, boolean modalOpen) {
        return modalOpen ? BLOCKED_MOUSE : mouse;
    }

    public static void drawTopmostOverlay(GuiGraphics graphics, int width, int height) {
        MoonSpireUiTextures.drawOverlay(graphics, width, height);
    }

    public static void close(Runnable clearInteractionState, Runnable rebuildWidgets) {
        clearInteractionState.run();
        rebuildWidgets.run();
    }
}
