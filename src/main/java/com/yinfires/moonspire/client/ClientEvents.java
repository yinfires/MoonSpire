package com.yinfires.moonspire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleVisualEvent;
import com.yinfires.moonspire.client.ui.MoonSpireBattleLayoutEditor;
import com.yinfires.moonspire.client.ui.MoonSpireClientConfig;
import com.yinfires.moonspire.network.ChallengeTargetPayload;
import com.yinfires.moonspire.network.EndTurnPayload;
import com.yinfires.moonspire.network.RequestDeveloperCenterPayload;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    public static final String CATEGORY = "key.categories.moonspire";
    public static final KeyMapping CHALLENGE = new KeyMapping("key.moonspire.challenge", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping OPEN_DECK = new KeyMapping("key.moonspire.open_deck", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY);
    public static final KeyMapping UI_DEBUG = new KeyMapping("key.moonspire.ui_debug", KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY);
    private static final ResourceLocation BATTLE_HUD_LAYER = ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "battle_hud");
    private static final Map<Integer, ItemStack> TEMP_MAIN_HANDS = new HashMap<>();
    private static long lastUiDebugKeyMillis;

    private ClientEvents() {
    }

    public static void registerModBus(IEventBus modEventBus) {
        modEventBus.addListener(ModBus::registerKeys);
        modEventBus.addListener(ModBus::registerHud);
    }

    public static final class ModBus {
        private ModBus() {
        }

        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(CHALLENGE);
            event.register(OPEN_DECK);
            if (MoonSpireClientConfig.developerMode()) {
                event.register(UI_DEBUG);
            }
        }

        public static void registerHud(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR, BATTLE_HUD_LAYER, BattleHud::render);
        }
    }

    @EventBusSubscriber(modid = MoonSpire.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (ClientBattleState.active() && (minecraft.level == null || minecraft.player == null)) {
                ClientBattleState.clear();
                return;
            }
            suppressVanillaBattleKeys(minecraft);
            while (CHALLENGE.consumeClick()) {
                if (minecraft.hitResult instanceof EntityHitResult entityHitResult) {
                    Entity target = entityHitResult.getEntity();
                    PacketDistributor.sendToServer(new ChallengeTargetPayload(target.getId()));
                }
            }
            while (OPEN_DECK.consumeClick()) {
                ClientScreens.openDeckScreen();
            }
            if (ClientBattleState.active()) {
                freezeLocalPlayer(minecraft);
                ClientBattleState.updateCameraAnchor(minecraft);
                if (minecraft.screen == null) {
                    minecraft.setScreen(new BattleScreen());
                }
                playVisualEvents(minecraft);
            } else if (minecraft.screen instanceof BattleScreen) {
                minecraft.setScreen(null);
            }
            while (UI_DEBUG.consumeClick()) {
                handleUiDebugKey(minecraft);
            }
        }

        @SubscribeEvent
        public static void usePreparedCard(InputEvent.InteractionKeyMappingTriggered event) {
            if (!ClientBattleState.active()) {
                return;
            }
            if (event.isAttack() || event.isPickBlock()) {
                event.setSwingHand(false);
                event.setCanceled(true);
                return;
            }
            if (!event.isUseItem()) {
                event.setSwingHand(false);
                event.setCanceled(true);
                return;
            }
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void mouseScroll(InputEvent.MouseScrollingEvent event) {
            if (!ClientBattleState.active()) {
                return;
            }
            if (Minecraft.getInstance().screen instanceof BattleScreen) {
                return;
            }
            double delta = event.getScrollDeltaY();
            if (delta != 0.0D) {
                ClientBattleState.zoomCamera(delta);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void keyInput(InputEvent.Key event) {
            if (!ClientBattleState.active()) {
                return;
            }
            if (event.getKey() == GLFW.GLFW_KEY_Q && event.getAction() == InputConstants.PRESS) {
                PacketDistributor.sendToServer(new EndTurnPayload());
            }
        }

        @SubscribeEvent
        public static void hideHands(RenderHandEvent event) {
            if (ClientBattleState.active()) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void stopMovement(MovementInputUpdateEvent event) {
            if (!ClientBattleState.active()) {
                return;
            }
            event.getInput().leftImpulse = 0.0F;
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }

        @SubscribeEvent
        public static void battleCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            if (!ClientBattleState.active()) {
                return;
            }
            event.setYaw(ClientBattleState.cameraYaw());
            event.setPitch(ClientBattleState.cameraPitch());
        }

        @SubscribeEvent
        public static void battleCameraDistance(CalculateDetachedCameraDistanceEvent event) {
            if (ClientBattleState.active()) {
                event.setDistance(ClientBattleState.cameraDistance());
            }
        }

        @SubscribeEvent
        public static void renderBattleWorldOverlay(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                BattleWorldOverlay.renderLevel(event.getPoseStack(), event.getCamera(), Minecraft.getInstance().renderBuffers().bufferSource());
            }
        }

        @SubscribeEvent
        public static void renderBattleLivingOverlay(RenderLivingEvent.Post<?, ?> event) {
            if (!ClientBattleState.active()) {
                return;
            }
            BattleWorldOverlay.renderLivingOverlay(event.getEntity(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        }

        @SubscribeEvent
        public static void tintHurtCombatants(RenderLivingEvent.Pre<?, ?> event) {
            int ticks = ClientBattleState.hurtFlashTicks(event.getEntity().getId());
            if (ticks > 0) {
                event.getPoseStack().translate(0.0D, 0.0D, 0.0D);
                event.getEntity().hurtTime = Math.max(event.getEntity().hurtTime, ticks);
                event.getEntity().hurtDuration = Math.max(event.getEntity().hurtDuration, 10);
            }
            ItemStack override = ClientBattleState.visualMainHandOverride(event.getEntity().getId());
            if (!override.isEmpty()) {
                TEMP_MAIN_HANDS.put(event.getEntity().getId(), event.getEntity().getItemBySlot(EquipmentSlot.MAINHAND).copy());
                event.getEntity().setItemSlot(EquipmentSlot.MAINHAND, override.copy());
            }
        }

        @SubscribeEvent
        public static void hideDefaultPlayerHand(RenderPlayerEvent.Pre event) {
            if (!ClientBattleState.active() || event.getEntity().getId() != ClientBattleState.snapshot().player().entityId()) {
                return;
            }
            if (!ClientBattleState.visualMainHandOverride(event.getEntity().getId()).isEmpty()) {
                return;
            }
            TEMP_MAIN_HANDS.put(event.getEntity().getId(), event.getEntity().getItemBySlot(EquipmentSlot.MAINHAND).copy());
            event.getEntity().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        @SubscribeEvent
        public static void restoreDefaultPlayerHand(RenderPlayerEvent.Post event) {
            restoreMainHand(event.getEntity());
        }

        @SubscribeEvent
        public static void restoreTemporaryHands(RenderLivingEvent.Post<?, ?> event) {
            restoreMainHand(event.getEntity());
        }

        @SubscribeEvent
        public static void renderBattleNameTag(RenderNameTagEvent event) {
            if (BattleWorldOverlay.suppressBattleNameTag(event.getEntity())) {
                event.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);
            }
        }

        @SubscribeEvent
        public static void hideVanillaInventoryLayers(RenderGuiLayerEvent.Pre event) {
            if (!ClientBattleState.active()) {
                return;
            }
            if (event.getName().getNamespace().equals(MoonSpire.MOD_ID)) {
                return;
            }
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void blockInventoryScreen(ScreenEvent.Opening event) {
            if (ClientBattleState.active()
                    && (event.getNewScreen() instanceof InventoryScreen || event.getNewScreen() instanceof AbstractContainerScreen<?>)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void clearBattleStateOnLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientBattleState.clear();
            TEMP_MAIN_HANDS.clear();
        }

        private static void suppressVanillaBattleKeys(Minecraft minecraft) {
            if (!ClientBattleState.active() || minecraft.options == null) {
                return;
            }
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyDown.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyRight.setDown(false);
            minecraft.options.keyJump.setDown(false);
            minecraft.options.keyShift.setDown(false);
            minecraft.options.keyInventory.consumeClick();
            minecraft.options.keyDrop.consumeClick();
            minecraft.options.keySwapOffhand.consumeClick();
            for (KeyMapping mapping : minecraft.options.keyHotbarSlots) {
                mapping.consumeClick();
            }
        }

        private static void freezeLocalPlayer(Minecraft minecraft) {
            if (minecraft.player == null) {
                return;
            }
            Vec3 movement = minecraft.player.getDeltaMovement();
            if (minecraft.player.hurtTime <= 0
                    && ClientBattleState.hurtFlashTicks(minecraft.player.getId()) <= 0
                    && ClientBattleState.knockbackReleaseTicks(minecraft.player.getId()) <= 0) {
                minecraft.player.setDeltaMovement(0.0D, movement.y, 0.0D);
            }
            minecraft.player.xxa = 0.0F;
            minecraft.player.yya = 0.0F;
            minecraft.player.zza = 0.0F;
            minecraft.player.setJumping(false);
        }

        private static void restoreMainHand(LivingEntity entity) {
            ItemStack original = TEMP_MAIN_HANDS.remove(entity.getId());
            if (original != null) {
                entity.setItemSlot(EquipmentSlot.MAINHAND, original);
            }
        }

        private static void playVisualEvents(Minecraft minecraft) {
            if (minecraft.level == null) {
                return;
            }
            Set<Integer> swungAttackers = new HashSet<>();
            for (BattleVisualEvent event : ClientBattleState.consumeVisualEvents()) {
                Entity attacker = minecraft.level.getEntity(event.attackerId());
                Entity target = minecraft.level.getEntity(event.targetId());
                if (attacker instanceof LivingEntity livingAttacker && swungAttackers.add(event.attackerId())) {
                    livingAttacker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                if (target != null) {
                    if (event.shieldSound()) {
                        minecraft.level.playLocalSound(target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9F, 1.0F, false);
                    }
                    if (event.armorEquipSound()) {
                        minecraft.level.playLocalSound(target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.9F, 1.0F, false);
                    }
                    if (event.hurtSound()) {
                        minecraft.level.playLocalSound(target.getX(), target.getY(), target.getZ(), target == minecraft.player ? SoundEvents.PLAYER_HURT : SoundEvents.HOSTILE_HURT, SoundSource.PLAYERS, 0.9F, 1.0F, false);
                        if (target instanceof LivingEntity livingTarget && attacker != null) {
                            livingTarget.animateHurt(attacker.getYRot());
                        }
                    }
                }
            }
        }
    }

    static boolean handleUiDebugKey(Minecraft minecraft) {
        long now = Util.getMillis();
        if (now - lastUiDebugKeyMillis < 250L) {
            return true;
        }
        lastUiDebugKeyMillis = now;
        MoonSpireClientConfig.reload();
        if (!MoonSpireClientConfig.developerMode()) {
            return true;
        }
        if (ClientBattleState.active() && minecraft.screen instanceof BattleScreen) {
            MoonSpireBattleLayoutEditor.toggle();
            return true;
        }
        PacketDistributor.sendToServer(new RequestDeveloperCenterPayload());
        return true;
    }

    private static void showDebugMessage(Minecraft minecraft, Component message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }
}
