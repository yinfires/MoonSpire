package com.yinfires.moonspire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleCombatantSnapshot;
import com.yinfires.moonspire.battle.BattleEffectType;
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
import java.util.function.Predicate;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
    private static final Map<Integer, TemporaryFireballState> VISUAL_FIREBALL_STATES = new HashMap<>();
    private static final Map<Integer, TemporaryShulkerState> VISUAL_SHULKER_STATES = new HashMap<>();
    private static final Map<Integer, TemporaryArmPoseState> TEMP_ARM_POSES = new HashMap<>();
    private static final Map<Integer, VisualRiptideState> VISUAL_RIPTIDE_STATES = new HashMap<>();
    private static final Set<Integer> VISUAL_RAVAGER_HEAD_RAMS = new HashSet<>();
    private static final Set<Integer> VISUAL_HOGLIN_HEAD_ATTACKS = new HashSet<>();
    private static final Set<Integer> VISUAL_WARDEN_MELEE_ATTACKS = new HashSet<>();
    private static final Set<Integer> VISUAL_BREEZE_WIND_CHARGES = new HashSet<>();
    private static final Set<Integer> VISUAL_LUNGE_POSE_PUSHES = new HashSet<>();
    private static final List<ScheduledBattleSound> SCHEDULED_BATTLE_SOUNDS = new ArrayList<>();
    private static final List<ScheduledWardenSonicBoomVisual> SCHEDULED_WARDEN_SONIC_BOOM_VISUALS = new ArrayList<>();
    private static final List<ScheduledSelfDestructExplosion> SCHEDULED_SELF_DESTRUCT_EXPLOSIONS = new ArrayList<>();
    private static long lastUiDebugKeyMillis;
    private static boolean battleRenderActive;

    private ClientEvents() {
    }

    public static void registerModBus(IEventBus modEventBus) {
        modEventBus.addListener(ModBus::registerKeys);
        modEventBus.addListener(ModBus::registerHud);
        modEventBus.addListener(ModBus::addSelfDestructWhiteFlashLayers);
        modEventBus.addListener(ModBus::addRiptideLayers);
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

        public static void addSelfDestructWhiteFlashLayers(EntityRenderersEvent.AddLayers event) {
            for (EntityType<?> type : event.getEntityTypes()) {
                addSelfDestructWhiteFlashLayer(event.getRenderer(type));
            }
            for (var skin : event.getSkins()) {
                addSelfDestructWhiteFlashLayer(event.getSkin(skin));
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void addSelfDestructWhiteFlashLayer(net.minecraft.client.renderer.entity.EntityRenderer<?> renderer) {
            if (renderer instanceof LivingEntityRenderer livingRenderer) {
                livingRenderer.addLayer(new SelfDestructWhiteFlashLayer(livingRenderer));
            }
        }

        public static void addRiptideLayers(EntityRenderersEvent.AddLayers event) {
            addDrownedRiptideLayer(event.getRenderer(EntityType.DROWNED), event);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void addDrownedRiptideLayer(net.minecraft.client.renderer.entity.EntityRenderer<?> renderer, EntityRenderersEvent.AddLayers event) {
            if (renderer instanceof LivingEntityRenderer livingRenderer) {
                livingRenderer.addLayer(new BattleRiptideLayer(livingRenderer, event.getEntityModels()));
            }
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
                stabilizeClientBattleSummons(minecraft);
                clearNonVisualUseStates(minecraft);
                ClientBattleState.updateCameraAnchor(minecraft);
                if (minecraft.screen == null) {
                    minecraft.setScreen(new BattleScreen());
                }
                playVisualEvents(minecraft);
                tickScheduledWardenSonicBoomVisuals(minecraft);
                playProjectileImpactVisuals(minecraft);
                spawnEvokerSpellParticles(minecraft);
                syncVisualWalkAnimations(minecraft);
                syncVisualHandOverrides(minecraft);
                syncVisualFireballStates(minecraft);
                syncVisualShulkerPeekStates(minecraft);
                tickScheduledBattleSounds(minecraft);
                tickScheduledSelfDestructExplosions(minecraft);
                syncVisualRiptideStates(minecraft);
                syncVisualRavagerHeadRams(minecraft);
                syncVisualHoglinHeadAttacks(minecraft);
                syncVisualWardenMeleeAttacks(minecraft);
                syncVisualBreezeWindCharges(minecraft);
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
                BattleWorldOverlay.renderLevel(event.getPoseStack(), event.getCamera(), Minecraft.getInstance().renderBuffers().bufferSource(), event.getPartialTick().getGameTimeDeltaPartialTick(true));
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
            if (ClientBattleState.fakeDeathHidden(event.getEntity().getId())) {
                event.setCanceled(true);
                return;
            }
            if (ClientBattleState.active()) {
                var combatant = ClientBattleState.snapshot().combatant(event.getEntity().getId());
                if (combatant != null
                        && combatant.fakeDead()
                        && ClientBattleState.fakeDeathAnimating(event.getEntity().getId())
                        && event.getEntity() instanceof LivingEntity living) {
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

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void applyVisualLungeOffset(RenderLivingEvent.Pre<?, ?> event) {
            LivingEntity entity = event.getEntity();
            if (ClientBattleState.fakeDeathHidden(entity.getId())) {
                return;
            }
            Vec3 renderedPosition = new Vec3(
                    Mth.lerp(event.getPartialTick(), entity.xOld, entity.getX()),
                    Mth.lerp(event.getPartialTick(), entity.yOld, entity.getY()),
                    Mth.lerp(event.getPartialTick(), entity.zOld, entity.getZ()));
            Vec3 offset = ClientBattleState.visualRenderOffset(entity.getId(), renderedPosition, event.getPartialTick());
            float selfDestructScale = ClientBattleState.visualSelfDestructScale(entity.getId(), event.getPartialTick());
            boolean scaleSelfDestruct = Math.abs(selfDestructScale - 1.0F) > 0.001F;
            if (offset.lengthSqr() > 0.000001D || scaleSelfDestruct) {
                event.getPoseStack().pushPose();
                event.getPoseStack().translate(offset.x, offset.y, offset.z);
                if (scaleSelfDestruct) {
                    event.getPoseStack().scale(selfDestructScale, selfDestructScale, selfDestructScale);
                }
                VISUAL_LUNGE_POSE_PUSHES.add(entity.getId());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void restoreVisualLungeOffset(RenderLivingEvent.Post<?, ?> event) {
            if (VISUAL_LUNGE_POSE_PUSHES.remove(event.getEntity().getId())) {
                event.getPoseStack().popPose();
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

        private static void stabilizeClientBattleSummons(Minecraft minecraft) {
            if (minecraft.level == null) {
                return;
            }
            for (var combatant : ClientBattleState.snapshot().players()) {
                stabilizeClientBattleSummon(minecraft.level.getEntity(combatant.entityId()), combatant);
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                stabilizeClientBattleSummon(minecraft.level.getEntity(combatant.entityId()), combatant);
            }
        }

        private static void stabilizeClientBattleSummon(Entity entity, BattleCombatantSnapshot combatant) {
            if (!(entity instanceof LivingEntity living)
                    || entity instanceof Vex
                    || !hasDynamicBattleLock(combatant)
                    || ClientBattleState.visualMovement(living.getId())) {
                return;
            }
            living.setNoGravity(true);
            living.noPhysics = true;
            living.setDeltaMovement(Vec3.ZERO);
            living.xxa = 0.0F;
            living.yya = 0.0F;
            living.zza = 0.0F;
            living.setJumping(false);
            living.resetFallDistance();
            living.hasImpulse = false;
            living.walkAnimation.update(0.0F, 1.0F);
            living.setOldPosAndRot();
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRotO = living.yHeadRot;
        }

        private static boolean hasDynamicBattleLock(BattleCombatantSnapshot combatant) {
            return combatant != null
                    && (combatant.dynamicCombatant()
                    || (combatant.effects() != null
                    && combatant.effects().stream().anyMatch(effect -> effect.type() == BattleEffectType.SUMMONED && effect.amount() > 0)));
        }

        private static void applyTemporaryMainHand(LivingEntity entity, ItemStack stack, boolean useItem) {
            TemporaryHandState state = TEMP_MAIN_HANDS.computeIfAbsent(
                    entity.getId(),
                    id -> TemporaryHandState.capture(entity));
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
                    id -> TemporaryHandState.capture(entity));
            ItemStack currentMainHand = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            if (currentMainHand != stack && !ItemStack.matches(currentMainHand, stack)) {
                entity.setItemSlot(EquipmentSlot.MAINHAND, stack);
            }
            if (entity instanceof AbstractSkeleton skeleton && ClientBattleState.visualAnimationType(entity.getId()) == BattleVisualEvent.AnimationType.BOW_DRAW) {
                skeleton.setAggressive(true);
            } else if (entity instanceof Drowned drowned && ClientBattleState.visualDrownedTridentPose(entity.getId())) {
                drowned.setAggressive(true);
            } else if (entity instanceof Vindicator vindicator && ClientBattleState.visualAnimationType(entity.getId()) == BattleVisualEvent.AnimationType.VINDICATOR_AXE_SWING) {
                vindicator.setAggressive(ClientBattleState.visualVindicatorAxeRaised(entity.getId()));
            } else if (entity instanceof Vex vex && ClientBattleState.visualAnimationType(entity.getId()) == BattleVisualEvent.AnimationType.VEX_CHARGE_LUNGE) {
                vex.setIsCharging(ClientBattleState.visualVexCharging(entity.getId()));
            } else if (ClientBattleState.visualAnimationType(entity.getId()) == BattleVisualEvent.AnimationType.PIGLIN_MELEE_SWING) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setAggressive(ClientBattleState.visualPiglinMeleeRaised(entity.getId()));
                }
            } else if (entity instanceof Witch witch && visualWitchDrinking(entity)) {
                witch.setUsingItem(true);
                state.startedWitchDrinking = true;
            }
            ItemStack mainHand = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            syncVanillaCrossbowPose(entity, mainHand);
            if (ClientBattleState.visualUsingItem(entity.getId()) && !useDrownedTridentPose(entity)) {
                if (!entity.isUsingItem()) {
                    entity.startUsingItem(InteractionHand.MAIN_HAND);
                }
                syncVisualUseItemState(entity, mainHand);
                state.startedUsing = true;
            } else if (!ClientBattleState.visualUsingItem(entity.getId()) && state.startedUsing && entity.isUsingItem()) {
                clearVisualUseItemState(entity);
            }
            if (!visualWitchDrinking(entity) && state.startedWitchDrinking && entity instanceof Witch witch) {
                witch.setUsingItem(false);
                state.startedWitchDrinking = false;
            }
        }

        private static void syncVanillaCrossbowPose(LivingEntity entity, ItemStack stack) {
            if (ClientBattleState.visualAnimationType(entity.getId()) != BattleVisualEvent.AnimationType.CROSSBOW_LOAD
                    || stack == null
                    || !stack.is(Items.CROSSBOW)) {
                return;
            }
            boolean loading = ClientBattleState.visualUsingItem(entity.getId());
            if (entity instanceof Pillager pillager) {
                pillager.setChargingCrossbow(loading);
                pillager.setAggressive(true);
            } else if (entity instanceof Piglin piglin) {
                piglin.setChargingCrossbow(loading);
                piglin.setAggressive(true);
            }
        }

        private static boolean useDrownedTridentPose(LivingEntity entity) {
            return entity instanceof Drowned && ClientBattleState.visualDrownedTridentPose(entity.getId());
        }

        private static boolean visualWitchDrinking(LivingEntity entity) {
            return entity instanceof Witch
                    && ClientBattleState.visualAnimationType(entity.getId()) == BattleVisualEvent.AnimationType.POTION_DRINK
                    && ClientBattleState.visualUsingItem(entity.getId());
        }

        private static void syncVisualRiptideStates(Minecraft minecraft) {
            if (minecraft.level == null) {
                VISUAL_RIPTIDE_STATES.clear();
                return;
            }
            Set<Integer> activeRiptides = new HashSet<>();
            for (var combatant : ClientBattleState.snapshot().players()) {
                syncVisualRiptideState(minecraft.level.getEntity(combatant.entityId()), activeRiptides);
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                syncVisualRiptideState(minecraft.level.getEntity(combatant.entityId()), activeRiptides);
            }
            for (int entityId : VISUAL_RIPTIDE_STATES.keySet().toArray(Integer[]::new)) {
                if (!activeRiptides.contains(entityId)) {
                    if (minecraft.level.getEntity(entityId) instanceof LivingEntity living) {
                        restoreVisualRiptideState(living);
                    } else {
                        VISUAL_RIPTIDE_STATES.remove(entityId);
                    }
                }
            }
        }

        private static void syncVisualRiptideState(Entity entity, Set<Integer> activeRiptides) {
            if (!(entity instanceof LivingEntity living)) {
                return;
            }
            int spinTicks = ClientBattleState.visualRiptideSpinTicks(living.getId());
            if (spinTicks <= 0) {
                restoreVisualRiptideState(living);
                return;
            }
            activeRiptides.add(living.getId());
            ItemStack stack = ClientBattleState.visualMainHandOverride(living.getId());
            if (stack.isEmpty()) {
                stack = new ItemStack(Items.TRIDENT);
            }
            VISUAL_RIPTIDE_STATES.computeIfAbsent(living.getId(), id -> VisualRiptideState.capture(living));
            living.autoSpinAttackTicks = 0;
            living.autoSpinAttackDmg = 0.0F;
            living.autoSpinAttackItemStack = stack.copy();
            living.setLivingEntityFlag(4, true);
            living.setPose(Pose.SPIN_ATTACK);
        }

        private static void restoreVisualRiptideState(LivingEntity living) {
            VisualRiptideState state = VISUAL_RIPTIDE_STATES.remove(living.getId());
            if (state != null) {
                state.restore(living);
            }
        }

        private static void syncVisualRavagerHeadRams(Minecraft minecraft) {
            if (minecraft.level == null) {
                VISUAL_RAVAGER_HEAD_RAMS.clear();
                return;
            }
            Set<Integer> activeRavagers = new HashSet<>();
            for (var combatant : ClientBattleState.snapshot().players()) {
                syncVisualRavagerHeadRam(minecraft.level.getEntity(combatant.entityId()), activeRavagers);
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                syncVisualRavagerHeadRam(minecraft.level.getEntity(combatant.entityId()), activeRavagers);
            }
            for (int entityId : VISUAL_RAVAGER_HEAD_RAMS.toArray(Integer[]::new)) {
                if (!activeRavagers.contains(entityId)) {
                    if (minecraft.level.getEntity(entityId) instanceof Ravager ravager) {
                        ravager.attackTick = 0;
                    }
                    VISUAL_RAVAGER_HEAD_RAMS.remove(entityId);
                }
            }
        }

        private static void syncVisualRavagerHeadRam(Entity entity, Set<Integer> activeRavagers) {
            if (!(entity instanceof Ravager ravager)) {
                return;
            }
            int attackTick = ClientBattleState.visualRavagerAttackTick(ravager.getId());
            if (attackTick > 0) {
                ravager.attackTick = attackTick;
                activeRavagers.add(ravager.getId());
                VISUAL_RAVAGER_HEAD_RAMS.add(ravager.getId());
            } else if (VISUAL_RAVAGER_HEAD_RAMS.remove(ravager.getId())) {
                ravager.attackTick = 0;
            }
        }

        private static void syncVisualHoglinHeadAttacks(Minecraft minecraft) {
            if (minecraft.level == null) {
                VISUAL_HOGLIN_HEAD_ATTACKS.clear();
                return;
            }
            Set<Integer> activeHoglins = new HashSet<>();
            for (var combatant : ClientBattleState.snapshot().players()) {
                syncVisualHoglinHeadAttack(minecraft.level.getEntity(combatant.entityId()), activeHoglins);
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                syncVisualHoglinHeadAttack(minecraft.level.getEntity(combatant.entityId()), activeHoglins);
            }
            for (int entityId : VISUAL_HOGLIN_HEAD_ATTACKS.toArray(Integer[]::new)) {
                if (!activeHoglins.contains(entityId)) {
                    clearVisualHoglinHeadAttack(minecraft.level.getEntity(entityId));
                    VISUAL_HOGLIN_HEAD_ATTACKS.remove(entityId);
                }
            }
        }

        private static void syncVisualHoglinHeadAttack(Entity entity, Set<Integer> activeHoglins) {
            int attackTick = entity == null ? 0 : ClientBattleState.visualHoglinAttackTick(entity.getId());
            if (attackTick > 0 && setVisualHoglinHeadAttack(entity, attackTick)) {
                activeHoglins.add(entity.getId());
                VISUAL_HOGLIN_HEAD_ATTACKS.add(entity.getId());
            } else if (entity != null && VISUAL_HOGLIN_HEAD_ATTACKS.remove(entity.getId())) {
                clearVisualHoglinHeadAttack(entity);
            }
        }

        private static boolean setVisualHoglinHeadAttack(Entity entity, int attackTick) {
            if (entity instanceof Hoglin hoglin) {
                hoglin.attackAnimationRemainingTicks = attackTick;
                return true;
            }
            if (entity instanceof Zoglin zoglin) {
                zoglin.attackAnimationRemainingTicks = attackTick;
                return true;
            }
            return false;
        }

        private static void clearVisualHoglinHeadAttack(Entity entity) {
            if (entity instanceof Hoglin hoglin) {
                hoglin.attackAnimationRemainingTicks = 0;
            } else if (entity instanceof Zoglin zoglin) {
                zoglin.attackAnimationRemainingTicks = 0;
            }
        }

        private static void syncVisualWardenMeleeAttacks(Minecraft minecraft) {
            if (minecraft.level == null) {
                VISUAL_WARDEN_MELEE_ATTACKS.clear();
                return;
            }
            Set<Integer> activeWardens = new HashSet<>();
            for (var combatant : ClientBattleState.snapshot().players()) {
                syncVisualWardenMeleeAttack(minecraft.level.getEntity(combatant.entityId()), activeWardens);
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                syncVisualWardenMeleeAttack(minecraft.level.getEntity(combatant.entityId()), activeWardens);
            }
            for (int entityId : VISUAL_WARDEN_MELEE_ATTACKS.toArray(Integer[]::new)) {
                if (!activeWardens.contains(entityId)) {
                    clearVisualWardenMeleeAttack(minecraft.level.getEntity(entityId));
                    VISUAL_WARDEN_MELEE_ATTACKS.remove(entityId);
                }
            }
        }

        private static void syncVisualWardenMeleeAttack(Entity entity, Set<Integer> activeWardens) {
            if (!(entity instanceof Warden warden)) {
                return;
            }
            int attackTick = ClientBattleState.visualWardenAttackTick(warden.getId());
            if (attackTick > 0) {
                activeWardens.add(warden.getId());
                VISUAL_WARDEN_MELEE_ATTACKS.add(warden.getId());
                warden.attackAnimationState.startIfStopped(warden.tickCount);
            } else if (VISUAL_WARDEN_MELEE_ATTACKS.remove(warden.getId())) {
                clearVisualWardenMeleeAttack(warden);
            }
        }

        private static void clearVisualWardenMeleeAttack(Entity entity) {
            if (entity instanceof Warden warden) {
                warden.attackAnimationState.stop();
            }
        }

        private static void syncVisualBreezeWindCharges(Minecraft minecraft) {
            if (minecraft.level == null) {
                VISUAL_BREEZE_WIND_CHARGES.clear();
                return;
            }
            Set<Integer> activeBreezes = new HashSet<>();
            for (var combatant : ClientBattleState.snapshot().players()) {
                syncVisualBreezeWindCharge(minecraft.level.getEntity(combatant.entityId()), activeBreezes);
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                syncVisualBreezeWindCharge(minecraft.level.getEntity(combatant.entityId()), activeBreezes);
            }
            for (int entityId : VISUAL_BREEZE_WIND_CHARGES.toArray(Integer[]::new)) {
                if (!activeBreezes.contains(entityId)) {
                    if (minecraft.level.getEntity(entityId) instanceof Breeze breeze) {
                        stopVisualBreezeWindCharge(breeze);
                    }
                    VISUAL_BREEZE_WIND_CHARGES.remove(entityId);
                }
            }
        }

        private static void syncVisualBreezeWindCharge(Entity entity, Set<Integer> activeBreezes) {
            if (!(entity instanceof Breeze breeze)) {
                return;
            }
            int ticks = ClientBattleState.visualWindChargeTicks(breeze.getId());
            if (ticks <= 0) {
                return;
            }
            activeBreezes.add(breeze.getId());
            if (VISUAL_BREEZE_WIND_CHARGES.add(breeze.getId())) {
                breeze.inhale.start(breeze.tickCount);
                breeze.shoot.start(breeze.tickCount);
            }
        }

        private static void stopVisualBreezeWindCharge(Breeze breeze) {
            breeze.inhale.stop();
            breeze.shoot.stop();
        }

        private static void syncVisualFireballStates(Minecraft minecraft) {
            if (minecraft.level == null) {
                VISUAL_FIREBALL_STATES.clear();
                return;
            }
            Set<Integer> activeFireballStates = new HashSet<>();
            for (var combatant : ClientBattleState.snapshot().players()) {
                syncVisualFireballState(minecraft.level.getEntity(combatant.entityId()), activeFireballStates);
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                syncVisualFireballState(minecraft.level.getEntity(combatant.entityId()), activeFireballStates);
            }
            for (int entityId : VISUAL_FIREBALL_STATES.keySet().toArray(Integer[]::new)) {
                if (!activeFireballStates.contains(entityId)) {
                    restoreVisualFireballState(minecraft.level.getEntity(entityId));
                }
            }
        }

        private static void syncVisualFireballState(Entity entity, Set<Integer> activeFireballStates) {
            if (!(entity instanceof LivingEntity living)) {
                return;
            }
            BattleVisualEvent.AnimationType type = ClientBattleState.visualAnimationType(living.getId());
            boolean charging = ClientBattleState.visualFireballCharging(living.getId());
            if (charging && type == BattleVisualEvent.AnimationType.BLAZE_FIREBALL && living instanceof Blaze blaze) {
                VISUAL_FIREBALL_STATES.computeIfAbsent(living.getId(), id -> TemporaryFireballState.capture(living));
                blaze.setCharged(true);
                activeFireballStates.add(living.getId());
            } else if (charging && type == BattleVisualEvent.AnimationType.GHAST_FIREBALL && living instanceof Ghast ghast) {
                VISUAL_FIREBALL_STATES.computeIfAbsent(living.getId(), id -> TemporaryFireballState.capture(living));
                ghast.setCharging(true);
                activeFireballStates.add(living.getId());
            }
        }

        private static void restoreVisualFireballState(Entity entity) {
            if (!(entity instanceof LivingEntity living)) {
                return;
            }
            TemporaryFireballState state = VISUAL_FIREBALL_STATES.remove(living.getId());
            if (state != null) {
                state.restore(living);
            }
        }

        private static void syncVisualShulkerPeekStates(Minecraft minecraft) {
            if (minecraft.level == null) {
                VISUAL_SHULKER_STATES.clear();
                return;
            }
            Set<Integer> activeShulkers = new HashSet<>();
            for (var combatant : ClientBattleState.snapshot().players()) {
                syncVisualShulkerPeekState(minecraft.level.getEntity(combatant.entityId()), activeShulkers);
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                syncVisualShulkerPeekState(minecraft.level.getEntity(combatant.entityId()), activeShulkers);
            }
            for (int entityId : VISUAL_SHULKER_STATES.keySet().toArray(Integer[]::new)) {
                if (!activeShulkers.contains(entityId)) {
                    restoreVisualShulkerState(minecraft.level.getEntity(entityId));
                }
            }
        }

        private static void syncVisualShulkerPeekState(Entity entity, Set<Integer> activeShulkers) {
            if (!(entity instanceof Shulker shulker)) {
                return;
            }
            if (ClientBattleState.visualAnimationType(shulker.getId()) != BattleVisualEvent.AnimationType.SHULKER_BULLET
                    || !ClientBattleState.visualShulkerBulletActive(shulker.getId())) {
                restoreVisualShulkerState(shulker);
                return;
            }
            VISUAL_SHULKER_STATES.computeIfAbsent(shulker.getId(), id -> TemporaryShulkerState.capture(shulker));
            shulker.setRawPeekAmount(100);
            activeShulkers.add(shulker.getId());
        }

        private static void restoreVisualShulkerState(Entity entity) {
            if (!(entity instanceof Shulker shulker)) {
                return;
            }
            TemporaryShulkerState state = VISUAL_SHULKER_STATES.remove(shulker.getId());
            if (state != null) {
                state.restore(shulker);
            }
        }

        private static void syncVisualUseItemState(LivingEntity entity, ItemStack mainHand) {
            int duration = Math.max(1, mainHand.getUseDuration(entity));
            int usedTicks = Math.max(0, ClientBattleState.visualTicksUsingItem(entity.getId()));
            entity.useItem = mainHand;
            entity.useItemRemaining = Math.max(1, duration - usedTicks);
            entity.setLivingEntityFlag(1, true);
            entity.setLivingEntityFlag(2, false);
        }

        private static void clearVisualUseItemState(LivingEntity entity) {
            entity.stopUsingItem();
            entity.useItem = ItemStack.EMPTY;
            entity.useItemRemaining = 0;
            entity.setLivingEntityFlag(1, false);
            entity.setLivingEntityFlag(2, false);
        }

        private static void syncVisualWalkAnimations(Minecraft minecraft) {
            if (minecraft.level == null) {
                return;
            }
            for (var combatant : ClientBattleState.snapshot().players()) {
                syncVisualWalkAnimation(minecraft.level.getEntity(combatant.entityId()));
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                syncVisualWalkAnimation(minecraft.level.getEntity(combatant.entityId()));
            }
        }

        private static void syncVisualWalkAnimation(Entity entity) {
            if (entity instanceof LivingEntity living && ClientBattleState.visualMovement(living.getId())) {
                living.walkAnimation.update(ClientBattleState.visualWalkSpeed(living.getId()), 0.65F);
            }
        }

        private static void restoreVisualMainHand(LivingEntity entity) {
            TemporaryHandState state = VISUAL_MAIN_HANDS.remove(entity.getId());
            if (state != null) {
                entity.setItemSlot(EquipmentSlot.MAINHAND, state.originalMainHand);
                state.restoreAggressive(entity);
                state.restoreCrossbowCharging(entity);
                state.restoreVexCharging(entity);
                state.restoreWitchDrinking(entity);
                restoreVisualRiptideState(entity);
                if (state.startedUsing && entity.isUsingItem()) {
                    clearVisualUseItemState(entity);
                }
            }
        }

        private static void restoreMainHand(LivingEntity entity) {
            TemporaryHandState state = TEMP_MAIN_HANDS.remove(entity.getId());
            if (state != null) {
                entity.setItemSlot(EquipmentSlot.MAINHAND, state.originalMainHand);
                state.restoreAggressive(entity);
                state.restoreCrossbowCharging(entity);
                state.restoreVexCharging(entity);
                state.restoreWitchDrinking(entity);
                if (state.startedUsing && entity.isUsingItem() && !ClientBattleState.visualUsingItem(entity.getId())) {
                    clearVisualUseItemState(entity);
                }
            }
        }

        private static void suppressDefaultBattleArmPose(LivingEntity entity, HumanoidModel<?> model) {
            if (!ClientBattleState.active() || ClientBattleState.snapshot().combatant(entity.getId()) == null) {
                return;
            }
            if (!(entity instanceof SpellcasterIllager) && ClientBattleState.visualEvokerSpellcasting(entity.getId())) {
                TEMP_ARM_POSES.computeIfAbsent(entity.getId(), id -> new TemporaryArmPoseState(model.leftArmPose, model.rightArmPose));
                model.leftArmPose = MoonSpireArmPoses.evokerSpellcasting();
                model.rightArmPose = MoonSpireArmPoses.evokerSpellcasting();
                return;
            }
            if (entity instanceof AbstractSkeleton && ClientBattleState.visualAnimationType(entity.getId()) == BattleVisualEvent.AnimationType.BOW_DRAW) {
                TEMP_ARM_POSES.computeIfAbsent(entity.getId(), id -> new TemporaryArmPoseState(model.leftArmPose, model.rightArmPose));
                if (entity.getMainArm() == HumanoidArm.RIGHT) {
                    model.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                    model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                } else {
                    model.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                    model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                }
                return;
            }
            if (!ClientBattleState.visualMainHandOverride(entity.getId()).isEmpty()) {
                return;
            }
            TEMP_ARM_POSES.computeIfAbsent(entity.getId(), id -> new TemporaryArmPoseState(model.leftArmPose, model.rightArmPose));
            model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        }

        private static boolean isEvokerSpellAnimation(BattleVisualEvent.AnimationType type) {
            return type == BattleVisualEvent.AnimationType.EVOKER_FANG_LINE
                    || type == BattleVisualEvent.AnimationType.EVOKER_FANG_CIRCLE
                    || type == BattleVisualEvent.AnimationType.EVOKER_SUMMON_VEX;
        }

        private static boolean isFireballAnimation(BattleVisualEvent.AnimationType type) {
            return type == BattleVisualEvent.AnimationType.BLAZE_FIREBALL
                    || type == BattleVisualEvent.AnimationType.GHAST_FIREBALL;
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
                for (int entityId : VISUAL_FIREBALL_STATES.keySet().toArray(Integer[]::new)) {
                    restoreVisualFireballState(minecraft.level.getEntity(entityId));
                }
                for (int entityId : VISUAL_SHULKER_STATES.keySet().toArray(Integer[]::new)) {
                    restoreVisualShulkerState(minecraft.level.getEntity(entityId));
                }
                for (int entityId : TEMP_ARM_POSES.keySet().toArray(Integer[]::new)) {
                    if (minecraft.level.getEntity(entityId) instanceof LivingEntity living) {
                        restoreArmPose(living);
                    }
                }
                for (int entityId : VISUAL_RIPTIDE_STATES.keySet().toArray(Integer[]::new)) {
                    if (minecraft.level.getEntity(entityId) instanceof LivingEntity living) {
                        restoreVisualRiptideState(living);
                    }
                }
                for (int entityId : VISUAL_RAVAGER_HEAD_RAMS.toArray(Integer[]::new)) {
                    if (minecraft.level.getEntity(entityId) instanceof Ravager ravager) {
                        ravager.attackTick = 0;
                    }
                }
                for (int entityId : VISUAL_HOGLIN_HEAD_ATTACKS.toArray(Integer[]::new)) {
                    clearVisualHoglinHeadAttack(minecraft.level.getEntity(entityId));
                }
                for (int entityId : VISUAL_WARDEN_MELEE_ATTACKS.toArray(Integer[]::new)) {
                    clearVisualWardenMeleeAttack(minecraft.level.getEntity(entityId));
                }
                for (int entityId : VISUAL_BREEZE_WIND_CHARGES.toArray(Integer[]::new)) {
                    if (minecraft.level.getEntity(entityId) instanceof Breeze breeze) {
                        stopVisualBreezeWindCharge(breeze);
                    }
                }
                VISUAL_BREEZE_WIND_CHARGES.clear();
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
            VISUAL_FIREBALL_STATES.clear();
            VISUAL_SHULKER_STATES.clear();
            TEMP_ARM_POSES.clear();
            VISUAL_RIPTIDE_STATES.clear();
            VISUAL_RAVAGER_HEAD_RAMS.clear();
            VISUAL_HOGLIN_HEAD_ATTACKS.clear();
            VISUAL_WARDEN_MELEE_ATTACKS.clear();
            VISUAL_BREEZE_WIND_CHARGES.clear();
            VISUAL_LUNGE_POSE_PUSHES.clear();
            SCHEDULED_BATTLE_SOUNDS.clear();
            SCHEDULED_WARDEN_SONIC_BOOM_VISUALS.clear();
            SCHEDULED_SELF_DESTRUCT_EXPLOSIONS.clear();
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
                clearVisualUseItemState(living);
            }
        }

        private static void clearEntityHandAnimation(Entity entity, boolean stopUsingItem) {
            if (entity instanceof LivingEntity living) {
                if (stopUsingItem && living.isUsingItem()) {
                    clearVisualUseItemState(living);
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
                if (attacker instanceof LivingEntity livingAttacker) {
                    applyVisualFacing(livingAttacker, event.lookTarget());
                }
                boolean playedCardVisual = event.playedCard() != null || !event.itemStack().isEmpty();
                boolean meleeLungeHit = event.animationType() == BattleVisualEvent.AnimationType.MELEE_LUNGE && event.animationTicks() <= 0;
                boolean vindicatorAxeHit = event.animationType() == BattleVisualEvent.AnimationType.VINDICATOR_AXE_SWING && event.animationTicks() <= 0;
                boolean vexChargeHit = event.animationType() == BattleVisualEvent.AnimationType.VEX_CHARGE_LUNGE && event.animationTicks() <= 0;
                boolean piglinMeleeHit = event.animationType() == BattleVisualEvent.AnimationType.PIGLIN_MELEE_SWING && event.animationTicks() <= 0;
                boolean wardenMeleeHit = event.animationType() == BattleVisualEvent.AnimationType.WARDEN_MELEE && event.animationTicks() <= 0;
                boolean shouldSwing = event.animationType() == BattleVisualEvent.AnimationType.NONE;
                boolean potionThrow = event.animationType() == BattleVisualEvent.AnimationType.POTION_THROW;
                if (((playedCardVisual && shouldSwing) || meleeLungeHit || vindicatorAxeHit || vexChargeHit || piglinMeleeHit || wardenMeleeHit || potionThrow) && attacker instanceof LivingEntity livingAttacker && swungAttackers.add(event.attackerId())) {
                    livingAttacker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                if (attacker instanceof LivingEntity livingAttacker) {
                    startWardenVisualState(livingAttacker, event.animationType());
                    scheduleUseSounds(livingAttacker, event);
                }
                if (event.animationType() == BattleVisualEvent.AnimationType.WARDEN_SONIC_BOOM) {
                    scheduleWardenSonicBoomVisual(event);
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
                        if (target instanceof LivingEntity livingTarget) {
                            playBattleHurtAnimation(livingTarget, attacker);
                        }
                    }
                }
                if (event.animationType() == BattleVisualEvent.AnimationType.UNDYING_REVIVE && target instanceof LivingEntity livingTarget) {
                    spawnUndyingParticles(livingTarget);
                }
            }
        }

        private static void startWardenVisualState(LivingEntity attacker, BattleVisualEvent.AnimationType animationType) {
            if (!(attacker instanceof Warden warden)) {
                return;
            }
            if (animationType == BattleVisualEvent.AnimationType.WARDEN_SONIC_BOOM) {
                warden.sonicBoomAnimationState.stop();
                warden.sonicBoomAnimationState.start(warden.tickCount);
            } else if (animationType == BattleVisualEvent.AnimationType.WARDEN_ROAR) {
                warden.roarAnimationState.stop();
                warden.roarAnimationState.start(warden.tickCount);
            }
        }

        private static void scheduleWardenSonicBoomVisual(BattleVisualEvent event) {
            int releaseDelay = Math.max(1, event.animationTicks() - 4);
            SCHEDULED_WARDEN_SONIC_BOOM_VISUALS.add(new ScheduledWardenSonicBoomVisual(event, releaseDelay));
        }

        private static void tickScheduledWardenSonicBoomVisuals(Minecraft minecraft) {
            if (minecraft.level == null) {
                SCHEDULED_WARDEN_SONIC_BOOM_VISUALS.clear();
                return;
            }
            Iterator<ScheduledWardenSonicBoomVisual> iterator = SCHEDULED_WARDEN_SONIC_BOOM_VISUALS.iterator();
            while (iterator.hasNext()) {
                ScheduledWardenSonicBoomVisual visual = iterator.next();
                if (visual.delayTicks > 0) {
                    visual.delayTicks--;
                    continue;
                }
                BattleVisualEvent event = visual.event;
                Entity attacker = minecraft.level.getEntity(event.attackerId());
                Entity target = minecraft.level.getEntity(event.targetId());
                if (attacker == null || target == null) {
                    iterator.remove();
                    continue;
                }
                spawnWardenSonicBoomParticles(minecraft, event, attacker, target);
                minecraft.level.playLocalSound(attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.0F, false);
                iterator.remove();
            }
        }

        private static void spawnWardenSonicBoomParticles(Minecraft minecraft, BattleVisualEvent event, Entity attacker, Entity target) {
            if (minecraft.level == null) {
                return;
            }
            Vec3 start = event.animationStart();
            if (start == null && attacker != null) {
                start = attacker.position().add(0.0D, attacker.getBbHeight() * 0.5D, 0.0D);
            }
            Vec3 end = event.animationStrike();
            if (end == null && target != null) {
                end = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            }
            if (start == null || end == null) {
                return;
            }
            Vec3 delta = end.subtract(start);
            double distance = delta.length();
            if (distance <= 0.001D) {
                minecraft.level.addParticle(ParticleTypes.SONIC_BOOM, start.x, start.y, start.z, 0.0D, 0.0D, 0.0D);
                return;
            }
            int steps = Mth.clamp((int) Math.ceil(distance * 2.0D), 3, 32);
            Vec3 step = delta.scale(1.0D / steps);
            for (int i = 0; i <= steps; i++) {
                Vec3 position = start.add(step.scale(i));
                minecraft.level.addParticle(ParticleTypes.SONIC_BOOM, position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
            }
        }

        private static void playProjectileImpactVisuals(Minecraft minecraft) {
            if (minecraft.level == null) {
                ClientBattleState.consumeProjectileImpactVisuals();
                return;
            }
            for (ClientBattleState.ProjectileImpactVisual impact : ClientBattleState.consumeProjectileImpactVisuals()) {
                if (impact.position() == null) {
                    continue;
                }
                Vec3 position = impact.position();
                if (impact.animationType() == BattleVisualEvent.AnimationType.WIND_CHARGE) {
                    Entity attacker = minecraft.level.getEntity(impact.attackerId());
                    boolean breeze = attacker instanceof Breeze;
                    SoundEvent burstSound = breeze ? SoundEvents.BREEZE_WIND_CHARGE_BURST.value() : SoundEvents.WIND_CHARGE_BURST.value();
                    minecraft.level.playLocalSound(position.x, position.y, position.z, burstSound, SoundSource.PLAYERS, 1.0F, 1.0F, false);
                    spawnWindChargeBurstParticles(minecraft, position);
                } else if (impact.animationType() == BattleVisualEvent.AnimationType.SHULKER_BULLET) {
                    minecraft.level.playLocalSound(position.x, position.y, position.z, SoundEvents.SHULKER_BULLET_HIT, SoundSource.PLAYERS, 1.0F, 1.0F, false);
                    spawnShulkerBulletImpactParticles(minecraft, position);
                } else if (isFireballAnimation(impact.animationType())) {
                    boolean large = impact.animationType() == BattleVisualEvent.AnimationType.GHAST_FIREBALL;
                    minecraft.level.playLocalSound(position.x, position.y, position.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, large ? 1.15F : 0.8F, large ? 0.9F : 1.15F, false);
                    spawnFireballImpactParticles(minecraft, position, large);
                }
            }
        }

        private static void spawnWindChargeBurstParticles(Minecraft minecraft, Vec3 position) {
            if (minecraft.level == null) {
                return;
            }
            minecraft.level.addParticle(ParticleTypes.GUST_EMITTER_SMALL, position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI * 0.25D;
                double x = Math.cos(angle);
                double z = Math.sin(angle);
                minecraft.level.addParticle(
                        ParticleTypes.SMALL_GUST,
                        position.x + x * 0.35D,
                        position.y,
                        position.z + z * 0.35D,
                        x * 0.12D,
                        0.02D,
                        z * 0.12D);
            }
        }

        private static void spawnShulkerBulletImpactParticles(Minecraft minecraft, Vec3 position) {
            if (minecraft.level == null) {
                return;
            }
            for (int i = 0; i < 10; i++) {
                double dx = (minecraft.level.random.nextDouble() - 0.5D) * 0.2D;
                double dy = (minecraft.level.random.nextDouble() - 0.5D) * 0.2D;
                double dz = (minecraft.level.random.nextDouble() - 0.5D) * 0.2D;
                minecraft.level.addParticle(ParticleTypes.END_ROD, position.x, position.y, position.z, dx, dy, dz);
            }
        }

        private static void spawnFireballImpactParticles(Minecraft minecraft, Vec3 position, boolean large) {
            if (minecraft.level == null) {
                return;
            }
            minecraft.level.addParticle(ParticleTypes.EXPLOSION, position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
            int count = large ? 18 : 10;
            for (int i = 0; i < count; i++) {
                double angle = i * (Math.PI * 2.0D / count);
                double radius = large ? 0.55D : 0.35D;
                double x = Math.cos(angle);
                double z = Math.sin(angle);
                double y = ((i % 3) - 1) * 0.06D;
                minecraft.level.addParticle(
                        ParticleTypes.FLAME,
                        position.x + x * radius,
                        position.y + y,
                        position.z + z * radius,
                        x * 0.08D,
                        0.03D,
                        z * 0.08D);
            }
        }

        private static void spawnEvokerSpellParticles(Minecraft minecraft) {
            if (minecraft.level == null) {
                return;
            }
            for (var combatant : ClientBattleState.snapshot().players()) {
                spawnEvokerSpellParticles(minecraft.level.getEntity(combatant.entityId()));
            }
            for (var combatant : ClientBattleState.snapshot().enemies()) {
                spawnEvokerSpellParticles(minecraft.level.getEntity(combatant.entityId()));
            }
        }

        private static void spawnEvokerSpellParticles(Entity entity) {
            if (!(entity instanceof LivingEntity living) || !ClientBattleState.visualEvokerSpellcasting(living.getId()) || living.level() == null) {
                return;
            }
            BattleVisualEvent.AnimationType spellType = ClientBattleState.visualEvokerSpellType(living.getId());
            float red = spellType == BattleVisualEvent.AnimationType.EVOKER_SUMMON_VEX ? 0.7F : 0.4F;
            float green = spellType == BattleVisualEvent.AnimationType.EVOKER_SUMMON_VEX ? 0.7F : 0.3F;
            float blue = spellType == BattleVisualEvent.AnimationType.EVOKER_SUMMON_VEX ? 0.8F : 0.35F;
            float angle = living.yBodyRot * ((float)Math.PI / 180.0F) + Mth.cos(living.tickCount * 0.6662F) * 0.25F;
            float xOffset = Mth.cos(angle);
            float zOffset = Mth.sin(angle);
            double horizontalScale = 0.6D * living.getScale();
            double y = living.getY() + 1.8D * living.getScale();
            living.level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, red, green, blue), living.getX() + xOffset * horizontalScale, y, living.getZ() + zOffset * horizontalScale, 0.0D, 0.0D, 0.0D);
            living.level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, red, green, blue), living.getX() - xOffset * horizontalScale, y, living.getZ() - zOffset * horizontalScale, 0.0D, 0.0D, 0.0D);
        }

        private static void spawnUndyingParticles(LivingEntity target) {
            for (int i = 0; i < 30; i++) {
                double x = target.getX() + (target.getRandom().nextDouble() - 0.5D) * target.getBbWidth();
                double y = target.getY() + target.getRandom().nextDouble() * target.getBbHeight();
                double z = target.getZ() + (target.getRandom().nextDouble() - 0.5D) * target.getBbWidth();
                target.level().addParticle(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, (target.getRandom().nextDouble() - 0.5D) * 0.5D, target.getRandom().nextDouble() * 0.5D, (target.getRandom().nextDouble() - 0.5D) * 0.5D);
            }
        }

        private static void applyVisualFacing(LivingEntity attacker, Vec3 lookTarget) {
            if (lookTarget == null) {
                return;
            }
            attacker.lookAt(EntityAnchorArgument.Anchor.EYES, lookTarget);
            attacker.setYBodyRot(attacker.getYRot());
            attacker.setYHeadRot(attacker.getYRot());
            attacker.yBodyRotO = attacker.getYRot();
            attacker.yHeadRotO = attacker.getYRot();
        }

        private static void playBattleHurtAnimation(LivingEntity target, Entity attacker) {
            float hurtYaw = attacker == null ? target.getYRot() : yawFromTargetToAttacker(target, attacker);
            target.animateHurt(hurtYaw);
        }

        private static float yawFromTargetToAttacker(LivingEntity target, Entity attacker) {
            double dx = attacker.getX() - target.getX();
            double dz = attacker.getZ() - target.getZ();
            if (dx * dx + dz * dz <= 0.0001D) {
                return target.getYRot();
            }
            return (float) (Math.atan2(dz, dx) * 57.2957763671875D) - 90.0F;
        }

        private static void scheduleUseSounds(LivingEntity attacker, BattleVisualEvent event) {
            int prepareTicks = ClientBattleState.projectilePrepareTicks(event);
            if (event.animationType() == BattleVisualEvent.AnimationType.BOW_DRAW) {
                scheduleBattleSound(attacker, SoundEvents.ARROW_SHOOT, prepareTicks, 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.CROSSBOW_LOAD) {
                scheduleBattleSound(attacker, SoundEvents.CROSSBOW_LOADING_START.value(), 0, 0.5F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), Math.max(1, prepareTicks / 2), 0.5F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.CROSSBOW_LOADING_END.value(), Math.max(1, prepareTicks), 0.5F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.CROSSBOW_SHOOT, Math.max(1, prepareTicks + 1), 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.TRIDENT_THROW) {
                scheduleBattleSound(attacker, SoundEvents.TRIDENT_THROW.value(), Math.max(1, prepareTicks), 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.CHANNELING_TRIDENT_THROW) {
                scheduleBattleSound(attacker, SoundEvents.TRIDENT_THROW.value(), Math.max(1, prepareTicks), 1.0F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.TRIDENT_THUNDER.value(), Math.max(1, event.animationTicks() + 8), 1.2F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.POTION_THROW) {
                scheduleBattleSound(attacker, attacker instanceof Witch ? SoundEvents.WITCH_THROW : SoundEvents.SPLASH_POTION_THROW, 0, 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.POTION_DRINK) {
                scheduleBattleSound(attacker, attacker instanceof Witch ? SoundEvents.WITCH_DRINK : SoundEvents.GENERIC_DRINK, 0, 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.WIND_CHARGE) {
                if (attacker instanceof Breeze) {
                    scheduleBattleSound(attacker, SoundEvents.BREEZE_CHARGE, 0, 0.9F, 1.0F);
                    scheduleBattleSound(attacker, SoundEvents.BREEZE_SHOOT, Math.max(1, prepareTicks), 1.0F, 1.0F);
                } else {
                    scheduleBattleSound(attacker, SoundEvents.WIND_CHARGE_THROW, Math.max(1, prepareTicks), 1.0F, 1.0F);
                }
            } else if (event.animationType() == BattleVisualEvent.AnimationType.SHULKER_BULLET) {
                scheduleBattleSound(attacker, SoundEvents.SHULKER_OPEN, 0, 0.8F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.SHULKER_SHOOT, Math.max(1, prepareTicks), 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.BLAZE_FIREBALL) {
                scheduleBattleSound(attacker, SoundEvents.BLAZE_SHOOT, Math.max(1, prepareTicks), 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.GHAST_FIREBALL) {
                scheduleBattleSound(attacker, SoundEvents.GHAST_WARN, 0, 1.0F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.GHAST_SHOOT, Math.max(1, prepareTicks), 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.RIPTIDE_RUSH) {
                scheduleBattleSound(attacker, SoundEvents.TRIDENT_RIPTIDE_1.value(), Math.max(1, ClientBattleState.RIPTIDE_CHARGE_TICKS), 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.HOGLIN_HEAD_ATTACK) {
                scheduleBattleSound(attacker, attacker instanceof Zoglin ? SoundEvents.ZOGLIN_ATTACK : SoundEvents.HOGLIN_ATTACK, 1, 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.WARDEN_MELEE) {
                scheduleBattleSound(attacker, SoundEvents.WARDEN_ATTACK_IMPACT, Math.max(1, event.animationTicks()), 1.2F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.WARDEN_SONIC_BOOM) {
                scheduleBattleSound(attacker, SoundEvents.WARDEN_SONIC_CHARGE, 0, 1.2F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.WARDEN_ROAR) {
                scheduleBattleSound(attacker, SoundEvents.WARDEN_ROAR, 0, 1.2F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.GUARDIAN_BEAM) {
                scheduleBattleSound(attacker, SoundEvents.GUARDIAN_ATTACK, 0, 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.EVOKER_FANG_LINE || event.animationType() == BattleVisualEvent.AnimationType.EVOKER_FANG_CIRCLE) {
                scheduleBattleSound(attacker, SoundEvents.EVOKER_PREPARE_ATTACK, 0, 1.0F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.EVOKER_CAST_SPELL, 20, 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.EVOKER_SUMMON_VEX) {
                scheduleBattleSound(attacker, SoundEvents.EVOKER_PREPARE_SUMMON, 0, 1.0F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.EVOKER_CAST_SPELL, 20, 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.UNDYING_REVIVE) {
                scheduleBattleSound(attacker, SoundEvents.TOTEM_USE, 0, 1.0F, 1.0F);
            } else if (event.animationType() == BattleVisualEvent.AnimationType.SELF_DESTRUCT) {
                scheduleBattleSound(attacker, SoundEvents.CREEPER_PRIMED, 0, 1.0F, 1.0F);
                scheduleBattleSound(attacker, SoundEvents.GENERIC_EXPLODE.value(), Math.max(1, event.animationTicks()), 1.4F, 0.9F);
                SCHEDULED_SELF_DESTRUCT_EXPLOSIONS.add(new ScheduledSelfDestructExplosion(attacker.getId(), Math.max(1, event.animationTicks())));
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

        private static void tickScheduledSelfDestructExplosions(Minecraft minecraft) {
            if (minecraft.level == null) {
                SCHEDULED_SELF_DESTRUCT_EXPLOSIONS.clear();
                return;
            }
            Iterator<ScheduledSelfDestructExplosion> iterator = SCHEDULED_SELF_DESTRUCT_EXPLOSIONS.iterator();
            while (iterator.hasNext()) {
                ScheduledSelfDestructExplosion explosion = iterator.next();
                if (explosion.delayTicks > 0) {
                    explosion.delayTicks--;
                    continue;
                }
                Entity entity = minecraft.level.getEntity(explosion.entityId);
                if (entity != null) {
                    minecraft.level.addParticle(
                            ParticleTypes.EXPLOSION_EMITTER,
                            entity.getX(),
                            entity.getY() + entity.getBbHeight() * 0.5D,
                            entity.getZ(),
                            0.0D,
                            0.0D,
                            0.0D);
                }
                iterator.remove();
            }
        }

    }

    private record TemporaryFireballState(Boolean originalBlazeCharged, Boolean originalGhastCharging) {
        private static TemporaryFireballState capture(LivingEntity entity) {
            Boolean blazeCharged = entity instanceof Blaze blaze ? blaze.isCharged() : null;
            Boolean ghastCharging = entity instanceof Ghast ghast ? ghast.isCharging() : null;
            return new TemporaryFireballState(blazeCharged, ghastCharging);
        }

        private void restore(LivingEntity entity) {
            if (originalBlazeCharged != null && entity instanceof Blaze blaze) {
                blaze.setCharged(originalBlazeCharged);
            }
            if (originalGhastCharging != null && entity instanceof Ghast ghast) {
                ghast.setCharging(originalGhastCharging);
            }
        }
    }

    private record TemporaryShulkerState(int originalRawPeekAmount) {
        private static TemporaryShulkerState capture(Shulker shulker) {
            return new TemporaryShulkerState(shulker.getRawPeekAmount());
        }

        private void restore(Shulker shulker) {
            shulker.setRawPeekAmount(originalRawPeekAmount);
        }
    }

    private static final class TemporaryHandState {
        private final ItemStack originalMainHand;
        private final Boolean originalAggressive;
        private final Boolean originalCrossbowCharging;
        private final Boolean originalPiglinCrossbowCharging;
        private final Boolean originalVexCharging;
        private final Boolean originalWitchDrinking;
        private boolean startedUsing;
        private boolean startedWitchDrinking;

        private TemporaryHandState(ItemStack originalMainHand, Boolean originalAggressive, Boolean originalCrossbowCharging, Boolean originalPiglinCrossbowCharging, Boolean originalVexCharging, Boolean originalWitchDrinking) {
            this.originalMainHand = originalMainHand;
            this.originalAggressive = originalAggressive;
            this.originalCrossbowCharging = originalCrossbowCharging;
            this.originalPiglinCrossbowCharging = originalPiglinCrossbowCharging;
            this.originalVexCharging = originalVexCharging;
            this.originalWitchDrinking = originalWitchDrinking;
        }

        private static TemporaryHandState capture(LivingEntity entity) {
            Boolean aggressive = entity instanceof net.minecraft.world.entity.Mob mob ? mob.isAggressive() : null;
            Boolean crossbowCharging = entity instanceof Pillager pillager ? pillager.isChargingCrossbow() : null;
            Boolean piglinCrossbowCharging = entity instanceof Piglin piglin ? piglin.isChargingCrossbow() : null;
            Boolean vexCharging = entity instanceof Vex vex ? vex.isCharging() : null;
            Boolean witchDrinking = entity instanceof Witch witch ? witch.isDrinkingPotion() : null;
            return new TemporaryHandState(entity.getItemBySlot(EquipmentSlot.MAINHAND).copy(), aggressive, crossbowCharging, piglinCrossbowCharging, vexCharging, witchDrinking);
        }

        private void restoreAggressive(LivingEntity entity) {
            if (originalAggressive != null && entity instanceof net.minecraft.world.entity.Mob mob) {
                mob.setAggressive(originalAggressive);
            }
        }

        private void restoreCrossbowCharging(LivingEntity entity) {
            if (originalCrossbowCharging != null && entity instanceof Pillager pillager) {
                pillager.setChargingCrossbow(originalCrossbowCharging);
            }
            if (originalPiglinCrossbowCharging != null && entity instanceof Piglin piglin) {
                piglin.setChargingCrossbow(originalPiglinCrossbowCharging);
            }
        }

        private void restoreVexCharging(LivingEntity entity) {
            if (originalVexCharging != null && entity instanceof Vex vex) {
                vex.setIsCharging(originalVexCharging);
            }
        }

        private void restoreWitchDrinking(LivingEntity entity) {
            if (originalWitchDrinking != null && entity instanceof Witch witch) {
                witch.setUsingItem(originalWitchDrinking);
                startedWitchDrinking = false;
            }
        }
    }

    private record TemporaryArmPoseState(HumanoidModel.ArmPose leftArmPose, HumanoidModel.ArmPose rightArmPose) {
    }

    private static final class VisualRiptideState {
        private final boolean originalSpinFlag;
        private final int originalAutoSpinAttackTicks;
        private final float originalAutoSpinAttackDmg;
        private final ItemStack originalAutoSpinAttackItemStack;
        private final Pose originalPose;

        private VisualRiptideState(boolean originalSpinFlag, int originalAutoSpinAttackTicks, float originalAutoSpinAttackDmg, ItemStack originalAutoSpinAttackItemStack, Pose originalPose) {
            this.originalSpinFlag = originalSpinFlag;
            this.originalAutoSpinAttackTicks = originalAutoSpinAttackTicks;
            this.originalAutoSpinAttackDmg = originalAutoSpinAttackDmg;
            this.originalAutoSpinAttackItemStack = originalAutoSpinAttackItemStack;
            this.originalPose = originalPose;
        }

        private static VisualRiptideState capture(LivingEntity entity) {
            ItemStack stack = entity.autoSpinAttackItemStack == null ? ItemStack.EMPTY : entity.autoSpinAttackItemStack.copy();
            return new VisualRiptideState(entity.isAutoSpinAttack(), entity.autoSpinAttackTicks, entity.autoSpinAttackDmg, stack, entity.getPose());
        }

        private void restore(LivingEntity entity) {
            entity.autoSpinAttackTicks = originalAutoSpinAttackTicks;
            entity.autoSpinAttackDmg = originalAutoSpinAttackDmg;
            entity.autoSpinAttackItemStack = originalAutoSpinAttackItemStack.isEmpty() ? null : originalAutoSpinAttackItemStack.copy();
            entity.setLivingEntityFlag(4, originalSpinFlag);
            if (!entity.isAutoSpinAttack() && entity.getPose() == Pose.SPIN_ATTACK) {
                entity.setPose(originalPose == Pose.SPIN_ATTACK ? Pose.STANDING : originalPose);
            }
        }
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

    private static final class ScheduledWardenSonicBoomVisual {
        private final BattleVisualEvent event;
        private int delayTicks;

        private ScheduledWardenSonicBoomVisual(BattleVisualEvent event, int delayTicks) {
            this.event = event;
            this.delayTicks = Math.max(0, delayTicks);
        }
    }

    private static final class ScheduledSelfDestructExplosion {
        private final int entityId;
        private int delayTicks;

        private ScheduledSelfDestructExplosion(int entityId, int delayTicks) {
            this.entityId = entityId;
            this.delayTicks = delayTicks;
        }
    }

    private static final class SelfDestructWhiteFlashLayer<T extends LivingEntity, M extends EntityModel<T>> extends net.minecraft.client.renderer.entity.layers.RenderLayer<T, M> {
        private SelfDestructWhiteFlashLayer(net.minecraft.client.renderer.entity.RenderLayerParent<T, M> renderer) {
            super(renderer);
        }

        @Override
        public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight, T livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float progress = ClientBattleState.selfDestructWhiteOverlayProgress(livingEntity.getId(), partialTick);
            if (progress <= 0.0F || livingEntity.isInvisible()) {
                return;
            }
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(getTextureLocation(livingEntity), false));
            getParentModel().renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.pack(progress, false), 0xFFFFFFFF);
        }
    }

    private static final class BattleRiptideLayer<T extends LivingEntity, M extends EntityModel<T>> extends net.minecraft.client.renderer.entity.layers.RenderLayer<T, M> {
        private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/trident_riptide.png");
        private final ModelPart box;

        private BattleRiptideLayer(net.minecraft.client.renderer.entity.RenderLayerParent<T, M> renderer, net.minecraft.client.model.geom.EntityModelSet models) {
            super(renderer);
            box = models.bakeLayer(ModelLayers.PLAYER_SPIN_ATTACK).getChild("box");
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!livingEntity.isAutoSpinAttack() || !ClientBattleState.visualRiptideSpin(livingEntity.getId())) {
                return;
            }
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            for (int i = 0; i < 3; i++) {
                poseStack.pushPose();
                float rotation = ageInTicks * -(45 + i * 5);
                poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
                float scale = 0.75F * i;
                poseStack.scale(scale, scale, scale);
                poseStack.translate(0.0F, -0.2F + 0.6F * i, 0.0F);
                box.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
    }

    static LivingEntity challengeTarget(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        LivingEntity targeted = targetedLivingEntity(minecraft, entity ->
                entity != minecraft.player && entity.isAlive() && MonsterDeckProfile.hasBattleDeck(entity));
        if (targeted != null) {
            return targeted;
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

    static LivingEntity targetedLivingEntity(Minecraft minecraft) {
        return targetedLivingEntity(minecraft, entity -> true);
    }

    static LivingEntity targetedLivingEntity(Minecraft minecraft, Predicate<LivingEntity> predicate) {
        if (minecraft == null) {
            return null;
        }
        Entity entity = minecraft.crosshairPickEntity;
        if (entity instanceof LivingEntity living && (predicate == null || predicate.test(living))) {
            return living;
        }
        return null;
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
