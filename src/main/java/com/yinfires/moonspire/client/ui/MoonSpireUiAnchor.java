package com.yinfires.moonspire.client.ui;

public enum MoonSpireUiAnchor {
    TOP_LEFT,
    TOP_RIGHT,
    TOP_CENTER,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    BOTTOM_CENTER,
    CENTER,
    MOUSE;

    public static MoonSpireUiAnchor fromString(String value) {
        for (MoonSpireUiAnchor anchor : values()) {
            if (anchor.name().equalsIgnoreCase(value)) {
                return anchor;
            }
        }
        return TOP_LEFT;
    }

    public String serializedName() {
        return name().toLowerCase();
    }
}
