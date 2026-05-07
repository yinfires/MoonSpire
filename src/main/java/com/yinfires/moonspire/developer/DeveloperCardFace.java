package com.yinfires.moonspire.developer;

public record DeveloperCardFace(
        String id,
        String imagePath,
        Area costArea,
        Area nameArea,
        Area artArea,
        Area typeArea,
        Area descriptionArea) {
    public static DeveloperCardFace defaultFace() {
        return new DeveloperCardFace(
                "default",
                "",
                new Area(6, 5, 20, 20),
                new Area(28, 11, 72, 13),
                new Area(18, 30, 92, 60),
                new Area(54, 90, 20, 12),
                new Area(16, 103, 96, 45));
    }

    public record Area(int x, int y, int width, int height) {
    }
}
