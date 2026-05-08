package com.yinfires.moonspire.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

public final class BattleHud {
    private BattleHud() {
    }

    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || ClientBattleState.active()) {
            return;
        }
        LivingEntity living = ClientEvents.challengeTarget(minecraft);
        if (living == null) {
            return;
        }
        Component key = ClientEvents.CHALLENGE.getTranslatedKeyMessage();
        Component prompt = Component.translatable("hud.moonspire.challenge_prompt", key);
        int x = graphics.guiWidth() / 2 + 12;
        int y = graphics.guiHeight() / 2 + 8;
        graphics.drawString(minecraft.font, prompt, x, y, 0xFFFFF0C2, true);
    }
}
