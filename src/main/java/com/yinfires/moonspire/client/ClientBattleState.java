package com.yinfires.moonspire.client;

import com.yinfires.moonspire.battle.BattlePhase;
import com.yinfires.moonspire.battle.BattleSnapshot;
import com.yinfires.moonspire.battle.BattleVisualEvent;
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
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ClientBattleState {
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
    private static final List<DamageNumber> damageNumbers = new ArrayList<>();
    private static final List<BlockGainAnimation> blockGainAnimations = new ArrayList<>();
    private static final List<ScheduledVisualEvent> pendingVisualEvents = new ArrayList<>();
    private static final Map<Integer, VisualState> visualStates = new HashMap<>();
    private static final Map<Integer, Long> fakeDeathStarts = new HashMap<>();
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
            enterBattleCamera();
        }
        if (previous.active() && !next.active()) {
            leaveBattleCamera();
            selectedHandIndex = -1;
            hoveredEntityId = -1;
            hoveredEntityIds.clear();
            damageNumbers.clear();
            blockGainAnimations.clear();
            pendingVisualEvents.clear();
            visualStates.clear();
            fakeDeathStarts.clear();
            monsterPlayedCard = null;
            monsterPlayedCardTicks = 0.0F;
            monsterPlayedCardEventSequence = 0L;
            MoonSpireBattleLayoutEditor.close();
            return;
        }
        if (next.active() && previous.active() && previous.round() != next.round()) {
            selectedHandIndex = -1;
        }
        for (BattleVisualEvent visualEvent : next.visualEvents()) {
            pendingVisualEvents.add(new ScheduledVisualEvent(visualEvent, visualEvent.delayTicks()));
            enqueueDamageNumbers(visualEvent);
        }
        updateFakeDeathStarts(next);
        clampSelectedHandIndex();
    }

    public static void clear() {
        setSnapshot(BattleSnapshot.inactive());
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
                lockedCameraCenter = playerEntity.getBoundingBox().getCenter().add(monsterEntity.getBoundingBox().getCenter()).scale(0.5D);
            }
            return lockedCameraCenter;
        }
        if (minecraft.player != null) {
            return minecraft.player.position();
        }
        return Vec3.ZERO;
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
            visualStates.computeIfAbsent(event.attackerId(), id -> new VisualState()).showItem(event.itemStack());
            if (event.healthDamage() > 0) {
                visualStates.computeIfAbsent(event.targetId(), id -> new VisualState()).hurtFlash();
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
        return state.itemStack;
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

    public static int fakeDeathRenderTicks(int entityId) {
        Long start = fakeDeathStarts.get(entityId);
        if (start == null) {
            return 0;
        }
        return Math.min(20, (int) ((System.nanoTime() - start) / 50_000_000L));
    }

    public static void tickDamageNumbers() {
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
        fakeDeathStarts.keySet().removeIf(id -> !fakeDeadIds.contains(id));
        for (int entityId : fakeDeadIds) {
            fakeDeathStarts.putIfAbsent(entityId, now);
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

    public static final class VisualState {
        private ItemStack itemStack = ItemStack.EMPTY;
        private int itemTicks;
        private int hurtTicks;
        private int knockbackTicks;

        public int hurtTicks() {
            return hurtTicks;
        }

        public int knockbackTicks() {
            return knockbackTicks;
        }

        private void showItem(ItemStack stack) {
            if (stack != null && !stack.isEmpty()) {
                itemStack = stack.copy();
                itemTicks = 18;
            }
        }

        private void hurtFlash() {
            hurtTicks = 10;
            knockbackTicks = 24;
        }

        private void tick() {
            if (itemTicks > 0) {
                itemTicks--;
            }
            if (hurtTicks > 0) {
                hurtTicks--;
            }
            if (knockbackTicks > 0) {
                knockbackTicks--;
            }
        }

        private boolean done() {
            return itemTicks <= 0 && hurtTicks <= 0 && knockbackTicks <= 0;
        }
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
        private final int delay;
        private int age;

        private DamageNumber(int entityId, int amount, boolean block, int delay) {
            this.entityId = entityId;
            this.amount = amount;
            this.block = block;
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
