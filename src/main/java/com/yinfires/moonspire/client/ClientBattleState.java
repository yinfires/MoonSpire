package com.yinfires.moonspire.client;

import com.yinfires.moonspire.MoonSpirePerfDiagnostics;
import com.yinfires.moonspire.battle.BattlePhase;
import com.yinfires.moonspire.battle.BattlePileSource;
import com.yinfires.moonspire.battle.BattleSnapshot;
import com.yinfires.moonspire.battle.BattleVisualEvent;
import com.yinfires.moonspire.battle.CombatantState;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ClientBattleState {
    private static final int MELEE_LUNGE_TICKS = 8;
    private static final int MELEE_HIT_PAUSE_TICKS = 6;
    private static final double LUNGE_STOP_DISTANCE = 1.55D;
    private static final double POUNCE_JUMP_HEIGHT = 1.0D;
    private static final double LUNGE_REACH = 10.0D;
    private static final int KNOCKBACK_RELEASE_TICKS = 24;
    private static final int KNOCKBACK_SETTLE_TICKS = 4;
    private static final double KNOCKBACK_HORIZONTAL_DRAG = 0.82D;
    private static final double KNOCKBACK_VERTICAL_DRAG = 0.98D;
    private static final double KNOCKBACK_GRAVITY = 0.08D;
    private static final double KNOCKBACK_MAX_FALL_SPEED = -3.92D;
    private static final double KNOCKBACK_STOP_SPEED_SQR = 0.0025D;
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
    private static final List<ScheduledVisualEvent> pendingVisualEvents = new ArrayList<>();
    private static final Map<Integer, VisualState> visualStates = new HashMap<>();
    private static final Map<Integer, Long> fakeDeathStarts = new HashMap<>();
    private static final Map<PileContentsKey, PileContents> pileContents = new HashMap<>();
    private static CardInstance monsterPlayedCard;
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
            pendingVisualEvents.clear();
            clearVisualStates();
            clearFakeDeathStarts();
            pileContents.clear();
            monsterPlayedCard = null;
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
        updateFakeDeathStarts(next);
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
        cameraAnchorEyeHeight = minecraft.player == null ? 0.0F : minecraft.player.getEyeHeight();
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
            attackerVisual.showItem(event.itemStack(), event.animationType(), event.animationTicks(), lungeStart(event), lungeStrike(event), isPounceEvent(event));
            if (event.healthDamage() > 0) {
                visualStates.computeIfAbsent(event.targetId(), id -> new VisualState()).hurtFlash(event.knockbackDelta());
            }
            if (event.gainedBlock() > 0) {
                blockGainAnimations.add(new BlockGainAnimation(event.targetId(), System.nanoTime()));
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
        }
    }

    public static float monsterPlayedCardAlpha() {
        if (monsterPlayedCard == null || !monsterPlayedCardHasEffect(CardEffectKind.EXHAUST)) {
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

    public static int visualTicksUsingItem(int entityId) {
        VisualState state = visualStates.get(entityId);
        return state == null ? 0 : state.ticksUsingItem();
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

    public static void clearVisualStates() {
        visualStates.clear();
    }

    public static int fakeDeathRenderTicks(int entityId) {
        Long start = fakeDeathStarts.get(entityId);
        if (start == null) {
            return 0;
        }
        return Math.min(CombatantState.FAKE_DEATH_ANIMATION_TICKS, (int) ((System.nanoTime() - start) / 50_000_000L));
    }

    public static void tickDamageNumbers() {
        tickDamageNumbers(true);
    }

    public static void tickClientLogic() {
        tickDamageNumbers(true);
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
        cameraAnchorEyeHeight = 0.0F;
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

    private static void updateFakeDeathStarts(BattleSnapshot next) {
        long now = System.nanoTime();
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
        Iterator<Integer> iterator = fakeDeathStarts.keySet().iterator();
        while (iterator.hasNext()) {
            int entityId = iterator.next();
            if (!fakeDeadIds.contains(entityId)) {
                resetFakeDeathAnimation(entityId);
                iterator.remove();
            }
        }
        for (int entityId : fakeDeadIds) {
            fakeDeathStarts.putIfAbsent(entityId, now);
        }
    }

    private static void clearFakeDeathStarts() {
        for (int entityId : fakeDeathStarts.keySet()) {
            resetFakeDeathAnimation(entityId);
        }
        fakeDeathStarts.clear();
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

    private static void updatePlayedCardDisplay(BattleSnapshot next, BattleVisualEvent event) {
        if (event.playedCard() == null || event.attackerId() != next.monster().entityId()) {
            return;
        }
        monsterPlayedCard = event.playedCard();
        monsterPlayedCardTicks = 20.0F;
        monsterPlayedCardEventSequence++;
    }

    private static boolean monsterPlayedCardHasEffect(CardEffectKind kind) {
        return monsterPlayedCard != null && monsterPlayedCard.effects().stream().anyMatch(effect -> effect.kind() == kind);
    }

    private static boolean isPounceEvent(BattleVisualEvent event) {
        return event != null
                && event.animationType() == BattleVisualEvent.AnimationType.MELEE_LUNGE
                && event.playedCard() != null
                && "builtin_monster_pounce".equals(event.playedCard().cardId());
    }

    private static Vec3 lungeStart(BattleVisualEvent event) {
        if (event == null || event.animationType() != BattleVisualEvent.AnimationType.MELEE_LUNGE || event.animationTicks() <= 0) {
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
        if (event == null || event.animationType() != BattleVisualEvent.AnimationType.MELEE_LUNGE || event.animationTicks() <= 0) {
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

    public static final class VisualState {
        private ItemStack itemStack = ItemStack.EMPTY;
        private BattleVisualEvent.AnimationType animationType = BattleVisualEvent.AnimationType.NONE;
        private int itemTicks;
        private int usingTicks;
        private int usingAge;
        private int lungeTicks;
        private int lungeAge;
        private int lungeSettleTicks;
        private Vec3 lungeStart = Vec3.ZERO;
        private Vec3 lungeStrike = Vec3.ZERO;
        private Vec3 previousLungePosition = Vec3.ZERO;
        private Vec3 currentLungePosition = Vec3.ZERO;
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

        public int lungeTicks() {
            return lungeTicks;
        }

        public BattleVisualEvent.AnimationType animationType() {
            if (itemTicks <= 0 && usingTicks <= 0 && lungeTicks <= 0) {
                return BattleVisualEvent.AnimationType.NONE;
            }
            return animationType;
        }

        private void showItem(ItemStack stack, BattleVisualEvent.AnimationType animationType, int animationTicks, Vec3 lungeStart, Vec3 lungeStrike, boolean pounceLunge) {
            BattleVisualEvent.AnimationType nextType = animationType == null ? BattleVisualEvent.AnimationType.NONE : animationType;
            if (nextType == BattleVisualEvent.AnimationType.MELEE_LUNGE && animationTicks > 0 && lungeStart != null && lungeStrike != null) {
                lungeTicks = Math.max(1, Math.max(MELEE_LUNGE_TICKS, animationTicks) + MELEE_HIT_PAUSE_TICKS);
                lungeAge = 0;
                this.lungeStart = lungeStart;
                this.lungeStrike = lungeStrike;
                this.pounceLunge = pounceLunge;
                previousLungePosition = lungeStart;
                currentLungePosition = lungeStart;
                lungeSettleTicks = 0;
                walkSpeed = 0.0F;
                this.animationType = nextType;
            }
            if (stack != null && !stack.isEmpty()) {
                itemStack = stack.copy();
                itemTicks = Math.max(18, animationTicks + 8);
                this.animationType = nextType;
                if (nextType == BattleVisualEvent.AnimationType.BOW_DRAW || nextType == BattleVisualEvent.AnimationType.CROSSBOW_LOAD) {
                    usingTicks = Math.max(usingTicks, Math.max(1, animationTicks));
                    usingAge = 0;
                }
            }
        }

        private ItemStack mainHandStack() {
            if (animationType == BattleVisualEvent.AnimationType.CROSSBOW_LOAD && usingTicks <= 0 && itemStack.is(Items.CROSSBOW)) {
                itemStack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
            }
            return itemStack;
        }

        private Vec3 lungePosition(float partialTick) {
            double age = Math.max(0.0D, lungeAge + Math.max(0.0F, partialTick));
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

        private boolean movingVisually() {
            return lungeTicks > 0 || lungeSettleTicks > 0 || knockbackTicks > 0 || knockbackSettleTicks > 0;
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
            if (itemTicks <= 0 && usingTicks <= 0 && lungeTicks <= 0) {
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
            return itemTicks <= 0 && usingTicks <= 0 && lungeTicks <= 0 && lungeSettleTicks <= 0 && hurtTicks <= 0 && knockbackReleaseTicks <= 0 && knockbackTicks <= 0 && knockbackSettleTicks <= 0;
        }
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
