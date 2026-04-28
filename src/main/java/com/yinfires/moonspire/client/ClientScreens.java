package com.yinfires.moonspire.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientScreens {
    private ClientScreens() {
    }

    public static void openDeckScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !ClientBattleState.active()) {
            minecraft.setScreen(new DeckScreen());
        }
    }

    public static void openCardForge(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !ClientBattleState.active()) {
            minecraft.setScreen(new CardForgeScreen(pos));
        }
    }
}
