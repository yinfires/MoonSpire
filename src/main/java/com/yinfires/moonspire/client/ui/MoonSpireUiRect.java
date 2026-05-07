package com.yinfires.moonspire.client.ui;

public record MoonSpireUiRect(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= right() && mouseY >= y && mouseY <= bottom();
    }

    public MoonSpireUiRect inset(int left, int top, int right, int bottom) {
        return new MoonSpireUiRect(x + left, y + top, Math.max(0, width - left - right), Math.max(0, height - top - bottom));
    }
}
