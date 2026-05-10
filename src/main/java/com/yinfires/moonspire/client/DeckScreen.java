package com.yinfires.moonspire.client;

import com.yinfires.moonspire.MoonSpirePerfDiagnostics;
import com.yinfires.moonspire.card.CardInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class DeckScreen extends NoBlurScreen {
    private static final int BOTTOM_RESERVE = 0;
    private final CardGridPanel cardPanel;
    private long syncedCardVersion;
    private int perfFrameIndex;

    public DeckScreen() {
        super(Component.translatable("screen.moonspire.deck"));
        cardPanel = new CardGridPanel(ClientCardState.cards().collection(), title, ClientCardState.version());
        syncedCardVersion = ClientCardState.version();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long start = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        try (CardRenderHelper.CardRenderContext ignored = CardRenderHelper.openFrameContext()) {
            long setCardsStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
            long setCardsNanos = 0L;
            if (syncedCardVersion != ClientCardState.version()) {
                cardPanel.setCards(ClientCardState.cards().collection(), ClientCardState.version());
                syncedCardVersion = ClientCardState.version();
            }
            if (MoonSpirePerfDiagnostics.enabled()) {
                setCardsNanos = MoonSpirePerfDiagnostics.now() - setCardsStart;
            }
            cardPanel.render(graphics, font, width, height, mouseX, mouseY, BOTTOM_RESERVE, card -> false,
                    (previewGraphics, previewFont, card, x, y, selectedCard) -> CardRenderHelper.renderCard(previewGraphics, previewFont, card, x, y, selectedCard, false));
            renderWidgets(graphics, mouseX, mouseY, partialTick);
            if (MoonSpirePerfDiagnostics.enabled() && perfFrameIndex < 10) {
                perfFrameIndex++;
                long elapsed = MoonSpirePerfDiagnostics.now() - start;
                MoonSpirePerfDiagnostics.markOperation("client.deck.render", elapsed,
                        "frameIndex=" + perfFrameIndex
                                + " setCardsMs=" + MoonSpirePerfDiagnostics.millis(setCardsNanos)
                                + " " + cardPanel.lastFrameStats().summary()
                                + " " + CardRenderHelper.frameStats().summary());
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (cardPanel.mouseClicked(width, height, BOTTOM_RESERVE, mouseX, mouseY, button)) {
            return true;
        }
        if (cardPanel.previewAt(width, height, BOTTOM_RESERVE, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (cardPanel.mouseReleased(button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && cardPanel.mouseDragged(width, height, BOTTOM_RESERVE, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return cardPanel.scroll(width, height, BOTTOM_RESERVE, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.OPEN_DECK.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_K) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
