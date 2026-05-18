package com.yinfires.moonspire.client;

import com.yinfires.moonspire.MoonSpirePerfDiagnostics;
import com.yinfires.moonspire.battle.BattleCombatantSnapshot;
import com.yinfires.moonspire.battle.BattlePhase;
import com.yinfires.moonspire.battle.BattlePileSource;
import com.yinfires.moonspire.battle.BattleSnapshot;
import com.yinfires.moonspire.battle.BattleVisualEvent;
import com.yinfires.moonspire.battle.CombatantState;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.client.ui.MoonSpireBattleLayoutEditor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ClientBattleState {
    private static final int MELEE_LUNGE_TICKS = 8;
    private static final int MELEE_HIT_PAUSE_TICKS = 6;
    private static final int VINDICATOR_AXE_RAISE_TICKS = 8;
    private static final int VINDICATOR_AXE_APPROACH_TICKS = 8;
    private static final int VINDICATOR_AXE_STRIKE_TICKS = 1;
    private static final int VINDICATOR_AXE_RECOVER_TICKS = 6;
    private static final int VEX_CHARGE_RAISE_TICKS = 8;
    private static final int VEX_CHARGE_APPROACH_TICKS = 7;
    private static final int VEX_CHARGE_HIT_PAUSE_TICKS = 6;
    private static final int RAVAGER_HEAD_RAM_RAISE_TICKS = 2;
    private static final int RAVAGER_HEAD_RAM_APPROACH_TICKS = 8;
    private static final int RAVAGER_HEAD_RAM_STRIKE_TICKS = 2;
    private static final int RAVAGER_HEAD_RAM_RECOVER_TICKS = 6;
    private static final int PIGLIN_MELEE_RAISE_TICKS = 8;
    private static final int PIGLIN_MELEE_APPROACH_TICKS = 8;
    private static final int PIGLIN_MELEE_STRIKE_TICKS = 1;
    private static final int PIGLIN_MELEE_RECOVER_TICKS = 6;
    private static final int RAVAGER_HEAD_ATTACK_TICKS = 10;
    private static final int HOGLIN_HEAD_ATTACK_RAISE_TICKS = 2;
    private static final int HOGLIN_HEAD_ATTACK_APPROACH_TICKS = 8;
    private static final int HOGLIN_HEAD_ATTACK_STRIKE_TICKS = 2;
    private static final int HOGLIN_HEAD_ATTACK_RECOVER_TICKS = 6;
    private static final int HOGLIN_HEAD_ATTACK_TICKS = 10;
    static final int RIPTIDE_CHARGE_TICKS = 16;
    private static final int RIPTIDE_RUSH_TICKS = 10;
    private static final int RIPTIDE_HIT_PAUSE_TICKS = 6;
    private static final int RIPTIDE_SPIN_TICKS = RIPTIDE_RUSH_TICKS + RIPTIDE_HIT_PAUSE_TICKS;
    private static final double DEFAULT_RIPTIDE_CENTER_Y_OFFSET = 0.9D;
    private static final double LUNGE_STOP_DISTANCE = 1.55D;
    private static final double POUNCE_JUMP_HEIGHT = 1.0D;
    private static final double LUNGE_REACH = 10.0D;
    private static final int KNOCKBACK_RELEASE_TICKS = 24;
    private static final int KNOCKBACK_SETTLE_TICKS = 4;
    static final int POTION_THROW_PREPARE_TICKS = 8;
    private static final int PROJECTILE_DEFAULT_PREPARE_TICKS = 20;
    private static final int WIND_CHARGE_PREPARE_TICKS = 15;
    private static final int SHULKER_BULLET_PREPARE_TICKS = 12;
    private static final double KNOCKBACK_HORIZONTAL_DRAG = 0.82D;
    private static final double KNOCKBACK_VERTICAL_DRAG = 0.98D;
    private static final double KNOCKBACK_GRAVITY = 0.08D;
    private static final double KNOCKBACK_MAX_FALL_SPEED = -3.92D;
    private static final double KNOCKBACK_STOP_SPEED_SQR = 0.0025D;
    private static final float SELF_DESTRUCT_MAX_SCALE = 1.35F;
    private static final int FAKE_DEATH_DISSIPATE_TICKS = 6;
    private static final int FAKE_DEATH_PARTICLE_COUNT = 24;
    private static BattleSnapshot snapshot = BattleSnapshot.inactive();
    private static long snapshotVersion;
    private static UUID serverBattleId = BattleSnapshot.INACTIVE_BATTLE_ID;
    private static long serverSnapshotSequence;
    private static int selectedHandIndex = -1;
    private static int hoveredEntityId = -1;
    private static final Set<Integer> hoveredEntityIds = new HashSet<>();
    private static CameraType previousCameraType;
    private static Entity previousCameraEntity;
    private static CameraAnchor cameraAnchor;
    private static Vec3 lockedCameraCenter;
    private static float cameraAnchorEyeHeight;
    private static float cameraYaw = 35.0F;
    private static float cameraPitch = 24.0F;
    private static float cameraDistance = 9.0F;
    private static final double CAMERA_CENTER_CLEARANCE = 0.32D;
    private static final double[] CAMERA_CENTER_VERTICAL_OFFSETS = {0.0D, 0.5D, 1.0D, 1.5D, 2.0D, -0.5D};
    private static final double[] CAMERA_CENTER_HORIZONTAL_OFFSETS = {0.0D, 0.5D, 1.0D, 1.5D, 2.0D};
    private static final int[][] CAMERA_CENTER_DIRECTIONS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1},
            {1, 1},
            {1, -1},
            {-1, 1},
            {-1, -1}
    };
    private static final List<DamageNumber> damageNumbers = new ArrayList<>();
    private static final List<BlockGainAnimation> blockGainAnimations = new ArrayList<>();
    private static final List<GuardianBeamAnimation> guardianBeamAnimations = new ArrayList<>();
    private static final List<ProjectileVisual> projectileVisuals = new ArrayList<>();
    private static final List<ProjectileImpactVisual> projectileImpactVisuals = new ArrayList<>();
    private static final List<ScheduledVisualEvent> pendingVisualEvents = new ArrayList<>();
    private static final Map<Integer, VisualState> visualStates = new HashMap<>();
    private static final Map<Integer, FakeDeathVisual> fakeDeathVisuals = new HashMap<>();
    private static final Map<PileContentsKey, PileContents> pileContents = new HashMap<>();
    private static CardInstance monsterPlayedCard;
    private static int monsterPlayedCardAttackerId = -1;
    private static float monsterPlayedCardTicks;
    private static long monsterPlayedCardEventSequence;

    private ClientBattleState() {
    }

    public static BattleSnapshot snapshot() {
        return snapshot;
    }

    public static long snapshotVersion() {
        return snapshotVersion;
    }

    public static void setSnapshot(BattleSnapshot next) {
        long start = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        BattleSnapshot previous = snapshot;
        if (next == null) {
            next = BattleSnapshot.inactive();
        }
        if (previous.active() && !next.active() && !next.battleId().equals(serverBattleId)) {
            return;
        }
        if (next.battleId().equals(serverBattleId) && next.sequence() < serverSnapshotSequence) {
            return;
        }
        if (!next.battleId().equals(serverBattleId)) {
            serverBattleId = next.battleId();
            serverSnapshotSequence = 0L;
        }
        serverSnapshotSequence = Math.max(serverSnapshotSequence, next.sequence());
        snapshot = next;
        snapshotVersion++;
        if (!previous.active() && next.active()) {
            long enterStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
            clearVisualStates();
            enterBattleCamera();
            if (MoonSpirePerfDiagnostics.enabled()) {
                MoonSpirePerfDiagnostics.mark("client.battle.enterCamera", MoonSpirePerfDiagnostics.now() - enterStart);
            }
        }
        if (previous.active() && !next.active()) {
            long leaveStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
            leaveBattleCamera();
            selectedHandIndex = -1;
            hoveredEntityId = -1;
            hoveredEntityIds.clear();
            damageNumbers.clear();
            blockGainAnimations.clear();
            guardianBeamAnimations.clear();
            projectileVisuals.clear();
            pendingVisualEvents.clear();
            clearVisualStates();
            clearFakeDeathVisuals();
            pileContents.clear();
            monsterPlayedCard = null;
            monsterPlayedCardAttackerId = -1;
            monsterPlayedCardTicks = 0.0F;
            monsterPlayedCardEventSequence = 0L;
            MoonSpireBattleLayoutEditor.close();
            if (MoonSpirePerfDiagnostics.enabled()) {
                MoonSpirePerfDiagnostics.mark("client.battle.leave", MoonSpirePerfDiagnostics.now() - leaveStart);
                MoonSpirePerfDiagnostics.markOperation("client.battle.setSnapshot", MoonSpirePerfDiagnostics.now() - start,
                        "battleId=" + next.battleId()
                                + " sequence=" + next.sequence()
                                + " active=false");
            }
            return;
        }
        if (next.active() && previous.active() && previous.round() != next.round()) {
            selectedHandIndex = -1;
        }
        long visualsStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        for (BattleVisualEvent visualEvent : next.visualEvents()) {
            pendingVisualEvents.add(new ScheduledVisualEvent(visualEvent, visualEvent.delayTicks()));
            enqueueDamageNumbers(visualEvent);
        }
        long visualsNanos = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() - visualsStart : 0L;
        long fakeDeathStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        updateFakeDeathVisuals(next);
        long fakeDeathNanos = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() - fakeDeathStart : 0L;
        clampSelectedHandIndex();
        if (MoonSpirePerfDiagnostics.enabled()) {
            MoonSpirePerfDiagnostics.markOperation("client.battle.setSnapshot", MoonSpirePerfDiagnostics.now() - start,
                    "battleId=" + next.battleId()
                            + " sequence=" + next.sequence()
                            + " active=" + next.active()
                            + " visualEvents=" + next.visualEvents().size()
                            + " visualMs=" + MoonSpirePerfDiagnostics.millis(visualsNanos)
                            + " fakeDeathMs=" + MoonSpirePerfDiagnostics.millis(fakeDeathNanos));
        }
    }

    public static void clear() {
        setSnapshot(BattleSnapshot.inactive(serverBattleId, serverSnapshotSequence + 1L));
    }

    public static void setPileContents(UUID battleId, BattlePileSource source, long deckVersion, int expectedCount, List<CardInstance> cards) {
        setPileContents(battleId, source, deckVersion, -1, expectedCount, cards);
    }

    public static void setPileContents(UUID battleId, BattlePileSource source, long deckVersion, int entityId, int expectedCount, List<CardInstance> cards) {
        if (battleId == null || source == null || cards == null || !battleId.equals(serverBattleId)) {
            return;
        }
        pileContents.put(new PileContentsKey(battleId, source, deckVersion, entityId), new PileContents(expectedCount, cards));
    }

    public static List<CardInstance> pileContents(UUID battleId, BattlePileSource source, long deckVersion) {
        return pileContents(battleId, source, deckVersion, -1);
    }

    public static List<CardInstance> pileContents(UUID battleId, BattlePileSource source, long deckVersion, int entityId) {
        PileContents contents = pileContents.get(new PileContentsKey(battleId, source, deckVersion, entityId));
        return contents == null ? List.of() : contents.cards();
    }

    public static long pileContentsVersionAtOrAfter(UUID battleId, BattlePileSource source, long deckVersion, int entityId) {
        long bestVersion = Long.MIN_VALUE;
        for (PileContentsKey key : pileContents.keySet()) {
            if (key.matches(battleId, source, entityId) && key.deckVersion >= deckVersion && key.deckVersion > bestVersion) {
                bestVersion = key.deckVersion;
            }
        }
        return bestVersion == Long.MIN_VALUE ? -1L : bestVersion;
    }

    public static boolean hasPileContents(UUID battleId, BattlePileSource source, long deckVersion) {
        return hasPileContents(battleId, source, deckVersion, -1);
    }

    public static boolean hasPileContents(UUID battleId, BattlePileSource source, long deckVersion, int entityId) {
        return pileContents.containsKey(new PileContentsKey(battleId, source, deckVersion, entityId));
    }

    public static boolean hasPileContentsAtOrAfter(UUID battleId, BattlePileSource source, long deckVersion, int entityId) {
        return pileContentsVersionAtOrAfter(battleId, source, deckVersion, entityId) >= 0L;
    }

    public static int pileExpectedCount(UUID battleId, BattlePileSource source, long deckVersion, int entityId) {
        PileContents contents = pileContents.get(new PileContentsKey(battleId, source, deckVersion, entityId));
        return contents == null ? -1 : contents.expectedCount();
    }

    public static boolean active() {
        return snapshot.active();
    }

    public static boolean playerTurn() {
        return active() && snapshot.phase() == BattlePhase.PLAYER_TURN;
    }

    public static boolean resolvingEffects() {
        return active() && snapshot.resolvingEffects();
    }

    public static int selectedHandIndex() {
        clampSelectedHandIndex();
        return selectedHandIndex;
    }

    public static void selectHandIndex(int index) {
        selectedHandIndex = index;
        clampSelectedHandIndex();
    }

    public static void cycleSelectedCard(int delta) {
        int handSize = snapshot.hand().size();
        if (handSize <= 0) {
            selectedHandIndex = -1;
            return;
        }
        int base = selectedHandIndex < 0 ? 0 : selectedHandIndex;
        selectedHandIndex = Math.floorMod(base + delta, handSize);
    }

    public static int selectedTargetId() {
        return snapshot.selectedTargetId();
    }

    public static int hoveredEntityId() {
        return hoveredEntityId;
    }

    public static void setHoveredEntityId(int entityId) {
        hoveredEntityId = entityId;
        hoveredEntityIds.clear();
        if (entityId >= 0) {
            hoveredEntityIds.add(entityId);
        }
    }

    public static void setHoveredEntityIds(Collection<Integer> entityIds) {
        hoveredEntityIds.clear();
        hoveredEntityId = -1;
        if (entityIds == null) {
            return;
        }
        for (int entityId : entityIds) {
            if (entityId < 0) {
                continue;
            }
            hoveredEntityIds.add(entityId);
            if (hoveredEntityId < 0) {
                hoveredEntityId = entityId;
            }
        }
    }

    public static boolean isHoveredEntityId(int entityId) {
        return hoveredEntityIds.contains(entityId);
    }

    public static boolean hasHoveredEntityIds() {
        return !hoveredEntityIds.isEmpty();
    }

    public static float cameraYaw() {
        return cameraYaw;
    }

    public static float cameraPitch() {
        return cameraPitch;
    }

    public static float cameraDistance() {
        return cameraDistance;
    }

    public static void rotateCamera(double deltaX, double deltaY) {
        cameraYaw = (float) (cameraYaw + deltaX * 0.35D);
        cameraPitch = (float) Math.max(-15.0D, Math.min(60.0D, cameraPitch + deltaY * 0.25D));
    }

    public static void zoomCamera(double scrollDelta) {
        cameraDistance = (float) Math.max(4.5D, Math.min(15.0D, cameraDistance - scrollDelta * 0.65D));
    }

    public static void updateCameraAnchor(Minecraft minecraft) {
        if (!active() || minecraft.level == null) {
            return;
        }
        if (cameraAnchor == null || cameraAnchor.level() != minecraft.level) {
            cameraAnchor = new CameraAnchor(minecraft.level);
        }
        cameraAnchor.refreshDimensions();
        Vec3 center = cameraCenter();
        cameraAnchor.setPos(center.x, center.y - cameraAnchorEyeHeight, center.z);
        cameraAnchor.setYRot(cameraYaw);
        cameraAnchor.setXRot(cameraPitch);
        cameraAnchor.setOldPosAndRot();
        if (minecraft.getCameraEntity() != cameraAnchor) {
            minecraft.setCameraEntity(cameraAnchor);
        }
    }

    public static Vec3 cameraCenter() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Vec3.ZERO;
        }
        var playerEntity = minecraft.level.getEntity(snapshot.player().entityId());
        var monsterEntity = minecraft.level.getEntity(snapshot.monster().entityId());
        if (playerEntity != null && monsterEntity != null) {
            if (lockedCameraCenter == null) {
                Vec3 midpoint = playerEntity.getBoundingBox().getCenter().add(monsterEntity.getBoundingBox().getCenter()).scale(0.5D);
                lockedCameraCenter = resolveCameraCenter(minecraft.level, midpoint);
            }
            return lockedCameraCenter;
        }
        if (minecraft.player != null) {
            return minecraft.player.position();
        }
        return Vec3.ZERO;
    }

    private static Vec3 resolveCameraCenter(Level level, Vec3 midpoint) {
        for (double verticalOffset : CAMERA_CENTER_VERTICAL_OFFSETS) {
            for (double radius : CAMERA_CENTER_HORIZONTAL_OFFSETS) {
                if (radius == 0.0D) {
                    Vec3 candidate = midpoint.add(0.0D, verticalOffset, 0.0D);
                    if (clearCameraCenter(level, candidate)) {
                        return candidate;
                    }
                    continue;
                }
                for (int[] direction : CAMERA_CENTER_DIRECTIONS) {
                    Vec3 candidate = midpoint.add(direction[0] * radius, verticalOffset, direction[1] * radius);
                    if (clearCameraCenter(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return midpoint;
    }

    private static boolean clearCameraCenter(Level level, Vec3 center) {
        AABB clearance = new AABB(
                center.x - CAMERA_CENTER_CLEARANCE,
                center.y - CAMERA_CENTER_CLEARANCE,
                center.z - CAMERA_CENTER_CLEARANCE,
                center.x + CAMERA_CENTER_CLEARANCE,
                center.y + CAMERA_CENTER_CLEARANCE,
                center.z + CAMERA_CENTER_CLEARANCE);
        return level.noCollision(clearance);
    }

    public static List<DamageNumber> damageNumbers() {
        return damageNumbers;
    }

    public static List<BlockGainAnimation> blockGainAnimations() {
        return blockGainAnimations;
    }

    public static List<GuardianBeamAnimation> guardianBeamAnimations() {
        return guardianBeamAnimations;
    }

    public static List<ProjectileVisual> projectileVisuals() {
        return projectileVisuals;
    }

    public static List<ProjectileImpactVisual> consumeProjectileImpactVisuals() {
        List<ProjectileImpactVisual> impacts = new ArrayList<>(projectileImpactVisuals);
        projectileImpactVisuals.clear();
        return impacts;
    }

    public static List<BattleVisualEvent> consumeVisualEvents() {
        List<BattleVisualEvent> events = new ArrayList<>();
        Iterator<ScheduledVisualEvent> iterator = pendingVisualEvents.iterator();
        while (iterator.hasNext()) {
            ScheduledVisualEvent scheduled = iterator.next();
            if (scheduled.delayTicks > 0) {
                scheduled.delayTicks--;
                continue;
            }
            BattleVisualEvent event = scheduled.event;
            VisualState attackerVisual = visualStates.computeIfAbsent(event.attackerId(), id -> new VisualState());
            attackerVisual.showItem(event.itemStack(), event.animationType(), event.animationTicks(), lungeStart(event), lungeStrike(event), isPounceEvent(event), riptideCenterYOffset(event));
            if (event.healthDamage() > 0 && !fakeDeathHidden(event.targetId())) {
                visualStates.computeIfAbsent(event.targetId(), id -> new VisualState()).hurtFlash(event.knockbackDelta());
            }
            if (event.gainedBlock() > 0) {
                blockGainAnimations.add(new BlockGainAnimation(event.targetId(), System.nanoTime()));
            }
            if (event.animationType() == BattleVisualEvent.AnimationType.GUARDIAN_BEAM) {
                guardianBeamAnimations.add(new GuardianBeamAnimation(event.attackerId(), event.targetId(), Math.max(1, event.animationTicks())));
            }
            if (isProjectileVisualEvent(event)) {
                projectileVisuals.add(ProjectileVisual.from(event));
            }
            updatePlayedCardDisplay(snapshot, event);
            events.add(event);
            iterator.remove();
        }
        return events;
    }

    public static ItemStack visualMainHandOverride(int entityId) {
        VisualState state = visualStates.get(entityId);
        if (state == null || state.itemTicks <= 0 || state.itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return state.mainHandStack();
    }

    public static CardInstance monsterPlayedCard() {
        return monsterPlayedCard;
    }

    public static int monsterPlayedCardAttackerId() {
        return monsterPlayedCardAttackerId;
    }

    public static long monsterPlayedCardEventSequence() {
        return monsterPlayedCardEventSequence;
    }

    public static void advanceMonsterPlayedCard(float deltaTicks, boolean resolvingEffects) {
        if (monsterPlayedCard == null) {
            return;
        }
        if (resolvingEffects) {
            return;
        }
        monsterPlayedCardTicks = Math.max(0.0F, monsterPlayedCardTicks - Math.max(0.0F, deltaTicks));
        if (monsterPlayedCardTicks <= 0.0F) {
            monsterPlayedCard = null;
            monsterPlayedCardAttackerId = -1;
        }
    }

    public static float monsterPlayedCardAlpha() {
        if (monsterPlayedCard == null || !monsterPlayedCardFadesOut()) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, monsterPlayedCardTicks / 10.0F));
    }

    public static void skipMonsterPlayedCardHold() {
        monsterPlayedCardTicks = 0.0F;
    }

    public static int hurtFlashTicks(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 0 : state.hurtTicks();
    }

    public static int knockbackReleaseTicks(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 0 : state.knockbackTicks();
    }

    public static boolean visualUsingItem(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.usingTicks() > 0;
    }

    public static boolean visualDrownedTridentPose(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.drownedTridentPose();
    }

    public static boolean visualVindicatorAxeRaised(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.vindicatorAxeRaised();
    }

    public static boolean visualVexCharging(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.vexCharging();
    }

    public static boolean visualPiglinMeleeRaised(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.piglinMeleeRaised();
    }

    public static int visualRavagerAttackTick(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 0 : state.ravagerAttackTick();
    }

    public static int visualHoglinAttackTick(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 0 : state.hoglinAttackTick();
    }

    public static boolean visualEvokerSpellcasting(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.evokerSpellcasting();
    }

    public static int visualWindChargeTicks(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 0 : state.windChargeTicks();
    }

    public static boolean visualFireballCharging(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.fireballChargeTicks() > 0;
    }

    public static boolean visualShulkerBulletActive(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.shulkerBulletTicks() > 0;
    }

    public static BattleVisualEvent.AnimationType visualEvokerSpellType(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? BattleVisualEvent.AnimationType.NONE : state.evokerSpellType();
    }

    public static int visualTicksUsingItem(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 0 : state.ticksUsingItem();
    }

    public static boolean visualRiptideSpin(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.riptideSpinTicks() > 0;
    }

    public static int visualRiptideSpinTicks(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 0 : state.riptideSpinTicks();
    }

    public static boolean visualMeleeLunge(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.lungeTicks() > 0;
    }

    public static boolean visualMovement(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.movingVisually();
    }

    public static float visualWalkSpeed(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 0.0F : state.walkSpeed();
    }

    public static Vec3 visualRenderOffset(int entityId, Vec3 renderedPosition, float partialTick) {
        VisualState state = visualStates.get(entityId);
        if (state == null || renderedPosition == null) {
            return Vec3.ZERO;
        }
        Vec3 offset = Vec3.ZERO;
        offset = offset.add(state.lungeOffset(renderedPosition, partialTick));
        Vec3 knockback = state.knockbackOffset(renderedPosition, partialTick);
        return knockback == null ? offset : offset.add(knockback);
    }

    public static BattleVisualEvent.AnimationType visualAnimationType(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? BattleVisualEvent.AnimationType.NONE : state.animationType();
    }

    public static float visualSelfDestructScale(int entityId, float partialTick) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 1.0F : state.selfDestructScale(partialTick);
    }

    public static float selfDestructWhiteOverlayProgress(int entityId, float partialTick) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 0.0F : state.selfDestructWhiteOverlayProgress(partialTick);
    }

    public static boolean visualSelfDestructing(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state != null && state.selfDestructTicks() > 0;
    }

    public static boolean fakeDeathHidden(int entityId) {
        FakeDeathVisual visual = fakeDeathVisuals.get(entityId);
        return visual != null && (visual.stage == FakeDeathStage.DISSIPATING || visual.stage == FakeDeathStage.HIDDEN);
    }

    public static boolean fakeDeathAnimating(int entityId) {
        FakeDeathVisual visual = fakeDeathVisuals.get(entityId);
        return visual != null && visual.stage == FakeDeathStage.DEATH_ANIMATION;
    }

    public static void clearVisualStates() {
        visualStates.clear();
        guardianBeamAnimations.clear();
        projectileVisuals.clear();
        projectileImpactVisuals.clear();
    }

    public static int fakeDeathRenderTicks(int entityId) {
        FakeDeathVisual visual = fakeDeathVisuals.get(entityId);
        if (visual == null || visual.stage != FakeDeathStage.DEATH_ANIMATION) {
            return 0;
        }
        return Math.min(CombatantState.FAKE_DEATH_ANIMATION_TICKS, visual.age);
    }

    public static void tickDamageNumbers() {
        tickDamageNumbers(true);
    }

    public static void tickClientLogic() {
        tickDamageNumbers(true);
        tickFakeDeathVisuals();
    }

    public static void tickDamageNumbers(boolean tickVisualStates) {
        Iterator<DamageNumber> iterator = damageNumbers.iterator();
        while (iterator.hasNext()) {
            DamageNumber number = iterator.next();
            number.tick();
            if (number.age() > 36) {
                iterator.remove();
            }
        }
        long now = System.nanoTime();
        Iterator<BlockGainAnimation> blockIterator = blockGainAnimations.iterator();
        while (blockIterator.hasNext()) {
            BlockGainAnimation animation = blockIterator.next();
            if (animation.done(now)) {
                blockIterator.remove();
            }
        }
        if (!tickVisualStates) {
            return;
        }
        Iterator<GuardianBeamAnimation> beamIterator = guardianBeamAnimations.iterator();
        while (beamIterator.hasNext()) {
            GuardianBeamAnimation animation = beamIterator.next();
            animation.tick(snapshot);
            if (animation.done()) {
                beamIterator.remove();
            }
        }
        Iterator<ProjectileVisual> projectileIterator = projectileVisuals.iterator();
        while (projectileIterator.hasNext()) {
            ProjectileVisual visual = projectileIterator.next();
            visual.tick();
            if (visual.consumeImpact()) {
                projectileImpactVisuals.add(visual.impactVisual());
            }
            if (visual.done()) {
                projectileIterator.remove();
            }
        }
        Iterator<VisualState> visualIterator = visualStates.values().iterator();
        while (visualIterator.hasNext()) {
            VisualState state = visualIterator.next();
            state.tick();
            if (state.done()) {
                visualIterator.remove();
            }
        }
    }

    private static void clampSelectedHandIndex() {
        int handSize = snapshot.hand().size();
        if (handSize <= 0) {
            selectedHandIndex = -1;
        } else if (selectedHandIndex >= handSize) {
            selectedHandIndex = handSize - 1;
        } else if (selectedHandIndex < 0) {
            selectedHandIndex = -1;
        }
    }

    private static void enterBattleCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null) {
            previousCameraType = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        previousCameraEntity = minecraft.getCameraEntity();
        lockedCameraCenter = null;
        cameraAnchorEyeHeight = minecraft.player == null ? 0.0F : minecraft.player.getEyeHeight();
        updateCameraAnchor(minecraft);
    }

    private static void leaveBattleCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null && previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
        }
        if (minecraft.getCameraEntity() == cameraAnchor) {
            minecraft.setCameraEntity(previousCameraEntity != null ? previousCameraEntity : minecraft.player);
        }
        previousCameraType = null;
        previousCameraEntity = null;
        lockedCameraCenter = null;
        cameraAnchorEyeHeight = 0.0F;
        cameraAnchor = null;
    }

    private static void enqueueDamageNumbers(BattleVisualEvent event) {
        if (event.blockedDamage() > 0) {
            damageNumbers.add(new DamageNumber(event.targetId(), event.blockedDamage(), true, event.delayTicks() + damageNumbers.size() * 2));
        }
        if (event.healthDamage() > 0) {
            damageNumbers.add(new DamageNumber(event.targetId(), event.healthDamage(), false, event.delayTicks() + damageNumbers.size() * 2 + 4));
        }
        if (event.healedHealth() > 0) {
            damageNumbers.add(new DamageNumber(event.targetId(), event.healedHealth(), false, true, event.delayTicks() + damageNumbers.size() * 2 + 4));
        }
    }

    private static void updateFakeDeathVisuals(BattleSnapshot next) {
        Set<Integer> fakeDeadIds = new HashSet<>();
        for (var player : next.players()) {
            if (player.fakeDead()) {
                fakeDeadIds.add(player.entityId());
            }
        }
        for (var enemy : next.enemies()) {
            if (enemy.fakeDead()) {
                fakeDeadIds.add(enemy.entityId());
            }
        }
        Iterator<Integer> iterator = fakeDeathVisuals.keySet().iterator();
        while (iterator.hasNext()) {
            int entityId = iterator.next();
            if (!fakeDeadIds.contains(entityId)) {
                resetFakeDeathAnimation(entityId);
                iterator.remove();
            }
        }
        for (int entityId : fakeDeadIds) {
            if (visualSelfDestructing(entityId)) {
                continue;
            }
            fakeDeathVisuals.putIfAbsent(entityId, new FakeDeathVisual());
        }
    }

    private static void tickFakeDeathVisuals() {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot.active()) {
            for (BattleCombatantSnapshot combatant : snapshot.players()) {
                addFakeDeathVisualIfReady(combatant);
            }
            for (BattleCombatantSnapshot combatant : snapshot.enemies()) {
                addFakeDeathVisualIfReady(combatant);
            }
        }
        Iterator<Map.Entry<Integer, FakeDeathVisual>> iterator = fakeDeathVisuals.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, FakeDeathVisual> entry = iterator.next();
            int entityId = entry.getKey();
            FakeDeathVisual visual = entry.getValue();
            BattleCombatantSnapshot combatant = snapshot.combatant(entityId);
            if (!snapshot.active() || combatant == null || !combatant.fakeDead()) {
                resetFakeDeathAnimation(entityId);
                iterator.remove();
                continue;
            }
            Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
            if (!(entity instanceof LivingEntity living) || living.isRemoved()) {
                iterator.remove();
                continue;
            }
            visual.tick(living);
        }
    }

    private static void addFakeDeathVisualIfReady(BattleCombatantSnapshot combatant) {
        if (combatant != null
                && combatant.fakeDead()
                && !visualSelfDestructing(combatant.entityId())
                && !fakeDeathVisuals.containsKey(combatant.entityId())) {
            fakeDeathVisuals.put(combatant.entityId(), new FakeDeathVisual());
        }
    }

    private static void clearFakeDeathVisuals() {
        for (int entityId : fakeDeathVisuals.keySet()) {
            resetFakeDeathAnimation(entityId);
        }
        fakeDeathVisuals.clear();
    }

    private static void resetFakeDeathAnimation(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (minecraft.level.getEntity(entityId) instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
            living.deathTime = 0;
        }
    }

    private static void clearVisualState(int entityId) {
        visualStates.remove(entityId);
    }

    private static void spawnFakeDeathDissipateParticles(LivingEntity living) {
        if (living.level() == null) {
            return;
        }
        double width = Math.max(0.25D, living.getBbWidth());
        double height = Math.max(0.25D, living.getBbHeight());
        for (int i = 0; i < FAKE_DEATH_PARTICLE_COUNT; i++) {
            double x = living.getX() + (living.getRandom().nextDouble() - 0.5D) * width;
            double y = living.getY() + living.getRandom().nextDouble() * height;
            double z = living.getZ() + (living.getRandom().nextDouble() - 0.5D) * width;
            double dx = (living.getRandom().nextDouble() - 0.5D) * 0.08D;
            double dy = living.getRandom().nextDouble() * 0.08D;
            double dz = (living.getRandom().nextDouble() - 0.5D) * 0.08D;
            living.level().addParticle(ParticleTypes.POOF, x, y, z, dx, dy, dz);
        }
    }

    private enum FakeDeathStage {
        DEATH_ANIMATION,
        DISSIPATING,
        HIDDEN
    }

    private static final class FakeDeathVisual {
        private FakeDeathStage stage = FakeDeathStage.DEATH_ANIMATION;
        private int age;

        private void tick(LivingEntity living) {
            if (stage == FakeDeathStage.DEATH_ANIMATION) {
                age++;
                living.deathTime = Math.max(living.deathTime, Math.min(CombatantState.FAKE_DEATH_ANIMATION_TICKS, age));
                if (age >= CombatantState.FAKE_DEATH_ANIMATION_TICKS) {
                    stage = FakeDeathStage.DISSIPATING;
                    age = 0;
                    clearVisualState(living.getId());
                    spawnFakeDeathDissipateParticles(living);
                }
                return;
            }
            if (stage == FakeDeathStage.DISSIPATING) {
                age++;
                if (age >= FAKE_DEATH_DISSIPATE_TICKS) {
                    stage = FakeDeathStage.HIDDEN;
                    age = 0;
                    clearVisualState(living.getId());
                }
            }
        }
    }

    private static void updatePlayedCardDisplay(BattleSnapshot next, BattleVisualEvent event) {
        if (event.playedCard() == null || event.attackerId() == next.localPlayerEntityId()) {
            return;
        }
        monsterPlayedCard = event.playedCard();
        monsterPlayedCardAttackerId = event.attackerId();
        monsterPlayedCardTicks = Math.max(20.0F, event.animationTicks());
        monsterPlayedCardEventSequence++;
    }

    private static boolean monsterPlayedCardHasEffect(CardEffectKind kind) {
        return monsterPlayedCard != null && monsterPlayedCard.effects().stream().anyMatch(effect -> effect.kind() == kind);
    }

    private static boolean monsterPlayedCardFadesOut() {
        return monsterPlayedCard != null
                && (MoonSpireCardRegistry.SELF_DESTRUCT_VIEW_CARD_ID.equals(monsterPlayedCard.cardId())
                || monsterPlayedCardHasEffect(CardEffectKind.EXHAUST));
    }

    private static boolean isPounceEvent(BattleVisualEvent event) {
        return event != null
                && event.animationType() == BattleVisualEvent.AnimationType.MELEE_LUNGE
                && event.playedCard() != null
                && "builtin_monster_pounce".equals(event.playedCard().cardId());
    }

    private static double riptideCenterYOffset(BattleVisualEvent event) {
        if (event == null || event.animationType() != BattleVisualEvent.AnimationType.RIPTIDE_RUSH) {
            return 0.0D;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(event.attackerId()) instanceof Entity attacker) {
            return Math.max(0.1D, attacker.getBbHeight() * 0.5D);
        }
        return DEFAULT_RIPTIDE_CENTER_Y_OFFSET;
    }

    private static boolean isProjectileVisualEvent(BattleVisualEvent event) {
        if (event == null || event.animationTicks() <= 0 || event.animationStart() == null || event.animationStrike() == null) {
            return false;
        }
        return event.animationType() == BattleVisualEvent.AnimationType.BOW_DRAW
                || event.animationType() == BattleVisualEvent.AnimationType.CROSSBOW_LOAD
                || event.animationType() == BattleVisualEvent.AnimationType.TRIDENT_THROW
                || event.animationType() == BattleVisualEvent.AnimationType.CHANNELING_TRIDENT_THROW
                || event.animationType() == BattleVisualEvent.AnimationType.POTION_THROW
                || event.animationType() == BattleVisualEvent.AnimationType.WIND_CHARGE
                || event.animationType() == BattleVisualEvent.AnimationType.SHULKER_BULLET
                || event.animationType() == BattleVisualEvent.AnimationType.BLAZE_FIREBALL
                || event.animationType() == BattleVisualEvent.AnimationType.GHAST_FIREBALL;
    }

    public static int projectilePrepareTicks(BattleVisualEvent event) {
        if (event == null) {
            return PROJECTILE_DEFAULT_PREPARE_TICKS;
        }
        return projectilePrepareTicks(event.animationType(), event.animationTicks());
    }

    private static int projectilePrepareTicks(BattleVisualEvent.AnimationType animationType, int animationTicks) {
        int prepareTicks = switch (animationType) {
            case POTION_THROW -> POTION_THROW_PREPARE_TICKS;
            case CROSSBOW_LOAD -> 25;
            case TRIDENT_THROW, CHANNELING_TRIDENT_THROW -> 18;
            case WIND_CHARGE -> WIND_CHARGE_PREPARE_TICKS;
            case SHULKER_BULLET -> SHULKER_BULLET_PREPARE_TICKS;
            default -> PROJECTILE_DEFAULT_PREPARE_TICKS;
        };
        return Math.max(0, Math.min(Math.max(0, animationTicks), prepareTicks));
    }

    private static boolean isProjectileAnimation(BattleVisualEvent.AnimationType animationType) {
        return animationType == BattleVisualEvent.AnimationType.BOW_DRAW
                || animationType == BattleVisualEvent.AnimationType.CROSSBOW_LOAD
                || animationType == BattleVisualEvent.AnimationType.TRIDENT_THROW
                || animationType == BattleVisualEvent.AnimationType.CHANNELING_TRIDENT_THROW
                || animationType == BattleVisualEvent.AnimationType.POTION_THROW
                || animationType == BattleVisualEvent.AnimationType.WIND_CHARGE
                || animationType == BattleVisualEvent.AnimationType.SHULKER_BULLET
                || isFireballAnimation(animationType);
    }

    private static Vec3 lungeStart(BattleVisualEvent event) {
        if (event == null || !usesMovementAnimation(event.animationType()) || event.animationTicks() <= 0) {
            return null;
        }
        if (event.animationStart() != null) {
            return event.animationStart();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(event.attackerId()) instanceof Entity attacker)) {
            return null;
        }
        return attacker.position();
    }

    private static Vec3 lungeStrike(BattleVisualEvent event) {
        if (event == null || !usesMovementAnimation(event.animationType()) || event.animationTicks() <= 0) {
            return null;
        }
        if (event.animationStrike() != null) {
            return event.animationStrike();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !(minecraft.level.getEntity(event.attackerId()) instanceof Entity attacker)
                || !(minecraft.level.getEntity(event.targetId()) instanceof Entity target)) {
            return null;
        }
        Vec3 start = attacker.position();
        Vec3 direction = target.position().subtract(start);
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        Vec3 normalized = horizontal.lengthSqr() > 0.0001D ? horizontal.normalize() : attacker.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
        double distance = Math.max(0.0D, Math.min(LUNGE_REACH, horizontal.length() - LUNGE_STOP_DISTANCE));
        return start.add(normalized.scale(distance));
    }

    private static boolean usesMovementAnimation(BattleVisualEvent.AnimationType animationType) {
        return animationType == BattleVisualEvent.AnimationType.MELEE_LUNGE
                || animationType == BattleVisualEvent.AnimationType.RIPTIDE_RUSH
                || animationType == BattleVisualEvent.AnimationType.VINDICATOR_AXE_SWING
                || animationType == BattleVisualEvent.AnimationType.VEX_CHARGE_LUNGE
                || animationType == BattleVisualEvent.AnimationType.RAVAGER_HEAD_RAM
                || animationType == BattleVisualEvent.AnimationType.PIGLIN_MELEE_SWING
                || animationType == BattleVisualEvent.AnimationType.HOGLIN_HEAD_ATTACK;
    }

    public static final class GuardianBeamAnimation {
        private final int attackerId;
        private final int targetId;
        private final int totalTicks;
        private int age;

        private GuardianBeamAnimation(int attackerId, int targetId, int totalTicks) {
            this.attackerId = attackerId;
            this.targetId = targetId;
            this.totalTicks = Math.max(1, totalTicks);
        }

        public int attackerId() {
            return attackerId;
        }

        public int targetId() {
            return targetId;
        }

        public int age() {
            return age;
        }

        public int totalTicks() {
            return totalTicks;
        }

        public float attackTime(float partialTick) {
            return age + Math.max(0.0F, partialTick);
        }

        public float attackScale(float partialTick) {
            return Math.max(0.0F, Math.min(1.0F, attackTime(partialTick) / (float) totalTicks));
        }

        private void tick(BattleSnapshot snapshot) {
            spawnBubbles(snapshot);
            age++;
        }

        private boolean done() {
            return age > totalTicks;
        }

        private void spawnBubbles(BattleSnapshot snapshot) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            Entity attacker = minecraft.level.getEntity(attackerId);
            Entity target = minecraft.level.getEntity(targetId);
            if (!(attacker instanceof LivingEntity livingAttacker) || target == null || snapshot.combatant(attackerId) == null || snapshot.combatant(targetId) == null) {
                return;
            }
            Vec3 start = new Vec3(livingAttacker.getX(), livingAttacker.getEyeY(), livingAttacker.getZ());
            Vec3 end = new Vec3(target.getX(), target.getY(0.5D), target.getZ());
            Vec3 delta = end.subtract(start);
            double distance = delta.length();
            if (distance <= 0.0001D) {
                return;
            }
            Vec3 direction = delta.scale(1.0D / distance);
            double scale = attackScale(0.0F);
            double position = livingAttacker.getRandom().nextDouble();
            while (position < distance) {
                position += 1.8D - scale + livingAttacker.getRandom().nextDouble() * (1.7D - scale);
                minecraft.level.addParticle(
                        ParticleTypes.BUBBLE,
                        start.x + direction.x * position,
                        start.y + direction.y * position,
                        start.z + direction.z * position,
                        0.0D,
                        0.0D,
                        0.0D);
            }
        }
    }

    public static final class ProjectileVisual {
        private final int attackerId;
        private final int targetId;
        private final ItemStack stack;
        private final BattleVisualEvent.AnimationType animationType;
        private final Vec3 start;
        private final Vec3 strike;
        private final int prepareTicks;
        private final int totalTicks;
        private int age;
        private boolean impactConsumed;

        private ProjectileVisual(BattleVisualEvent event) {
            this.attackerId = event.attackerId();
            this.targetId = event.targetId();
            this.animationType = event.animationType();
            this.start = event.animationStart();
            this.strike = event.animationStrike();
            this.prepareTicks = Math.max(0, Math.min(event.animationTicks(), projectilePrepareTicks(event)));
            this.totalTicks = Math.max(1, event.animationTicks());
            this.stack = projectileStackForVisual(event);
        }

        private static ProjectileVisual from(BattleVisualEvent event) {
            return new ProjectileVisual(event);
        }

        public int attackerId() {
            return attackerId;
        }

        public int targetId() {
            return targetId;
        }

        public ItemStack stack() {
            return stack;
        }

        public BattleVisualEvent.AnimationType animationType() {
            return animationType;
        }

        public int age() {
            return age;
        }

        public Vec3 position(float partialTick) {
            int flightTicks = Math.max(1, totalTicks - prepareTicks);
            double flightAge = Math.max(0.0D, Math.min(flightTicks, age + Math.max(0.0F, partialTick) - prepareTicks));
            double progress = flightAge / flightTicks;
            return start.lerp(strike, progress);
        }

        public Vec3 direction() {
            Vec3 direction = strike.subtract(start);
            return direction.lengthSqr() > 0.0001D ? direction.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        }

        public boolean visible(float partialTick) {
            return age + Math.max(0.0F, partialTick) >= prepareTicks && age <= totalTicks;
        }

        private void tick() {
            age++;
        }

        private boolean consumeImpact() {
            if (impactConsumed || age < totalTicks) {
                return false;
            }
            impactConsumed = true;
            return true;
        }

        private ProjectileImpactVisual impactVisual() {
            return new ProjectileImpactVisual(attackerId, targetId, animationType, strike);
        }

        private boolean done() {
            return age > totalTicks;
        }

        private static ItemStack projectileStackForVisual(BattleVisualEvent event) {
            ItemStack stack = event.projectileStack() == null || event.projectileStack().isEmpty()
                    ? event.itemStack()
                    : event.projectileStack();
            if (stack == null || stack.isEmpty()) {
                return defaultProjectileStack(event.animationType());
            }
            if (event.animationType() == BattleVisualEvent.AnimationType.POTION_THROW) {
                return visualOnlyPotionStack(stack);
            }
            return stack.copy();
        }

        private static ItemStack defaultProjectileStack(BattleVisualEvent.AnimationType animationType) {
            if (animationType == BattleVisualEvent.AnimationType.TRIDENT_THROW
                    || animationType == BattleVisualEvent.AnimationType.CHANNELING_TRIDENT_THROW) {
                return new ItemStack(Items.TRIDENT);
            }
            if (animationType == BattleVisualEvent.AnimationType.POTION_THROW) {
                return new ItemStack(Items.SPLASH_POTION);
            }
            if (animationType == BattleVisualEvent.AnimationType.WIND_CHARGE) {
                return new ItemStack(Items.WIND_CHARGE);
            }
            if (animationType == BattleVisualEvent.AnimationType.SHULKER_BULLET) {
                return new ItemStack(Items.SHULKER_SHELL);
            }
            if (isFireballAnimation(animationType)) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(Items.ARROW);
        }

        private static ItemStack visualOnlyPotionStack(ItemStack original) {
            ItemStack stack = original.copy();
            PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(java.util.Optional.empty(), java.util.Optional.of(contents.getColor()), List.of()));
            return stack;
        }
    }

    public record ProjectileImpactVisual(int attackerId, int targetId, BattleVisualEvent.AnimationType animationType, Vec3 position) {
    }

    public static final class VisualState {
        private ItemStack itemStack = ItemStack.EMPTY;
        private BattleVisualEvent.AnimationType animationType = BattleVisualEvent.AnimationType.NONE;
        private int itemTicks;
        private int usingTicks;
        private int usingAge;
        private int lungeTicks;
        private int lungeAge;
        private int lungeSettleTicks;
        private int selfDestructTicks;
        private int selfDestructAge;
        private int selfDestructTotalTicks;
        private int evokerSpellTicks;
        private int evokerSpellAge;
        private int windChargeTicks;
        private int shulkerBulletTicks;
        private int fireballChargeTicks;
        private BattleVisualEvent.AnimationType evokerSpellType = BattleVisualEvent.AnimationType.NONE;
        private Vec3 lungeStart = Vec3.ZERO;
        private Vec3 lungeStrike = Vec3.ZERO;
        private Vec3 previousLungePosition = Vec3.ZERO;
        private Vec3 currentLungePosition = Vec3.ZERO;
        private double riptideCenterYOffset = DEFAULT_RIPTIDE_CENTER_Y_OFFSET;
        private boolean pounceLunge;
        private float walkSpeed;
        private int hurtTicks;
        private int knockbackReleaseTicks;
        private int knockbackTicks;
        private int knockbackAge;
        private int knockbackSettleTicks;
        private Vec3 previousKnockbackPosition = Vec3.ZERO;
        private Vec3 currentKnockbackPosition = Vec3.ZERO;
        private Vec3 knockbackVelocity = Vec3.ZERO;
        private Vec3 knockbackAnchor;

        public int hurtTicks() {
            return hurtTicks;
        }

        public int knockbackTicks() {
            return knockbackReleaseTicks;
        }

        public int usingTicks() {
            return usingTicks;
        }

        public int ticksUsingItem() {
            return usingTicks <= 0 ? 0 : usingAge;
        }

        public boolean drownedTridentPose() {
            if (itemTicks <= 0 || !itemStack.is(Items.TRIDENT)) {
                return false;
            }
            if (animationType == BattleVisualEvent.AnimationType.RIPTIDE_RUSH) {
                return lungeTicks > 0 && lungeAge < RIPTIDE_CHARGE_TICKS;
            }
            return animationType == BattleVisualEvent.AnimationType.TRIDENT_THROW
                    || animationType == BattleVisualEvent.AnimationType.CHANNELING_TRIDENT_THROW;
        }

        public int riptideSpinTicks() {
            if (animationType != BattleVisualEvent.AnimationType.RIPTIDE_RUSH || lungeTicks <= 0) {
                return 0;
            }
            if (lungeAge < RIPTIDE_CHARGE_TICKS) {
                return 0;
            }
            int spinTicks = RIPTIDE_SPIN_TICKS - (lungeAge - RIPTIDE_CHARGE_TICKS);
            return Math.max(0, Math.min(RIPTIDE_SPIN_TICKS, spinTicks));
        }

        public int lungeTicks() {
            return lungeTicks;
        }

        public int selfDestructTicks() {
            return selfDestructTicks;
        }

        public int windChargeTicks() {
            return windChargeTicks;
        }

        public int fireballChargeTicks() {
            return fireballChargeTicks;
        }

        public int shulkerBulletTicks() {
            return shulkerBulletTicks;
        }

        public BattleVisualEvent.AnimationType animationType() {
            if (itemTicks <= 0 && usingTicks <= 0 && lungeTicks <= 0 && selfDestructTicks <= 0 && evokerSpellTicks <= 0 && windChargeTicks <= 0 && shulkerBulletTicks <= 0 && fireballChargeTicks <= 0) {
                return BattleVisualEvent.AnimationType.NONE;
            }
            return animationType;
        }

        private void showItem(ItemStack stack, BattleVisualEvent.AnimationType animationType, int animationTicks, Vec3 lungeStart, Vec3 lungeStrike, boolean pounceLunge, double riptideCenterYOffset) {
            BattleVisualEvent.AnimationType nextType = animationType == null ? BattleVisualEvent.AnimationType.NONE : animationType;
            if (usesMovementAnimation(nextType) && animationTicks > 0 && lungeStart != null && lungeStrike != null) {
                int minimumTicks = nextType == BattleVisualEvent.AnimationType.RIPTIDE_RUSH
                        ? RIPTIDE_CHARGE_TICKS + RIPTIDE_RUSH_TICKS + RIPTIDE_HIT_PAUSE_TICKS
                        : nextType == BattleVisualEvent.AnimationType.VINDICATOR_AXE_SWING
                        ? VINDICATOR_AXE_RAISE_TICKS + VINDICATOR_AXE_APPROACH_TICKS + VINDICATOR_AXE_STRIKE_TICKS + VINDICATOR_AXE_RECOVER_TICKS
                        : nextType == BattleVisualEvent.AnimationType.VEX_CHARGE_LUNGE
                        ? VEX_CHARGE_RAISE_TICKS + VEX_CHARGE_APPROACH_TICKS + VEX_CHARGE_HIT_PAUSE_TICKS
                        : nextType == BattleVisualEvent.AnimationType.RAVAGER_HEAD_RAM
                        ? RAVAGER_HEAD_RAM_RAISE_TICKS + RAVAGER_HEAD_RAM_APPROACH_TICKS + RAVAGER_HEAD_RAM_STRIKE_TICKS + RAVAGER_HEAD_RAM_RECOVER_TICKS
                        : nextType == BattleVisualEvent.AnimationType.PIGLIN_MELEE_SWING
                        ? PIGLIN_MELEE_RAISE_TICKS + PIGLIN_MELEE_APPROACH_TICKS + PIGLIN_MELEE_STRIKE_TICKS + PIGLIN_MELEE_RECOVER_TICKS
                        : nextType == BattleVisualEvent.AnimationType.HOGLIN_HEAD_ATTACK
                        ? HOGLIN_HEAD_ATTACK_RAISE_TICKS + HOGLIN_HEAD_ATTACK_APPROACH_TICKS + HOGLIN_HEAD_ATTACK_STRIKE_TICKS + HOGLIN_HEAD_ATTACK_RECOVER_TICKS
                        : MELEE_LUNGE_TICKS + MELEE_HIT_PAUSE_TICKS;
                lungeTicks = Math.max(1, Math.max(minimumTicks, animationTicks));
                lungeAge = 0;
                this.lungeStart = lungeStart;
                this.lungeStrike = lungeStrike;
                this.pounceLunge = nextType == BattleVisualEvent.AnimationType.MELEE_LUNGE && pounceLunge;
                this.riptideCenterYOffset = nextType == BattleVisualEvent.AnimationType.RIPTIDE_RUSH
                        ? Math.max(0.1D, riptideCenterYOffset)
                        : DEFAULT_RIPTIDE_CENTER_Y_OFFSET;
                previousLungePosition = lungeStart;
                currentLungePosition = lungeStart;
                lungeSettleTicks = 0;
                walkSpeed = 0.0F;
                this.animationType = nextType;
            }
            if (nextType == BattleVisualEvent.AnimationType.SELF_DESTRUCT && animationTicks > 0) {
                selfDestructTicks = Math.max(1, animationTicks);
                selfDestructTotalTicks = selfDestructTicks;
                selfDestructAge = 0;
                this.animationType = nextType;
            }
            if (isEvokerSpellAnimation(nextType) && animationTicks > 0) {
                evokerSpellTicks = Math.max(1, animationTicks);
                evokerSpellAge = 0;
                evokerSpellType = nextType;
                this.animationType = nextType;
            }
            if (nextType == BattleVisualEvent.AnimationType.WIND_CHARGE && animationTicks > 0) {
                windChargeTicks = Math.max(1, animationTicks);
                this.animationType = nextType;
            }
            if (nextType == BattleVisualEvent.AnimationType.SHULKER_BULLET && animationTicks > 0) {
                shulkerBulletTicks = Math.max(1, animationTicks);
                this.animationType = nextType;
            }
            if (isFireballAnimation(nextType) && animationTicks > 0) {
                fireballChargeTicks = Math.max(1, projectilePrepareTicks(nextType, animationTicks));
                this.animationType = nextType;
            }
            if (stack != null && !stack.isEmpty()) {
                itemStack = stack.copy();
                int handTicks = isProjectileAnimation(nextType) ? projectilePrepareTicks(nextType, animationTicks) : animationTicks;
                itemTicks = Math.max(18, handTicks + 8);
                this.animationType = nextType;
                if (nextType == BattleVisualEvent.AnimationType.BOW_DRAW
                        || nextType == BattleVisualEvent.AnimationType.CROSSBOW_LOAD
                        || nextType == BattleVisualEvent.AnimationType.TRIDENT_THROW
                        || nextType == BattleVisualEvent.AnimationType.CHANNELING_TRIDENT_THROW
                        || nextType == BattleVisualEvent.AnimationType.POTION_DRINK) {
                    usingTicks = Math.max(usingTicks, Math.max(1, handTicks));
                    usingAge = 0;
                } else if (nextType == BattleVisualEvent.AnimationType.RIPTIDE_RUSH) {
                    usingTicks = Math.max(usingTicks, RIPTIDE_CHARGE_TICKS);
                    usingAge = 0;
                }
            }
        }

        private boolean evokerSpellcasting() {
            return evokerSpellTicks > 0 && isEvokerSpellAnimation(evokerSpellType);
        }

        private BattleVisualEvent.AnimationType evokerSpellType() {
            return evokerSpellcasting() ? evokerSpellType : BattleVisualEvent.AnimationType.NONE;
        }

        private ItemStack mainHandStack() {
            if (animationType == BattleVisualEvent.AnimationType.CROSSBOW_LOAD && usingTicks <= 0 && itemStack.is(Items.CROSSBOW)) {
                itemStack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
            }
            return itemStack;
        }

        private Vec3 lungePosition(float partialTick) {
            double age = Math.max(0.0D, lungeAge + Math.max(0.0F, partialTick));
            if (animationType == BattleVisualEvent.AnimationType.RIPTIDE_RUSH) {
                if (age <= RIPTIDE_CHARGE_TICKS) {
                    return lungeStart;
                }
                Vec3 riptideStart = riptideVisualPosition(lungeStart);
                Vec3 riptideStrike = riptideVisualPosition(lungeStrike);
                age -= RIPTIDE_CHARGE_TICKS;
                if (age <= RIPTIDE_RUSH_TICKS) {
                    return lerp(riptideStart, riptideStrike, age / RIPTIDE_RUSH_TICKS);
                }
                if (age <= RIPTIDE_RUSH_TICKS + RIPTIDE_HIT_PAUSE_TICKS) {
                    return riptideStrike;
                }
                return riptideStrike;
            }
            if (animationType == BattleVisualEvent.AnimationType.VINDICATOR_AXE_SWING) {
                if (age <= VINDICATOR_AXE_RAISE_TICKS) {
                    return lungeStart;
                }
                age -= VINDICATOR_AXE_RAISE_TICKS;
                if (age <= VINDICATOR_AXE_APPROACH_TICKS) {
                    return lungePosition(age / VINDICATOR_AXE_APPROACH_TICKS);
                }
                age -= VINDICATOR_AXE_APPROACH_TICKS;
                if (age <= VINDICATOR_AXE_STRIKE_TICKS) {
                    return lungeStrike;
                }
                return lungeStrike;
            }
            if (animationType == BattleVisualEvent.AnimationType.VEX_CHARGE_LUNGE) {
                if (age <= VEX_CHARGE_RAISE_TICKS) {
                    return lungeStart;
                }
                age -= VEX_CHARGE_RAISE_TICKS;
                if (age <= VEX_CHARGE_APPROACH_TICKS) {
                    return lungePosition(age / VEX_CHARGE_APPROACH_TICKS);
                }
                return lungeStrike;
            }
            if (animationType == BattleVisualEvent.AnimationType.RAVAGER_HEAD_RAM) {
                if (age <= RAVAGER_HEAD_RAM_RAISE_TICKS) {
                    return lungeStart;
                }
                age -= RAVAGER_HEAD_RAM_RAISE_TICKS;
                if (age <= RAVAGER_HEAD_RAM_APPROACH_TICKS) {
                    return lungePosition(age / RAVAGER_HEAD_RAM_APPROACH_TICKS);
                }
                return lungeStrike;
            }
            if (animationType == BattleVisualEvent.AnimationType.PIGLIN_MELEE_SWING) {
                if (age <= PIGLIN_MELEE_RAISE_TICKS) {
                    return lungeStart;
                }
                age -= PIGLIN_MELEE_RAISE_TICKS;
                if (age <= PIGLIN_MELEE_APPROACH_TICKS) {
                    return lungePosition(age / PIGLIN_MELEE_APPROACH_TICKS);
                }
                return lungeStrike;
            }
            if (animationType == BattleVisualEvent.AnimationType.HOGLIN_HEAD_ATTACK) {
                if (age <= HOGLIN_HEAD_ATTACK_RAISE_TICKS) {
                    return lungeStart;
                }
                age -= HOGLIN_HEAD_ATTACK_RAISE_TICKS;
                if (age <= HOGLIN_HEAD_ATTACK_APPROACH_TICKS) {
                    return lungePosition(age / HOGLIN_HEAD_ATTACK_APPROACH_TICKS);
                }
                return lungeStrike;
            }
            if (age <= MELEE_LUNGE_TICKS) {
                return lungePosition(age / MELEE_LUNGE_TICKS);
            }
            age -= MELEE_LUNGE_TICKS;
            if (age <= MELEE_HIT_PAUSE_TICKS) {
                return lungeStrike;
            }
            return lungeStrike;
        }

        private Vec3 lungeOffset(Vec3 renderedPosition, float partialTick) {
            if (renderedPosition == null) {
                return Vec3.ZERO;
            }
            if (lungeTicks > 0) {
                Vec3 visualPosition = lungePosition(partialTick);
                return visualPosition == null ? Vec3.ZERO : visualPosition.subtract(renderedPosition);
            }
            if (lungeSettleTicks > 0) {
                double age = Math.max(0.0D, KNOCKBACK_SETTLE_TICKS - lungeSettleTicks + Math.max(0.0F, partialTick));
                double progress = Math.max(0.0D, Math.min(1.0D, age / KNOCKBACK_SETTLE_TICKS));
                double eased = 1.0D - Math.pow(1.0D - progress, 2.0D);
                return currentLungePosition.subtract(renderedPosition).scale(1.0D - eased);
            }
            return Vec3.ZERO;
        }

        private Vec3 lungePosition(double progress) {
            Vec3 position = lerp(lungeStart, lungeStrike, progress);
            if (!pounceLunge) {
                return position;
            }
            double t = Math.max(0.0D, Math.min(1.0D, progress));
            return position.add(0.0D, Math.sin(Math.PI * t) * POUNCE_JUMP_HEIGHT, 0.0D);
        }

        private Vec3 riptideVisualPosition(Vec3 footPosition) {
            return footPosition.add(0.0D, riptideCenterYOffset, 0.0D);
        }

        private Vec3 knockbackOffset(Vec3 renderedPosition, float partialTick) {
            if (renderedPosition == null) {
                return Vec3.ZERO;
            }
            if (knockbackTicks > 0) {
                if (knockbackAnchor == null) {
                    knockbackAnchor = renderedPosition;
                }
                double t = Math.max(0.0D, Math.min(1.0D, partialTick));
                Vec3 visualPosition = knockbackAnchor.add(lerp(previousKnockbackPosition, currentKnockbackPosition, t));
                return visualPosition.subtract(renderedPosition);
            }
            if (knockbackSettleTicks > 0) {
                double age = Math.max(0.0D, KNOCKBACK_SETTLE_TICKS - knockbackSettleTicks + Math.max(0.0F, partialTick));
                double progress = Math.max(0.0D, Math.min(1.0D, age / KNOCKBACK_SETTLE_TICKS));
                double eased = 1.0D - Math.pow(1.0D - progress, 2.0D);
                Vec3 visualPosition = knockbackAnchor == null ? renderedPosition : knockbackAnchor.add(currentKnockbackPosition);
                return visualPosition.subtract(renderedPosition).scale(1.0D - eased);
            }
            return Vec3.ZERO;
        }

        private float walkSpeed() {
            return walkSpeed;
        }

        private float selfDestructScale(float partialTick) {
            if (selfDestructTicks <= 0 || selfDestructTotalTicks <= 0) {
                return 1.0F;
            }
            float progress = Math.max(0.0F, Math.min(1.0F, (selfDestructAge + Math.max(0.0F, partialTick)) / selfDestructTotalTicks));
            float pulse = 0.5F + 0.5F * (float) Math.sin(progress * Math.PI * 8.0F);
            return 1.0F + (SELF_DESTRUCT_MAX_SCALE - 1.0F) * progress + pulse * 0.06F;
        }

        private float selfDestructWhiteOverlayProgress(float partialTick) {
            if (selfDestructTicks <= 0 || selfDestructTotalTicks <= 0) {
                return 0.0F;
            }
            float progress = Math.max(0.0F, Math.min(1.0F, (selfDestructAge + Math.max(0.0F, partialTick)) / selfDestructTotalTicks));
            return (int) (progress * 10.0F) % 2 == 0 ? 0.0F : Math.max(0.5F, progress);
        }

        private boolean movingVisually() {
            return lungeTicks > 0 || lungeSettleTicks > 0 || knockbackTicks > 0 || knockbackSettleTicks > 0;
        }

        private boolean vindicatorAxeRaised() {
            return animationType == BattleVisualEvent.AnimationType.VINDICATOR_AXE_SWING
                    && lungeTicks > 0
                    && lungeAge <= VINDICATOR_AXE_RAISE_TICKS + VINDICATOR_AXE_APPROACH_TICKS;
        }

        private boolean vexCharging() {
            return animationType == BattleVisualEvent.AnimationType.VEX_CHARGE_LUNGE
                    && lungeTicks > 0
                    && lungeAge <= VEX_CHARGE_RAISE_TICKS + VEX_CHARGE_APPROACH_TICKS;
        }

        private boolean piglinMeleeRaised() {
            return animationType == BattleVisualEvent.AnimationType.PIGLIN_MELEE_SWING
                    && lungeTicks > 0
                    && lungeAge <= PIGLIN_MELEE_RAISE_TICKS + PIGLIN_MELEE_APPROACH_TICKS;
        }

        private int ravagerAttackTick() {
            if (animationType != BattleVisualEvent.AnimationType.RAVAGER_HEAD_RAM || lungeTicks <= 0) {
                return 0;
            }
            int attackAge = lungeAge - RAVAGER_HEAD_RAM_RAISE_TICKS;
            if (attackAge < 0 || attackAge >= RAVAGER_HEAD_ATTACK_TICKS) {
                return 0;
            }
            return Math.max(1, RAVAGER_HEAD_ATTACK_TICKS - attackAge);
        }

        private int hoglinAttackTick() {
            if (animationType != BattleVisualEvent.AnimationType.HOGLIN_HEAD_ATTACK || lungeTicks <= 0) {
                return 0;
            }
            int attackAge = lungeAge - HOGLIN_HEAD_ATTACK_RAISE_TICKS;
            if (attackAge < 0 || attackAge >= HOGLIN_HEAD_ATTACK_TICKS) {
                return 0;
            }
            return Math.max(1, HOGLIN_HEAD_ATTACK_TICKS - attackAge);
        }

        private void hurtFlash(Vec3 knockbackVelocity) {
            hurtTicks = 10;
            knockbackReleaseTicks = KNOCKBACK_RELEASE_TICKS;
            if (knockbackVelocity != null && knockbackVelocity.lengthSqr() > 0.0001D) {
                previousKnockbackPosition = Vec3.ZERO;
                currentKnockbackPosition = Vec3.ZERO;
                this.knockbackVelocity = knockbackVelocity;
                knockbackTicks = KNOCKBACK_RELEASE_TICKS;
                knockbackAge = 0;
                knockbackSettleTicks = 0;
                knockbackAnchor = null;
            } else {
                knockbackTicks = 0;
                knockbackAge = 0;
                this.knockbackVelocity = Vec3.ZERO;
                previousKnockbackPosition = Vec3.ZERO;
                currentKnockbackPosition = Vec3.ZERO;
                knockbackSettleTicks = 0;
                knockbackAnchor = null;
            }
        }

        private void tick() {
            if (itemTicks > 0) {
                itemTicks--;
                if (itemTicks <= 0) {
                    itemStack = ItemStack.EMPTY;
                }
            }
            if (usingTicks > 0) {
                usingTicks--;
                usingAge++;
            } else {
                usingAge = 0;
            }
            if (selfDestructTicks > 0) {
                selfDestructTicks--;
                selfDestructAge++;
            } else {
                selfDestructAge = 0;
                selfDestructTotalTicks = 0;
            }
            if (evokerSpellTicks > 0) {
                evokerSpellTicks--;
                evokerSpellAge++;
            } else {
                evokerSpellAge = 0;
                evokerSpellType = BattleVisualEvent.AnimationType.NONE;
            }
            if (windChargeTicks > 0) {
                windChargeTicks--;
            }
            if (shulkerBulletTicks > 0) {
                shulkerBulletTicks--;
            }
            if (fireballChargeTicks > 0) {
                fireballChargeTicks--;
            }
            float lungeWalkSpeed = 0.0F;
            if (lungeTicks > 0) {
                previousLungePosition = currentLungePosition;
                currentLungePosition = lungePosition(0.0F);
                double walked = currentLungePosition.subtract(previousLungePosition).multiply(1.0D, 0.0D, 1.0D).length();
                lungeWalkSpeed = (float) Math.max(0.0D, Math.min(1.0D, walked * 4.0D));
                lungeTicks--;
                lungeAge++;
                if (lungeTicks <= 0) {
                    lungeSettleTicks = KNOCKBACK_SETTLE_TICKS;
                }
            } else if (lungeSettleTicks > 0) {
                lungeSettleTicks--;
            }
            if (hurtTicks > 0) {
                hurtTicks--;
            }
            if (knockbackReleaseTicks > 0) {
                knockbackReleaseTicks--;
            }
            float knockbackWalkSpeed = tickKnockbackVisual();
            walkSpeed = Math.max(lungeWalkSpeed, knockbackWalkSpeed);
            if (walkSpeed <= 0.001F) {
                walkSpeed = 0.0F;
            }
            if (itemTicks <= 0 && usingTicks <= 0 && lungeTicks <= 0 && selfDestructTicks <= 0 && evokerSpellTicks <= 0 && windChargeTicks <= 0 && shulkerBulletTicks <= 0 && fireballChargeTicks <= 0) {
                animationType = BattleVisualEvent.AnimationType.NONE;
            }
        }

        private float tickKnockbackVisual() {
            if (knockbackTicks > 0) {
                previousKnockbackPosition = currentKnockbackPosition;
                Vec3 velocity = nextKnockbackVelocity(knockbackVelocity, knockbackAge);
                Vec3 nextPosition = currentKnockbackPosition.add(velocity);
                if (nextPosition.y < 0.0D && velocity.y < 0.0D) {
                    nextPosition = new Vec3(nextPosition.x, 0.0D, nextPosition.z);
                    velocity = new Vec3(velocity.x, 0.0D, velocity.z);
                }
                currentKnockbackPosition = nextPosition;
                knockbackVelocity = new Vec3(
                        velocity.x * KNOCKBACK_HORIZONTAL_DRAG,
                        velocity.y * KNOCKBACK_VERTICAL_DRAG,
                        velocity.z * KNOCKBACK_HORIZONTAL_DRAG);
                knockbackTicks--;
                knockbackAge++;
                double walked = currentKnockbackPosition.subtract(previousKnockbackPosition).multiply(1.0D, 0.0D, 1.0D).length();
                if (knockbackTicks <= 0 || (knockbackAge >= 4 && knockbackVelocity.lengthSqr() <= KNOCKBACK_STOP_SPEED_SQR)) {
                    beginKnockbackSettle();
                }
                return (float) Math.max(0.0D, Math.min(1.0D, walked * 4.0D));
            }
            if (knockbackSettleTicks > 0) {
                knockbackSettleTicks--;
                if (knockbackSettleTicks <= 0) {
                    previousKnockbackPosition = Vec3.ZERO;
                    currentKnockbackPosition = Vec3.ZERO;
                    knockbackAnchor = null;
                }
            }
            return 0.0F;
        }

        private Vec3 nextKnockbackVelocity(Vec3 velocity, int age) {
            if (velocity == null) {
                return Vec3.ZERO;
            }
            if (age > 0 || velocity.y > 0.0D) {
                double y = Math.max(KNOCKBACK_MAX_FALL_SPEED, velocity.y - KNOCKBACK_GRAVITY);
                return new Vec3(velocity.x, y, velocity.z);
            }
            return velocity;
        }

        private void beginKnockbackSettle() {
            knockbackSettleTicks = KNOCKBACK_SETTLE_TICKS;
            knockbackTicks = 0;
            knockbackAge = 0;
            previousKnockbackPosition = currentKnockbackPosition;
            knockbackVelocity = Vec3.ZERO;
        }

        private boolean done() {
            return itemTicks <= 0 && usingTicks <= 0 && lungeTicks <= 0 && lungeSettleTicks <= 0 && selfDestructTicks <= 0 && evokerSpellTicks <= 0 && windChargeTicks <= 0 && shulkerBulletTicks <= 0 && fireballChargeTicks <= 0 && hurtTicks <= 0 && knockbackReleaseTicks <= 0 && knockbackTicks <= 0 && knockbackSettleTicks <= 0;
        }
    }

    private static boolean isFireballAnimation(BattleVisualEvent.AnimationType type) {
        return type == BattleVisualEvent.AnimationType.BLAZE_FIREBALL
                || type == BattleVisualEvent.AnimationType.GHAST_FIREBALL;
    }

    private static boolean isEvokerSpellAnimation(BattleVisualEvent.AnimationType type) {
        return type == BattleVisualEvent.AnimationType.EVOKER_FANG_LINE
                || type == BattleVisualEvent.AnimationType.EVOKER_FANG_CIRCLE
                || type == BattleVisualEvent.AnimationType.EVOKER_SUMMON_VEX;
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double amount) {
        double t = Math.max(0.0D, Math.min(1.0D, amount));
        return from.add(to.subtract(from).scale(t));
    }

    private static final class ScheduledVisualEvent {
        private final BattleVisualEvent event;
        private int delayTicks;

        private ScheduledVisualEvent(BattleVisualEvent event, int delayTicks) {
            this.event = event;
            this.delayTicks = Math.max(0, delayTicks);
        }
    }

    public static final class DamageNumber {
        private final int entityId;
        private final int amount;
        private final boolean block;
        private final boolean healing;
        private final int delay;
        private int age;

        private DamageNumber(int entityId, int amount, boolean block, int delay) {
            this(entityId, amount, block, false, delay);
        }

        private DamageNumber(int entityId, int amount, boolean block, boolean healing, int delay) {
            this.entityId = entityId;
            this.amount = amount;
            this.block = block;
            this.healing = healing;
            this.delay = delay;
        }

        public int entityId() {
            return entityId;
        }

        public int amount() {
            return amount;
        }

        public boolean block() {
            return block;
        }

        public boolean healing() {
            return healing;
        }

        public int age() {
            return age;
        }

        public int visibleAge() {
            return Math.max(0, age - delay);
        }

        private void tick() {
            age++;
        }
    }

    public static final class BlockGainAnimation {
        private static final long TOTAL_NANOS = 950_000_000L;
        private final int entityId;
        private final long startNanos;

        private BlockGainAnimation(int entityId, long startNanos) {
            this.entityId = entityId;
            this.startNanos = startNanos;
        }

        public int entityId() {
            return entityId;
        }

        public float progress(long nowNanos) {
            return Math.max(0.0F, Math.min(1.0F, (nowNanos - startNanos) / (float) TOTAL_NANOS));
        }

        private boolean done(long nowNanos) {
            return nowNanos - startNanos > TOTAL_NANOS;
        }
    }

    private record PileContentsKey(UUID battleId, BattlePileSource source, long deckVersion, int entityId) {
        private boolean matches(UUID battleId, BattlePileSource source, int entityId) {
            return this.entityId == entityId && this.source == source && this.battleId.equals(battleId);
        }
    }

    private record PileContents(int expectedCount, List<CardInstance> cards) {
        private PileContents {
            cards = List.copyOf(cards == null ? List.of() : cards);
        }
    }

    private static final class CameraAnchor extends Entity {
        private CameraAnchor(Level level) {
            super(EntityType.MARKER, level);
            noPhysics = true;
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        public boolean isSpectator() {
            return true;
        }

        @Override
        public boolean isPickable() {
            return false;
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return super.getDimensions(pose).withEyeHeight(cameraAnchorEyeHeight);
        }
    }
}
