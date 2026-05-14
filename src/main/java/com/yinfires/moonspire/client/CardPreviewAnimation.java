package com.yinfires.moonspire.client;

import java.util.Objects;

public final class CardPreviewAnimation {
    private static final float POSITION_PER_TICK = 0.48F;
    private static final float SCALE_PER_TICK = 0.42F;
    private static final float PROGRESS_PER_TICK = 0.42F;
    private static final float POSITION_VISIBLE_THRESHOLD = 0.5F;
    private static final float SCALE_VISIBLE_THRESHOLD = 0.008F;
    private static final float PROGRESS_VISIBLE_THRESHOLD = 0.02F;
    private static final float POSITION_SNAP_THRESHOLD = 0.05F;
    private static final float SCALE_SNAP_THRESHOLD = 0.0015F;
    private static final float PROGRESS_SNAP_THRESHOLD = 0.003F;
    private static final float CLOSING_SCALE_EPSILON = 0.01F;

    private Object key;
    private float baseCenterX;
    private float baseCenterY;
    private float baseScale;
    private float centerX;
    private float centerY;
    private float scale;
    private int localWidth = CardRenderHelper.CARD_WIDTH;
    private int localHeight = CardRenderHelper.CARD_HEIGHT;
    private float progress;
    private float targetCenterX;
    private float targetCenterY;
    private float targetScale;
    private long lastAdvanceNanos;
    private boolean firstAdvance;

    public void setOpenTarget(Object key, float fromX, float fromY, float toX, float toY, float fromScale, float toScale) {
        setOpenTarget(key, fromX, fromY, toX, toY, fromScale, toScale, CardRenderHelper.CARD_WIDTH, CardRenderHelper.CARD_HEIGHT);
    }

    public void setOpenTarget(Object key, float fromX, float fromY, float toX, float toY, float fromScale, float toScale, int localWidth, int localHeight) {
        if (!Objects.equals(key, this.key)) {
            this.key = key;
            this.baseCenterX = fromX;
            this.baseCenterY = fromY;
            this.baseScale = fromScale;
            this.centerX = fromX;
            this.centerY = fromY;
            this.scale = fromScale;
            this.localWidth = localWidth;
            this.localHeight = localHeight;
            this.progress = 0.0F;
            this.lastAdvanceNanos = 0L;
            this.firstAdvance = true;
        }
        this.baseCenterX = fromX;
        this.baseCenterY = fromY;
        this.baseScale = fromScale;
        this.localWidth = localWidth;
        this.localHeight = localHeight;
        this.targetCenterX = toX;
        this.targetCenterY = toY;
        this.targetScale = toScale;
    }

    public void setClosingTarget() {
        if (key == null) {
            return;
        }
        targetCenterX = baseCenterX;
        targetCenterY = baseCenterY;
        targetScale = baseScale;
    }

    public void advance() {
        if (key == null) {
            return;
        }
        advance(frameTicks());
    }

    public void advance(float deltaTicks) {
        if (key == null) {
            return;
        }
        if (firstAdvance) {
            firstAdvance = false;
            return;
        }
        centerX = approach(centerX, targetCenterX, frameAmount(POSITION_PER_TICK, deltaTicks), POSITION_SNAP_THRESHOLD);
        centerY = approach(centerY, targetCenterY, frameAmount(POSITION_PER_TICK, deltaTicks), POSITION_SNAP_THRESHOLD);
        scale = approach(scale, targetScale, frameAmount(SCALE_PER_TICK, deltaTicks), SCALE_SNAP_THRESHOLD);
        progress = approach(progress, closing() ? 0.0F : 1.0F, frameAmount(PROGRESS_PER_TICK, deltaTicks), PROGRESS_SNAP_THRESHOLD);
    }

    public void snapPositionToTarget() {
        centerX = targetCenterX;
        centerY = targetCenterY;
    }

    public void clear() {
        key = null;
        progress = 0.0F;
        localWidth = CardRenderHelper.CARD_WIDTH;
        localHeight = CardRenderHelper.CARD_HEIGHT;
        lastAdvanceNanos = 0L;
        firstAdvance = false;
    }

    public Object key() {
        return key;
    }

    public boolean matches(Object key) {
        return Objects.equals(this.key, key);
    }

    public boolean visible() {
        return key != null
                && (progress > PROGRESS_VISIBLE_THRESHOLD
                || Math.abs(scale - baseScale) > SCALE_VISIBLE_THRESHOLD
                || Math.abs(centerX - baseCenterX) > POSITION_VISIBLE_THRESHOLD
                || Math.abs(centerY - baseCenterY) > POSITION_VISIBLE_THRESHOLD);
    }

    public boolean finishedClosing() {
        if (key == null) {
            return true;
        }
        boolean closed = closing() && !visible();
        if (closed) {
            centerX = baseCenterX;
            centerY = baseCenterY;
            scale = baseScale;
            progress = 0.0F;
        }
        return closed;
    }

    public boolean contains(double mouseX, double mouseY) {
        if (key == null) {
            return false;
        }
        float halfW = localWidth * scale / 2.0F;
        float halfH = localHeight * scale / 2.0F;
        return mouseX >= centerX - halfW && mouseX <= centerX + halfW
                && mouseY >= centerY - halfH && mouseY <= centerY + halfH;
    }

    public Bounds bounds() {
        int width = Math.round(localWidth * scale);
        int height = Math.round(localHeight * scale);
        int x = Math.round(centerX - width / 2.0F);
        int y = Math.round(centerY - height / 2.0F);
        return new Bounds(x, y, width, height);
    }

    public RenderBounds renderBounds() {
        float width = localWidth * scale;
        float height = localHeight * scale;
        float x = centerX - width / 2.0F;
        float y = centerY - height / 2.0F;
        return new RenderBounds(x, y, width, height);
    }

    public float centerX() {
        return centerX;
    }

    public float centerY() {
        return centerY;
    }

    public float scale() {
        return scale;
    }

    public float progress() {
        return progress;
    }

    public record Bounds(int x, int y, int width, int height) {
    }

    public record RenderBounds(float x, float y, float width, float height) {
    }

    private float frameTicks() {
        long now = System.nanoTime();
        if (lastAdvanceNanos == 0L) {
            lastAdvanceNanos = now;
            return 0.0F;
        }
        float deltaTicks = (now - lastAdvanceNanos) / 50_000_000.0F;
        lastAdvanceNanos = now;
        if (!Float.isFinite(deltaTicks) || deltaTicks <= 0.0F) {
            return 0.0F;
        }
        return Math.max(0.05F, Math.min(1.5F, deltaTicks));
    }

    private boolean closing() {
        return targetScale <= baseScale + CLOSING_SCALE_EPSILON;
    }

    private static float approach(float current, float target, float amount, float snapThreshold) {
        if (Math.abs(target - current) < snapThreshold) {
            return target;
        }
        return current + (target - current) * amount;
    }

    private static float frameAmount(float perTickAmount, float deltaTicks) {
        return 1.0F - (float) Math.pow(1.0F - perTickAmount, Math.max(0.0F, deltaTicks));
    }
}
