package com.yinfires.moonspire.client;

import com.yinfires.moonspire.battle.BattleSnapshot;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.network.PrepareCardsPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class BattlePrepareScreen extends Screen {
    private final List<Integer> selected = new ArrayList<>();

    public BattlePrepareScreen() {
        super(Component.translatable("screen.moonspire.prepare"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("screen.moonspire.confirm_prepare"), button -> {
            PacketDistributor.sendToServer(new PrepareCardsPayload(List.copyOf(selected)));
            onClose();
        }).bounds(width / 2 - 70, height - 30, 140, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            onClose();
            return;
        }
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.moonspire.prepare_status", cost(snapshot), snapshot.playerMaxEnergy(), snapshot.phaseTicksLeft() / 20), width / 2, 27, 0xFFE7D7FF);
        int count = snapshot.hand().size();
        int totalWidth = count * CardRenderHelper.CARD_WIDTH + Math.max(0, count - 1) * 10;
        int x = Math.max(10, (width - totalWidth) / 2);
        int y = Math.max(48, height / 2 - CardRenderHelper.CARD_HEIGHT / 2);
        for (int i = 0; i < count; i++) {
            CardRenderHelper.renderCard(graphics, font, snapshot.hand().get(i), x + i * (CardRenderHelper.CARD_WIDTH + 10), y, selected.contains(i));
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
    public boolean isPauseScreen() {
        return false;
    }

    private boolean clickCard(double mouseX, double mouseY) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        int count = snapshot.hand().size();
        int totalWidth = count * CardRenderHelper.CARD_WIDTH + Math.max(0, count - 1) * 10;
        int startX = Math.max(10, (width - totalWidth) / 2);
        int y = Math.max(48, height / 2 - CardRenderHelper.CARD_HEIGHT / 2);
        for (int i = 0; i < count; i++) {
            int x = startX + i * (CardRenderHelper.CARD_WIDTH + 10);
            if (mouseX >= x && mouseX <= x + CardRenderHelper.CARD_WIDTH && mouseY >= y && mouseY <= y + CardRenderHelper.CARD_HEIGHT) {
                if (selected.contains(i)) {
                    selected.remove(Integer.valueOf(i));
                } else if (cost(snapshot) + snapshot.hand().get(i).cost() <= snapshot.playerMaxEnergy()) {
                    selected.add(i);
                }
                return true;
            }
        }
        return false;
    }

    private int cost(BattleSnapshot snapshot) {
        int cost = 0;
        for (int index : selected) {
            if (index >= 0 && index < snapshot.hand().size()) {
                CardInstance card = snapshot.hand().get(index);
                cost += card.cost();
            }
        }
        return cost;
    }
}
