package com.yinfires.moonspire.client.ui;

public record MoonSpireUiElement(String id, MoonSpireUiAnchor anchor, int x, int y, int width, int height, float scale) {
    public MoonSpireUiElement {
        width = Math.max(1, width);
        height = Math.max(1, height);
        scale = Math.max(0.1F, scale);
    }

    public MoonSpireUiElement withX(int nextX) {
        return new MoonSpireUiElement(id, anchor, nextX, y, width, height, scale);
    }

    public MoonSpireUiElement withY(int nextY) {
        return new MoonSpireUiElement(id, anchor, x, nextY, width, height, scale);
    }

    public MoonSpireUiElement withWidth(int nextWidth) {
        return new MoonSpireUiElement(id, anchor, x, y, nextWidth, height, scale);
    }

    public MoonSpireUiElement withHeight(int nextHeight) {
        return new MoonSpireUiElement(id, anchor, x, y, width, nextHeight, scale);
    }

    public MoonSpireUiElement withScale(float nextScale) {
        return new MoonSpireUiElement(id, anchor, x, y, width, height, nextScale);
    }

    public MoonSpireUiElement withAnchor(MoonSpireUiAnchor nextAnchor) {
        return new MoonSpireUiElement(id, nextAnchor, x, y, width, height, scale);
    }
}
