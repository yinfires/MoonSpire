package com.yinfires.moonspire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattlePhase;
import com.yinfires.moonspire.battle.MonsterDeckProfile;
import com.yinfires.moonspire.battle.BattleVisualEvent;
import com.yinfires.moonspire.client.ui.MoonSpireBattleLayoutEditor;
import com.yinfires.moonspire.client.ui.MoonSpireClientConfig;
import com.yinfires.moonspire.network.ChallengeTargetPayload;
import com.yinfires.moonspire.network.EndTurnPayload;
import com.yinfires.moonspire.network.RequestDeveloperCenterPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
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
    private static final double CHALLENGE_RANGE = 10.0D;
    private static final Map<Integer, TemporaryHandState> TEMP_MAIN_HANDS = new HashMap<>();
    private static final Map<Integer, TemporaryHandState> VISUAL_MAIN_HANDS = new HashMap<>();
    private static final Map<Integer, TemporaryArmPoseState> TEMP_ARM_POSES = new HashMap<>();
    private static final List<ScheduledBattleSound> SCHEDULED_BATTLE_SOUNDS = new ArrayList<>();
    private static long lastUiDebugKeyMillis;
    private static boolean battleRenderActive;

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
                clearBattleRenderState(minecraft, true);
                ClientBattleState.clear();
                return;
            }
            boolean active = ClientBattleState.active();
            if (active != battleRenderActive) {
                clearBattleRenderState(minecraft, true);
                battleRenderActive = active;
            }
            suppressVanillaBattleKeys(minecraft);
            while (CHALLENGE.consumeClick()) {
                LivingEntity target = challengeTarget(minecraft);
                if (target != null) {
                    PacketDistributor.sendToServer(new ChallengeTargetPayload(target.getId()));
                }
            }
            while (OPEN_DECK.consumeClick()) {
                ClientScreens.openDeckScreen();
            }
            if (ClientBattleState.active()) {
                ClientBattleState.tickClientLogic();
                freezeLocalPlayer(minecraft);
                clearNonVisualUseStates(minecraft);
                ClientBattleState.updateCameraAnchor(minecraft);
                if (minecraft.screen == null) {
                    minecraft.setScreen(new BattleScreen());
                }
                playVisualEvents(minecraft);
                syncVisualHandOverrides(minecraft);
                tickScheduledBattleSounds(minecraft);
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
            event.setSwingHand(false);
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
            if (Minecraft.getInstance().screen instanceof BattleScreen) {
                return;
            }
            if (event.getKey() == GLFW.GLFW_KEY_Q && event.getAction() == InputConstants.PRESS) {
                var snapshot = ClientBattleState.snapshot();
                if (snapshot.phase() == BattlePhase.PLAYER_TURN && !snapshot.localPlayerEndedTurn() && !snapshot.localPlayerFakeDead()) {
                    PacketDistributor.sendToServer(new EndTurnPayload());
                }
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
            if (ClientBattleState.active()) {
                var combatant = ClientBattleState.snapshot().combatant(event.getEntity().getId());
                if (combatant != null && combatant.fakeDead() && event.getEntity() instanceof LivingEntity living) {
                    living.deathTime = Math.max(living.deathTime, ClientBattleState.fakeDeathRenderTicks(event.getEntity().getId()));
                }
            }
            int ticks = ClientBattleState.hurtFlashTicks(event.getEntity().getId());
            if (ticks > 0) {
                event.getPoseStack().translate(0.0D, 0.0D, 0.0D);
                event.getEntity().hurtTime = Math.max(event.getEntity().hurtTime, ticks);
                event.getEntity().hurtDuration = Math.max(event.getEntity().hurtDuration, 10);
            }
            ItemStack override = ClientBattleState.visualMainHandOverride(event.getEntity().getId());
            if (!override.isEmpty()) {
                syncVisualHandOverride(event.getEntity(), override);
            }
            if (event.getRenderer().getModel() instanceof HumanoidModel<?> humanoidModel) {
                suppressDefaultBattleArmPose(event.getEntity(), humanoidModel);
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
            applyTemporaryMainHand(event.getEntity(), ItemStack.EMPTY, false);
        }

        @SubscribeEvent
        public static void restoreDefaultPlayerHand(RenderPlayerEvent.Post event) {
            restoreMainHand(event.getEntity());
        }

        @SubscribeEvent
        public static void restoreTemporaryHands(RenderLivingEvent.Post<?, ?> event) {
            restoreMainHand(event.getEntity());
            restoreArmPose(event.getEntity());
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
            clearBattleRenderState(Minecraft.getInstance(), true);
            ClientBattleState.clear();
            battleRenderActive = false;
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
            minecraft.options.keyAttack.setDown(false);
            minecraft.options.keyUse.setDown(false);
            minecraft.options.keyAttack.consumeClick();
            minecraft.options.keyUse.consumeClick();
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

        private static void applyTemporaryMainHand(LivingEntity entity, ItemStack stack, boolean useItem) {
            TemporaryHandState state = TEMP_MAIN_HANDS.computeIfAbsent(
                    entity.getId(),
                    id -> new TemporaryHandState(entity.getItemBySlot(EquipmentSlot.MAINHAND).copy()));
            entity.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
            if (useItem && !entity.isUsingItem()) {
                entity.startUsingItem(InteractionHand.MAIN_HAND);
                state.startedUsing = true;
            }
        }

        private static void syncVisualHandOverrides(Minecraft minecraft) {
            if (minecraft.level == null) {
                return;
            }
            Set<Integer> activeVisualHands = new HashSet<>();
            for (var combatant : ClientBattleState.snapshot().players()) {
                syncVisualHandOverride(minecraft.level.getEntity(combatant.entityId()), activeVisualHands);
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                syncVisualHandOverride(minecraft.level.getEntity(combatant.entityId()), activeVisualHands);
            }
            for (int entityId : VISUAL_MAIN_HANDS.keySet().toArray(Integer[]::new)) {
                if (!activeVisualHands.contains(entityId) && minecraft.level.getEntity(entityId) instanceof LivingEntity living) {
                    restoreVisualMainHand(living);
                }
            }
        }

        private static void syncVisualHandOverride(Entity entity, Set<Integer> activeVisualHands) {
            if (!(entity instanceof LivingEntity living)) {
                return;
            }
            ItemStack override = ClientBattleState.visualMainHandOverride(living.getId());
            if (override.isEmpty()) {
                restoreVisualMainHand(living);
                return;
            }
            activeVisualHands.add(living.getId());
            syncVisualHandOverride(living, override);
        }

        private static void syncVisualHandOverride(LivingEntity entity, ItemStack stack) {
            TemporaryHandState state = VISUAL_MAIN_HANDS.computeIfAbsent(
                    entity.getId(),
                    id -> new TemporaryHandState(entity.getItemBySlot(EquipmentSlot.MAINHAND).copy()));
            if (!ItemStack.matches(entity.getItemBySlot(EquipmentSlot.MAINHAND), stack)) {
                entity.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
            }
            if (ClientBattleState.visualUsingItem(entity.getId()) && !entity.isUsingItem()) {
                entity.startUsingItem(InteractionHand.MAIN_HAND);
                state.startedUsing = true;
            } else if (!ClientBattleState.visualUsingItem(entity.getId()) && state.startedUsing && entity.isUsingItem()) {
                entity.stopUsingItem();
            }
        }

        private static void restoreVisualMainHand(LivingEntity entity) {
            TemporaryHandState state = VISUAL_MAIN_HANDS.remove(entity.getId());
            if (state != null) {
                entity.setItemSlot(EquipmentSlot.MAINHAND, state.originalMainHand);
                if (state.startedUsing && entity.isUsingItem()) {
                    entity.stopUsingItem();
                }
            }
        }

        private static void restoreMainHand(LivingEntity entity) {
            TemporaryHandState state = TEMP_MAIN_HANDS.remove(entity.getId());
            if (state != null) {
                entity.setItemSlot(EquipmentSlot.MAINHAND, state.originalMainHand);
                if (state.startedUsing && entity.isUsingItem() && !ClientBattleState.visualUsingItem(entity.getId())) {
                    entity.stopUsingItem();
                }
            }
        }

        private static void suppressDefaultBattleArmPose(LivingEntity entity, HumanoidModel<?> model) {
            if (!ClientBattleState.active() || ClientBattleState.snapshot().combatant(entity.getId()) == null) {
                return;
            }
            if (!ClientBattleState.visualMainHandOverride(entity.getId()).isEmpty()) {
                return;
            }
            TEMP_ARM_POSES.computeIfAbsent(entity.getId(), id -> new TemporaryArmPoseState(model.leftArmPose, model.rightArmPose));
            model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        }

        private static void restoreArmPose(LivingEntity entity) {
            TemporaryArmPoseState state = TEMP_ARM_POSES.remove(entity.getId());
            if (state != null && Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> renderer
                    && renderer.getModel() instanceof HumanoidModel<?> model) {
                model.leftArmPose = state.leftArmPose;
                model.rightArmPose = state.rightArmPose;
            }
        }

        private static void clearBattleRenderState(Minecraft minecraft, boolean stopUsingItems) {
            if (minecraft.level != null) {
                for (int entityId : TEMP_MAIN_HANDS.keySet().toArray(Integer[]::new)) {
                    if (minecraft.level.getEntity(entityId) instanceof LivingEntity living) {
                        restoreMainHand(living);
                    }
                }
                for (int entityId : VISUAL_MAIN_HANDS.keySet().toArray(Integer[]::new)) {
                    if (minecraft.level.getEntity(entityId) instanceof LivingEntity living) {
                        restoreVisualMainHand(living);
                    }
                }
                for (int entityId : TEMP_ARM_POSES.keySet().toArray(Integer[]::new)) {
                    if (minecraft.level.getEntity(entityId) instanceof LivingEntity living) {
                        restoreArmPose(living);
                    }
                }
                for (var combatant : ClientBattleState.snapshot().players()) {
                    clearEntityHandAnimation(minecraft.level.getEntity(combatant.entityId()), stopUsingItems);
                }
                for (var combatant : ClientBattleState.snapshot().enemies()) {
                    clearEntityHandAnimation(minecraft.level.getEntity(combatant.entityId()), stopUsingItems);
                }
            }
            clearEntityHandAnimation(minecraft.player, stopUsingItems);
            TEMP_MAIN_HANDS.clear();
            VISUAL_MAIN_HANDS.clear();
            TEMP_ARM_POSES.clear();
            SCHEDULED_BATTLE_SOUNDS.clear();
            ClientBattleState.clearVisualStates();
        }

        private static void clearNonVisualUseStates(Minecraft minecraft) {
            if (minecraft.level != null) {
                for (var combatant : ClientBattleState.snapshot().players()) {
                    clearEntityUseState(minecraft.level.getEntity(combatant.entityId()));
                }
                for (var combatant : ClientBattleState.snapshot().enemies()) {
                    clearEntityUseState(minecraft.level.getEntity(combatant.entityId()));
                }
            }
            clearEntityUseState(minecraft.player);
        }

        private static void clearEntityUseState(Entity entity) {
            if (entity instanceof LivingEntity living && !ClientBattleState.visualUsingItem(living.getId()) && living.isUsingItem()) {
                living.stopUsingItem();
            }
        }

        private static void clearEntityHandAnimation(Entity entity, boolean stopUsingItem) {
            if (entity instanceof LivingEntity living) {
                if (stopUsingItem && living.isUsingItem()) {
                    living.stopUsingItem();
                }
                living.swinging = false;
                living.swingTime = 0;
                living.attackAnim = 0.0F;
                living.oAttackAnim = 0.0F;
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
                boolean playedCardVisual = event.playedCard() != null || !event.itemStack().isEmpty();
                boolean shouldSwing = event.animationType() == BattleVisualEvent.AnimationType.NONE;
                if (playedCardVisual && shouldSwing && attacker instanceof LivingEntity livingAttacker && swungAttackers.add(event.attackerId())) {
                    livingAttacker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                if (attacker instanceof LivingEntity livingAttacker) {
                    scheduleUseSounds(livingAttacker, event);
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

        private static void scheduleUseSounds(LivingEntity attacker, BattleVisualEvent event) {
            if (event.animationType() == BattleVisualEvent.AnimationType.BOW_DRAW) {
                scheduleBattleSound(attacker, SoundEvents.ARROW_SHOOT, event.animationTicks(), 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.CROSSBOW_LOAD) {
                scheduleBattleSound(attacker, SoundEvents.CROSSBOW_LOADING_START.value(), 0, 0.5F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), Math.max(1, event.animationTicks() / 2), 0.5F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.CROSSBOW_LOADING_END.value(), Math.max(1, event.animationTicks()), 0.5F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.CROSSBOW_SHOOT, Math.max(1, event.animationTicks() + 1), 1.0F, 1.0F);
            }
        }

        private static void scheduleBattleSound(LivingEntity entity, SoundEvent sound, int delayTicks, float volume, float pitch) {
            SCHEDULED_BATTLE_SOUNDS.add(new ScheduledBattleSound(entity.getId(), sound, Math.max(0, delayTicks), volume, pitch));
        }

        private static void tickScheduledBattleSounds(Minecraft minecraft) {
            if (minecraft.level == null) {
                SCHEDULED_BATTLE_SOUNDS.clear();
                return;
            }
            Iterator<ScheduledBattleSound> iterator = SCHEDULED_BATTLE_SOUNDS.iterator();
            while (iterator.hasNext()) {
                ScheduledBattleSound sound = iterator.next();
                if (sound.delayTicks > 0) {
                    sound.delayTicks--;
                    continue;
                }
                Entity entity = minecraft.level.getEntity(sound.entityId);
                if (entity != null) {
                    minecraft.level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), sound.sound, SoundSource.PLAYERS, sound.volume, sound.pitch, false);
                }
                iterator.remove();
            }
        }

    }

    private static final class TemporaryHandState {
        private final ItemStack originalMainHand;
        private boolean startedUsing;

        private TemporaryHandState(ItemStack originalMainHand) {
            this.originalMainHand = originalMainHand;
        }
    }

    private record TemporaryArmPoseState(HumanoidModel.ArmPose leftArmPose, HumanoidModel.ArmPose rightArmPose) {
    }

    private static final class ScheduledBattleSound {
        private final int entityId;
        private final SoundEvent sound;
        private int delayTicks;
        private final float volume;
        private final float pitch;

        private ScheduledBattleSound(int entityId, SoundEvent sound, int delayTicks, float volume, float pitch) {
            this.entityId = entityId;
            this.sound = sound;
            this.delayTicks = delayTicks;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    static LivingEntity challengeTarget(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        Vec3 start = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 end = start.add(look.scale(CHALLENGE_RANGE));
        AABB search = minecraft.player.getBoundingBox().expandTowards(look.scale(CHALLENGE_RANGE)).inflate(1.0D);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : minecraft.level.getEntitiesOfClass(LivingEntity.class, search)) {
            if (entity == minecraft.player || !entity.isAlive() || !MonsterDeckProfile.hasBattleDeck(entity)) {
                continue;
            }
            var hit = entity.getBoundingBox().inflate(0.25D).clip(start, end);
            if (hit.isPresent()) {
                double distance = start.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = entity;
                }
            }
        }
        return best;
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
