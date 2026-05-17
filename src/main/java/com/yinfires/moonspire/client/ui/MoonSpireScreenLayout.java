package com.yinfires.moonspire.client.ui;

public final class MoonSpireScreenLayout {
    private MoonSpireScreenLayout() {
    }

    public static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static int fitWidth(int preferred, int min, int available) {
        return Math.max(min, Math.min(preferred, Math.max(min, available)));
    }

    public static int gap(int preferred, int min, int available, int neededAtPreferred) {
        if (available >= neededAtPreferred) {
            return preferred;
        }
        return Math.max(min, Math.min(preferred, preferred - (neededAtPreferred - available)));
    }

    public static int centerX(int screenWidth, int width, int margin) {
        return clamp((screenWidth - width) / 2, margin, Math.max(margin, screenWidth - margin - width));
    }

    public static int safeX(int x, int width, int screenWidth, int margin) {
        return clamp(x, margin, Math.max(margin, screenWidth - margin - width));
    }

    public static int safeY(int y, int height, int screenHeight, int margin) {
        return clamp(y, margin, Math.max(margin, screenHeight - margin - height));
    }

    public static float fitScale(int preferredW, int preferredH, int availableW, int availableH, float minScale) {
        if (preferredW <= 0 || preferredH <= 0) {
            return 1.0F;
        }
        float scale = Math.min(availableW / (float) preferredW, availableH / (float) preferredH);
        return Math.max(minScale, Math.min(1.0F, scale));
    }

    public static ButtonRows buttonRows(int screenWidth, int y, int count, int preferredW, int minW, int h, int preferredGap, int minGap, int margin) {
        int available = Math.max(1, screenWidth - margin * 2);
        int singleRowGap = gap(preferredGap, minGap, available, count * preferredW + Math.max(0, count - 1) * preferredGap);
        int singleRowW = count <= 0 ? 0 : count * preferredW + Math.max(0, count - 1) * singleRowGap;
        if (singleRowW <= available) {
            return new ButtonRows(centerX(screenWidth, singleRowW, margin), y, preferredW, h, singleRowGap, count, 1);
        }

        int columns = Math.max(1, Math.min(count, (available + minGap) / Math.max(1, minW + minGap)));
        int width = Math.max(minW, Math.min(preferredW, (available - Math.max(0, columns - 1) * minGap) / columns));
        int rowW = columns * width + Math.max(0, columns - 1) * minGap;
        int rows = (count + columns - 1) / columns;
        return new ButtonRows(centerX(screenWidth, rowW, margin), y, width, h, minGap, columns, rows);
    }

    public record ButtonRows(int x, int y, int w, int h, int gap, int columns, int rows) {
        public int x(int index) {
            return x + (index % columns) * (w + gap);
        }

        public int y(int index) {
            return y + (index / columns) * (h + gap);
        }

        public int height() {
            return rows * h + Math.max(0, rows - 1) * gap;
        }
    }
}
