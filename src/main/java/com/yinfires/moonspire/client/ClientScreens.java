package com.yinfires.moonspire.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import com.yinfires.moonspire.network.OpenCardRewardScreenPayload;
import java.util.List;
import java.util.UUID;

public final class ClientScreens {
    private ClientScreens() {
    }

    public static void openDeckScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.screen instanceof DeckScreen) {
            minecraft.setScreen(null);
            return;
        }
        if (!ClientBattleState.active() && minecraft.screen == null) {
            minecraft.setScreen(new DeckScreen());
        }
    }

    public static void openCardForge(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !ClientBattleState.active()) {
            minecraft.setScreen(new CardForgeScreen(pos));
        }
    }

    public static void openCardReward(UUID rewardId, List<OpenCardRewardScreenPayload.RewardPage> pages) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || rewardId == null || pages == null || pages.isEmpty()) {
            return;
        }
        minecraft.setScreen(new CardRewardScreen(rewardId, pages));
    }
}
