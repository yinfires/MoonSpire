package com.yinfires.moonspire.client;

import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.PlayerCardData;
import com.yinfires.moonspire.network.SetDeckPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class DeckScreen extends Screen {
    private final List<UUID> selected = new ArrayList<>();
    private int scroll;

    public DeckScreen() {
        super(Component.translatable("screen.moonspire.deck"));
        selected.addAll(ClientCardState.cards().deck());
    }

    @Override
    protected void init() {
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.moonspire.save_deck"), button -> {
            PacketDistributor.sendToServer(new SetDeckPayload(List.copyOf(selected)));
            onClose();
        }).bounds(width / 2 - 100, bottom, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 + 4, bottom, 96, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        PlayerCardData data = ClientCardState.cards();
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.moonspire.deck_count", selected.size(), PlayerCardData.MIN_DECK_SIZE, PlayerCardData.MAX_DECK_SIZE), width / 2, 25, 0xFFE7D7FF);

        int columns = Math.max(1, (width - 40) / (CardRenderHelper.CARD_WIDTH + 10));
        int startX = (width - (columns * CardRenderHelper.CARD_WIDTH + (columns - 1) * 10)) / 2;
        int startY = 45;
        List<CardInstance> cards = data.collection();
        for (int i = scroll; i < cards.size(); i++) {
            int visible = i - scroll;
            int col = visible % columns;
            int row = visible / columns;
            int x = startX + col * (CardRenderHelper.CARD_WIDTH + 10);
            int y = startY + row * (CardRenderHelper.CARD_HEIGHT + 10);
            if (y > height - 150) {
                continue;
            }
            CardInstance card = cards.get(i);
            CardRenderHelper.renderCard(graphics, font, card, x, y, selected.contains(card.id()));
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && clickCard(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, ClientCardState.cards().collection().size() - 1);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean clickCard(double mouseX, double mouseY) {
        List<CardInstance> cards = ClientCardState.cards().collection();
        int columns = Math.max(1, (width - 40) / (CardRenderHelper.CARD_WIDTH + 10));
        int startX = (width - (columns * CardRenderHelper.CARD_WIDTH + (columns - 1) * 10)) / 2;
        int startY = 45;
        for (int i = scroll; i < cards.size(); i++) {
            int visible = i - scroll;
            int col = visible % columns;
            int row = visible / columns;
            int x = startX + col * (CardRenderHelper.CARD_WIDTH + 10);
            int y = startY + row * (CardRenderHelper.CARD_HEIGHT + 10);
            if (mouseX >= x && mouseX <= x + CardRenderHelper.CARD_WIDTH && mouseY >= y && mouseY <= y + CardRenderHelper.CARD_HEIGHT) {
                UUID id = cards.get(i).id();
                if (selected.contains(id)) {
                    selected.remove(id);
                } else if (selected.size() < PlayerCardData.MAX_DECK_SIZE) {
                    selected.add(id);
                }
                return true;
            }
        }
        return false;
    }
}
