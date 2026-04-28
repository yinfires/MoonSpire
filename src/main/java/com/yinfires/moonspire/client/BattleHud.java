package com.yinfires.moonspire.client;

import com.yinfires.moonspire.battle.BattleSnapshot;
import com.yinfires.moonspire.card.CardInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class BattleHud {
    private BattleHud() {
    }

    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = graphics.guiWidth();
        int y = graphics.guiHeight() - 62;
        graphics.fill(8, y - 34, width - 8, y + 56, 0x99110F18);
        graphics.renderOutline(8, y - 34, width - 16, 90, 0xFF6E5A8A);
        graphics.drawString(minecraft.font, Component.translatable("hud.moonspire.player_stats", hp(snapshot.playerHealth()), hp(snapshot.playerMaxHealth()), snapshot.playerDefense(), snapshot.playerEnergyLeft(), snapshot.playerMaxEnergy()), 16, y - 26, 0xFFEDE8FF, false);
        graphics.drawString(minecraft.font, Component.translatable("hud.moonspire.monster_stats", hp(snapshot.monsterHealth()), hp(snapshot.monsterMaxHealth()), snapshot.monsterDefense()), 16, y - 14, 0xFFFFC2C2, false);
        graphics.drawString(minecraft.font, Component.translatable("hud.moonspire.piles", snapshot.drawPile(), snapshot.discardPile(), snapshot.phase().name(), snapshot.phaseTicksLeft() / 20), 16, y - 2, 0xFFC9C2DD, false);

        int x = 16;
        for (CardInstance card : snapshot.prepared()) {
            graphics.fill(x, y + 14, x + 66, y + 48, 0xCC17151F);
            graphics.renderOutline(x, y + 14, 66, 34, 0xFF8B75B5);
            if (!card.sourceStack().isEmpty()) {
                graphics.renderFakeItem(card.sourceStack(), x + 4, y + 23);
            }
            graphics.drawString(minecraft.font, "A" + card.attack() + " D" + card.defense(), x + 23, y + 19, 0xFFEDE8FF, false);
            graphics.drawString(minecraft.font, "C" + card.cost() + " S" + card.speed(), x + 23, y + 32, 0xFFFFE6A7, false);
            x += 72;
            if (x > width - 80) {
                break;
            }
        }
    }

    private static int hp(float value) {
        return Math.max(0, Math.round(value));
    }
}
