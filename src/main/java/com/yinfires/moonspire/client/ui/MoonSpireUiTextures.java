package com.yinfires.moonspire.client.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yinfires.moonspire.MoonSpire;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class MoonSpireUiTextures {
    public static final int CHEST_OVERLAY_TOP = 0xC0101010;
    public static final int CHEST_OVERLAY_BOTTOM = 0xD0101010;
    public static final ResourceLocation PANEL_DARK = texture("gui/ui/panel_dark.png");
    public static final ResourceLocation BUTTON = texture("gui/ui/button.png");
    public static final ResourceLocation BUTTON_HOVERED = texture("gui/ui/button_hovered.png");
    public static final ResourceLocation BUTTON_DISABLED = texture("gui/ui/button_disabled.png");
    public static final ResourceLocation HEALTH_BAR = texture("gui/ui/health_bar.png");
    public static final ResourceLocation HEALTH_FILL = texture("gui/ui/health_fill.png");
    public static final ResourceLocation BLOCK_FILL = texture("gui/ui/block_fill.png");
    public static final ResourceLocation COST_AVAILABLE = texture("gui/ui/cost_available.png");
    public static final ResourceLocation COST_EMPTY = texture("gui/ui/cost_empty.png");
    public static final ResourceLocation DRAW_PILE = texture("gui/ui/draw_pile.png");
    public static final ResourceLocation DISCARD_PILE = texture("gui/ui/discard_pile.png");
    public static final ResourceLocation EXHAUST_PILE = texture("gui/ui/exhaust_pile.png");
    public static final ResourceLocation PILE_COUNT_BADGE = texture("gui/ui/pile_count_badge.png");
    public static final ResourceLocation TOOLTIP = texture("gui/ui/tooltip.png");
    public static final ResourceLocation SCROLLBAR_TRACK = texture("gui/ui/scrollbar_track.png");
    public static final ResourceLocation SCROLLBAR_THUMB = texture("gui/ui/scrollbar_thumb.png");
    public static final ResourceLocation FORGE_SLOT = texture("gui/ui/forge_slot.png");
    public static final ResourceLocation CARD_LIST_TOGGLE = texture("gui/ui/card_list_toggle.png");
    public static final ResourceLocation CARD_BASE = texture("gui/cards/card_base.png");
    public static final ResourceLocation BLOCK_GAIN_ANIMATION = texture("gui/animations/block_gain.png");

    private MoonSpireUiTextures() {
    }

    public static void drawDarkPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        blitNinePatch(graphics, PANEL_DARK, x, y, width, height, 8, 8, 8, 8, 32, 32);
    }

    public static void drawButton(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered, boolean active) {
        ResourceLocation texture = !active ? BUTTON_DISABLED : hovered ? BUTTON_HOVERED : BUTTON;
        blitButtonStretch(graphics, texture, x, y, width, height, 12, 11, 104, 42, 19, 128, 64);
    }

    public static void drawHealthBar(GuiGraphics graphics, int x, int y, int width, int height) {
        blitNinePatch(graphics, HEALTH_BAR, x, y, width, height, 8, 8, 8, 8, 32, 16);
    }

    public static void drawTooltip(GuiGraphics graphics, int x, int y, int width, int height) {
        blitNinePatch(graphics, TOOLTIP, x, y, width, height, 8, 8, 8, 8, 32, 32);
    }

    public static void drawScrollbarTrack(GuiGraphics graphics, int x, int y, int width, int height) {
        blitNinePatch(graphics, SCROLLBAR_TRACK, x, y, width, height, 3, 3, 3, 3, 12, 12);
    }

    public static void drawScrollbarThumb(GuiGraphics graphics, int x, int y, int width, int height) {
        blitNinePatch(graphics, SCROLLBAR_THUMB, x, y, width, height, 3, 3, 3, 3, 12, 12);
    }

    public static void drawForgeSlot(GuiGraphics graphics, int x, int y, int width, int height) {
        blitNinePatch(graphics, FORGE_SLOT, x, y, width, height, 8, 8, 8, 8, 32, 16);
    }

    public static void drawCardListToggle(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        graphics.blit(CARD_LIST_TOGGLE, x, y, width, height, 0.0F, 0.0F, 24, 56, 24, 56);
        if (hovered) {
            graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0x22FFFFFF);
        }
    }

    public static void drawOverlay(GuiGraphics graphics, int width, int height) {
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 1000.0F);
        RenderSystem.disableDepthTest();
        graphics.fillGradient(0, 0, width, height, CHEST_OVERLAY_TOP, CHEST_OVERLAY_BOTTOM);
        graphics.flush();
        RenderSystem.enableDepthTest();
        graphics.pose().popPose();
    }

    public static void drawWorldBillboard(Matrix4f matrix, VertexConsumer consumer, int x, int y, int width, int height, int light, float u0, float v0, float u1, float v1) {
        drawWorldBillboard(matrix, consumer, x, y, width, height, 0.0F, light, u0, v0, u1, v1);
    }

    public static void drawWorldBillboard(Matrix4f matrix, VertexConsumer consumer, int x, int y, int width, int height, float z, int light, float u0, float v0, float u1, float v1) {
        drawWorldBillboard(matrix, consumer, x, y, width, height, z, light, 255, 255, 255, 255, u0, v0, u1, v1);
    }

    public static void drawWorldBillboard(Matrix4f matrix, VertexConsumer consumer, int x, int y, int width, int height, float z, int light, int r, int g, int b, int a, float u0, float v0, float u1, float v1) {
        vertex(matrix, consumer, x, y + height, z, r, g, b, a, u0, v1, light);
        vertex(matrix, consumer, x + width, y + height, z, r, g, b, a, u1, v1, light);
        vertex(matrix, consumer, x + width, y, z, r, g, b, a, u1, v0, light);
        vertex(matrix, consumer, x, y, z, r, g, b, a, u0, v0, light);
    }

    public static void drawWorldNinePatch(Matrix4f matrix, VertexConsumer consumer, int x, int y, int width, int height, float z, int light, int left, int right, int top, int bottom, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int centerWidth = Math.max(0, width - left - right);
        int centerHeight = Math.max(0, height - top - bottom);
        int rightX = x + width - right;
        int bottomY = y + height - bottom;
        int centerX = x + left;
        int centerY = y + top;
        int srcRight = textureWidth - right;
        int srcBottom = textureHeight - bottom;
        drawWorldPatch(matrix, consumer, x, y, left, top, z, light, 0, 0, left, top, textureWidth, textureHeight);
        drawWorldPatch(matrix, consumer, centerX, y, centerWidth, top, z, light, left, 0, Math.max(0, textureWidth - left - right), top, textureWidth, textureHeight);
        drawWorldPatch(matrix, consumer, rightX, y, right, top, z, light, srcRight, 0, right, top, textureWidth, textureHeight);
        drawWorldPatch(matrix, consumer, x, centerY, left, centerHeight, z, light, 0, top, left, Math.max(0, textureHeight - top - bottom), textureWidth, textureHeight);
        drawWorldPatch(matrix, consumer, centerX, centerY, centerWidth, centerHeight, z, light, left, top, Math.max(0, textureWidth - left - right), Math.max(0, textureHeight - top - bottom), textureWidth, textureHeight);
        drawWorldPatch(matrix, consumer, rightX, centerY, right, centerHeight, z, light, srcRight, top, right, Math.max(0, textureHeight - top - bottom), textureWidth, textureHeight);
        drawWorldPatch(matrix, consumer, x, bottomY, left, bottom, z, light, 0, srcBottom, left, bottom, textureWidth, textureHeight);
        drawWorldPatch(matrix, consumer, centerX, bottomY, centerWidth, bottom, z, light, left, srcBottom, Math.max(0, textureWidth - left - right), bottom, textureWidth, textureHeight);
        drawWorldPatch(matrix, consumer, rightX, bottomY, right, bottom, z, light, srcRight, srcBottom, right, bottom, textureWidth, textureHeight);
    }

    public static void blitNinePatch(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int left, int right, int top, int bottom, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int centerWidth = Math.max(0, width - left - right);
        int centerHeight = Math.max(0, height - top - bottom);
        int rightX = x + width - right;
        int bottomY = y + height - bottom;
        int centerX = x + left;
        int centerY = y + top;
        int srcRight = textureWidth - right;
        int srcBottom = textureHeight - bottom;
        graphics.blit(texture, x, y, left, top, 0.0F, 0.0F, left, top, textureWidth, textureHeight);
        graphics.blit(texture, centerX, y, centerWidth, top, left, 0.0F, Math.max(0, textureWidth - left - right), top, textureWidth, textureHeight);
        graphics.blit(texture, rightX, y, right, top, srcRight, 0.0F, right, top, textureWidth, textureHeight);
        graphics.blit(texture, x, centerY, left, centerHeight, 0.0F, top, left, Math.max(0, textureHeight - top - bottom), textureWidth, textureHeight);
        graphics.blit(texture, centerX, centerY, centerWidth, centerHeight, left, top, Math.max(0, textureWidth - left - right), Math.max(0, textureHeight - top - bottom), textureWidth, textureHeight);
        graphics.blit(texture, rightX, centerY, right, centerHeight, srcRight, top, right, Math.max(0, textureHeight - top - bottom), textureWidth, textureHeight);
        graphics.blit(texture, x, bottomY, left, bottom, 0.0F, srcBottom, left, bottom, textureWidth, textureHeight);
        graphics.blit(texture, centerX, bottomY, centerWidth, bottom, left, srcBottom, Math.max(0, textureWidth - left - right), bottom, textureWidth, textureHeight);
        graphics.blit(texture, rightX, bottomY, right, bottom, srcRight, srcBottom, right, bottom, textureWidth, textureHeight);
    }

    public static void blitButtonStretch(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int srcX, int srcY, int srcW, int srcH, int srcCapWidth, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (srcCapWidth <= 0 || srcH <= 0) {
            graphics.blit(texture, x, y, width, height, (float) srcX, (float) srcY, srcW, srcH, textureWidth, textureHeight);
            return;
        }
        int capWidth = Math.max(1, Math.round(srcCapWidth * (height / (float) srcH)));
        if (width <= capWidth * 2) {
            graphics.blit(texture, x, y, width, height, (float) srcX, (float) srcY, srcW, srcH, textureWidth, textureHeight);
            return;
        }
        int centerWidth = width - capWidth * 2;
        int centerSrcWidth = srcW - srcCapWidth * 2;
        if (centerSrcWidth <= 0) {
            graphics.blit(texture, x, y, width, height, (float) srcX, (float) srcY, srcW, srcH, textureWidth, textureHeight);
            return;
        }
        int rightSrcX = srcX + srcW - srcCapWidth;
        graphics.blit(texture, x, y, capWidth, height, (float) srcX, (float) srcY, srcCapWidth, srcH, textureWidth, textureHeight);
        graphics.blit(texture, x + capWidth, y, centerWidth, height, (float) (srcX + srcCapWidth), (float) srcY, centerSrcWidth, srcH, textureWidth, textureHeight);
        graphics.blit(texture, x + capWidth + centerWidth, y, capWidth, height, (float) rightSrcX, (float) srcY, srcCapWidth, srcH, textureWidth, textureHeight);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer consumer, float x, float y, float z, float u, float v, int light) {
        vertex(matrix, consumer, x, y, z, 255, 255, 255, 255, u, v, light);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer consumer, float x, float y, float z, int r, int g, int b, int a, float u, float v, int light) {
        consumer.addVertex(matrix, x, y, z).setUv(u, v).setColor(r, g, b, a).setLight(light);
    }

    private static void drawWorldPatch(Matrix4f matrix, VertexConsumer consumer, int x, int y, int width, int height, float z, int light, int srcX, int srcY, int srcW, int srcH, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || srcW <= 0 || srcH <= 0) {
            return;
        }
        float u0 = srcX / (float) textureWidth;
        float v0 = srcY / (float) textureHeight;
        float u1 = (srcX + srcW) / (float) textureWidth;
        float v1 = (srcY + srcH) / (float) textureHeight;
        drawWorldBillboard(matrix, consumer, x, y, width, height, z, light, u0, v0, u1, v1);
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "textures/" + path);
    }
}
