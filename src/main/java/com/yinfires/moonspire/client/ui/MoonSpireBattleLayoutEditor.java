package com.yinfires.moonspire.client.ui;

import java.io.IOException;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class MoonSpireBattleLayoutEditor {
    private static final int HANDLE_SIZE = 5;
    private static boolean enabled;
    private static String selectedId = "energy";
    private static MoonSpireUiLayout workingLayout;
    private static boolean dragging;
    private static int dragMouseX;
    private static int dragMouseY;
    private static int dragStartX;
    private static int dragStartY;
    private static Component statusMessage = Component.empty();
    private static int statusTicks;

    private MoonSpireBattleLayoutEditor() {
    }

    public static boolean enabled() {
        return enabled && MoonSpireClientConfig.developerMode();
    }

    public static void toggle() {
        if (!MoonSpireClientConfig.developerMode()) {
            enabled = false;
            return;
        }
        enabled = !enabled;
        if (enabled) {
            workingLayout = MoonSpireUiLayout.current().copy();
        } else {
            dragging = false;
        }
    }

    public static void close() {
        enabled = false;
        dragging = false;
        workingLayout = null;
    }

    public static MoonSpireUiLayout layout() {
        if (enabled()) {
            if (workingLayout == null) {
                workingLayout = MoonSpireUiLayout.current().copy();
            }
            return workingLayout;
        }
        return MoonSpireUiLayout.current();
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!enabled() || button != 0) {
            return false;
        }
        String hit = elementAt(mouseX, mouseY, screenWidth, screenHeight);
        if (hit == null) {
            return false;
        }
        selectedId = hit;
        MoonSpireUiElement element = workingLayout.element(selectedId);
        dragging = true;
        dragMouseX = (int) mouseX;
        dragMouseY = (int) mouseY;
        dragStartX = element.x();
        dragStartY = element.y();
        return true;
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {
        if (!enabled() || !dragging || selectedId == null) {
            return false;
        }
        MoonSpireUiElement element = workingLayout.element(selectedId);
        int dx = adjustedDragX(element.anchor(), (int) mouseX - dragMouseX);
        int dy = adjustedDragY(element.anchor(), (int) mouseY - dragMouseY);
        workingLayout = workingLayout.update(element.withX(dragStartX + dx).withY(dragStartY + dy));
        return true;
    }

    public static boolean mouseReleased(int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    public static boolean mouseScrolled(double scrollY, boolean shift, boolean control) {
        if (!enabled() || selectedId == null) {
            return false;
        }
        MoonSpireUiElement element = workingLayout.element(selectedId);
        int delta = scrollY > 0.0D ? 1 : -1;
        if (control) {
            workingLayout = workingLayout.update(element.withHeight(element.height() + delta * 4));
        } else if (shift) {
            workingLayout = workingLayout.update(element.withWidth(element.width() + delta * 4));
        } else {
            workingLayout = workingLayout.update(element.withScale(element.scale() + delta * 0.05F));
        }
        return true;
    }

    public static boolean keyPressed(int keyCode, int modifiers) {
        if (!enabled() || selectedId == null) {
            return false;
        }
        boolean shift = (modifiers & 0x1) != 0;
        boolean control = (modifiers & 0x2) != 0;
        MoonSpireUiElement element = workingLayout.element(selectedId);
        int amount = shift ? 10 : 1;
        if (keyCode == 258) {
            cycleSelectedId(shift ? -1 : 1);
            return true;
        }
        if (keyCode == 262) {
            workingLayout = workingLayout.update(control ? element.withWidth(element.width() + amount) : element.withX(element.x() + amount));
            return true;
        }
        if (keyCode == 263) {
            workingLayout = workingLayout.update(control ? element.withWidth(element.width() - amount) : element.withX(element.x() - amount));
            return true;
        }
        if (keyCode == 264) {
            workingLayout = workingLayout.update(control ? element.withHeight(element.height() + amount) : element.withY(element.y() + amount));
            return true;
        }
        if (keyCode == 265) {
            workingLayout = workingLayout.update(control ? element.withHeight(element.height() - amount) : element.withY(element.y() - amount));
            return true;
        }
        if (keyCode == 83 && control) {
            save();
            return true;
        }
        if (keyCode == 82 && control) {
            reload();
            return true;
        }
        if (keyCode == 68 && control) {
            reset();
            return true;
        }
        return false;
    }

    public static void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!enabled()) {
            return;
        }
        MoonSpireUiRect selectedRect = null;
        for (MoonSpireUiElement element : workingLayout.elements()) {
            if (element.anchor() == MoonSpireUiAnchor.MOUSE) {
                continue;
            }
            MoonSpireUiRect rect = workingLayout.resolve(element.id(), screenWidth, screenHeight, mouseX, mouseY);
            if (element.id().equals(selectedId)) {
                selectedRect = rect;
            } else {
                int color = 0x998BD3FF;
                graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), color);
                graphics.fill(rect.right() - HANDLE_SIZE, rect.bottom() - HANDLE_SIZE, rect.right(), rect.bottom(), color);
            }
        }
        if (selectedRect != null) {
            int color = 0xFFFFFF00;
            graphics.renderOutline(selectedRect.x(), selectedRect.y(), selectedRect.width(), selectedRect.height(), color);
            graphics.fill(selectedRect.right() - HANDLE_SIZE, selectedRect.bottom() - HANDLE_SIZE, selectedRect.right(), selectedRect.bottom(), color);
        }
        MoonSpireUiElement selected = workingLayout.element(selectedId);
        int panelW = 238;
        int panelH = statusTicks > 0 ? 68 : 56;
        int panelX = 8;
        int panelY = screenHeight - panelH - 8;
        MoonSpireUiTextures.drawDarkPanel(graphics, panelX, panelY, panelW, panelH);
        graphics.drawString(font, Component.translatable("debug.moonspire.layout_editor.title"), panelX + 8, panelY + 7, 0xFFFFD166, false);
        graphics.drawString(font, Component.translatable("debug.moonspire.layout_editor.selected", selected.id()), panelX + 8, panelY + 20, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("debug.moonspire.layout_editor.values", selected.anchor().serializedName(), selected.x(), selected.y(), selected.width(), selected.height(), String.format(java.util.Locale.ROOT, "%.2f", selected.scale())), panelX + 8, panelY + 32, 0xFFE3C48C, false);
        if (statusTicks > 0) {
            graphics.drawString(font, statusMessage, panelX + 8, panelY + 46, 0xFFB8E6FF, false);
            statusTicks--;
        }
    }

    public static void save() {
        if (workingLayout == null) {
            return;
        }
        try {
            MoonSpireUiLayout.saveOverride(workingLayout);
            status(Component.translatable("debug.moonspire.layout_editor.saved"));
        } catch (IOException ignored) {
            status(Component.translatable("debug.moonspire.layout_editor.save_failed"));
        }
    }

    public static void reload() {
        MoonSpireUiLayout.reload();
        workingLayout = MoonSpireUiLayout.current().copy();
        status(Component.translatable("debug.moonspire.layout_editor.reloaded"));
    }

    public static void reset() {
        try {
            MoonSpireUiLayout.resetOverride();
            workingLayout = MoonSpireUiLayout.current().copy();
            status(Component.translatable("debug.moonspire.layout_editor.reset"));
        } catch (IOException ignored) {
            status(Component.translatable("debug.moonspire.layout_editor.reset_failed"));
        }
    }

    private static String elementAt(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        String found = null;
        for (MoonSpireUiElement element : workingLayout.elements()) {
            if (element.anchor() == MoonSpireUiAnchor.MOUSE) {
                continue;
            }
            MoonSpireUiRect rect = workingLayout.resolve(element.id(), screenWidth, screenHeight);
            if (rect.contains(mouseX, mouseY)) {
                found = element.id();
            }
        }
        return found;
    }

    private static void status(Component message) {
        statusMessage = message;
        statusTicks = 120;
    }

    private static void cycleSelectedId(int delta) {
        var ids = workingLayout.elements().stream()
                .filter(element -> element.anchor() != MoonSpireUiAnchor.MOUSE)
                .map(MoonSpireUiElement::id)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        int index = ids.indexOf(selectedId);
        if (index < 0) {
            index = 0;
        } else {
            index = Math.floorMod(index + delta, ids.size());
        }
        selectedId = ids.get(index);
        status(Component.translatable("debug.moonspire.layout_editor.selected", selectedId));
    }

    private static int adjustedDragX(MoonSpireUiAnchor anchor, int delta) {
        return switch (anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> -delta;
            default -> delta;
        };
    }

    private static int adjustedDragY(MoonSpireUiAnchor anchor, int delta) {
        return switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER -> -delta;
            default -> delta;
        };
    }
}
