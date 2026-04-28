package com.yinfires.moonspire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattlePhase;
import com.yinfires.moonspire.network.ChallengeTargetPayload;
import com.yinfires.moonspire.network.UsePreparedCardPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientEvents {
    public static final String CATEGORY = "key.categories.moonspire";
    public static final KeyMapping CHALLENGE = new KeyMapping("key.moonspire.challenge", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping OPEN_DECK = new KeyMapping("key.moonspire.open_deck", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY);

    private ClientEvents() {
    }

    @EventBusSubscriber(modid = MoonSpire.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(CHALLENGE);
            event.register(OPEN_DECK);
        }

        @SubscribeEvent
        public static void registerHud(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "battle_hud"), BattleHud::render);
        }
    }

    @EventBusSubscriber(modid = MoonSpire.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (CHALLENGE.consumeClick()) {
                if (minecraft.hitResult instanceof EntityHitResult entityHitResult) {
                    Entity target = entityHitResult.getEntity();
                    PacketDistributor.sendToServer(new ChallengeTargetPayload(target.getId()));
                }
            }
            while (OPEN_DECK.consumeClick()) {
                ClientScreens.openDeckScreen();
            }
            if (ClientBattleState.active()
                    && ClientBattleState.snapshot().phase() == BattlePhase.PREPARE
                    && !(minecraft.screen instanceof BattlePrepareScreen)) {
                minecraft.setScreen(new BattlePrepareScreen());
            }
        }

        @SubscribeEvent
        public static void usePreparedCard(InputEvent.InteractionKeyMappingTriggered event) {
            if (!ClientBattleState.active() || ClientBattleState.snapshot().phase() != BattlePhase.EXECUTE || !event.isUseItem()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            int targetId = -1;
            if (minecraft.hitResult instanceof EntityHitResult entityHitResult) {
                targetId = entityHitResult.getEntity().getId();
            }
            PacketDistributor.sendToServer(new UsePreparedCardPayload(targetId));
            event.setSwingHand(true);
            event.setCanceled(true);
        }
    }
}
