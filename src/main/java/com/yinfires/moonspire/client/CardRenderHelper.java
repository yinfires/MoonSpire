package com.yinfires.moonspire.client;

import com.yinfires.moonspire.card.CardInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class CardRenderHelper {
    public static final int CARD_WIDTH = 88;
    public static final int CARD_HEIGHT = 118;

    private CardRenderHelper() {
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected) {
        int border = selected ? 0xFFFFD166 : 0xFF6E5A8A;
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 0xDD17151F);
        graphics.renderOutline(x, y, CARD_WIDTH, CARD_HEIGHT, border);
        graphics.fill(x + 4, y + 4, x + CARD_WIDTH - 4, y + 22, 0xAA2E2540);
        graphics.drawString(font, trim(font, card.name(), CARD_WIDTH - 12), x + 6, y + 9, 0xFFEDE8FF, false);
        if (!card.sourceStack().isEmpty()) {
            graphics.renderFakeItem(card.sourceStack(), x + 8, y + 28);
        }
        graphics.drawString(font, "ATK " + card.attack(), x + 30, y + 29, 0xFFFF8A80, false);
        graphics.drawString(font, "DEF " + card.defense(), x + 30, y + 41, 0xFF8BD3FF, false);
        graphics.drawString(font, "C " + card.cost() + "  S " + card.speed(), x + 8, y + 61, 0xFFFFE6A7, false);
        graphics.drawString(font, trim(font, card.description(), CARD_WIDTH - 12), x + 6, y + 80, 0xFFC9C2DD, false);
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }
}
