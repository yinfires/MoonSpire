package com.yinfires.moonspire.client;

import com.mojang.math.Axis;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.MoonSpirePerfDiagnostics;
import com.yinfires.moonspire.battle.BattleCombatantSnapshot;
import com.yinfires.moonspire.battle.BattleEffectSnapshot;
import com.yinfires.moonspire.battle.BattleEffectType;
import com.yinfires.moonspire.battle.BattleEnemyIntentSnapshot;
import com.yinfires.moonspire.battle.BattlePhase;
import com.yinfires.moonspire.battle.BattlePileSource;
import com.yinfires.moonspire.battle.BattleSnapshot;
import com.yinfires.moonspire.battle.BattleVisualEvent;
import com.yinfires.moonspire.battle.PendingHandSelectionSnapshot;
import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.CardTarget;
import com.yinfires.moonspire.client.ui.MoonSpireBattleLayoutEditor;
import com.yinfires.moonspire.client.ui.MoonSpireModalLayer;
import com.yinfires.moonspire.client.ui.MoonSpireUiLayout;
import com.yinfires.moonspire.client.ui.MoonSpireUiRect;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import com.yinfires.moonspire.network.CancelBattlePayload;
import com.yinfires.moonspire.network.EndTurnPayload;
import com.yinfires.moonspire.network.RequestBattlePilePayload;
import com.yinfires.moonspire.network.SelectBattleTargetPayload;
import com.yinfires.moonspire.network.SelectHandCardsPayload;
import com.yinfires.moonspire.network.UseCardPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class BattleScreen extends NoBlurScreen {
    private static final int ENTRY_SPEED_X_OFFSET = 72;
    private static final int ENTRY_HEADER_Y_OFFSET = 7;
    private static final int ENTRY_ROW_HEIGHT = 42;
    private static final int ENTRY_EFFECT_Y_OFFSET = ENTRY_ROW_HEIGHT + 3;
    private static final int ENTRY_EFFECT_ICON_SIZE = 15;
    private static final int ENTRY_EFFECT_SPACING = 18;
    private static final int ENTRY_EFFECT_BAND_HEIGHT = ENTRY_EFFECT_Y_OFFSET + ENTRY_EFFECT_ICON_SIZE - ENTRY_ROW_HEIGHT;
    private static final int ENTRY_GAP = 4;
    private static final int ENTRY_OCCUPIED_HEIGHT = ENTRY_ROW_HEIGHT + ENTRY_EFFECT_BAND_HEIGHT;
    private static final int ENTRY_TOTAL_HEIGHT = ENTRY_OCCUPIED_HEIGHT + ENTRY_GAP;
    private static final int ENTRY_SCROLL_STEP = 28;
    private static final int ENTRY_AREA_BOTTOM_MARGIN = 8;
    private static final int ENTRY_INTENT_GUTTER = 60;
    private static final float MODAL_CONTENT_Z = 1100.0F;
    private static final int HUD_TIP_WIDTH = 182;
    private static final int HUD_TIP_PAD = 6;
    private static final int HUD_TIP_PARAGRAPH_GAP = 10;
    private static final Component END_TURN_SHORTCUT = Component.translatable("screen.moonspire.end_turn.shortcut");
    private static final int HAND_SIDE_MARGIN = 100;
    private static final int HAND_MAX_SPACING = 54;
    private static final int HAND_MIN_SPACING = 38;
    private static final int HAND_BASELINE_OFFSET = 6;
    private static final int HAND_ARC_DROP = 24;
    private static final int HAND_HOVER_LIFT = 54;
    private static final float HAND_EDGE_ANGLE = 8.0F;
    private static final float HAND_PREVIEW_SCALE = 0.74F;
    private static final float HAND_BASE_SCALE = 0.98F;
    private static final float HAND_MIN_SCALE = 0.62F;
    private static final int CARD_GRID_BOTTOM_RESERVE = 0;
    private static final int TURN_BANNER_TICKS = 42;
    private static final int FLY_TO_DISCARD_TICKS = 10;
    private static final int PLAYED_CARD_TO_CENTER_TICKS = 4;
    private static final int PLAYED_CARD_HOLD_TICKS = 4;
    private static final float PLAYED_CARD_NORMAL_SCALE = CardRenderHelper.SMALL_CARD_WIDTH / (float) CardRenderHelper.CARD_WIDTH;
    private static final float MONSTER_PLAYED_CARD_SCALE = PLAYED_CARD_NORMAL_SCALE * 1.03F;
    private static final float DISCARD_FLY_SCALE = PLAYED_CARD_NORMAL_SCALE * 0.25F;
    private static final float DRAG_PLAYABLE_PULSE_TICKS = 8.0F;
    private static final float DRAG_CARD_SCALE = 0.84F;
    private static final int HAND_SELECTION_BUTTON_W = 144;
    private static final int HAND_SELECTION_BUTTON_H = 28;
    private static final int HAND_SELECTION_CARD_SPACING = 76;
    private static final float HAND_SELECTION_CARD_SCALE = 0.74F;
    private static final int HAND_SELECTION_EXHAUST_TICKS = 14;
    private static final int PILE_ICON_TEXTURE_SIZE = 32;
    private static final int PILE_BADGE_TEXTURE_SIZE = 128;
    private static final int PILE_BADGE_SIZE = 24;
    private static final float PILE_HOVER_SCALE = 1.18F;
    private static final float PILE_COUNT_TEXT_SCALE = 2.0F;
    private static final int EXHAUST_COUNT_TEXT_COLOR = 0xFFE07CFF;
    private static final int CARD_RENDER_DATA_CACHE_LIMIT = 512;
    private final Map<UUID, HandCardAnimation> handAnimations = new HashMap<>();
    private final List<FlyingCardAnimation> flyingCards = new ArrayList<>();
    private final CardPreviewAnimation monsterIntentPreview = new CardPreviewAnimation();
    private final PileHoverAnimation drawPileHover = new PileHoverAnimation();
    private final PileHoverAnimation discardPileHover = new PileHoverAnimation();
    private final PileHoverAnimation exhaustPileHover = new PileHoverAnimation();
    private DragState dragState;
    private CardGridPanel pileOverlay;
    private PileOverlaySource pileOverlaySource = PileOverlaySource.NONE;
    private int pileOverlayEntityId = -1;
    private int hoveredHandIndex = -1;
    private int hoveredMonsterIntentIndex = -1;
    private int hoveredMonsterIntentEntityId = -1;
    private BattleSnapshot previousSnapshot = BattleSnapshot.inactive();
    private BattlePhase turnBannerPhase = BattlePhase.PLAYER_TURN;
    private int turnBannerTicks;
    private int uiTicks;
    private boolean rotatingCamera;
    private boolean cameraDragMoved;
    private int pendingTargetClickId = -1;
    private double lastMouseX;
    private double lastMouseY;
    private long syncedSnapshotVersion = -1L;
    private long renderedMonsterPlayedCardSequence = -1L;
    private long frameIndex;
    private long lastAnimationNanos;
    private float currentFrameTicks;
    private double playerEntryScroll;
    private double enemyEntryScroll;
    private FrameCache frameCache = FrameCache.empty();
    private boolean awaitingUseCardSnapshot;
    private boolean awaitingEndTurnSnapshot;
    private int awaitingEndTurnRound = -1;
    private final Set<UUID> locallyUsedCardIds = new HashSet<>();
    private final Set<UUID> locallyDisplayedVisualCardIds = new HashSet<>();
    private HandSelectionOverlay handSelectionOverlay = HandSelectionOverlay.empty();
    private HandSelectionConfirmation handSelectionConfirmation = HandSelectionConfirmation.empty();
    private PileRequestKey requestedPileKey;
    private PileRequestKey displayedPileKey;
    private PileRequestKey stablePileUpdateKey;
    private int stablePileDisplayCount = -1;
    private int nextPileOverlayRefreshTick;
    private int pileOverlayPerfFrameIndex;
    private final Map<CardRenderDataCacheKey, CardRenderData> cardRenderDataCache = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CardRenderDataCacheKey, CardRenderData> eldest) {
            return size() > CARD_RENDER_DATA_CACHE_LIMIT;
        }
    };
    private long cardRenderDataDeveloperRevision = -1L;
    private BattleSnapshot cardRenderDataPreviewHashSnapshot;
    private int cardRenderDataPreviewHash;
    private long battleRenderPerfStart;
    private long battleRenderSnapshotNanos;
    private long battleRenderEntriesNanos;
    private long battleRenderIntentNanos;
    private long battleRenderBottomNanos;
    private long battleRenderBottomEnergyNanos;
    private long battleRenderBottomMasksNanos;
    private long battleRenderBottomPilesNanos;
    private long battleRenderBottomEndTurnNanos;
    private long battleRenderBottomFlushNanos;
    private long battleRenderBottomHandNanos;
    private long battleRenderHandBaseNanos;
    private long battleRenderHandBaseFlushNanos;
    private long battleRenderHandPoseNanos;
    private long battleRenderHandDepthNanos;
    private long battleRenderHandTextNanos;
    private long battleRenderHandContentKeyNanos;
    private long battleRenderHandValuesNanos;
    private long battleRenderHandTextVisibilityNanos;
    private long battleRenderHandTextSubmitNanos;
    private long battleRenderHandHoverNanos;
    private int battleRenderHandCardOffscreenSkips;
    private int battleRenderHandDescOffscreenSkips;
    private long battleRenderFlyingNanos;
    private long battleRenderDraggedNanos;
    private long battleRenderMonsterPlayedNanos;
    private long battleRenderOtherNanos;

    public BattleScreen() {
        super(Component.translatable("screen.moonspire.battle"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        frameIndex++;
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            lastAnimationNanos = 0L;
            onClose();
            return;
        }
        try (CardRenderHelper.CardRenderContext ignored = CardRenderHelper.openFrameContext()) {
            currentFrameTicks = animationFrameTicks();
            beginBattleRenderPerf();
            prepareCardRenderDataCache();
            if (syncedSnapshotVersion != ClientBattleState.snapshotVersion()) {
                syncSnapshotAnimations(snapshot);
                syncedSnapshotVersion = ClientBattleState.snapshotVersion();
            }
            reconcileEndTurnAwaiting(snapshot);
            boolean pileModalActive = pileOverlay != null;
            boolean handSelectionActive = snapshot.pendingHandSelection().active();
            boolean handSelectionWaiting = handSelectionConfirmation.active();
            boolean handSelectionModalActive = handSelectionActive && !handSelectionWaiting;
            boolean modalInputBlocked = pileModalActive || handSelectionModalActive || handSelectionWaiting;
            frameCache = modalInputBlocked ? FrameCache.empty() : createFrameCache(snapshot, mouseX, mouseY, partialTick);
            if (pileModalActive || handSelectionModalActive) {
                dragState = null;
                rotatingCamera = false;
                pendingTargetClickId = -1;
                if (pileModalActive) {
                    hoveredHandIndex = -1;
                }
                hoveredMonsterIntentIndex = -1;
                hoveredMonsterIntentEntityId = -1;
                monsterIntentPreview.clear();
                ClientBattleState.setHoveredEntityId(-1);
                renderModalBackground(graphics, snapshot, partialTick, pileModalActive);
                if (pileModalActive) {
                    renderPileOverlay(graphics, mouseX, mouseY);
                } else {
                    renderHandSelectionOverlay(graphics, snapshot, mouseX, mouseY, partialTick);
                }
                return;
            }
            int hoveredTarget = interactiveTargetUnderMouse(mouseX, mouseY, snapshot);
            CardInstance draggedCard = draggedCard(snapshot);
            List<Integer> draggedTargets = highlightedTargetsForDraggedCard(draggedCard, snapshot, mouseX, mouseY);
            if (!draggedTargets.isEmpty()) {
                ClientBattleState.setHoveredEntityIds(draggedTargets);
                hoveredTarget = draggedTargets.get(0);
            } else if (modalInputBlocked) {
                hoveredTarget = -1;
                ClientBattleState.setHoveredEntityId(-1);
            } else {
                ClientBattleState.setHoveredEntityId(hoveredTarget);
            }
            renderBattleBase(graphics, snapshot, modalInputBlocked ? MoonSpireModalLayer.BLOCKED_MOUSE : mouseX, modalInputBlocked ? MoonSpireModalLayer.BLOCKED_MOUSE : mouseY, partialTick, handSelectionWaiting);
            finishBattleRenderPerf(snapshot);
        }
    }

    private void renderBattleBase(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick, boolean modalBackground) {
        long segmentStart = perfStart();
        boolean hideBaseForLocalHandSelection = modalBackground && handSelectionConfirmation.active();
        EffectTooltip hoveredEffect = null;
        if (!hideBaseForLocalHandSelection) {
            hoveredEffect = renderEntries(graphics, snapshot, mouseX, mouseY);
        }
        battleRenderEntriesNanos += elapsedPerf(segmentStart);
        segmentStart = perfStart();
        if (!modalBackground && shouldRenderMonsterIntent(snapshot, mouseX, mouseY)) {
            renderMonsterIntent(graphics, snapshot, mouseX, mouseY);
        } else {
            hoveredMonsterIntentIndex = -1;
            monsterIntentPreview.clear();
        }
        battleRenderIntentNanos += elapsedPerf(segmentStart);
        segmentStart = perfStart();
        renderBottomBar(graphics, snapshot, mouseX, mouseY, partialTick, !snapshot.pendingHandSelection().active() || handSelectionConfirmation.active());
        battleRenderBottomNanos += elapsedPerf(segmentStart);
        segmentStart = perfStart();
        renderFlyingCards(graphics, snapshot, partialTick, modalBackground);
        battleRenderFlyingNanos += elapsedPerf(segmentStart);
        if (!hideBaseForLocalHandSelection) {
            segmentStart = perfStart();
            renderDraggedCard(graphics, snapshot, mouseX, mouseY, partialTick);
            battleRenderDraggedNanos += elapsedPerf(segmentStart);
            segmentStart = perfStart();
            renderMonsterPlayedCard(graphics, snapshot, modalBackground);
            battleRenderMonsterPlayedNanos += elapsedPerf(segmentStart);
        }
        segmentStart = perfStart();
        renderTurnBanner(graphics, partialTick);
        MoonSpireBattleLayoutEditor.render(graphics, font, width, height, mouseX, mouseY);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        if (!hideBaseForLocalHandSelection) {
            renderExhaustTooltip(graphics, snapshot, mouseX, mouseY);
            renderHudTooltip(graphics, snapshot, mouseX, mouseY);
            renderEffectTooltip(graphics, hoveredEffect);
        }
        battleRenderOtherNanos += elapsedPerf(segmentStart);
    }

    private void renderModalBackground(GuiGraphics graphics, BattleSnapshot snapshot, float partialTick, boolean lightweight) {
        renderEntries(graphics, snapshot, MoonSpireModalLayer.BLOCKED_MOUSE, MoonSpireModalLayer.BLOCKED_MOUSE);
        renderBottomBar(graphics, snapshot, MoonSpireModalLayer.BLOCKED_MOUSE, MoonSpireModalLayer.BLOCKED_MOUSE, partialTick, false);
        if (!lightweight) {
            renderMonsterPlayedCard(graphics, snapshot, true);
        }
        renderTurnBanner(graphics, partialTick);
        if (!lightweight) {
            renderWidgets(graphics, MoonSpireModalLayer.BLOCKED_MOUSE, MoonSpireModalLayer.BLOCKED_MOUSE, partialTick);
        }
    }

    @Override
    public void tick() {
        uiTicks++;
        ClientBattleState.tickDamageNumbers(false);
        if (turnBannerTicks > 0) {
            turnBannerTicks--;
        }
    }

    private boolean handSelectionInputBlocked(BattleSnapshot snapshot) {
        return handSelectionConfirmation.active() || snapshot.pendingHandSelection().active();
    }

    private void clearBaseInteractions() {
        dragState = null;
        rotatingCamera = false;
        pendingTargetClickId = -1;
        hoveredHandIndex = -1;
        hoveredMonsterIntentIndex = -1;
        hoveredMonsterIntentEntityId = -1;
        monsterIntentPreview.clear();
        ClientBattleState.setHoveredEntityId(-1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            return false;
        }
        if (handSelectionConfirmation.active()) {
            clearBaseInteractions();
            return true;
        }
        if (snapshot.pendingHandSelection().active()) {
            return clickHandSelectionOverlay(mouseX, mouseY, button, snapshot);
        }
        if (pileOverlay != null) {
            updatePileOverlayCards(snapshot);
            if (button == 0 && pileOverlay.mouseClicked(width, height, CARD_GRID_BOTTOM_RESERVE, mouseX, mouseY, button)) {
                pendingTargetClickId = -1;
                return true;
            }
            if (button == 1) {
                closePileOverlay();
            }
            pendingTargetClickId = -1;
            return true;
        }
        if (MoonSpireBattleLayoutEditor.mouseClicked(mouseX, mouseY, button, width, height)) {
            pendingTargetClickId = -1;
            return true;
        }
        if (button == 1) {
            dragState = null;
            pendingTargetClickId = -1;
            int entityId = combatantEntryUnderMouse(mouseX, mouseY, snapshot);
            if (entityId == -1) {
                entityId = interactiveTargetUnderMouse(mouseX, mouseY, snapshot);
            }
            if (entityId != -1) {
                openEntityDeckOverlay(entityId, snapshot);
            }
            return true;
        }
        if (button == 0) {
            if (clickPile(mouseX, mouseY, snapshot)) {
                return true;
            }
            if (clickEndTurn(mouseX, mouseY, snapshot)) {
                return true;
            }
            int visibleHandIndex = handPreviewIndexAt(mouseX, mouseY, snapshot);
            if (visibleHandIndex < 0) {
                visibleHandIndex = handIndexAt(mouseX, mouseY, snapshot);
            }
            int handIndex = handSnapshotIndexForVisibleIndex(snapshot, visibleHandIndex);
            if (handIndex >= 0) {
                ClientBattleState.selectHandIndex(handIndex);
                if (!cardActionsLocked(snapshot)) {
                    clearHandHoverAnimations(snapshot);
                    dragState = new DragState(handIndex, snapshot.hand().get(handIndex).id());
                }
                pendingTargetClickId = -1;
                return true;
            }
            if (targetingBlockedByUiAt(mouseX, mouseY, snapshot)) {
                pendingTargetClickId = -1;
                return true;
            }
            pendingTargetClickId = selectableTargetUnderMouse(mouseX, mouseY, snapshot);
            rotatingCamera = true;
            cameraDragMoved = false;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (handSelectionInputBlocked(ClientBattleState.snapshot())) {
            clearBaseInteractions();
            return true;
        }
        if (pileOverlay != null) {
            pileOverlay.mouseReleased(button);
            return true;
        }
        if (MoonSpireBattleLayoutEditor.mouseReleased(button)) {
            return true;
        }
        if (button == 0 && rotatingCamera) {
            rotatingCamera = false;
            if (!cameraDragMoved) {
                PacketDistributor.sendToServer(new SelectBattleTargetPayload(pendingTargetClickId));
            }
            pendingTargetClickId = -1;
            return true;
        }
        if (button == 0 && dragState != null) {
            BattleSnapshot snapshot = ClientBattleState.snapshot();
            DragState releasedDrag = dragState;
            boolean played = playDraggedCard(mouseX, mouseY);
            if (!played) {
                beginDraggedCardReturn(snapshot, releasedDrag, mouseX, mouseY);
            }
            dragState = null;
            clearHandHoverProgress();
            return true;
        }
        if (button == 0) {
            rotatingCamera = false;
            pendingTargetClickId = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (handSelectionInputBlocked(ClientBattleState.snapshot())) {
            clearBaseInteractions();
            return true;
        }
        if (pileOverlay != null) {
            updatePileOverlayCards(ClientBattleState.snapshot());
            if (button == 0) {
                pileOverlay.mouseDragged(width, height, CARD_GRID_BOTTOM_RESERVE, mouseY);
            }
            return true;
        }
        if (button == 0 && MoonSpireBattleLayoutEditor.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        if (rotatingCamera && button == 0) {
            ClientBattleState.rotateCamera(mouseX - lastMouseX, mouseY - lastMouseY);
            if (Math.abs(mouseX - lastMouseX) + Math.abs(mouseY - lastMouseY) > 1.5D) {
                cameraDragMoved = true;
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return dragState != null || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (handSelectionInputBlocked(ClientBattleState.snapshot())) {
            return true;
        }
        if (pileOverlay != null && scrollY != 0.0D) {
            updatePileOverlayCards(ClientBattleState.snapshot());
            pileOverlay.scroll(width, height, CARD_GRID_BOTTOM_RESERVE, scrollY);
            return true;
        }
        if (MoonSpireBattleLayoutEditor.mouseScrolled(scrollY, hasShiftDown(), hasControlDown())) {
            return true;
        }
        if (scrollEntries(mouseX, mouseY, scrollY, ClientBattleState.snapshot())) {
            return true;
        }
        if (scrollY != 0.0D) {
            ClientBattleState.zoomCamera(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.UI_DEBUG.matches(keyCode, scanCode)) {
            return ClientEvents.handleUiDebugKey(Minecraft.getInstance());
        }
        if (handSelectionInputBlocked(ClientBattleState.snapshot())) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                Minecraft.getInstance().setScreen(new PauseScreen(true));
                return true;
            }
            return true;
        }
        if (pileOverlay != null) {
            if (ClientEvents.OPEN_DECK.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_K) {
                if (pileOverlaySource == PileOverlaySource.BATTLE_DECK) {
                    closePileOverlay();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                Minecraft.getInstance().setScreen(new PauseScreen(true));
            }
            return true;
        }
        if (ClientEvents.OPEN_DECK.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_K) {
            openBattleDeckOverlay(ClientBattleState.snapshot());
            return true;
        }
        if (MoonSpireBattleLayoutEditor.keyPressed(keyCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Q) {
            BattleSnapshot snapshot = ClientBattleState.snapshot();
            if (canEndTurn(snapshot)) {
                beginAwaitingEndTurnSnapshot(snapshot);
                PacketDistributor.sendToServer(new EndTurnPayload());
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            PacketDistributor.sendToServer(new CancelBattlePayload());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(new PauseScreen(true));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private EffectTooltip renderEntries(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY) {
        EffectTooltip playerTooltip = renderEntryArea(graphics, snapshot, liveCombatants(snapshot.players()), layout().resolve("player_entry", width, height), false, mouseX, mouseY);
        EffectTooltip enemyTooltip = renderEntryArea(graphics, snapshot, liveCombatants(snapshot.enemies()), layout().resolve("monster_entry", width, height), true, mouseX, mouseY);
        return enemyTooltip != null ? enemyTooltip : playerTooltip;
    }

    private EffectTooltip renderEntryArea(GuiGraphics graphics, BattleSnapshot snapshot, List<BattleCombatantSnapshot> entries, MoonSpireUiRect baseRect, boolean enemyArea, int mouseX, int mouseY) {
        if (entries.isEmpty()) {
            clampEntryScroll(entries.size(), baseRect, enemyArea);
            return null;
        }
        MoonSpireUiRect area = entryAreaRect(baseRect);
        double scroll = clampEntryScroll(entries.size(), baseRect, enemyArea);
        EffectTooltip hoveredEffect = null;
        graphics.enableScissor(Math.max(0, area.x() - ENTRY_INTENT_GUTTER - 2), Math.max(0, area.y() - 2), Math.min(width, area.right() + ENTRY_INTENT_GUTTER + 2), Math.min(height, area.bottom() + 2));
        try {
            for (int i = 0; i < entries.size(); i++) {
                BattleCombatantSnapshot entry = entries.get(i);
                MoonSpireUiRect row = entryRowRect(baseRect, i, scroll);
                if (row.bottom() < area.y() || row.y() > area.bottom()) {
                    continue;
                }
                EffectTooltip entryHoveredEffect = renderEntry(graphics, entry, row, area, entityName(entry.entityId(), Component.translatable(enemyArea ? "screen.moonspire.monster_entry" : "screen.moonspire.player_entry")), enemyArea, mouseX, mouseY);
                if (entryHoveredEffect != null) {
                    hoveredEffect = entryHoveredEffect;
                }
            }
        } finally {
            graphics.disableScissor();
        }
        return hoveredEffect;
    }

    private MoonSpireUiRect entryRowRect(MoonSpireUiRect baseRect, int index, double scroll) {
        return new MoonSpireUiRect(baseRect.x(), (int) Math.round(baseRect.y() + index * ENTRY_TOTAL_HEIGHT - scroll), baseRect.width(), ENTRY_OCCUPIED_HEIGHT);
    }

    private MoonSpireUiRect entryAreaRect(MoonSpireUiRect baseRect) {
        MoonSpireUiRect exhaust = layout().resolve("exhaust_pile", width, height);
        int bottomLimit = Math.max(baseRect.y() + ENTRY_OCCUPIED_HEIGHT, exhaust.y() - ENTRY_AREA_BOTTOM_MARGIN);
        return new MoonSpireUiRect(baseRect.x(), baseRect.y(), baseRect.width(), Math.max(ENTRY_OCCUPIED_HEIGHT, bottomLimit - baseRect.y()));
    }

    private EffectTooltip renderEntry(GuiGraphics graphics, BattleCombatantSnapshot entry, MoonSpireUiRect rect, MoonSpireUiRect area, Component fallbackName, boolean selectable, int mouseX, int mouseY) {
        if (entry.fakeDead()) {
            return null;
        }
        boolean hovered = ClientBattleState.isHoveredEntityId(entry.entityId());
        boolean selected = selectable && !ClientBattleState.hasHoveredEntityIds() && ClientBattleState.selectedTargetId() == entry.entityId();
        int x = rect.x();
        int y = rect.y();
        if (selected) {
            graphics.renderOutline(x - 1, y - 1, rect.width() + 2, rect.height() + 2, 0xDDFFD166);
        } else if (hovered) {
            graphics.renderOutline(x - 1, y - 1, rect.width() + 2, rect.height() + 2, 0xDDA8F7FF);
        }
        int intentX = selectable ? x - 54 : x + rect.width() + 8;
        renderIntentSummary(graphics, entry, intentX, y + 8);
        graphics.drawString(font, fallbackName, x + 8, y + ENTRY_HEADER_Y_OFFSET, 0xFFFFEAC2, false);
        MoonSpireUiRect speedRect = entrySpeedTextRect(entry, rect);
        graphics.drawString(font, Component.translatable("screen.moonspire.speed_short", entry.roundSpeed()), speedRect.x(), speedRect.y(), 0xFFB8E6FF, false);
        renderHealthBar(graphics, x + 8, y + 22, rect.width() - 16, 12, entry);
        return renderEffects(graphics, entry.effects(), area, x + 8, y + ENTRY_EFFECT_Y_OFFSET, mouseX, mouseY);
    }

    private void renderIntentSummary(GuiGraphics graphics, BattleCombatantSnapshot entry, int x, int y) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        List<CardInstance> intentCards = snapshot.intentCardsFor(entry.entityId());
        if (intentCards.isEmpty()) {
            return;
        }
        int attack = 0;
        int block = 0;
        int negativeEffects = 0;
        int positiveEffects = 0;
        int paralysis = Math.max(0, CardRenderHelper.effectAmount(entry, BattleEffectType.PARALYSIS));
        for (CardInstance card : intentCards) {
            boolean paralyzedAttack = paralysis > 0 && card.hasAttack();
            if (paralyzedAttack) {
                paralysis--;
            }
            for (CardEffect effect : card.effects()) {
                if (effect.amount() <= 0) {
                    continue;
                }
                boolean automaticEnemySide = snapshot.isEnemyEntity(entry.entityId());
                List<Integer> targets = targetIdsForEffectTarget(effect.target(), snapshot, automaticEnemySide, -1, entry.entityId());
                int totalAmount = Math.max(0, effect.amount()) * Math.max(1, effect.count());
                int primaryOpponent = primaryOpponentEntityId(snapshot, automaticEnemySide);
                if (CardInstance.isAttackDamageEffect(effect.kind()) && targets.contains(primaryOpponent)) {
                    BattleCombatantSnapshot opponent = snapshot.combatant(primaryOpponent);
                    if (opponent == null) {
                        continue;
                    }
                    int baseDamage = paralyzedAttack ? Math.max(0, effect.amount() - CardBalance.PARALYSIS_ATTACK_DAMAGE_REDUCTION) : effect.amount();
                    attack += CardRenderHelper.previewDamageAmount(baseDamage, entry.roundSpeed(), opponent.roundSpeed(), opponent.defense(), CardRenderHelper.effectAmount(opponent, BattleEffectType.GUARD), CardRenderHelper.effectAmount(entry, BattleEffectType.STRENGTH), CardRenderHelper.effectAmount(entry, BattleEffectType.WEAKNESS) > 0, card.hasEffect(CardEffectKind.REMOTE), CardRenderHelper.effectAmount(opponent, BattleEffectType.GLOWING) > 0) * Math.max(1, effect.count());
                } else if (effect.kind() == CardEffectKind.BLOCK && targets.contains(entry.entityId())) {
                    block += totalAmount;
                } else if (negativeEffect(effect.kind()) && targets.contains(primaryOpponent)) {
                    negativeEffects += totalAmount;
                } else if (positiveEffect(effect.kind()) && targets.contains(entry.entityId())) {
                    positiveEffects += totalAmount;
                }
            }
        }
        int lineY = y;
        if (attack > 0) {
            graphics.drawString(font, Component.translatable("screen.moonspire.intent_attack", attack), x, lineY, 0xFFFF6961, false);
            lineY += 10;
        }
        if (block > 0) {
            graphics.drawString(font, Component.translatable("screen.moonspire.intent_block", block), x, lineY, 0xFF66BFFF, false);
            lineY += 10;
        }
        if (positiveEffects > 0) {
            graphics.drawString(font, Component.translatable("screen.moonspire.intent_positive", positiveEffects), x, lineY, 0xFF8DE6A6, false);
            lineY += 10;
        }
        if (negativeEffects > 0) {
            graphics.drawString(font, Component.translatable("screen.moonspire.intent_effect", negativeEffects), x, lineY, 0xFFFF8AA0, false);
        }
    }

    private void renderHealthBar(GuiGraphics graphics, int x, int y, int w, int h, BattleCombatantSnapshot entry) {
        CardRenderHelper.renderCombatantBar(graphics, font, entry, x, y, w, h);
    }

    private EffectTooltip renderEffects(GuiGraphics graphics, List<BattleEffectSnapshot> effects, MoonSpireUiRect area, int x, int y, int mouseX, int mouseY) {
        EffectTooltip hovered = null;
        for (int i = 0; i < effects.size(); i++) {
            BattleEffectSnapshot effect = effects.get(i);
            int ex = x + i * ENTRY_EFFECT_SPACING;
            Component amount = Component.translatable("screen.moonspire.effect_short", effect.amount());
            ResourceLocation texture = effectIconTexture(effect.type());
            if (texture != null) {
                graphics.blit(texture, ex, y, ENTRY_EFFECT_ICON_SIZE, ENTRY_EFFECT_ICON_SIZE, 0.0F, 0.0F, 64, 64, 64, 64);
            } else {
                graphics.renderOutline(ex, y, ENTRY_EFFECT_ICON_SIZE, ENTRY_EFFECT_ICON_SIZE, 0xCCFFB1C0);
                Component marker = Component.translatable("screen.moonspire.effect_unknown_icon");
                graphics.drawString(font, marker, ex + (ENTRY_EFFECT_ICON_SIZE - font.width(marker)) / 2, y + 2, 0xFFFFB1C0, false);
            }
            int amountColor = effect.type() == BattleEffectType.STRENGTH && effect.amount() < 0 ? 0xFFFF5454 : 0xFFFFFFFF;
            graphics.drawString(font, amount, ex + ENTRY_EFFECT_ICON_SIZE - font.width(amount), y + 7, amountColor, true);
            if (area.contains(mouseX, mouseY) && mouseX >= ex && mouseX <= ex + ENTRY_EFFECT_ICON_SIZE && mouseY >= y && mouseY <= y + ENTRY_EFFECT_ICON_SIZE) {
                hovered = new EffectTooltip(effect.type(), effect.amount(), ex, y);
            }
        }
        return hovered;
    }

    private void renderEffectTooltip(GuiGraphics graphics, EffectTooltip tooltip) {
        if (tooltip == null) {
            return;
        }
        int tipHeight = CardRenderHelper.effectTipHeight(font, tooltip.type(), tooltip.amount());
        int tipX = clampTooltipX(tooltip.x(), CardRenderHelper.TIP_WIDTH);
        int preferredY = tooltip.y() + ENTRY_EFFECT_ICON_SIZE + 3;
        int tipY = preferredY + tipHeight <= height - 6 ? preferredY : tooltip.y() - tipHeight - 3;
        tipY = clampTooltipY(tipY, tipHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        CardRenderHelper.renderEffectTip(graphics, font, tooltip.type(), tooltip.amount(), tipX, tipY);
        graphics.pose().popPose();
    }

    private static ResourceLocation effectIconTexture(BattleEffectType type) {
        if (type.iconTexturePath() == null || type.iconTexturePath().isBlank()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "textures/" + type.iconTexturePath());
    }

    private void renderMonsterIntent(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY) {
        int intentEntityId = currentIntentEntityId(snapshot);
        List<CardInstance> intentCards = intentCardsFor(snapshot, intentEntityId);
        if (intentCards.isEmpty()) {
            hoveredMonsterIntentEntityId = -1;
            return;
        }
        MoonSpireUiRect rect = layout().resolve("monster_intent", width, height);
        int spacing = 54;
        int total = (intentCards.size() - 1) * spacing + CardRenderHelper.SMALL_CARD_WIDTH;
        int x = rect.x() + rect.width() / 2 - total / 2;
        int y = rect.y();
        int hoveredIndex = -1;
        for (int i = 0; i < intentCards.size(); i++) {
            int cardX = x + i * spacing;
            if (mouseX >= cardX && mouseX <= cardX + CardRenderHelper.SMALL_CARD_WIDTH && mouseY >= y && mouseY <= y + CardRenderHelper.SMALL_CARD_HEIGHT) {
                hoveredIndex = i;
            }
        }
        if (hoveredIndex >= 0) {
            hoveredMonsterIntentIndex = hoveredIndex;
            hoveredMonsterIntentEntityId = intentEntityId;
        } else if (hoveredMonsterIntentEntityId == intentEntityId && hoveredMonsterIntentIndex >= 0 && hoveredMonsterIntentIndex < intentCards.size()) {
            int hoveredX = x + hoveredMonsterIntentIndex * spacing;
            IntentPreviewBounds preview = intentPreviewBoundsForSmallCard(hoveredX, y);
            if (preview.contains(mouseX, mouseY)) {
                hoveredIndex = hoveredMonsterIntentIndex;
            } else {
                hoveredMonsterIntentIndex = -1;
                hoveredMonsterIntentEntityId = -1;
            }
        } else {
            hoveredMonsterIntentIndex = -1;
            hoveredMonsterIntentEntityId = -1;
        }
        updateMonsterIntentPreview(intentCards, hoveredIndex, x, y, spacing);
        for (int i = 0; i < intentCards.size(); i++) {
            CardInstance card = intentCards.get(i);
            if (monsterIntentPreview.visible() && monsterIntentPreview.matches(card.id())) {
                continue;
            }
            BattleCombatantSnapshot attacker = intentAttacker(snapshot, intentEntityId);
            CardRenderHelper.renderSmallCard(graphics, font, card, x + i * spacing, y, false, false, cardValues(snapshot, card, attacker, snapshot.isEnemyEntity(attacker.entityId())), false);
        }
        CardInstance previewCard = monsterIntentPreviewCard(intentCards);
        if (previewCard == null) {
            monsterIntentPreview.clear();
        } else if (monsterIntentPreview.visible()) {
            renderAnimatedIntentPreview(graphics, snapshot, previewCard, intentEntityId, monsterIntentPreview);
            if (monsterIntentPreview.progress() > 0.86F) {
                CardPreviewAnimation.Bounds bounds = monsterIntentPreview.bounds();
                CardRenderHelper.renderKeywordTipsBeside(graphics, font, previewCard, bounds.x(), bounds.y(), bounds.width(), bounds.height(), width, height);
            }
        }
    }

    private void updateMonsterIntentPreview(List<CardInstance> intentCards, int hoveredIndex, int x, int y, int spacing) {
        if (hoveredIndex >= 0) {
            int hoveredX = x + hoveredIndex * spacing;
            IntentPreviewBounds preview = intentPreviewBoundsForSmallCard(hoveredX, y);
            CardInstance card = intentCards.get(hoveredIndex);
            monsterIntentPreview.setOpenTarget(card.id(), hoveredX + CardRenderHelper.SMALL_CARD_WIDTH / 2.0F, y + CardRenderHelper.SMALL_CARD_HEIGHT / 2.0F,
                    preview.centerX(), preview.centerY(), 1.0F, preview.scale(), CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
            monsterIntentPreview.advance(currentFrameTicks);
            return;
        }
        monsterIntentPreview.setClosingTarget();
        monsterIntentPreview.advance(currentFrameTicks);
        if (monsterIntentPreview.finishedClosing()) {
            monsterIntentPreview.clear();
        }
    }

    private void renderAnimatedIntentPreview(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, int attackerEntityId, CardPreviewAnimation animation) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 120.0F);
        CardPreviewAnimation.RenderBounds bounds = animation.renderBounds();
        graphics.pose().translate(bounds.x(), bounds.y(), 0.0F);
        graphics.pose().scale(animation.scale(), animation.scale(), 1.0F);
        BattleCombatantSnapshot attacker = intentAttacker(snapshot, attackerEntityId);
        CardRenderHelper.renderSmallCard(graphics, font, card, 0, 0, false, false, cardValues(snapshot, card, attacker, snapshot.isEnemyEntity(attacker.entityId())), false);
        graphics.pose().popPose();
    }

    private CardInstance monsterIntentPreviewCard(List<CardInstance> intentCards) {
        Object key = monsterIntentPreview.key();
        if (key == null) {
            return null;
        }
        for (CardInstance card : intentCards) {
            if (monsterIntentPreview.matches(card.id())) {
                return card;
            }
        }
        return null;
    }

    private int currentIntentEntityId(BattleSnapshot snapshot) {
        int hovered = ClientBattleState.hoveredEntityId();
        if (liveIntentCombatant(snapshot, hovered)) {
            return hovered;
        }
        int selected = ClientBattleState.selectedTargetId();
        if (liveIntentCombatant(snapshot, selected)) {
            return selected;
        }
        return -1;
    }

    private boolean liveIntentCombatant(BattleSnapshot snapshot, int entityId) {
        return snapshot.hasIntentCardsFor(entityId) && !combatantFakeDead(snapshot, entityId);
    }

    private List<CardInstance> intentCardsFor(BattleSnapshot snapshot, int enemyEntityId) {
        List<CardInstance> cards = snapshot.intentCardsFor(enemyEntityId);
        if (!cards.isEmpty()) {
            return cards;
        }
        return enemyEntityId == snapshot.monster().entityId() && snapshot.monsterIntent() != null ? List.of(snapshot.monsterIntent()) : List.of();
    }

    private BattleCombatantSnapshot intentAttacker(BattleSnapshot snapshot, int enemyEntityId) {
        BattleCombatantSnapshot combatant = snapshot.combatant(enemyEntityId);
        return combatant == null ? snapshot.monster() : combatant;
    }

    private void renderBottomBar(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick, boolean renderHand) {
        long segmentStart = perfStart();
        renderEnergy(graphics, layout().resolve("energy", width, height), snapshot);
        battleRenderBottomEnergyNanos += elapsedPerf(segmentStart);
        segmentStart = perfStart();
        List<ScreenRect> handMasks = renderHand ? handOcclusionMasks(snapshot, mouseX, mouseY, partialTick) : List.of();
        battleRenderBottomMasksNanos += elapsedPerf(segmentStart);
        segmentStart = perfStart();
        renderPile(graphics, layout().resolve("draw_pile", width, height), snapshot.drawPile(), true, drawPileAt(mouseX, mouseY), drawPileHover, partialTick, handMasks);
        renderPile(graphics, layout().resolve("discard_pile", width, height), snapshot.discardPile(), false, discardPileAt(mouseX, mouseY), discardPileHover, partialTick, handMasks);
        renderExhaustPile(graphics, layout().resolve("exhaust_pile", width, height), snapshot.exhaustPile(), visibleExhaustPileAt(mouseX, mouseY, snapshot), partialTick);
        battleRenderBottomPilesNanos += elapsedPerf(segmentStart);
        segmentStart = perfStart();
        renderEndTurnButton(graphics, snapshot, layout().resolve("end_turn", width, height), mouseX, mouseY, partialTick);
        battleRenderBottomEndTurnNanos += elapsedPerf(segmentStart);
        if (renderHand) {
            segmentStart = perfStart();
            graphics.flush();
            battleRenderBottomFlushNanos += elapsedPerf(segmentStart);
            segmentStart = perfStart();
            renderHand(graphics, snapshot, mouseX, mouseY, partialTick);
            battleRenderBottomHandNanos += elapsedPerf(segmentStart);
        }
    }

    private void renderPile(GuiGraphics graphics, MoonSpireUiRect rect, int count, boolean drawPile, boolean hovered, PileHoverAnimation animation, float partialTick, List<ScreenRect> handMasks) {
        animation.setHovered(hovered);
        animation.advance(currentFrameTicks);
        PileIconBounds icon = pileIconBounds(rect);
        ResourceLocation texture = drawPile ? MoonSpireUiTextures.DRAW_PILE : MoonSpireUiTextures.DISCARD_PILE;
        graphics.pose().pushPose();
        graphics.pose().translate(icon.centerX(), icon.centerY(), 0.0F);
        float scale = animation.scale(partialTick);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-icon.size() / 2.0F, -icon.size() / 2.0F, 0.0F);
        graphics.blit(texture, 0, 0, icon.size(), icon.size(), 0.0F, 0.0F, PILE_ICON_TEXTURE_SIZE, PILE_ICON_TEXTURE_SIZE, PILE_ICON_TEXTURE_SIZE, PILE_ICON_TEXTURE_SIZE);
        graphics.pose().popPose();
        renderPileBadge(graphics, rect, icon, count, drawPile, handMasks);
    }

    private PileIconBounds pileIconBounds(MoonSpireUiRect rect) {
        int size = Math.max(8, Math.min(rect.width(), rect.height()));
        float centerX = rect.x() + rect.width() / 2.0F;
        float centerY = rect.y() + rect.height() / 2.0F;
        return new PileIconBounds(centerX, centerY, size);
    }

    private void renderPileBadge(GuiGraphics graphics, MoonSpireUiRect rect, PileIconBounds icon, int count, boolean drawPile, List<ScreenRect> handMasks) {
        int badgeX = drawPile ? Math.round(icon.centerX() + icon.size() / 2.0F - PILE_BADGE_SIZE) : Math.round(icon.centerX() - icon.size() / 2.0F);
        int badgeY = Math.round(icon.centerY() + icon.size() / 2.0F - PILE_BADGE_SIZE);
        badgeX = Math.max(rect.x(), Math.min(rect.right() - PILE_BADGE_SIZE, badgeX));
        badgeY = Math.max(rect.y(), Math.min(rect.bottom() - PILE_BADGE_SIZE, badgeY));
        int x = badgeX;
        int y = badgeY;
        renderWithOcclusionMask(graphics, x, y, PILE_BADGE_SIZE, PILE_BADGE_SIZE, handMasks, () -> {
            graphics.blit(MoonSpireUiTextures.PILE_COUNT_BADGE, x, y, PILE_BADGE_SIZE, PILE_BADGE_SIZE, 0.0F, 0.0F, PILE_BADGE_TEXTURE_SIZE, PILE_BADGE_TEXTURE_SIZE, PILE_BADGE_TEXTURE_SIZE, PILE_BADGE_TEXTURE_SIZE);
            drawPileCount(graphics, Integer.toString(count), x + PILE_BADGE_SIZE / 2.0F, y + PILE_BADGE_SIZE / 2.0F);
        });
    }

    private void renderExhaustPile(GuiGraphics graphics, MoonSpireUiRect rect, int count, boolean hovered, float partialTick) {
        exhaustPileHover.setHovered(hovered);
        exhaustPileHover.advance(currentFrameTicks);
        if (count <= 0) {
            return;
        }
        PileIconBounds icon = pileIconBounds(rect);
        graphics.pose().pushPose();
        graphics.pose().translate(icon.centerX(), icon.centerY(), 0.0F);
        float scale = exhaustPileHover.scale(partialTick);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-icon.size() / 2.0F, -icon.size() / 2.0F, 0.0F);
        graphics.blit(MoonSpireUiTextures.EXHAUST_PILE, 0, 0, icon.size(), icon.size(), 0.0F, 0.0F, 64, 64, 64, 64);
        graphics.pose().popPose();
        drawBoldExhaustCount(graphics, Integer.toString(count), icon.centerX(), icon.centerY());
    }

    private void drawBoldExhaustCount(GuiGraphics graphics, String text, float centerX, float centerY) {
        FormattedCharSequence countText = FormattedCharSequence.forward(text, Style.EMPTY.withBold(true));
        int textWidth = font.width(countText);
        float scale = Math.min(2.2F, 26.0F / Math.max(1.0F, textWidth));
        float textX = centerX - textWidth * scale / 2.0F;
        float textY = centerY - font.lineHeight * scale / 2.0F + 1.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, countText, 0, 0, EXHAUST_COUNT_TEXT_COLOR, false);
        graphics.pose().popPose();
    }

    private void renderExhaustTooltip(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY) {
        if (!visibleExhaustPileAt(mouseX, mouseY, snapshot)) {
            return;
        }
        Component title = Component.translatable("screen.moonspire.exhaust_pile");
        Component description = Component.translatable("screen.moonspire.exhaust_pile.tooltip");
        int pad = 6;
        int tooltipW = Math.max(font.width(title), font.width(description)) + pad * 2;
        int tooltipH = font.lineHeight * 2 + pad * 2 + 2;
        int x = Math.min(width - tooltipW - 6, mouseX + 14);
        int y = Math.min(height - tooltipH - 6, mouseY + 14);
        x = Math.max(6, x);
        y = Math.max(6, y);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        MoonSpireUiTextures.drawTooltip(graphics, x, y, tooltipW, tooltipH);
        graphics.drawString(font, title, x + pad, y + pad, 0xFFFFD84D, false);
        graphics.drawString(font, description, x + pad, y + pad + font.lineHeight + 2, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }

    private void drawPileCount(GuiGraphics graphics, String text, float centerX, float centerY) {
        float scale = Math.min(PILE_COUNT_TEXT_SCALE, (PILE_BADGE_SIZE - 4.0F) / Math.max(1.0F, font.width(text)));
        float textX = centerX - font.width(text) * scale / 2.0F + 1.5F;
        float textY = centerY - font.lineHeight * scale / 2.0F + 1.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }

    private void renderWithOcclusionMask(GuiGraphics graphics, int x, int y, int w, int h, List<ScreenRect> masks, Runnable renderer) {
        if (masks.isEmpty()) {
            renderer.run();
            graphics.flush();
            return;
        }
        List<ScreenRect> visibleRects = List.of(new ScreenRect(x, y, x + w, y + h));
        boolean clipped = false;
        for (ScreenRect mask : masks) {
            int before = visibleRects.size();
            visibleRects = subtractMask(visibleRects, mask);
            if (visibleRects.isEmpty()) {
                return;
            }
            clipped |= visibleRects.size() != before || !visibleRects.getFirst().equals(new ScreenRect(x, y, x + w, y + h));
        }
        if (!clipped) {
            renderer.run();
            graphics.flush();
            return;
        }
        for (ScreenRect rect : visibleRects) {
            graphics.enableScissor(rect.left(), rect.top(), rect.right(), rect.bottom());
            renderer.run();
            graphics.flush();
            graphics.disableScissor();
        }
    }

    private List<ScreenRect> subtractMask(List<ScreenRect> rects, ScreenRect mask) {
        List<ScreenRect> result = new ArrayList<>();
        for (ScreenRect rect : rects) {
            result.addAll(subtractMask(rect, mask));
        }
        return result;
    }

    private List<ScreenRect> subtractMask(ScreenRect rect, ScreenRect mask) {
        int left = Math.max(rect.left(), mask.left());
        int top = Math.max(rect.top(), mask.top());
        int right = Math.min(rect.right(), mask.right());
        int bottom = Math.min(rect.bottom(), mask.bottom());
        if (left >= right || top >= bottom) {
            return List.of(rect);
        }
        List<ScreenRect> result = new ArrayList<>(4);
        addRect(result, rect.left(), rect.top(), rect.right(), top);
        addRect(result, rect.left(), bottom, rect.right(), rect.bottom());
        addRect(result, rect.left(), top, left, bottom);
        addRect(result, right, top, rect.right(), bottom);
        return result;
    }

    private void addRect(List<ScreenRect> rects, int left, int top, int right, int bottom) {
        if (right > left && bottom > top) {
            rects.add(new ScreenRect(left, top, right, bottom));
        }
    }

    private void renderEnergy(GuiGraphics graphics, MoonSpireUiRect rect, BattleSnapshot snapshot) {
        CardRenderHelper.renderEnergyCostDisplay(graphics, font, snapshot.player().energyLeft(), snapshot.player().maxEnergy(), rect.x(), rect.y(), rect.width(), rect.height());
    }

    private boolean canEndTurn(BattleSnapshot snapshot) {
        return snapshot.phase() == BattlePhase.PLAYER_TURN
                && !snapshot.localPlayerEndedTurn()
                && !snapshot.localPlayerFakeDead()
                && !endTurnActionLocked(snapshot);
    }

    private boolean endTurnActionLocked(BattleSnapshot snapshot) {
        return endTurnStateLocked(snapshot);
    }

    private boolean endTurnStateLocked(BattleSnapshot snapshot) {
        return snapshot.resolvingEffects()
                || snapshot.pendingHandSelection().active()
                || handSelectionConfirmation.active()
                || awaitingUseCardSnapshot
                || awaitingEndTurnSnapshot;
    }

    private boolean endTurnVisualEnabled(BattleSnapshot snapshot) {
        return snapshot.phase() == BattlePhase.PLAYER_TURN
                && !snapshot.localPlayerEndedTurn()
                && !snapshot.localPlayerFakeDead()
                && !endTurnVisualLocked(snapshot);
    }

    private boolean endTurnVisualLocked(BattleSnapshot snapshot) {
        if (awaitingUseCardSnapshot
                || hasPlayerPlayedCardAnimation()
                || pileOverlay != null
                || snapshot.pendingHandSelection().active()
                || handSelectionConfirmation.active()) {
            return false;
        }
        return snapshot.resolvingEffects()
                || awaitingEndTurnSnapshot;
    }

    private boolean hasPlayerPlayedCardAnimation() {
        for (FlyingCardAnimation animation : flyingCards) {
            if (animation.played() && !animation.done()) {
                return true;
            }
        }
        return false;
    }

    private void beginAwaitingEndTurnSnapshot(BattleSnapshot snapshot) {
        awaitingEndTurnSnapshot = true;
        awaitingEndTurnRound = snapshot.round();
    }

    private void clearAwaitingEndTurnSnapshot() {
        awaitingEndTurnSnapshot = false;
        awaitingEndTurnRound = -1;
    }

    private void reconcileEndTurnAwaiting(BattleSnapshot snapshot) {
        if (!awaitingEndTurnSnapshot) {
            return;
        }
        if (snapshot.phase() != BattlePhase.PLAYER_TURN || snapshot.localPlayerEndedTurn() || snapshot.round() != awaitingEndTurnRound) {
            clearAwaitingEndTurnSnapshot();
        }
    }

    private void renderEndTurnButton(GuiGraphics graphics, BattleSnapshot snapshot, MoonSpireUiRect rect, int mouseX, int mouseY, float partialTick) {
        boolean enabled = canEndTurn(snapshot);
        boolean visualEnabled = enabled || endTurnVisualEnabled(snapshot);
        boolean hasPlayable = enabled && hasPlayableCard(snapshot);
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        boolean highlightedNoPlay = enabled && !hasPlayable;
        if (highlightedNoPlay) {
            float pulse = 0.45F + 0.55F * (float) Math.sin((uiTicks + partialTick) * 0.22F);
            int alpha = 92 + Math.round(80.0F * pulse);
            int glow = (alpha << 24) | 0x0047F5FF;
            graphics.fill(rect.x() - 3, rect.y() - 3, rect.x() + rect.width() + 3, rect.y() + rect.height() + 3, glow);
        }
        MoonSpireUiTextures.drawButton(graphics, rect.x(), rect.y(), rect.width(), rect.height(), hovered, visualEnabled);
        Component label = endTurnButtonLabel(snapshot, visualEnabled);
        int textColor = highlightedNoPlay ? 0xFFFFD84D : hovered ? 0xFFFF5F63 : 0xFFFFFFFF;
        float labelScale = Math.min(1.0F, Math.min((rect.width() - 8.0F) / Math.max(1.0F, font.width(label)), (rect.height() - 4.0F) / Math.max(1.0F, font.lineHeight)));
        CardRenderHelper.drawOutlinedScreenText(graphics, font, label, rect.x() + rect.width() / 2, rect.y() + rect.height() / 2, labelScale, textColor, 0xFF46393B);
    }

    private Component endTurnButtonLabel(BattleSnapshot snapshot, boolean visualEnabled) {
        if (snapshot.localPlayerFakeDead()) {
            return Component.translatable("screen.moonspire.end_turn.dead");
        }
        if (waitingForOtherPlayers(snapshot)) {
            return Component.translatable("screen.moonspire.end_turn.waiting_players");
        }
        if (!visualEnabled) {
            return Component.translatable("screen.moonspire.turn.monster");
        }
        return Component.translatable("screen.moonspire.end_turn", snapshot.round());
    }

    private boolean waitingForOtherPlayers(BattleSnapshot snapshot) {
        if (snapshot.phase() != BattlePhase.PLAYER_TURN || snapshot.localPlayerFakeDead()) {
            return false;
        }
        if (!snapshot.localPlayerEndedTurn() && !awaitingEndTurnSnapshot) {
            return false;
        }
        return hasOtherAliveUnendedPlayer(snapshot);
    }

    private boolean hasOtherAliveUnendedPlayer(BattleSnapshot snapshot) {
        for (BattleCombatantSnapshot player : snapshot.players()) {
            if (player.entityId() != snapshot.localPlayerEntityId() && !player.fakeDead() && !player.endedTurn()) {
                return true;
            }
        }
        return false;
    }

    private void renderHudTooltip(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY) {
        HudTooltip tooltip = hudTooltipAt(snapshot, mouseX, mouseY);
        if (tooltip == null) {
            return;
        }
        renderHudTooltip(graphics, tooltip, mouseX, mouseY);
    }

    private HudTooltip hudTooltipAt(BattleSnapshot snapshot, double mouseX, double mouseY) {
        if (entrySpeedTextRect(snapshot.player(), layout().resolve("player_entry", width, height)).contains(mouseX, mouseY)
                || entrySpeedTextRect(snapshot.monster(), layout().resolve("monster_entry", width, height)).contains(mouseX, mouseY)) {
            return new HudTooltip(
                    Component.translatable("screen.moonspire.speed"),
                    List.of(Component.translatable("screen.moonspire.speed.tooltip")));
        }
        if (layout().resolve("energy", width, height).contains(mouseX, mouseY)) {
            return new HudTooltip(
                    Component.translatable("screen.moonspire.energy"),
                    List.of(Component.translatable("screen.moonspire.energy.tooltip")));
        }
        if (endTurnButtonAt(mouseX, mouseY)) {
            return new HudTooltip(
                    Component.translatable("screen.moonspire.end_turn.title"),
                    List.of(
                            Component.translatable("screen.moonspire.end_turn.tooltip.shortcut", END_TURN_SHORTCUT),
                            Component.translatable("screen.moonspire.end_turn.tooltip.primary"),
                            Component.translatable("screen.moonspire.end_turn.tooltip.flow")));
        }
        MoonSpireUiRect discardPileRect = layout().resolve("discard_pile", width, height);
        if (discardPileRect.contains(mouseX, mouseY)) {
            return new HudTooltip(
                    Component.translatable("screen.moonspire.discard_pile"),
                    List.of(
                            Component.translatable("screen.moonspire.discard_pile.tooltip.primary"),
                            Component.translatable("screen.moonspire.discard_pile.tooltip.click")),
                    discardPileRect);
        }
        MoonSpireUiRect drawPileRect = layout().resolve("draw_pile", width, height);
        if (drawPileRect.contains(mouseX, mouseY)) {
            return new HudTooltip(
                    Component.translatable("screen.moonspire.draw_pile"),
                    List.of(
                            Component.translatable("screen.moonspire.draw_pile.tooltip.primary"),
                            Component.translatable("screen.moonspire.draw_pile.tooltip.click")),
                    drawPileRect);
        }
        return null;
    }

    private MoonSpireUiRect entrySpeedTextRect(BattleCombatantSnapshot entry, MoonSpireUiRect entryRect) {
        Component speed = Component.translatable("screen.moonspire.speed_short", entry.roundSpeed());
        return new MoonSpireUiRect(entryRect.x() + ENTRY_SPEED_X_OFFSET, entryRect.y() + ENTRY_HEADER_Y_OFFSET, font.width(speed), font.lineHeight);
    }

    private void renderHudTooltip(GuiGraphics graphics, HudTooltip tooltip, int mouseX, int mouseY) {
        List<FormattedCharSequence> titleLines = font.split(tooltip.title(), HUD_TIP_WIDTH - HUD_TIP_PAD * 2);
        List<List<FormattedCharSequence>> paragraphs = new ArrayList<>(tooltip.paragraphs().size());
        int contentHeight = HUD_TIP_PAD * 2 + titleLines.size() * 10 + 3;
        for (Component paragraph : tooltip.paragraphs()) {
            List<FormattedCharSequence> lines = font.split(paragraph, HUD_TIP_WIDTH - HUD_TIP_PAD * 2);
            paragraphs.add(lines);
            contentHeight += lines.size() * 10 + HUD_TIP_PARAGRAPH_GAP;
        }
        int tooltipH = contentHeight - HUD_TIP_PARAGRAPH_GAP;
        int x = clampTooltipX(mouseX + 14, HUD_TIP_WIDTH);
        int y = clampTooltipY(mouseY + 14, tooltipH);
        MoonSpireUiRect avoid = tooltip.avoidRect();
        if (avoid != null && overlaps(x, y, HUD_TIP_WIDTH, tooltipH, avoid)) {
            int centeredY = Math.round(avoid.y() + avoid.height() / 2.0F - tooltipH / 2.0F);
            int centeredX = Math.round(avoid.x() + avoid.width() / 2.0F - HUD_TIP_WIDTH / 2.0F);
            int[][] candidates = {
                    {avoid.x() - HUD_TIP_WIDTH - 8, centeredY},
                    {avoid.right() + 8, centeredY},
                    {centeredX, avoid.y() - tooltipH - 8},
                    {centeredX, avoid.bottom() + 8},
                    {mouseX + 14, mouseY - tooltipH - 14}
            };
            for (int[] candidate : candidates) {
                int candidateX = clampTooltipX(candidate[0], HUD_TIP_WIDTH);
                int candidateY = clampTooltipY(candidate[1], tooltipH);
                if (!overlaps(candidateX, candidateY, HUD_TIP_WIDTH, tooltipH, avoid)) {
                    x = candidateX;
                    y = candidateY;
                    break;
                }
            }
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        MoonSpireUiTextures.drawTooltip(graphics, x, y, HUD_TIP_WIDTH, tooltipH);
        int lineY = y + HUD_TIP_PAD;
        for (FormattedCharSequence line : titleLines) {
            graphics.drawString(font, line, x + HUD_TIP_PAD, lineY, 0xFFD49A22, false);
            lineY += 10;
        }
        lineY += 3;
        for (int i = 0; i < paragraphs.size(); i++) {
            for (FormattedCharSequence line : paragraphs.get(i)) {
                graphics.drawString(font, line, x + HUD_TIP_PAD, lineY, 0xFFF2E6D2, false);
                lineY += 10;
            }
            if (i < paragraphs.size() - 1) {
                lineY += HUD_TIP_PARAGRAPH_GAP;
            }
        }
        graphics.pose().popPose();
    }

    private int clampTooltipX(int x, int tooltipW) {
        return Math.max(6, Math.min(width - tooltipW - 6, x));
    }

    private int clampTooltipY(int y, int tooltipH) {
        return Math.max(6, Math.min(height - tooltipH - 6, y));
    }

    private boolean overlaps(int x, int y, int tooltipW, int tooltipH, MoonSpireUiRect rect) {
        return x < rect.right() && x + tooltipW > rect.x() && y < rect.bottom() && y + tooltipH > rect.y();
    }

    private void renderHand(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick) {
        boolean useFrameCache = frameCache.snapshot() == snapshot;
        List<CardInstance> visibleCards = useFrameCache ? frameCache.visibleCards(snapshot) : visibleHandCards(snapshot);
        int count = visibleCards.size();
        if (count <= 0) {
            hoveredHandIndex = -1;
            return;
        }
        long start = perfStart();
        HandLayout layout = useFrameCache ? frameCache.layout(snapshot) : handLayout(visibleCards);
        int selectedIndex = ClientBattleState.selectedHandIndex();
        int hoveredIndex = draggingHandCard() ? -1 : hoveredHandIndex(mouseX, mouseY, snapshot, visibleCards, layout, partialTick);
        if (hoveredIndex >= count) {
            hoveredHandIndex = -1;
            hoveredIndex = -1;
        }
        syncHandAnimationTargets(snapshot, layout, visibleCards, hoveredIndex);
        for (HandCardAnimation animation : handAnimations.values()) {
            animation.advance(currentFrameTicks);
        }
        UUID selectedCardId = selectedHandCardId(snapshot, selectedIndex);
        List<HandRenderEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CardInstance card = visibleCards.get(i);
            if (hoveredIndex == i) {
                continue;
            }
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null) {
                entries.add(new HandRenderEntry(card, animation, card.id().equals(selectedCardId), cardRenderData(snapshot, card, true)));
            }
        }
        renderHandCards(graphics, snapshot, entries, partialTick);
        if (hoveredIndex >= 0) {
            CardInstance card = visibleCards.get(hoveredIndex);
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null) {
                CardRenderData renderData = cardRenderData(snapshot, card, true);
                long hoverStart = perfStart();
                renderHoveredHandCard(graphics, snapshot, card, animation, layout.card(hoveredIndex), partialTick, renderData);
                battleRenderHandHoverNanos += elapsedPerf(hoverStart);
            }
        }
        recordPerf(PerfBucket.HAND_RENDER, start);
    }

    private List<ScreenRect> handOcclusionMasks(BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick) {
        boolean useFrameCache = frameCache.snapshot() == snapshot;
        List<CardInstance> visibleCards = useFrameCache ? frameCache.visibleCards(snapshot) : visibleHandCards(snapshot);
        if (visibleCards.isEmpty()) {
            return List.of();
        }
        HandLayout layout = useFrameCache ? frameCache.layout(snapshot) : handLayout(visibleCards);
        int hoveredIndex = draggingHandCard() ? -1 : hoveredHandIndex(mouseX, mouseY, snapshot, visibleCards, layout, partialTick);
        int selectedIndex = ClientBattleState.selectedHandIndex();
        UUID selectedCardId = selectedHandCardId(snapshot, selectedIndex);
        List<ScreenRect> masks = new ArrayList<>();
        for (int i = 0; i < visibleCards.size(); i++) {
            HandCardAnimation animation = handAnimations.get(visibleCards.get(i).id());
            if (animation == null) {
                HandCardBounds bounds = layout.card(i);
                float halfW = CardRenderHelper.SMALL_CARD_WIDTH * bounds.scale() / 2.0F;
                float halfH = CardRenderHelper.SMALL_CARD_HEIGHT * bounds.scale() / 2.0F;
                masks.add(screenRect(bounds.centerX() - halfW, bounds.centerY() - halfH, bounds.centerX() + halfW, bounds.centerY() + halfH));
                continue;
            }
            boolean selected = visibleCards.get(i).id().equals(selectedCardId);
            masks.add(screenRect(handCardScreenBounds(animation, partialTick, selected)));
        }
        if (hoveredIndex >= 0 && hoveredIndex < visibleCards.size()) {
            HandCardBounds base = layout.card(hoveredIndex);
            CardPreviewBounds preview = previewBounds(base);
            HandCardAnimation animation = handAnimations.get(visibleCards.get(hoveredIndex).id());
            float progress = animation == null ? 1.0F : smoothStep(animation.hover(partialTick));
            float centerX = animation == null ? preview.centerX() : lerp(animation.x(partialTick), preview.centerX(), progress);
            float centerY = animation == null ? preview.centerY() : lerp(animation.y(partialTick), preview.centerY(), progress);
            float baseScale = animation == null ? base.scale() : animation.scale(partialTick);
            float scale = animation == null
                    ? HAND_PREVIEW_SCALE
                    : lerp(baseScale * (CardRenderHelper.SMALL_CARD_WIDTH / (float) CardRenderHelper.CARD_WIDTH), HAND_PREVIEW_SCALE, progress);
            float halfW = CardRenderHelper.CARD_WIDTH * scale / 2.0F;
            float halfH = CardRenderHelper.CARD_HEIGHT * scale / 2.0F;
            masks.add(screenRect(centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH));
        }
        CardInstance dragged = draggedCard(snapshot);
        if (dragged != null) {
            HandCardAnimation animation = handAnimations.get(dragged.id());
            if (dragged.requiresExplicitTarget() && animation != null) {
                masks.add(screenRect(handCardScreenBounds(animation, partialTick, false)));
            } else {
                float halfW = CardRenderHelper.SMALL_CARD_WIDTH * HAND_BASE_SCALE / 2.0F;
                float halfH = CardRenderHelper.SMALL_CARD_HEIGHT * HAND_BASE_SCALE / 2.0F;
                masks.add(screenRect(mouseX - halfW, mouseY - halfH, mouseX + halfW, mouseY + halfH));
            }
        }
        return masks;
    }

    private void pushHandCardPose(GuiGraphics graphics, HandCardAnimation animation, float partialTick, boolean selected) {
        float x = animation.x(partialTick);
        float y = animation.y(partialTick);
        float angle = animation.angle(partialTick);
        float scale = animation.scale(partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        float selectedScale = selected ? 1.08F : 1.0F;
        graphics.pose().scale(scale * selectedScale, scale * selectedScale, 1.0F);
        graphics.pose().translate(-CardRenderHelper.SMALL_CARD_WIDTH / 2.0F, -CardRenderHelper.SMALL_CARD_HEIGHT / 2.0F, 0.0F);
    }

    private boolean renderHandCardBaseAndArt(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, float partialTick, boolean selected, boolean clipCard) {
        return renderHandCardBaseAndArt(graphics, snapshot, card, animation, partialTick, selected, clipCard, null);
    }

    private boolean renderHandCardBaseAndArt(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, float partialTick, boolean selected, boolean clipCard, String contentKey) {
        long poseStart = perfStart();
        pushHandCardPose(graphics, animation, partialTick, selected);
        battleRenderHandPoseNanos += elapsedPerf(poseStart);
        long baseStart = perfStart();
        boolean playable = playable(card, snapshot);
        renderSmallPlayableGlow(graphics, playable, 0.0F, 0.0F, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        if (clipCard) {
            CardRenderHelper.enablePoseScissor(graphics, 0, 0, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        }
        boolean itemArtRendered = CardRenderHelper.renderSmallCardBaseAndArt(graphics, card, 0, 0, false, contentKey);
        if (clipCard) {
            graphics.disableScissor();
        }
        renderSmallPlayableOutline(graphics, playable, 0.0F, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        graphics.pose().popPose();
        battleRenderHandBaseNanos += elapsedPerf(baseStart);
        return itemArtRendered;
    }

    private void renderHandCardTextUnclipped(GuiGraphics graphics, CardInstance card, HandCardAnimation animation, float partialTick, boolean selected, boolean unaffordable, CardRenderHelper.CardValues values, boolean showDescription) {
        renderHandCardTextUnclipped(graphics, card, animation, partialTick, selected, unaffordable, values, CardRenderHelper.SmallCardTextVisibility.all(showDescription));
    }

    private void renderHandCardTextUnclipped(GuiGraphics graphics, CardInstance card, HandCardAnimation animation, float partialTick, boolean selected, boolean unaffordable, CardRenderHelper.CardValues values, CardRenderHelper.SmallCardTextVisibility visibility) {
        renderHandCardTextUnclipped(graphics, card, animation, partialTick, selected, unaffordable, values, visibility, null);
    }

    private void renderHandCardTextUnclipped(GuiGraphics graphics, CardInstance card, HandCardAnimation animation, float partialTick, boolean selected, boolean unaffordable, CardRenderHelper.CardValues values, CardRenderHelper.SmallCardTextVisibility visibility, String contentKey) {
        long poseStart = perfStart();
        pushHandCardPose(graphics, animation, partialTick, selected);
        battleRenderHandPoseNanos += elapsedPerf(poseStart);
        CardRenderHelper.renderSmallCardText(graphics, font, card, 0, 0, unaffordable, values, visibility, contentKey);
        graphics.pose().popPose();
    }

    private void renderHandCards(GuiGraphics graphics, BattleSnapshot snapshot, List<HandRenderEntry> entries, float partialTick) {
        if (entries.isEmpty()) {
            return;
        }
        List<HandRenderEntry> visibleEntries = visibleHandEntries(entries, partialTick);
        if (visibleEntries.isEmpty()) {
            return;
        }
        for (int i = 0; i < visibleEntries.size(); i++) {
            HandRenderEntry entry = visibleEntries.get(i);
            boolean itemArtRendered = renderHandCardBaseAndArt(graphics, snapshot, entry.card(), entry.animation(), partialTick, entry.selected(), true, entry.renderData().contentKey());
            if (itemArtRendered) {
                long depthStart = perfStart();
                CardRenderHelper.clearBatchedItemArtDepth(graphics);
                battleRenderHandDepthNanos += elapsedPerf(depthStart);
            }
            renderHandCardTextEntry(graphics, snapshot, entry, partialTick);
            long flushStart = perfStart();
            graphics.flush();
            battleRenderHandBaseFlushNanos += elapsedPerf(flushStart);
        }
    }

    private List<HandRenderEntry> visibleHandEntries(List<HandRenderEntry> entries, float partialTick) {
        List<HandRenderEntry> visibleEntries = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            HandRenderEntry entry = entries.get(i);
            HandCardScreenBounds cardBounds = handCardScreenBounds(entry.animation(), partialTick, entry.selected());
            if (outsideScreen(cardBounds)) {
                battleRenderHandCardOffscreenSkips++;
            } else {
                visibleEntries.add(entry);
            }
        }
        return visibleEntries;
    }

    private void renderHandCardTextEntries(GuiGraphics graphics, BattleSnapshot snapshot, List<HandRenderEntry> visibleEntries, float partialTick) {
        for (int i = 0; i < visibleEntries.size(); i++) {
            renderHandCardTextEntry(graphics, snapshot, visibleEntries.get(i), partialTick);
        }
    }

    private void renderHandCardTextEntry(GuiGraphics graphics, BattleSnapshot snapshot, HandRenderEntry entry, float partialTick) {
        long textStart = perfStart();
        CardRenderHelper.CardValues values = entry.renderData().values();
        boolean unaffordable = entry.card().cost() > snapshot.player().energyLeft();
        String contentKey = entry.renderData().contentKey();
        long visibilityStart = perfStart();
        boolean showDescription = handDescriptionVisible(entry, values, contentKey, partialTick);
        battleRenderHandTextVisibilityNanos += elapsedPerf(visibilityStart);
        CardRenderHelper.SmallCardTextVisibility visibility = CardRenderHelper.SmallCardTextVisibility.all(showDescription);
        long submitStart = perfStart();
        renderHandCardTextUnclipped(graphics, entry.card(), entry.animation(), partialTick, entry.selected(), unaffordable, values, visibility, contentKey);
        battleRenderHandTextSubmitNanos += elapsedPerf(submitStart);
        battleRenderHandTextNanos += elapsedPerf(textStart);
    }

    private boolean handDescriptionVisible(HandRenderEntry entry, CardRenderHelper.CardValues values, float partialTick) {
        return handDescriptionVisible(entry, values, null, partialTick);
    }

    private boolean handDescriptionVisible(HandRenderEntry entry, CardRenderHelper.CardValues values, String contentKey, float partialTick) {
        CardRenderHelper.CardLocalArea descArea = CardRenderHelper.smallDescriptionArea(entry.card());
        HandCardScreenBounds descBounds = handAreaBounds(entry, descArea, partialTick);
        if (outsideScreen(descBounds)) {
            battleRenderHandDescOffscreenSkips++;
            return false;
        }
        if (!insideScreen(descBounds)) {
            HandCardScreenBounds textBounds = handAreaBounds(entry, CardRenderHelper.smallDescriptionTextArea(font, entry.card(), values, contentKey), partialTick);
            if (outsideScreen(textBounds)) {
                battleRenderHandDescOffscreenSkips++;
                return false;
            }
        }
        return true;
    }

    private HandCardScreenBounds handAreaBounds(HandRenderEntry entry, CardRenderHelper.CardLocalArea area, float partialTick) {
        if (area.width() <= 0 || area.height() <= 0) {
            return HandCardScreenBounds.empty();
        }
        return handLocalAreaScreenBounds(entry.animation(), partialTick, entry.selected(), area.x(), area.y(), area.width(), area.height());
    }

    private boolean outsideScreen(HandCardScreenBounds bounds) {
        if (bounds.isEmpty()) {
            return true;
        }
        return bounds.right() <= 0.0F || bounds.left() >= width || bounds.bottom() <= 0.0F || bounds.top() >= height;
    }

    private boolean insideScreen(HandCardScreenBounds bounds) {
        return bounds.left() >= 0.0F && bounds.right() <= width && bounds.top() >= 0.0F && bounds.bottom() <= height;
    }

    private void renderHandCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, float partialTick, boolean selected) {
        CardRenderData renderData = cardRenderData(snapshot, card, true);
        boolean itemArtRendered = renderHandCardBaseAndArt(graphics, snapshot, card, animation, partialTick, selected, true, renderData.contentKey());
        if (itemArtRendered) {
            long depthStart = perfStart();
            CardRenderHelper.clearBatchedItemArtDepth(graphics);
            battleRenderHandDepthNanos += elapsedPerf(depthStart);
        }
        long textStart = perfStart();
        renderHandCardTextUnclipped(graphics, card, animation, partialTick, selected, card.cost() > snapshot.player().energyLeft(), renderData.values(), CardRenderHelper.SmallCardTextVisibility.all(true), renderData.contentKey());
        battleRenderHandTextNanos += elapsedPerf(textStart);
        long flushStart = perfStart();
        graphics.flush();
        battleRenderHandBaseFlushNanos += elapsedPerf(flushStart);
    }

    private void renderHoveredHandCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, HandCardBounds baseBounds, float partialTick) {
        renderHoveredHandCard(graphics, snapshot, card, animation, baseBounds, partialTick, cardRenderData(snapshot, card));
    }

    private void renderHoveredHandCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, HandCardBounds baseBounds, float partialTick, CardRenderData renderData) {
        long start = perfStart();
        boolean unaffordable = card.cost() > snapshot.player().energyLeft();
        float progress = smoothStep(animation.hover(partialTick));
        CardPreviewBounds preview = previewBounds(baseBounds);
        float baseScale = animation.scale(partialTick);
        float previewScale = handPreviewScale();
        float centerX = lerp(animation.x(partialTick), preview.centerX(), progress);
        float centerY = lerp(animation.y(partialTick), preview.centerY(), progress);
        float scale = lerp(baseScale, previewScale, progress);
        float angle = lerp(animation.angle(partialTick), 0.0F, progress);
        renderSmallPreviewPlayableGlow(graphics, playable(card, snapshot), centerX, centerY, scale, 0.0F);
        renderScaledSmallCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, true, renderData);
        renderSmallPreviewPlayableOutline(graphics, playable(card, snapshot), centerX, centerY, scale, 0.0F);
        if (progress > 0.88F) {
            CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, preview.x(), preview.y(), preview.width(), preview.height(), width, height);
        }
        recordPerf(PerfBucket.HOVER_PREVIEW, start);
    }

    private void renderDraggedDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, boolean playableHere) {
        long start = perfStart();
        DragGlow dragGlow = dragGlowFor(card, playableHere);
        boolean basePlayable = playable(card, snapshot);
        renderDetailedPlayableGlow(graphics, basePlayable, centerX, centerY, scale, dragGlow.pulse());
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, 0.0F, card.cost() > snapshot.player().energyLeft(), false, cardRenderData(snapshot, card, true));
        renderDetailedPlayableOutline(graphics, basePlayable, centerX, centerY, scale, dragGlow.pulse());
        recordPerf(PerfBucket.TARGET_CARD, start);
    }

    private void renderDraggedSmallCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, boolean playableHere) {
        long start = perfStart();
        DragGlow dragGlow = dragGlowFor(card, playableHere);
        boolean basePlayable = playable(card, snapshot);
        boolean unaffordable = card.cost() > snapshot.player().energyLeft();
        CardRenderData renderData = cardRenderData(snapshot, card, true);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 80.0F);
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-CardRenderHelper.SMALL_CARD_WIDTH / 2.0F, -CardRenderHelper.SMALL_CARD_HEIGHT / 2.0F, 0.0F);
        renderSmallPlayableGlow(graphics, basePlayable, dragGlow.pulse(), 0.0F, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        CardRenderHelper.enablePoseScissor(graphics, 0, 0, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        CardRenderHelper.renderSmallCard(graphics, font, card, 0, 0, false, unaffordable, renderData.values(), false, true, renderData.contentKey());
        graphics.disableScissor();
        renderSmallPlayableOutline(graphics, basePlayable, dragGlow.pulse(), CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        graphics.pose().popPose();
        recordPerf(PerfBucket.TARGET_CARD, start);
    }

    private void renderTargetingHandCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, float partialTick, boolean playableHere) {
        long start = perfStart();
        boolean unaffordable = card.cost() > snapshot.player().energyLeft();
        CardRenderData renderData = cardRenderData(snapshot, card, true);
        float x = animation.x(partialTick);
        float y = animation.y(partialTick);
        float scale = animation.scale(partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 80.0F);
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-CardRenderHelper.SMALL_CARD_WIDTH / 2.0F, -CardRenderHelper.SMALL_CARD_HEIGHT / 2.0F, 0.0F);
        DragGlow dragGlow = dragGlowFor(card, playableHere);
        renderSmallPlayableGlow(graphics, playable(card, snapshot), dragGlow.pulse(), 0.0F, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        CardRenderHelper.enablePoseScissor(graphics, 0, 0, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        CardRenderHelper.renderSmallCard(graphics, font, card, 0, 0, false, unaffordable, renderData.values(), false, true, renderData.contentKey());
        graphics.disableScissor();
        renderSmallPlayableOutline(graphics, playable(card, snapshot), dragGlow.pulse(), CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        graphics.pose().popPose();
        recordPerf(PerfBucket.TARGET_CARD, start);
    }

    private void renderPileOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (pileOverlay == null) {
            return;
        }
        long start = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        long updateStart = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        updatePileOverlayCards(snapshot);
        long updateNanos = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() - updateStart : 0L;
        pileOverlay.render(graphics, font, width, height, mouseX, mouseY, CARD_GRID_BOTTOM_RESERVE, card -> false, CardRenderHelper.CardValues::original,
                (previewGraphics, previewFont, card, x, y, selected, contentKey) -> {
                    previewGraphics.pose().pushPose();
                    previewGraphics.pose().translate(x, y, 0.0F);
                    CardRenderHelper.renderCard(previewGraphics, previewFont, card, 0, 0, false, CardRenderHelper.CardValues.original(card), false, false, contentKey);
                    previewGraphics.pose().popPose();
                });
        if (MoonSpirePerfDiagnostics.enabled() && pileOverlayPerfFrameIndex < 10) {
            pileOverlayPerfFrameIndex++;
            long elapsed = MoonSpirePerfDiagnostics.now() - start;
            MoonSpirePerfDiagnostics.markOperation("client.battle.pileRender", elapsed,
                    "frameIndex=" + pileOverlayPerfFrameIndex
                            + " battleId=" + snapshot.battleId()
                            + " sequence=" + snapshot.sequence()
                            + " source=" + pileOverlaySource
                            + " deckVersion=" + snapshot.localDeckVersion()
                            + " updateMs=" + MoonSpirePerfDiagnostics.millis(updateNanos)
                            + " " + pileOverlay.lastFrameStats().summary()
                            + " " + CardRenderHelper.frameStats().summary());
        }
        if (pileOverlay.shouldLogScrollDiagnostics()) {
            long elapsed = MoonSpirePerfDiagnostics.now() - start;
            MoonSpirePerfDiagnostics.log("client.battle.pileScrollRender",
                    "durationMs=" + MoonSpirePerfDiagnostics.millis(elapsed)
                            + " frameIndex=" + pileOverlayPerfFrameIndex
                            + " battleId=" + snapshot.battleId()
                            + " sequence=" + snapshot.sequence()
                            + " source=" + pileOverlaySource
                            + " deckVersion=" + snapshot.localDeckVersion()
                            + " updateMs=" + MoonSpirePerfDiagnostics.millis(updateNanos)
                            + " " + pileOverlay.lastFrameStats().summary()
                            + " " + CardRenderHelper.frameStats().summary());
            pileOverlay.markScrollDiagnosticsLogged();
        }
    }

    private float animationFrameTicks() {
        long now = System.nanoTime();
        if (lastAnimationNanos == 0L) {
            lastAnimationNanos = now;
            return 1.0F;
        }
        float deltaTicks = (now - lastAnimationNanos) / 50_000_000.0F;
        lastAnimationNanos = now;
        if (!Float.isFinite(deltaTicks) || deltaTicks <= 0.0F) {
            return 0.25F;
        }
        return clamp(deltaTicks, 0.05F, 1.5F);
    }

    private void renderFlyingCards(GuiGraphics graphics, BattleSnapshot snapshot, float partialTick, boolean suppressPlayedCards) {
        Iterator<FlyingCardAnimation> iterator = flyingCards.iterator();
        while (iterator.hasNext()) {
            FlyingCardAnimation animation = iterator.next();
            animation.advance(currentFrameTicks);
            if (animation.played() && !animation.released() && !snapshot.resolvingEffects() && !awaitingUseCardSnapshot && animation.readyToRelease()) {
                animation.release();
            }
            if (animation.done()) {
                iterator.remove();
                continue;
            }
            if (suppressPlayedCards && animation.played()) {
                continue;
            }
            float alpha = animation.alpha();
            if (alpha <= 0.03F) {
                continue;
            }
            boolean unaffordable = !animation.played() && animation.card().cost() > snapshot.player().energyLeft();
            if (animation.showPlayableGlow()) {
                renderDetailedPlayableGlow(graphics, animation.x(), animation.y(), animation.scale());
            }
            renderScaledDetailedCard(graphics, snapshot, animation.card(), animation.x(), animation.y(), animation.scale(), 0.0F, unaffordable, true, alpha);
            if (animation.showPlayableGlow()) {
                renderDetailedPlayableOutline(graphics, animation.x(), animation.y(), animation.scale());
            }
        }
    }

    private void renderMonsterPlayedCard(GuiGraphics graphics, BattleSnapshot snapshot) {
        renderMonsterPlayedCard(graphics, snapshot, false);
    }

    private void renderMonsterPlayedCard(GuiGraphics graphics, BattleSnapshot snapshot, boolean modalBackground) {
        long sequence = ClientBattleState.monsterPlayedCardEventSequence();
        if (renderedMonsterPlayedCardSequence == sequence) {
            ClientBattleState.advanceMonsterPlayedCard(currentFrameTicks, snapshot.resolvingEffects());
        } else {
            renderedMonsterPlayedCardSequence = sequence;
        }
        CardInstance card = ClientBattleState.monsterPlayedCard();
        if (card == null) {
            renderedMonsterPlayedCardSequence = -1L;
            return;
        }
        if (modalBackground) {
            return;
        }
        MoonSpireUiRect rect = layout().resolve("monster_intent", width, height);
        float halfW = CardRenderHelper.CARD_WIDTH * MONSTER_PLAYED_CARD_SCALE / 2.0F;
        float halfH = CardRenderHelper.CARD_HEIGHT * MONSTER_PLAYED_CARD_SCALE / 2.0F;
        float minX = halfW + 4.0F;
        float maxX = width - halfW - 4.0F;
        float minY = halfH + 4.0F;
        float maxY = height - halfH - 4.0F;
        float centerX = minX <= maxX ? clamp(rect.x() + rect.width() / 2.0F, minX, maxX) : width / 2.0F;
        float centerY = minY <= maxY ? clamp(rect.y() + rect.height() / 2.0F, minY, maxY) : height / 2.0F;
        BattleCombatantSnapshot attacker = snapshot.combatant(ClientBattleState.monsterPlayedCardAttackerId());
        boolean attackerIsMonster = attacker == null || snapshot.isEnemyEntity(attacker.entityId());
        CardRenderHelper.CardValues values = cardValues(snapshot, card, attacker == null ? snapshot.monster() : attacker, attackerIsMonster);
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, MONSTER_PLAYED_CARD_SCALE, 0.0F, false, true, ClientBattleState.monsterPlayedCardAlpha(), values);
    }

    private void renderHandSelectionOverlay(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick) {
        PendingHandSelectionSnapshot selection = snapshot.pendingHandSelection();
        if (!selection.active()) {
            handSelectionOverlay.clearIfInactive();
            return;
        }
        handSelectionOverlay.sync(selection, snapshot);
        MoonSpireModalLayer.drawTopmostOverlay(graphics, width, height);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, MODAL_CONTENT_Z);
        try {
            renderHandSelectionCards(graphics, snapshot, mouseX, mouseY, partialTick, false);
            Component title = handSelectionOverlay.ready(selection)
                    ? Component.translatable(handSelectionConfirmKey(selection.action()))
                    : Component.translatable(handSelectionChooseKey(selection.action()), selection.requiredCount());
            CardRenderHelper.drawOutlinedScreenText(graphics, font, title, width / 2, Math.max(34, height / 5), 1.45F, 0xFFFFFFFF, 0xFF101010);
            ButtonRect confirm = handSelectionConfirmButton();
            boolean ready = handSelectionOverlay.ready(selection);
            boolean hovered = ready && confirm.contains(mouseX, mouseY) && !handSelectionOverlay.confirming();
            MoonSpireUiTextures.drawButton(graphics, confirm.x(), confirm.y(), confirm.w(), confirm.h(), hovered, ready && !handSelectionOverlay.confirming());
            Component label = Component.translatable("screen.moonspire.hand_selection.confirm_button");
            CardRenderHelper.drawOutlinedScreenText(graphics, font, label, confirm.x() + confirm.w() / 2, confirm.y() + confirm.h() / 2, 1.0F, ready && !handSelectionOverlay.confirming() ? 0xFFFFFFFF : 0xFF8E989A, 0xFF313638);
            renderHandSelectionHoveredPreview(graphics, snapshot, mouseX, mouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderHandSelectionCards(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick, boolean drawHoveredPreview) {
        PendingHandSelectionSnapshot selection = snapshot.pendingHandSelection();
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        HandLayout visibleLayout = handLayout(visibleCards);
        int hoveredIndex = handSelectionHoveredCardIndex(mouseX, mouseY, snapshot, partialTick);
        UUID hoveredCardId = hoveredIndex >= 0 ? snapshot.hand().get(hoveredIndex).id() : null;
        int hoveredVisibleIndex = hoveredCardId == null ? -1 : visibleCardsIndex(visibleCards, hoveredCardId);
        syncHandAnimationTargets(snapshot, visibleLayout, visibleCards, hoveredVisibleIndex);
        for (HandCardAnimation animation : handAnimations.values()) {
            animation.advance(currentFrameTicks);
        }
        for (int i = 0; i < visibleCards.size(); i++) {
            CardInstance card = visibleCards.get(i);
            if (card.id().equals(hoveredCardId)) {
                continue;
            }
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null) {
                renderHandCard(graphics, snapshot, card, animation, partialTick, false);
            }
        }
        for (UUID selectedId : handSelectionOverlay.selectedIds()) {
            if (selectedId.equals(hoveredCardId)) {
                continue;
            }
            CardInstance card = cardById(snapshot.hand(), selectedId);
            if (card == null) {
                continue;
            }
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null) {
                renderHandCard(graphics, snapshot, card, animation, partialTick, true);
            }
        }
        if (drawHoveredPreview && hoveredIndex >= 0) {
            CardInstance card = snapshot.hand().get(hoveredIndex);
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null) {
                renderHandSelectionHoveredCard(graphics, snapshot, hoveredIndex, card, partialTick);
            }
        }
    }

    private void renderHandSelectionHoveredPreview(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick) {
        int hoveredIndex = handSelectionHoveredCardIndex(mouseX, mouseY, snapshot, partialTick);
        if (hoveredIndex < 0) {
            return;
        }
        CardInstance card = snapshot.hand().get(hoveredIndex);
        HandCardAnimation animation = handAnimations.get(card.id());
        if (animation != null) {
            renderHandSelectionHoveredCard(graphics, snapshot, hoveredIndex, card, partialTick);
        }
    }

    private int handSelectionHoveredCardIndex(double mouseX, double mouseY, BattleSnapshot snapshot, float partialTick) {
        if (dragState != null || handSelectionOverlay.confirming()) {
            return -1;
        }
        int directHover = handSelectionCardIndexAt(mouseX, mouseY, snapshot, partialTick);
        if (hoveredHandIndex >= 0 && hoveredHandIndex < snapshot.hand().size()) {
            CardInstance hoveredCard = snapshot.hand().get(hoveredHandIndex);
            boolean currentSelectable = snapshot.pendingHandSelection().candidateCardIds().contains(hoveredCard.id()) || handSelectionOverlay.isSelected(hoveredCard.id());
            boolean currentSticky = currentSelectable && handSelectionStickyAt(mouseX, mouseY, snapshot, hoveredHandIndex, partialTick);
            if (directHover >= 0 && directHover != hoveredHandIndex && isSelectableHandSelectionCard(snapshot, directHover)) {
                if (!currentSticky) {
                    hoveredHandIndex = directHover;
                    return directHover;
                }
            }
            if (currentSticky) {
                return hoveredHandIndex;
            }
        }
        if (directHover >= 0 && directHover != hoveredHandIndex && isSelectableHandSelectionCard(snapshot, directHover)) {
            hoveredHandIndex = directHover;
            return directHover;
        }
        hoveredHandIndex = -1;
        return -1;
    }

    private boolean isSelectableHandSelectionCard(BattleSnapshot snapshot, int index) {
        if (index < 0 || index >= snapshot.hand().size()) {
            return false;
        }
        CardInstance card = snapshot.hand().get(index);
        return snapshot.pendingHandSelection().candidateCardIds().contains(card.id()) || handSelectionOverlay.isSelected(card.id());
    }

    private boolean handSelectionStickyAt(double mouseX, double mouseY, BattleSnapshot snapshot, int index, float partialTick) {
        return handSelectionPreviewBounds(snapshot, index, partialTick).contains(mouseX, mouseY);
    }

    private void renderHandSelectionHoveredCard(GuiGraphics graphics, BattleSnapshot snapshot, int handIndex, CardInstance card, float partialTick) {
        boolean unaffordable = card.cost() > snapshot.player().energyLeft();
        float previewScale = handSelectionPreviewScale();
        CardPreviewBounds bounds = handSelectionPreviewBounds(snapshot, handIndex, partialTick);
        float centerX = bounds.centerX();
        float centerY = bounds.centerY();
        renderSmallPreviewPlayableGlow(graphics, playable(card, snapshot), centerX, centerY, previewScale, 0.0F);
        renderScaledSmallCard(graphics, snapshot, card, centerX, centerY, previewScale, 0.0F, unaffordable, true);
        renderSmallPreviewPlayableOutline(graphics, playable(card, snapshot), centerX, centerY, previewScale, 0.0F);
        CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, bounds.x(), bounds.y(), bounds.width(), bounds.height(), width, height);
    }

    private float clampPreviewCenterX(float centerX, float scale) {
        float halfW = CardRenderHelper.CARD_WIDTH * scale / 2.0F;
        float minX = halfW + 8.0F;
        float maxX = width - halfW - 8.0F;
        return minX <= maxX ? clamp(centerX, minX, maxX) : width / 2.0F;
    }

    private float clampPreviewCenterY(float centerY, float scale) {
        float halfH = CardRenderHelper.CARD_HEIGHT * scale / 2.0F;
        float minY = halfH + 48.0F;
        float maxY = height - halfH - 8.0F;
        return minY <= maxY ? clamp(centerY, minY, maxY) : height / 2.0F;
    }

    private float clampSmallPreviewCenterX(float centerX, float scale) {
        float halfW = CardRenderHelper.SMALL_CARD_WIDTH * scale / 2.0F;
        float minX = halfW + 8.0F;
        float maxX = width - halfW - 8.0F;
        return minX <= maxX ? clamp(centerX, minX, maxX) : width / 2.0F;
    }

    private float clampSmallPreviewCenterY(float centerY, float scale) {
        float halfH = CardRenderHelper.SMALL_CARD_HEIGHT * scale / 2.0F;
        float minY = halfH + 48.0F;
        float maxY = height - halfH - 8.0F;
        return minY <= maxY ? clamp(centerY, minY, maxY) : height / 2.0F;
    }

    private boolean clickHandSelectionOverlay(double mouseX, double mouseY, int button, BattleSnapshot snapshot) {
        dragState = null;
        rotatingCamera = false;
        pendingTargetClickId = -1;
        pileOverlay = null;
        pileOverlaySource = PileOverlaySource.NONE;
        pileOverlayEntityId = -1;
        if (button != 0) {
            return true;
        }
        PendingHandSelectionSnapshot selection = snapshot.pendingHandSelection();
        handSelectionOverlay.sync(selection, snapshot);
        if (handSelectionOverlay.confirming()) {
            return true;
        }
        ButtonRect confirm = handSelectionConfirmButton();
        if (confirm.contains(mouseX, mouseY)) {
            if (handSelectionOverlay.ready(selection)) {
                handSelectionOverlay.confirm();
                List<UUID> selectedIds = handSelectionOverlay.selectedIds();
                handSelectionConfirmation = HandSelectionConfirmation.of(selection, selectedIds);
                for (UUID cardId : selectedIds) {
                    CardInstance card = cardById(snapshot.hand(), cardId);
                    HandCardAnimation animation = handAnimations.get(cardId);
                    if (card != null && animation != null) {
                        if (selection.action() == PendingHandSelectionSnapshot.Action.EXHAUST || selection.action() == PendingHandSelectionSnapshot.Action.CONSUME_ARROW) {
                            flyingCards.add(FlyingCardAnimation.exhaustInPlace(card, animation.currentX(), animation.currentY(), animation.currentScale()));
                        } else {
                            flyingCards.add(FlyingCardAnimation.toDiscard(card, animation.currentX(), animation.currentY(), discardPileCenterX(), discardPileCenterY()));
                        }
                    }
                }
                handSelectionOverlay.clearConfirmed();
                PacketDistributor.sendToServer(new SelectHandCardsPayload(selectedIds));
            }
            return true;
        }
        int index = handSelectionCardIndexAt(mouseX, mouseY, snapshot, 0.0F);
        if (index >= 0) {
            CardInstance card = snapshot.hand().get(index);
            if (selection.candidateCardIds().contains(card.id())) {
                handSelectionOverlay.toggle(card.id(), selection.requiredCount());
            }
        }
        return true;
    }

    private void renderTurnBanner(GuiGraphics graphics, float partialTick) {
        if (turnBannerTicks <= 0) {
            return;
        }
        float raw = (turnBannerTicks - partialTick) / (float) TURN_BANNER_TICKS;
        float fade = raw > 0.72F ? (1.0F - raw) / 0.28F : Math.min(1.0F, raw / 0.22F);
        fade = clamp(fade, 0.0F, 1.0F);
        int bannerW = 188;
        int bannerH = 34;
        int x = width / 2 - bannerW / 2;
        int y = Math.max(42, height / 4 - bannerH / 2);
        int alpha = Math.round(178.0F * fade);
        int textAlpha = Math.round(255.0F * fade);
        graphics.fill(x, y, x + bannerW, y + bannerH, (alpha << 24) | 0x00241916);
        graphics.renderOutline(x, y, bannerW, bannerH, (textAlpha << 24) | 0x00FFD166);
        graphics.drawCenteredString(font, phaseComponent(turnBannerPhase), width / 2, y + 12, (textAlpha << 24) | 0x00FFF0C2);
    }

    private void renderDraggedCard(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick) {
        CardInstance card = draggedCard(snapshot);
        if (card == null) {
            return;
        }
        if (card.requiresExplicitTarget()) {
            int pointed = frameCache.targetEntity(mouseX, mouseY, snapshot);
            boolean canPlayHere = playableDraggedCardAt(card, snapshot, mouseX, mouseY);
            MoonSpireUiRect targetRect = targetRectForCard(card, pointed, snapshot);
            HandCardAnimation animation = handAnimations.get(card.id());
            float startX = animation != null ? animation.x(partialTick) : mouseX;
            float startY = animation != null ? animation.y(partialTick) - CardRenderHelper.SMALL_CARD_HEIGHT * animation.scale(partialTick) * 0.38F : mouseY;
            int color = pointed == -1 ? 0x99FFD166 : 0xDDA8F7FF;
            long aimStart = perfStart();
            drawAimLine(graphics, startX, startY, mouseX, mouseY, color);
            recordPerf(PerfBucket.AIM_LINE, aimStart);
            if (animation != null) {
                renderTargetingHandCard(graphics, snapshot, card, animation, partialTick, canPlayHere);
            }
            if (pointed != -1 && !targetRect.contains(mouseX, mouseY)) {
                graphics.renderOutline(targetRect.x() - 1, targetRect.y() - 1, targetRect.width() + 2, targetRect.height() + 2, 0xDDA8F7FF);
            }
            return;
        }
        boolean playableHere = playableDraggedCardAt(card, snapshot, mouseX, mouseY);
        renderDraggedSmallCard(graphics, snapshot, card, mouseX, mouseY, HAND_BASE_SCALE, playableHere);
    }

    private MoonSpireUiRect targetRectForCard(CardInstance card, int targetEntityId, BattleSnapshot snapshot) {
        if (targetEntityId == snapshot.player().entityId()) {
            return layout().resolve("player_entry", width, height);
        }
        return layout().resolve("monster_entry", width, height);
    }

    private long perfStart() {
        return MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
    }

    private void recordPerf(PerfBucket bucket, long startNanos) {
        if (!MoonSpirePerfDiagnostics.enabled() || startNanos == 0L) {
            return;
        }
        long elapsed = MoonSpirePerfDiagnostics.now() - startNanos;
        if (bucket == PerfBucket.SNAPSHOT_SYNC) {
            battleRenderSnapshotNanos += elapsed;
        }
    }

    private long elapsedPerf(long startNanos) {
        return MoonSpirePerfDiagnostics.enabled() && startNanos != 0L ? MoonSpirePerfDiagnostics.now() - startNanos : 0L;
    }

    private void beginBattleRenderPerf() {
        if (!MoonSpirePerfDiagnostics.enabled()) {
            return;
        }
        battleRenderPerfStart = MoonSpirePerfDiagnostics.now();
        battleRenderSnapshotNanos = 0L;
        battleRenderEntriesNanos = 0L;
        battleRenderIntentNanos = 0L;
        battleRenderBottomNanos = 0L;
        battleRenderBottomEnergyNanos = 0L;
        battleRenderBottomMasksNanos = 0L;
        battleRenderBottomPilesNanos = 0L;
        battleRenderBottomEndTurnNanos = 0L;
        battleRenderBottomFlushNanos = 0L;
        battleRenderBottomHandNanos = 0L;
        battleRenderHandBaseNanos = 0L;
        battleRenderHandBaseFlushNanos = 0L;
        battleRenderHandPoseNanos = 0L;
        battleRenderHandDepthNanos = 0L;
        battleRenderHandTextNanos = 0L;
        battleRenderHandContentKeyNanos = 0L;
        battleRenderHandValuesNanos = 0L;
        battleRenderHandTextVisibilityNanos = 0L;
        battleRenderHandTextSubmitNanos = 0L;
        battleRenderHandHoverNanos = 0L;
        battleRenderHandCardOffscreenSkips = 0;
        battleRenderHandDescOffscreenSkips = 0;
        battleRenderFlyingNanos = 0L;
        battleRenderDraggedNanos = 0L;
        battleRenderMonsterPlayedNanos = 0L;
        battleRenderOtherNanos = 0L;
    }

    private void finishBattleRenderPerf(BattleSnapshot snapshot) {
        if (!MoonSpirePerfDiagnostics.enabled() || battleRenderPerfStart == 0L) {
            return;
        }
        long elapsed = MoonSpirePerfDiagnostics.now() - battleRenderPerfStart;
        MoonSpirePerfDiagnostics.markOperation("client.battle.render", elapsed,
                "frameIndex=" + frameIndex
                        + " battleId=" + snapshot.battleId()
                        + " sequence=" + snapshot.sequence()
                        + " hand=" + snapshot.hand().size()
                        + " snapshotMs=" + MoonSpirePerfDiagnostics.millis(battleRenderSnapshotNanos)
                        + " entriesMs=" + MoonSpirePerfDiagnostics.millis(battleRenderEntriesNanos)
                        + " intentMs=" + MoonSpirePerfDiagnostics.millis(battleRenderIntentNanos)
                        + " bottomMs=" + MoonSpirePerfDiagnostics.millis(battleRenderBottomNanos)
                        + " bottomEnergyMs=" + MoonSpirePerfDiagnostics.millis(battleRenderBottomEnergyNanos)
                        + " bottomMasksMs=" + MoonSpirePerfDiagnostics.millis(battleRenderBottomMasksNanos)
                        + " bottomPilesMs=" + MoonSpirePerfDiagnostics.millis(battleRenderBottomPilesNanos)
                        + " bottomEndTurnMs=" + MoonSpirePerfDiagnostics.millis(battleRenderBottomEndTurnNanos)
                        + " bottomFlushMs=" + MoonSpirePerfDiagnostics.millis(battleRenderBottomFlushNanos)
                        + " bottomHandMs=" + MoonSpirePerfDiagnostics.millis(battleRenderBottomHandNanos)
                        + " handBaseMs=" + MoonSpirePerfDiagnostics.millis(battleRenderHandBaseNanos)
                        + " handBaseFlushMs=" + MoonSpirePerfDiagnostics.millis(battleRenderHandBaseFlushNanos)
                        + " handPoseMs=" + MoonSpirePerfDiagnostics.millis(battleRenderHandPoseNanos)
                        + " handDepthMs=" + MoonSpirePerfDiagnostics.millis(battleRenderHandDepthNanos)
                        + " handTextMs=" + MoonSpirePerfDiagnostics.millis(battleRenderHandTextNanos)
                        + " handContentKeyMs=" + MoonSpirePerfDiagnostics.millis(battleRenderHandContentKeyNanos)
                        + " handValuesMs=" + MoonSpirePerfDiagnostics.millis(battleRenderHandValuesNanos)
                        + " handTextVisibilityMs=" + MoonSpirePerfDiagnostics.millis(battleRenderHandTextVisibilityNanos)
                        + " handTextSubmitMs=" + MoonSpirePerfDiagnostics.millis(battleRenderHandTextSubmitNanos)
                        + " handHoverMs=" + MoonSpirePerfDiagnostics.millis(battleRenderHandHoverNanos)
                        + " handCardOffscreenSkips=" + battleRenderHandCardOffscreenSkips
                        + " handDescOffscreenSkips=" + battleRenderHandDescOffscreenSkips
                        + " flyingMs=" + MoonSpirePerfDiagnostics.millis(battleRenderFlyingNanos)
                        + " draggedMs=" + MoonSpirePerfDiagnostics.millis(battleRenderDraggedNanos)
                        + " monsterPlayedMs=" + MoonSpirePerfDiagnostics.millis(battleRenderMonsterPlayedNanos)
                        + " otherMs=" + MoonSpirePerfDiagnostics.millis(battleRenderOtherNanos)
                        + " " + CardRenderHelper.frameStats().summary());
        battleRenderPerfStart = 0L;
    }

    private void syncSnapshotAnimations(BattleSnapshot snapshot) {
        long start = perfStart();
        if (!snapshot.active()) {
            handAnimations.clear();
            flyingCards.clear();
            previousSnapshot = BattleSnapshot.inactive();
            awaitingUseCardSnapshot = false;
            clearAwaitingEndTurnSnapshot();
            locallyUsedCardIds.clear();
            locallyDisplayedVisualCardIds.clear();
            handSelectionOverlay = HandSelectionOverlay.empty();
            handSelectionConfirmation = HandSelectionConfirmation.empty();
            requestedPileKey = null;
            displayedPileKey = null;
            pileOverlaySource = PileOverlaySource.NONE;
            pileOverlayEntityId = -1;
            recordPerf(PerfBucket.SNAPSHOT_SYNC, start);
            return;
        }
        boolean keepLocalUsedCardsHidden = awaitingUseCardSnapshot || snapshot.resolvingEffects();
        Set<UUID> expectedPlayedCardIds = new HashSet<>(locallyUsedCardIds);
        awaitingUseCardSnapshot = false;
        if (!snapshot.pendingHandSelection().active()) {
            handSelectionOverlay.clearIfInactive();
        }
        reconcileHandSelectionConfirmation(snapshot);
        HandLayout layout = handLayout(snapshot);
        boolean firstActiveSnapshot = !previousSnapshot.active();
        Set<UUID> currentIds = currentHandIds(snapshot.hand());
        locallyUsedCardIds.removeIf(id -> !keepLocalUsedCardsHidden || !currentIds.contains(id));
        Set<UUID> representedFlyingIds = flyingCardIds();
        for (FlyingCardAnimation animation : flyingCards) {
            if (!animation.played() || animation.released()) {
                continue;
            }
            if (!keepLocalUsedCardsHidden) {
                animation.release();
            }
        }
        if (firstActiveSnapshot) {
            showTurnBanner(snapshot.phase());
        } else if (snapshot.phase() != previousSnapshot.phase()) {
            showTurnBanner(snapshot.phase());
        }
        for (CardInstance oldCard : previousSnapshot.hand()) {
            if (!currentIds.contains(oldCard.id()) && !representedFlyingIds.contains(oldCard.id()) && oldCard.hasEffect(CardEffectKind.ETHEREAL) && !expectedPlayedCardIds.contains(oldCard.id())) {
                HandCardAnimation from = handAnimations.get(oldCard.id());
                float startX = from != null ? from.currentX() : drawPileCenterX();
                float startY = from != null ? from.currentY() : drawPileCenterY();
                float scale = from != null ? from.currentScale() : HAND_BASE_SCALE;
                flyingCards.add(FlyingCardAnimation.exhaustInPlace(oldCard, startX, startY, scale));
                representedFlyingIds.add(oldCard.id());
            }
        }
        for (CardInstance oldCard : previousSnapshot.hand()) {
            if (!currentIds.contains(oldCard.id()) && !representedFlyingIds.contains(oldCard.id())) {
                HandCardAnimation from = handAnimations.get(oldCard.id());
                float startX = from != null ? from.currentX() : drawPileCenterX();
                float startY = from != null ? from.currentY() : drawPileCenterY();
                if (expectedPlayedCardIds.contains(oldCard.id())) {
                    boolean exhausts = oldCard.hasEffect(CardEffectKind.EXHAUST);
                    FlyingCardAnimation animation = FlyingCardAnimation.played(oldCard, startX, startY, battlefieldCenterX(), battlefieldCenterY(), discardPileCenterX(), discardPileCenterY(), exhausts);
                    if (exhausts) {
                        animation.releaseExhaust();
                    } else {
                        animation.releaseToDiscard();
                    }
                    flyingCards.add(animation);
                } else {
                    flyingCards.add(FlyingCardAnimation.toDiscard(oldCard, startX, startY, discardPileCenterX(), discardPileCenterY()));
                }
                representedFlyingIds.add(oldCard.id());
            }
        }
        for (BattleVisualEvent event : snapshot.visualEvents()) {
            CardInstance playedCard = event.playedCard();
            if (playedCard == null || event.attackerId() != snapshot.localPlayerEntityId() || event.animationType() != BattleVisualEvent.AnimationType.SELF_DESTRUCT) {
                continue;
            }
            if (representedFlyingIds.contains(playedCard.id()) || locallyDisplayedVisualCardIds.contains(playedCard.id())) {
                continue;
            }
            FlyingCardAnimation animation = FlyingCardAnimation.played(playedCard, battlefieldCenterX(), battlefieldCenterY(), battlefieldCenterX(), battlefieldCenterY(), discardPileCenterX(), discardPileCenterY(), true, Math.max(PLAYED_CARD_HOLD_TICKS, event.animationTicks()));
            animation.releaseExhaust();
            flyingCards.add(animation);
            representedFlyingIds.add(playedCard.id());
            locallyDisplayedVisualCardIds.add(playedCard.id());
        }
        for (int i = 0; i < snapshot.hand().size(); i++) {
            CardInstance card = snapshot.hand().get(i);
            HandCardBounds target = layout.card(i);
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation == null) {
                animation = new HandCardAnimation(card);
                if (firstActiveSnapshot || !containsCard(previousSnapshot.hand(), card.id())) {
                    animation.setInstant(drawPileCenterX(), drawPileCenterY(), 0.0F, target.scale(), 0.0F);
                } else {
                    animation.setInstant(target.centerX(), target.centerY(), target.angle(), target.scale(), 0.0F);
                }
                handAnimations.put(card.id(), animation);
            }
        }
        handAnimations.keySet().removeIf(id -> !currentIds.contains(id));
        previousSnapshot = snapshot;
        recordPerf(PerfBucket.SNAPSHOT_SYNC, start);
    }

    private void reconcileHandSelectionConfirmation(BattleSnapshot snapshot) {
        if (!handSelectionConfirmation.active()) {
            return;
        }
        PendingHandSelectionSnapshot selection = snapshot.pendingHandSelection();
        if (!selection.active()) {
            handSelectionConfirmation = HandSelectionConfirmation.empty();
            handSelectionOverlay.clearIfInactive();
            return;
        }
        if (!handSelectionConfirmation.matches(selection)) {
            handSelectionConfirmation = HandSelectionConfirmation.empty();
            handSelectionOverlay.sync(selection, snapshot);
            return;
        }
        if (handSelectionConfirmation.matches(selection)) {
            return;
        }
        handSelectionConfirmation = HandSelectionConfirmation.empty();
        handSelectionOverlay.sync(selection, snapshot);
    }

    private void syncHandAnimationTargets(BattleSnapshot snapshot, HandLayout layout, List<CardInstance> visibleCards, int hoveredIndex) {
        Map<UUID, Integer> visibleIndices = new HashMap<>();
        for (int i = 0; i < visibleCards.size(); i++) {
            visibleIndices.put(visibleCards.get(i).id(), i);
        }
        int count = snapshot.hand().size();
        boolean selectionActive = snapshot.pendingHandSelection().active();
        for (int i = 0; i < count; i++) {
            CardInstance card = snapshot.hand().get(i);
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation == null) {
                continue;
            }
            int selectionIndex = handSelectionOverlay.selectedIndex(card.id());
            if (selectionActive && selectionIndex >= 0) {
                HandCardBounds target = handSelectionSelectedBounds(selectionIndex, handSelectionOverlay.selectedCount());
                animation.setTarget(target.centerX(), target.centerY(), target.angle(), target.scale(), 1.0F);
                continue;
            }
            if (dragState != null && dragState.cardId().equals(card.id()) && card.requiresExplicitTarget()) {
                HandCardBounds target = centerTargetingHandBounds(layout);
                animation.setTarget(target.centerX(), target.centerY(), 0.0F, target.scale(), 1.0F);
                continue;
            }
            Integer visibleIndex = visibleIndices.get(card.id());
            if (visibleIndex == null) {
                continue;
            }
            HandCardBounds bounds = layout.card(visibleIndex);
            float targetX = bounds.centerX();
            float targetY = bounds.centerY();
            float targetAngle = bounds.angle();
            float targetScale = bounds.scale();
            float targetHover = 0.0F;
            if (hoveredIndex >= 0 && hoveredIndex == visibleIndex) {
                targetAngle = 0.0F;
                targetScale = bounds.scale() * 1.04F;
                targetHover = 1.0F;
            }
            animation.setTarget(targetX, targetY, targetAngle, targetScale, targetHover);
        }
    }

    private boolean draggingHandCard() {
        return dragState != null;
    }

    private void beginDraggedCardReturn(BattleSnapshot snapshot, DragState releasedDrag, double mouseX, double mouseY) {
        if (releasedDrag == null || releasedDrag.handIndex() < 0 || releasedDrag.handIndex() >= snapshot.hand().size()) {
            return;
        }
        CardInstance card = snapshot.hand().get(releasedDrag.handIndex());
        if (!card.id().equals(releasedDrag.cardId())) {
            return;
        }
        HandCardAnimation animation = handAnimations.get(card.id());
        if (animation == null) {
            return;
        }
        HandCardBounds start = card.requiresExplicitTarget()
                ? new HandCardBounds(animation.currentX(), animation.currentY(), 0.0F, animation.currentScale())
                : new HandCardBounds((float) mouseX, (float) mouseY, 0.0F, HAND_BASE_SCALE);
        animation.startReturn(start);
    }

    private void clearHandHoverProgress() {
        hoveredHandIndex = -1;
        for (HandCardAnimation animation : handAnimations.values()) {
            animation.clearHoverOnly();
        }
    }

    private void clearHandHoverAnimations(BattleSnapshot snapshot) {
        hoveredHandIndex = -1;
        Map<UUID, HandCardBounds> targets = handAnimationTargetsByCardId(snapshot);
        for (HandCardAnimation animation : handAnimations.values()) {
            animation.clearHover(targets.get(animation.cardId()));
        }
    }

    private Map<UUID, HandCardBounds> handAnimationTargetsByCardId(BattleSnapshot snapshot) {
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        HandLayout layout = handLayout(visibleCards);
        Map<UUID, HandCardBounds> targets = new HashMap<>();
        for (int i = 0; i < visibleCards.size(); i++) {
            targets.put(visibleCards.get(i).id(), layout.card(i));
        }
        return targets;
    }

    private void renderScaledDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips) {
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, suppressTips, 1.0F, null);
    }

    private void renderScaledDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips, float alpha) {
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, suppressTips, alpha, null);
    }

    private void renderScaledDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips, CardRenderHelper.CardValues values) {
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, suppressTips, 1.0F, values);
    }

    private void renderScaledDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips, float alpha, CardRenderHelper.CardValues values) {
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, suppressTips, alpha, values, null);
    }

    private void renderScaledDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips, CardRenderData renderData) {
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, suppressTips, 1.0F, renderData.values(), renderData.contentKey());
    }

    private void renderScaledDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips, float alpha, CardRenderHelper.CardValues values, String contentKey) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 120.0F);
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-CardRenderHelper.CARD_WIDTH / 2.0F, -CardRenderHelper.CARD_HEIGHT / 2.0F, 0.0F);
        graphics.setColor(1.0F, 1.0F, 1.0F, clamp(alpha, 0.0F, 1.0F));
        renderCardBody(graphics, snapshot, card, unaffordable, values, contentKey);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
        if (!suppressTips && scale >= 0.95F) {
            int x = Math.round(centerX - CardRenderHelper.CARD_WIDTH * scale / 2.0F);
            int y = Math.round(centerY - CardRenderHelper.CARD_HEIGHT * scale / 2.0F);
            CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, x, y, width, height);
        }
    }

    private void renderScaledSmallCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips) {
        renderScaledSmallCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, suppressTips, cardRenderData(snapshot, card, true));
    }

    private void renderScaledSmallCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips, CardRenderData renderData) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 120.0F);
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-CardRenderHelper.SMALL_CARD_WIDTH / 2.0F, -CardRenderHelper.SMALL_CARD_HEIGHT / 2.0F, 0.0F);
        CardRenderHelper.renderSmallCard(graphics, font, card, 0, 0, false, unaffordable, renderData.values(), false, true, renderData.contentKey());
        graphics.pose().popPose();
        if (!suppressTips && scale >= handPreviewScale() * 0.95F) {
            int x = Math.round(centerX - CardRenderHelper.SMALL_CARD_WIDTH * scale / 2.0F);
            int y = Math.round(centerY - CardRenderHelper.SMALL_CARD_HEIGHT * scale / 2.0F);
            int w = Math.round(CardRenderHelper.SMALL_CARD_WIDTH * scale);
            int h = Math.round(CardRenderHelper.SMALL_CARD_HEIGHT * scale);
            CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, x, y, w, h, width, height);
        }
    }

    private void renderCardBody(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, boolean unaffordable) {
        renderCardBody(graphics, snapshot, card, unaffordable, null);
    }

    private void renderCardBody(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, boolean unaffordable, CardRenderHelper.CardValues values) {
        renderCardBody(graphics, snapshot, card, unaffordable, values, null);
    }

    private void renderCardBody(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, boolean unaffordable, CardRenderHelper.CardValues values, String contentKey) {
        if (contentKey == null) {
            CardRenderData renderData = cardRenderData(snapshot, card);
            CardRenderHelper.renderDetailedCard(graphics, font, card, 0, 0, false, values == null ? renderData.values() : values, unaffordable, false, renderData.contentKey());
            return;
        }
        CardRenderHelper.renderDetailedCard(graphics, font, card, 0, 0, false, values == null ? cardValues(snapshot, card) : values, unaffordable, false, contentKey);
    }

    private void renderCardPreview(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, int x, int y, boolean unaffordable) {
        CardRenderData renderData = cardRenderData(snapshot, card);
        CardRenderHelper.renderDetailedCard(graphics, font, card, x, y, false, renderData.values(), unaffordable, false, renderData.contentKey());
        CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, x, y, width, height);
    }

    private void prepareCardRenderDataCache() {
        long developerRevision = DeveloperDataManager.cacheRevision();
        if (cardRenderDataDeveloperRevision != developerRevision) {
            cardRenderDataCache.clear();
            cardRenderDataPreviewHashSnapshot = null;
            cardRenderDataPreviewHash = 0;
            cardRenderDataDeveloperRevision = developerRevision;
        }
    }

    private CardRenderData cardRenderData(BattleSnapshot snapshot, CardInstance card) {
        return cardRenderData(snapshot, card, false);
    }

    private CardRenderData cardRenderData(BattleSnapshot snapshot, CardInstance card, boolean recordHandPerf) {
        BattleCardPreviewContext context = previewContext(snapshot, card);
        return cardRenderData(snapshot, card, context.attacker(), context.monsterCard(), recordHandPerf);
    }

    private CardRenderData cardRenderData(BattleSnapshot snapshot, CardInstance card, BattleCombatantSnapshot attacker, boolean monsterCard) {
        return cardRenderData(snapshot, card, attacker, monsterCard, false);
    }

    private CardRenderData cardRenderData(BattleSnapshot snapshot, CardInstance card, BattleCombatantSnapshot attacker, boolean monsterCard, boolean recordHandPerf) {
        prepareCardRenderDataCache();
        int hoveredEntityId = ClientBattleState.hoveredEntityId();
        boolean draggedForPreview = dragState != null && dragState.cardId().equals(card.id());
        CardRenderDataCacheKey key = new CardRenderDataCacheKey(
                snapshot.battleId(),
                card.id(),
                card.renderStateHash(),
                attacker.entityId(),
                monsterCard,
                hoveredEntityId,
                draggedForPreview,
                cardValuesPreviewStateHash(snapshot));
        CardRenderData cached = cardRenderDataCache.get(key);
        if (cached != null) {
            return cached;
        }
        long contentKeyStart = recordHandPerf ? perfStart() : 0L;
        String contentKey = CardRenderHelper.contentKey(card);
        if (recordHandPerf) {
            battleRenderHandContentKeyNanos += elapsedPerf(contentKeyStart);
        }
        long valuesStart = recordHandPerf ? perfStart() : 0L;
        CardRenderHelper.CardValues values = buildCardValues(snapshot, card, attacker, monsterCard);
        if (recordHandPerf) {
            battleRenderHandValuesNanos += elapsedPerf(valuesStart);
        }
        CardRenderData built = new CardRenderData(contentKey, values);
        cardRenderDataCache.put(key, built);
        return built;
    }

    private int cardValuesPreviewStateHash(BattleSnapshot snapshot) {
        if (cardRenderDataPreviewHashSnapshot == snapshot) {
            return cardRenderDataPreviewHash;
        }
        int hash = 17;
        hash = 31 * hash + snapshot.localPlayerEntityId();
        hash = 31 * hash + snapshot.players().size();
        for (BattleCombatantSnapshot combatant : snapshot.players()) {
            hash = appendCombatantPreviewStateHash(hash, combatant);
        }
        hash = 31 * hash + snapshot.enemies().size();
        for (BattleCombatantSnapshot combatant : snapshot.enemies()) {
            hash = appendCombatantPreviewStateHash(hash, combatant);
        }
        cardRenderDataPreviewHashSnapshot = snapshot;
        cardRenderDataPreviewHash = hash;
        return hash;
    }

    private int appendCombatantPreviewStateHash(int hash, BattleCombatantSnapshot combatant) {
        hash = 31 * hash + combatant.entityId();
        hash = 31 * hash + combatant.defense();
        hash = 31 * hash + combatant.roundSpeed();
        hash = 31 * hash + (combatant.fakeDead() ? 1 : 0);
        hash = 31 * hash + combatant.effects().size();
        for (BattleEffectSnapshot effect : combatant.effects()) {
            hash = 31 * hash + effect.type().hashCode();
            hash = 31 * hash + effect.amount();
        }
        return hash;
    }

    private CardRenderHelper.CardValues cardValues(BattleSnapshot snapshot, CardInstance card) {
        return cardRenderData(snapshot, card).values();
    }

    private BattleCardPreviewContext previewContext(BattleSnapshot snapshot, CardInstance card) {
        BattleCombatantSnapshot attacker = snapshot.player();
        boolean monsterCard = snapshot.monsterHand().contains(card) || snapshot.monsterIntentCards().contains(card);
        for (BattleEnemyIntentSnapshot intent : snapshot.enemyIntents()) {
            if (intent.cards().contains(card)) {
                BattleCombatantSnapshot intentUser = snapshot.combatant(intent.entityId());
                if (intentUser != null) {
                    attacker = intentUser;
                    monsterCard = snapshot.isEnemyEntity(intentUser.entityId());
                    break;
                }
            }
        }
        if (monsterCard && attacker.entityId() == snapshot.player().entityId()) {
            attacker = snapshot.monster();
        }
        return new BattleCardPreviewContext(attacker, monsterCard);
    }

    private CardRenderHelper.CardValues cardValues(BattleSnapshot snapshot, CardInstance card, BattleCombatantSnapshot attacker, BattleCombatantSnapshot defender) {
        return cardValues(snapshot, card, attacker, snapshot.isEnemyEntity(attacker.entityId()));
    }

    private CardRenderHelper.CardValues cardValues(BattleSnapshot snapshot, CardInstance card, BattleCombatantSnapshot attacker, boolean monsterCard) {
        return cardRenderData(snapshot, card, attacker, monsterCard).values();
    }

    private CardRenderHelper.CardValues buildCardValues(BattleSnapshot snapshot, CardInstance card, BattleCombatantSnapshot attacker, boolean monsterCard) {
        int attack = card.enemyDirectDamageAmount();
        int defense = card.selfEffectAmount(CardEffectKind.BLOCK);
        List<Integer> damageAmounts = new ArrayList<>(card.effects().size());
        List<Integer> blockAmounts = new ArrayList<>(card.effects().size());
        int previewAttackTotal = 0;
        boolean hasPreviewAttack = false;
        boolean paralyzedAttack = paralyzedAttackForCardPreview(snapshot, card, attacker);
        for (CardEffect effect : card.effects()) {
            int damageAmount = effect.amount();
            int blockAmount = effect.amount();
            BattleCombatantSnapshot singleTarget = singlePreviewTarget(card, effect.target(), snapshot, monsterCard);
            if (CardInstance.isAttackDamageEffect(effect.kind())) {
                int attackerStrength = CardRenderHelper.effectAmount(attacker, BattleEffectType.STRENGTH);
                boolean attackerWeak = CardRenderHelper.effectAmount(attacker, BattleEffectType.WEAKNESS) > 0;
                int baseDamage = paralyzedAttack ? Math.max(0, effect.amount() - CardBalance.PARALYSIS_ATTACK_DAMAGE_REDUCTION) : effect.amount();
                if (singleTarget.entityId() >= 0) {
                    damageAmount = CardRenderHelper.previewDamageAmount(baseDamage, attacker.roundSpeed(), singleTarget.roundSpeed(), singleTarget.defense(), CardRenderHelper.effectAmount(singleTarget, BattleEffectType.GUARD), attackerStrength, attackerWeak, card.hasEffect(CardEffectKind.REMOTE), CardRenderHelper.effectAmount(singleTarget, BattleEffectType.GLOWING) > 0);
                } else {
                    damageAmount = CardRenderHelper.previewDamageAmount(baseDamage, 1, 1, 0, 0, attackerStrength, attackerWeak, card.hasEffect(CardEffectKind.REMOTE), false);
                }
            }
            damageAmounts.add(damageAmount);
            blockAmounts.add(blockAmount);
            if (CardInstance.isAttackDamageEffect(effect.kind()) && effect.target().targetsEnemy()) {
                previewAttackTotal += damageAmount * effect.count();
                hasPreviewAttack = true;
            }
        }
        if (hasPreviewAttack) {
            attack = previewAttackTotal;
        }
        return new CardRenderHelper.CardValues(attack, defense, damageAmounts, blockAmounts);
    }

    private boolean paralyzedAttackForCardPreview(BattleSnapshot snapshot, CardInstance card, BattleCombatantSnapshot attacker) {
        int paralysis = Math.max(0, CardRenderHelper.effectAmount(attacker, BattleEffectType.PARALYSIS));
        if (paralysis <= 0 || !card.hasAttack()) {
            return false;
        }
        List<CardInstance> intentCards = snapshot.intentCardsFor(attacker.entityId());
        if (!intentCards.isEmpty()) {
            for (CardInstance intentCard : intentCards) {
                if (intentCard.id().equals(card.id())) {
                    return paralysis > 0;
                }
                if (intentCard.hasAttack()) {
                    paralysis = Math.max(0, paralysis - 1);
                }
            }
        }
        return true;
    }

    private static boolean positiveEffect(CardEffectKind kind) {
        return kind == CardEffectKind.HEAL
                || kind == CardEffectKind.DRAW_CARDS
                || kind == CardEffectKind.GAIN_ENERGY
                || kind == CardEffectKind.GUARD
                || kind == CardEffectKind.UNDYING
                || kind == CardEffectKind.SUMMON_VEX
                || kind == CardEffectKind.STRENGTH
                || kind == CardEffectKind.REGENERATION
                || kind == CardEffectKind.HASTE
                || kind == CardEffectKind.THORNS
                || kind == CardEffectKind.FUSE;
    }

    private static boolean negativeEffect(CardEffectKind kind) {
        return kind == CardEffectKind.BLEED
                || kind == CardEffectKind.LOSE_STRENGTH
                || kind == CardEffectKind.POISON
                || kind == CardEffectKind.BURN
                || kind == CardEffectKind.WITHER
                || kind == CardEffectKind.TIDAL_EROSION
                || kind == CardEffectKind.PARALYSIS
                || kind == CardEffectKind.HUNGER
                || kind == CardEffectKind.WEAKNESS
                || kind == CardEffectKind.SLOWNESS
                || kind == CardEffectKind.GLOWING;
    }

    private BattleCombatantSnapshot singlePreviewTarget(CardInstance card, CardTarget target, BattleSnapshot snapshot, boolean monsterCard) {
        List<Integer> ids = targetIdsForEffectTarget(target, snapshot, monsterCard);
        if (ids.size() != 1) {
            return BattleCombatantSnapshot.empty();
        }
        int entityId = ids.getFirst();
        if (!monsterCard && target.requiresExplicitTarget()) {
            if (dragState == null || !dragState.cardId().equals(card.id()) || !ClientBattleState.isHoveredEntityId(entityId)) {
                return BattleCombatantSnapshot.empty();
            }
        }
        if (entityId == snapshot.player().entityId()) {
            return snapshot.player();
        }
        if (entityId == snapshot.monster().entityId()) {
            return snapshot.monster();
        }
        BattleCombatantSnapshot combatant = snapshot.combatant(entityId);
        return combatant == null ? BattleCombatantSnapshot.empty() : combatant;
    }

    private boolean playable(CardInstance card, BattleSnapshot snapshot) {
        return snapshot.phase() == BattlePhase.PLAYER_TURN
                && !snapshot.localPlayerEndedTurn()
                && !snapshot.localPlayerFakeDead()
                && !awaitingEndTurnSnapshot
                && !cardActionsLocked(snapshot)
                && card.cost() <= snapshot.player().energyLeft()
                && card.hasAnyEffect();
    }

    private boolean playableDraggedCardAt(CardInstance card, BattleSnapshot snapshot, double mouseX, double mouseY) {
        if (!playable(card, snapshot)) {
            return false;
        }
        if (card.requiresExplicitTarget()) {
            return validDraggedTarget(card, targetEntityUnderMouse(mouseX, mouseY, snapshot), snapshot);
        }
        return playAreaContains(mouseX, mouseY);
    }

    private DragGlow dragGlowFor(CardInstance card, boolean playableHere) {
        if (dragState == null || !dragState.cardId().equals(card.id())) {
            return new DragGlow(0.0F);
        }
        return new DragGlow(dragState.updatePlayable(playableHere, currentFrameTicks));
    }

    private boolean hasPlayableCard(BattleSnapshot snapshot) {
        for (CardInstance card : snapshot.hand()) {
            if (playable(card, snapshot)) {
                return true;
            }
        }
        return false;
    }

    private void renderSmallPlayableGlow(GuiGraphics graphics, boolean playable, float pulse, float yOffset, int cardW, int cardH) {
        if (!playable && pulse <= 0.02F) {
            return;
        }
        if (playable) {
            graphics.fill(-3, Math.round(yOffset) - 3, cardW + 3, Math.round(yOffset) + cardH + 3, 0x44A8F7FF);
        }
        if (pulse > 0.02F) {
            int alpha = Math.round(140.0F * pulse);
            int spread = Math.round(5.0F + 8.0F * (1.0F - pulse));
            graphics.fill(-spread, Math.round(yOffset) - spread, cardW + spread, Math.round(yOffset) + cardH + spread, (alpha << 24) | 0x00D7FFFF);
        }
    }

    private void renderSmallPlayableOutline(GuiGraphics graphics, boolean playable, float pulse, int cardW, int cardH) {
        if (playable) {
            graphics.renderOutline(-1, -1, cardW + 2, cardH + 2, 0xDDA8F7FF);
        }
        if (pulse > 0.02F) {
            int alpha = Math.round(230.0F * pulse);
            int spread = Math.round(2.0F + 8.0F * (1.0F - pulse));
            graphics.renderOutline(-spread, -spread, cardW + spread * 2, cardH + spread * 2, (alpha << 24) | 0x00E6FFFF);
        }
    }

    private void renderDetailedPlayableGlow(GuiGraphics graphics, float centerX, float centerY, float scale) {
        renderDetailedPlayableGlow(graphics, true, centerX, centerY, scale, 0.0F);
    }

    private void renderDetailedPlayableGlow(GuiGraphics graphics, float centerX, float centerY, float scale, float pulse) {
        renderDetailedPlayableGlow(graphics, true, centerX, centerY, scale, pulse);
    }

    private void renderDetailedPlayableGlow(GuiGraphics graphics, boolean playable, float centerX, float centerY, float scale, float pulse) {
        if (!playable && pulse <= 0.02F) {
            return;
        }
        float halfW = CardRenderHelper.CARD_WIDTH * scale / 2.0F;
        float halfH = CardRenderHelper.CARD_HEIGHT * scale / 2.0F;
        if (playable) {
            graphics.fill(Math.round(centerX - halfW - 4.0F), Math.round(centerY - halfH - 4.0F),
                    Math.round(centerX + halfW + 4.0F), Math.round(centerY + halfH + 4.0F), 0x44A8F7FF);
        }
        if (pulse > 0.02F) {
            int alpha = Math.round(140.0F * pulse);
            float spread = 7.0F + 12.0F * (1.0F - pulse);
            graphics.fill(Math.round(centerX - halfW - spread), Math.round(centerY - halfH - spread),
                    Math.round(centerX + halfW + spread), Math.round(centerY + halfH + spread), (alpha << 24) | 0x00D7FFFF);
        }
    }

    private void renderDetailedPlayableOutline(GuiGraphics graphics, float centerX, float centerY, float scale) {
        renderDetailedPlayableOutline(graphics, true, centerX, centerY, scale, 0.0F);
    }

    private void renderDetailedPlayableOutline(GuiGraphics graphics, float centerX, float centerY, float scale, float pulse) {
        renderDetailedPlayableOutline(graphics, true, centerX, centerY, scale, pulse);
    }

    private void renderDetailedPlayableOutline(GuiGraphics graphics, boolean playable, float centerX, float centerY, float scale, float pulse) {
        if (!playable && pulse <= 0.02F) {
            return;
        }
        int x = Math.round(centerX - CardRenderHelper.CARD_WIDTH * scale / 2.0F);
        int y = Math.round(centerY - CardRenderHelper.CARD_HEIGHT * scale / 2.0F);
        int w = Math.round(CardRenderHelper.CARD_WIDTH * scale);
        int h = Math.round(CardRenderHelper.CARD_HEIGHT * scale);
        if (playable) {
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xDDA8F7FF);
        }
        if (pulse > 0.02F) {
            int alpha = Math.round(230.0F * pulse);
            int spread = Math.round(3.0F + 10.0F * (1.0F - pulse));
            graphics.renderOutline(x - spread, y - spread, w + spread * 2, h + spread * 2, (alpha << 24) | 0x00E6FFFF);
        }
    }

    private void renderSmallPreviewPlayableGlow(GuiGraphics graphics, boolean playable, float centerX, float centerY, float scale, float pulse) {
        if (!playable && pulse <= 0.02F) {
            return;
        }
        float halfW = CardRenderHelper.SMALL_CARD_WIDTH * scale / 2.0F;
        float halfH = CardRenderHelper.SMALL_CARD_HEIGHT * scale / 2.0F;
        if (playable) {
            graphics.fill(Math.round(centerX - halfW - 4.0F), Math.round(centerY - halfH - 4.0F),
                    Math.round(centerX + halfW + 4.0F), Math.round(centerY + halfH + 4.0F), 0x44A8F7FF);
        }
        if (pulse > 0.02F) {
            int alpha = Math.round(140.0F * pulse);
            float spread = 7.0F + 12.0F * (1.0F - pulse);
            graphics.fill(Math.round(centerX - halfW - spread), Math.round(centerY - halfH - spread),
                    Math.round(centerX + halfW + spread), Math.round(centerY + halfH + spread), (alpha << 24) | 0x00D7FFFF);
        }
    }

    private void renderSmallPreviewPlayableOutline(GuiGraphics graphics, boolean playable, float centerX, float centerY, float scale, float pulse) {
        if (!playable && pulse <= 0.02F) {
            return;
        }
        int x = Math.round(centerX - CardRenderHelper.SMALL_CARD_WIDTH * scale / 2.0F);
        int y = Math.round(centerY - CardRenderHelper.SMALL_CARD_HEIGHT * scale / 2.0F);
        int w = Math.round(CardRenderHelper.SMALL_CARD_WIDTH * scale);
        int h = Math.round(CardRenderHelper.SMALL_CARD_HEIGHT * scale);
        if (playable) {
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xDDA8F7FF);
        }
        if (pulse > 0.02F) {
            int alpha = Math.round(230.0F * pulse);
            int spread = Math.round(3.0F + 10.0F * (1.0F - pulse));
            graphics.renderOutline(x - spread, y - spread, w + spread * 2, h + spread * 2, (alpha << 24) | 0x00E6FFFF);
        }
    }

    private float battlefieldCenterX() {
        return width / 2.0F;
    }

    private float battlefieldCenterY() {
        return height / 2.0F - 18.0F;
    }

    private boolean playAreaContains(double mouseX, double mouseY) {
        MoonSpireUiRect playArea = layout().resolve("play_area", width, height);
        MoonSpireUiRect handRect = layout().resolve("hand", width, height);
        return mouseX >= playArea.x()
                && mouseX <= playArea.right()
                && mouseY >= playArea.y()
                && mouseY <= Math.min(playArea.bottom(), handRect.y() + 24);
    }

    private float drawPileCenterX() {
        MoonSpireUiRect rect = layout().resolve("draw_pile", width, height);
        return rect.x() + rect.width() / 2.0F;
    }

    private float drawPileCenterY() {
        MoonSpireUiRect rect = layout().resolve("draw_pile", width, height);
        return rect.y() + rect.height() / 2.0F;
    }

    private float discardPileCenterX() {
        MoonSpireUiRect rect = layout().resolve("discard_pile", width, height);
        return rect.x() + rect.width() / 2.0F;
    }

    private float discardPileCenterY() {
        MoonSpireUiRect rect = layout().resolve("discard_pile", width, height);
        return rect.y() + rect.height() / 2.0F;
    }

    private float exhaustPileCenterX() {
        MoonSpireUiRect rect = layout().resolve("exhaust_pile", width, height);
        return rect.x() + rect.width() / 2.0F;
    }

    private float exhaustPileCenterY() {
        MoonSpireUiRect rect = layout().resolve("exhaust_pile", width, height);
        return rect.y() + rect.height() / 2.0F;
    }

    private ButtonRect handSelectionConfirmButton() {
        int x = width / 2 - HAND_SELECTION_BUTTON_W / 2;
        int y = Math.max(height / 2 + 46, Math.min(height - 112, height * 2 / 3 - 12));
        return new ButtonRect(x, y, HAND_SELECTION_BUTTON_W, HAND_SELECTION_BUTTON_H);
    }

    private HandCardBounds handSelectionSelectedBounds(int selectedIndex, int selectedCount) {
        ButtonRect button = handSelectionConfirmButton();
        int count = Math.max(1, selectedCount);
        int spacing = Math.min(HAND_SELECTION_CARD_SPACING, Math.max(42, (width - 32) / count));
        float total = (count - 1) * spacing;
        float x = width / 2.0F - total / 2.0F + selectedIndex * spacing;
        float y = button.y() - CardRenderHelper.SMALL_CARD_HEIGHT * HAND_SELECTION_CARD_SCALE * 0.5F - 18.0F;
        return new HandCardBounds(x, y, 0.0F, HAND_SELECTION_CARD_SCALE);
    }

    private Set<UUID> currentHandIds(List<CardInstance> cards) {
        Set<UUID> ids = new HashSet<>();
        for (CardInstance card : cards) {
            ids.add(card.id());
        }
        return ids;
    }

    private boolean containsCard(List<CardInstance> cards, UUID id) {
        for (CardInstance card : cards) {
            if (card.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private CardInstance cardById(List<CardInstance> cards, UUID id) {
        for (CardInstance card : cards) {
            if (card.id().equals(id)) {
                return card;
            }
        }
        return null;
    }

    private Set<UUID> flyingCardIds() {
        Set<UUID> ids = new HashSet<>();
        for (FlyingCardAnimation animation : flyingCards) {
            ids.add(animation.card().id());
        }
        return ids;
    }

    private Component phaseComponent(BattlePhase phase) {
        return switch (phase) {
            case PLAYER_TURN -> Component.translatable("screen.moonspire.turn.player");
            case PLAYER_ALLY_TURN -> Component.translatable("screen.moonspire.turn.player_ally");
            case MONSTER_TURN -> Component.translatable("screen.moonspire.turn.monster");
            default -> Component.translatable(phase.translationKey());
        };
    }

    private void showTurnBanner(BattlePhase phase) {
        if (phase == BattlePhase.PLAYER_TURN || phase == BattlePhase.PLAYER_ALLY_TURN || phase == BattlePhase.MONSTER_TURN) {
            turnBannerPhase = phase;
            turnBannerTicks = TURN_BANNER_TICKS;
        } else {
            turnBannerTicks = 0;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static float approach(float current, float target, float amount) {
        if (Math.abs(target - current) < 0.01F) {
            return target;
        }
        return current + (target - current) * amount;
    }

    private static float frameAmount(float perTickAmount, float deltaTicks) {
        return 1.0F - (float) Math.pow(1.0F - perTickAmount, Math.max(0.0F, deltaTicks));
    }

    private static float smoothStep(float progress) {
        float t = clamp(progress, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private void drawAimLine(GuiGraphics graphics, float startX, float startY, float endX, float endY, int color) {
        drawSoftLine(graphics, startX, startY, endX, endY, color, 3);
        float dx = endX - startX;
        float dy = endY - startY;
        float len = Math.max(0.001F, (float) Math.sqrt(dx * dx + dy * dy));
        float ux = dx / len;
        float uy = dy / len;
        float px = -uy;
        float py = ux;
        float baseX = endX - ux * 12.0F;
        float baseY = endY - uy * 12.0F;
        drawSoftLine(graphics, endX, endY, baseX + px * 7.0F, baseY + py * 7.0F, color, 2);
        drawSoftLine(graphics, endX, endY, baseX - px * 7.0F, baseY - py * 7.0F, color, 2);
    }

    private void drawSoftLine(GuiGraphics graphics, float startX, float startY, float endX, float endY, int color, int size) {
        int steps = Math.max(4, Math.round(Math.max(Math.abs(endX - startX), Math.abs(endY - startY)) / 20.0F));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(lerp(startX, endX, t));
            int y = Math.round(lerp(startY, endY, t));
            graphics.fill(x - size / 2, y - size / 2, x + size / 2 + 1, y + size / 2 + 1, color);
        }
    }

    private boolean clickPile(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (drawPileAt(mouseX, mouseY)) {
            openPileOverlay(Component.translatable("screen.moonspire.draw_pile"), PileOverlaySource.DRAW, snapshot);
            return true;
        }
        if (discardPileAt(mouseX, mouseY)) {
            openPileOverlay(Component.translatable("screen.moonspire.discard_pile"), PileOverlaySource.DISCARD, snapshot);
            return true;
        }
        if (visibleExhaustPileAt(mouseX, mouseY, snapshot)) {
            openPileOverlay(Component.translatable("screen.moonspire.exhaust_pile"), PileOverlaySource.EXHAUST, snapshot);
            return true;
        }
        return false;
    }

    private void openBattleDeckOverlay(BattleSnapshot snapshot) {
        if (!snapshot.active() || snapshot.pendingHandSelection().active() || pileOverlay != null || MoonSpireBattleLayoutEditor.enabled()) {
            return;
        }
        openPileOverlay(Component.translatable("screen.moonspire.deck_view"), PileOverlaySource.BATTLE_DECK, snapshot);
    }

    private void openEntityDeckOverlay(int entityId, BattleSnapshot snapshot) {
        if (entityId < 0 || !snapshot.active()) {
            return;
        }
        openPileOverlay(Component.translatable("screen.moonspire.deck_view"), PileOverlaySource.ENTITY_DECK, snapshot, entityId);
    }

    private void openPileOverlay(List<CardInstance> cards, Component title, PileOverlaySource source) {
        pileOverlay = new CardGridPanel(cards, title, new StaticPileKey(source, ClientBattleState.snapshot().sequence()));
        pileOverlaySource = source;
        pileOverlayEntityId = -1;
        requestedPileKey = null;
        displayedPileKey = null;
        stablePileUpdateKey = null;
        stablePileDisplayCount = -1;
        nextPileOverlayRefreshTick = 0;
        pileOverlayPerfFrameIndex = 0;
    }

    private void openPileOverlay(Component title, PileOverlaySource source, BattleSnapshot snapshot) {
        openPileOverlay(title, source, snapshot, -1);
    }

    private void openPileOverlay(Component title, PileOverlaySource source, BattleSnapshot snapshot, int entityId) {
        pileOverlay = new CardGridPanel(List.of(), title, new StaticPileKey(source, snapshot.sequence()));
        pileOverlaySource = source;
        pileOverlayEntityId = source == PileOverlaySource.ENTITY_DECK ? entityId : -1;
        requestedPileKey = null;
        displayedPileKey = null;
        stablePileUpdateKey = null;
        stablePileDisplayCount = -1;
        nextPileOverlayRefreshTick = 0;
        pileOverlayPerfFrameIndex = 0;
        pileOverlay.setDisplayCountOverride(expectedPileCount(snapshot, source));
        requestPileContentsIfNeeded(snapshot);
    }

    private void closePileOverlay() {
        pileOverlay = null;
        pileOverlaySource = PileOverlaySource.NONE;
        pileOverlayEntityId = -1;
        requestedPileKey = null;
        displayedPileKey = null;
        stablePileUpdateKey = null;
        stablePileDisplayCount = -1;
        nextPileOverlayRefreshTick = 0;
        pileOverlayPerfFrameIndex = 0;
    }

    private void updatePileOverlayCards(BattleSnapshot snapshot) {
        if (pileOverlay == null) {
            return;
        }
        if (!pileOverlaySource.remote()) {
            return;
        }
        BattlePileSource source = pileOverlaySource.remoteSource();
        long deckVersion = pileOverlayDeckVersion(snapshot, pileOverlaySource);
        long snapshotDeckVersion = deckVersion;
        int entityId = pileOverlayEntityId(pileOverlaySource);
        long availableVersion = ClientBattleState.pileContentsVersionAtOrAfter(snapshot.battleId(), source, deckVersion, entityId);
        if (availableVersion >= 0L) {
            deckVersion = availableVersion;
        }
        PileRequestKey key = new PileRequestKey(snapshot.battleId(), source, deckVersion, entityId);
        int expectedCount = expectedPileCount(snapshot, pileOverlaySource);
        if (deckVersion != snapshotDeckVersion) {
            int cachedCount = ClientBattleState.pileExpectedCount(snapshot.battleId(), source, deckVersion, entityId);
            if (cachedCount >= 0) {
                expectedCount = cachedCount;
            }
        }
        if (key.equals(stablePileUpdateKey) && expectedCount == stablePileDisplayCount) {
            requestPileContentsIfNeeded(snapshot);
            return;
        }
        pileOverlay.setDisplayCountOverride(expectedCount);
        requestPileContentsIfNeeded(snapshot);
        if (ClientBattleState.hasPileContents(snapshot.battleId(), source, deckVersion, entityId)) {
            if (key.equals(displayedPileKey)) {
                stablePileUpdateKey = key;
                stablePileDisplayCount = expectedCount;
                return;
            }
            pileOverlay.setCards(ClientBattleState.pileContents(snapshot.battleId(), source, deckVersion, entityId), key);
            displayedPileKey = key;
            stablePileUpdateKey = key;
            stablePileDisplayCount = expectedCount;
        }
    }

    private void requestPileContentsIfNeeded(BattleSnapshot snapshot) {
        if (pileOverlay == null || !pileOverlaySource.remote() || !snapshot.active()) {
            return;
        }
        BattlePileSource source = pileOverlaySource.remoteSource();
        long deckVersion = pileOverlayDeckVersion(snapshot, pileOverlaySource);
        int entityId = pileOverlayEntityId(pileOverlaySource);
        if (ClientBattleState.hasPileContentsAtOrAfter(snapshot.battleId(), source, deckVersion, entityId)) {
            if (shouldRefreshRemotePile(snapshot, source, deckVersion, entityId)) {
                PacketDistributor.sendToServer(new RequestBattlePilePayload(snapshot.battleId(), source, deckVersion, entityId));
            }
            return;
        }
        PileRequestKey key = new PileRequestKey(snapshot.battleId(), source, deckVersion, entityId);
        if (key.equals(requestedPileKey)) {
            return;
        }
        requestedPileKey = key;
        PacketDistributor.sendToServer(new RequestBattlePilePayload(snapshot.battleId(), source, deckVersion, entityId));
    }

    private boolean shouldRefreshRemotePile(BattleSnapshot snapshot, BattlePileSource source, long deckVersion, int entityId) {
        if (!snapshot.resolvingEffects() && !snapshot.pendingHandSelection().active()) {
            return false;
        }
        if (uiTicks < nextPileOverlayRefreshTick) {
            return false;
        }
        nextPileOverlayRefreshTick = uiTicks + 10;
        return true;
    }

    private int expectedPileCount(BattleSnapshot snapshot, PileOverlaySource source) {
        if (source == PileOverlaySource.ENTITY_DECK) {
            BattleCombatantSnapshot combatant = snapshot.combatant(pileOverlayEntityId(source));
            if (combatant != null) {
                return combatant.battleDeckCount();
            }
            return ClientBattleState.pileExpectedCount(snapshot.battleId(), source.remoteSource(), pileOverlayDeckVersion(snapshot, source), pileOverlayEntityId(source));
        }
        return switch (source) {
            case BATTLE_DECK -> snapshot.player().battleDeckCount();
            case DRAW -> snapshot.drawPile();
            case DISCARD -> snapshot.discardPile();
            case EXHAUST -> snapshot.exhaustPile();
            default -> -1;
        };
    }

    private long pileOverlayDeckVersion(BattleSnapshot snapshot, PileOverlaySource source) {
        if (source == PileOverlaySource.ENTITY_DECK) {
            BattleCombatantSnapshot combatant = snapshot.combatant(pileOverlayEntityId(source));
            return combatant == null ? 0L : combatant.deckVersion();
        }
        return snapshot.localDeckVersion();
    }

    private int pileOverlayEntityId(PileOverlaySource source) {
        return source == PileOverlaySource.ENTITY_DECK ? pileOverlayEntityId : -1;
    }

    private boolean clickEndTurn(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (canEndTurn(snapshot) && endTurnButtonAt(mouseX, mouseY)) {
            beginAwaitingEndTurnSnapshot(snapshot);
            PacketDistributor.sendToServer(new EndTurnPayload());
            return true;
        }
        return false;
    }

    private int handIndexAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (frameCache.matches(snapshot, mouseX, mouseY)) {
            return frameCache.handIndex();
        }
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        return directVisibleHandIndexAt(mouseX, mouseY, visibleCards, handLayout(visibleCards));
    }

    private int directHandIndexAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        return directVisibleHandIndexAt(mouseX, mouseY, visibleCards, handLayout(visibleCards));
    }

    private int directVisibleHandIndexAt(double mouseX, double mouseY, List<CardInstance> visibleCards, HandLayout layout) {
        for (int i = visibleCards.size() - 1; i >= 0; i--) {
            if (layout.card(i).contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    private int handPreviewIndexAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (draggingHandCard()) {
            return -1;
        }
        if (frameCache.matches(snapshot, mouseX, mouseY)) {
            return frameCache.previewIndex();
        }
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        if (hoveredHandIndex < 0 || hoveredHandIndex >= visibleCards.size()) {
            return -1;
        }
        HandLayout layout = handLayout(visibleCards);
        return hoveredHandPreviewBounds(visibleCards, layout, hoveredHandIndex, 1.0F).contains(mouseX, mouseY) ? hoveredHandIndex : -1;
    }

    private int handSelectionCardIndexAt(double mouseX, double mouseY, BattleSnapshot snapshot, float partialTick) {
        if (hoveredHandIndex >= 0 && hoveredHandIndex < snapshot.hand().size()
                && handSelectionPreviewBounds(snapshot, hoveredHandIndex, partialTick).contains(mouseX, mouseY)) {
            return hoveredHandIndex;
        }
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        HandLayout visibleLayout = handLayout(visibleCards);
        for (int i = visibleCards.size() - 1; i >= 0; i--) {
            if (visibleLayout.card(i).contains(mouseX, mouseY)) {
                return handIndexForCardId(snapshot, visibleCards.get(i).id());
            }
        }
        for (UUID selectedId : handSelectionOverlay.selectedIds()) {
            int selectedIndex = handSelectionOverlay.selectedIndex(selectedId);
            HandCardBounds selectedBounds = handSelectionSelectedBounds(selectedIndex, handSelectionOverlay.selectedCount());
            if (selectedBounds.contains(mouseX, mouseY)) {
                return handIndexForCardId(snapshot, selectedId);
            }
        }
        return -1;
    }

    private int handSnapshotIndexForVisibleIndex(BattleSnapshot snapshot, int visibleIndex) {
        if (visibleIndex < 0) {
            return -1;
        }
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        if (visibleIndex >= visibleCards.size()) {
            return -1;
        }
        return handIndexForCardId(snapshot, visibleCards.get(visibleIndex).id());
    }

    private int hoveredHandIndex(double mouseX, double mouseY, BattleSnapshot snapshot, List<CardInstance> visibleCards, HandLayout layout, float partialTick) {
        if (draggingHandCard()) {
            hoveredHandIndex = -1;
            return -1;
        }
        if (frameCache.matches(snapshot, mouseX, mouseY)) {
            hoveredHandIndex = frameCache.hoveredIndex();
            return hoveredHandIndex;
        }
        if (visibleCards.isEmpty()) {
            hoveredHandIndex = -1;
            return -1;
        }
        if (hoveredHandIndex >= visibleCards.size()) {
            hoveredHandIndex = -1;
        }
        int directHover = directVisibleHandIndexAt(mouseX, mouseY, visibleCards, layout);
        if (hoveredHandIndex >= 0) {
            int previousHover = hoveredHandIndex;
            boolean currentSticky = handHoverStickyAt(mouseX, mouseY, visibleCards, layout, previousHover, partialTick);
            if (currentSticky) {
                return previousHover;
            }
            if (directHover >= 0 && directHover != previousHover) {
                hoveredHandIndex = directHover;
                return hoveredHandIndex;
            }
            hoveredHandIndex = -1;
            return -1;
        }
        if (directHover >= 0) {
            hoveredHandIndex = directHover;
            return hoveredHandIndex;
        }
        hoveredHandIndex = -1;
        return -1;
    }

    private boolean handHoverStickyAt(double mouseX, double mouseY, List<CardInstance> visibleCards, HandLayout layout, int index, float partialTick) {
        if (draggingHandCard()) {
            return false;
        }
        if (index < 0 || index >= visibleCards.size()) {
            return false;
        }
        return hoveredHandPreviewBounds(visibleCards, layout, index, partialTick).contains(mouseX, mouseY);
    }

    private CardPreviewBounds hoveredHandPreviewBounds(List<CardInstance> visibleCards, HandLayout layout, int index, float partialTick) {
        HandCardBounds baseBounds = layout.card(index);
        HandCardAnimation animation = handAnimations.get(visibleCards.get(index).id());
        if (animation == null) {
            return previewBounds(baseBounds);
        }
        float progress = smoothStep(animation.hover(partialTick));
        CardPreviewBounds preview = previewBounds(baseBounds);
        float baseScale = animation.scale(partialTick);
        float scale = lerp(baseScale, handPreviewScale(), progress);
        float centerX = lerp(animation.x(partialTick), preview.centerX(), progress);
        float centerY = lerp(animation.y(partialTick), preview.centerY(), progress);
        int previewW = Math.round(CardRenderHelper.SMALL_CARD_WIDTH * scale);
        int previewH = Math.round(CardRenderHelper.SMALL_CARD_HEIGHT * scale);
        int x = Math.round(centerX - previewW / 2.0F);
        int y = Math.round(centerY - previewH / 2.0F);
        return new CardPreviewBounds(x, y, previewW, previewH);
    }

    private HandCardBounds centerTargetingHandBounds(HandLayout layout) {
        float centerX = width / 2.0F;
        float scale = HAND_BASE_SCALE;
        if (!layout.cards().isEmpty()) {
            scale = Math.max(HAND_MIN_SCALE, layout.card(layout.cards().size() / 2).scale());
        }
        int cardW = Math.round(CardRenderHelper.SMALL_CARD_WIDTH * scale);
        int cardH = Math.round(CardRenderHelper.SMALL_CARD_HEIGHT * scale);
        centerX = Math.max(8.0F + cardW / 2.0F, Math.min(width - cardW / 2.0F - 8.0F, centerX));
        int bottom = Math.round(layout().resolve("hand", width, height).bottom() - 16.0F);
        float centerY = bottom - cardH / 2.0F;
        centerY = Math.max(48.0F + cardH / 2.0F, Math.min(height - cardH / 2.0F - 8.0F, centerY));
        return new HandCardBounds(centerX, centerY, 0.0F, scale);
    }

    private CardPreviewBounds previewBounds(HandCardBounds cardBounds) {
        int previewW = handPreviewWidth();
        int previewH = handPreviewHeight();
        int x = Math.round(cardBounds.centerX() - previewW / 2.0F);
        x = Math.max(8, Math.min(width - previewW - 8, x));
        int bottom = Math.round(layout().resolve("hand", width, height).bottom() - 16.0F);
        int y = bottom - previewH;
        y = Math.max(48, Math.min(height - previewH - 8, y));
        return new CardPreviewBounds(x, y, previewW, previewH);
    }

    private CardPreviewBounds handSelectionPreviewBounds(BattleSnapshot snapshot, int handIndex, float partialTick) {
        int previewW = handPreviewWidth();
        int previewH = handPreviewHeight();
        HandCardBounds base = handSelectionStableBounds(snapshot, handIndex);
        float centerX = clampSmallPreviewCenterX(base.centerX(), handSelectionPreviewScale());
        float centerY = clampSmallPreviewCenterY(base.centerY(), handSelectionPreviewScale());
        int x = Math.round(centerX - previewW / 2.0F);
        int y = Math.round(centerY - previewH / 2.0F);
        return new CardPreviewBounds(x, y, previewW, previewH);
    }

    private HandCardBounds handSelectionStableBounds(BattleSnapshot snapshot, int handIndex) {
        if (handIndex < 0 || handIndex >= snapshot.hand().size()) {
            return new HandCardBounds(width / 2.0F, height / 2.0F, 0.0F, HAND_SELECTION_CARD_SCALE);
        }
        CardInstance card = snapshot.hand().get(handIndex);
        int selectedIndex = handSelectionOverlay.selectedIndex(card.id());
        if (selectedIndex >= 0) {
            return handSelectionSelectedBounds(selectedIndex, handSelectionOverlay.selectedCount());
        }
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        int visibleIndex = visibleCardsIndex(visibleCards, card.id());
        if (visibleIndex >= 0) {
            return handLayout(visibleCards).card(visibleIndex);
        }
        HandCardAnimation animation = handAnimations.get(card.id());
        if (animation != null) {
            return new HandCardBounds(animation.currentX(), animation.currentY(), 0.0F, animation.currentScale());
        }
        return new HandCardBounds(width / 2.0F, height / 2.0F, 0.0F, HAND_SELECTION_CARD_SCALE);
    }

    private float handPreviewScale() {
        return handPreviewWidth() / (float) CardRenderHelper.SMALL_CARD_WIDTH;
    }

    private float handSelectionPreviewScale() {
        return handPreviewScale();
    }

    private int handPreviewWidth() {
        return Math.round(CardRenderHelper.CARD_WIDTH * HAND_PREVIEW_SCALE);
    }

    private int handPreviewHeight() {
        return Math.round(CardRenderHelper.SMALL_CARD_HEIGHT * handPreviewScale());
    }

    private int combatantEntryUnderMouse(double mouseX, double mouseY, BattleSnapshot snapshot) {
        int playerTarget = combatantEntryUnderMouse(mouseX, mouseY, liveCombatants(snapshot.players()), layout().resolve("player_entry", width, height), false);
        if (playerTarget != -1) {
            return playerTarget;
        }
        return combatantEntryUnderMouse(mouseX, mouseY, liveCombatants(snapshot.enemies()), layout().resolve("monster_entry", width, height), true);
    }

    private int combatantEntryUnderMouse(double mouseX, double mouseY, List<BattleCombatantSnapshot> entries, MoonSpireUiRect baseRect, boolean enemyArea) {
        MoonSpireUiRect area = entryAreaRect(baseRect);
        if (!area.contains(mouseX, mouseY)) {
            return -1;
        }
        double scroll = clampEntryScroll(entries.size(), baseRect, enemyArea);
        for (int i = 0; i < entries.size(); i++) {
            MoonSpireUiRect row = entryRowRect(baseRect, i, scroll);
            if (row.bottom() < area.y() || row.y() > area.bottom()) {
                continue;
            }
            if (row.contains(mouseX, mouseY)) {
                return entries.get(i).entityId();
            }
        }
        return -1;
    }

    private int interactiveTargetUnderMouse(double mouseX, double mouseY, BattleSnapshot snapshot) {
        int entryTarget = combatantEntryUnderMouse(mouseX, mouseY, snapshot);
        if (entryTarget != -1) {
            return entryTarget;
        }
        return targetingBlockedByUiAt(mouseX, mouseY, snapshot) ? -1 : targetEntityUnderMouse(mouseX, mouseY, snapshot);
    }

    private int selectableTargetUnderMouse(double mouseX, double mouseY, BattleSnapshot snapshot) {
        int target = interactiveTargetUnderMouse(mouseX, mouseY, snapshot);
        return snapshot.isEnemyEntity(target) && !combatantFakeDead(snapshot, target) ? target : -1;
    }

    private int targetEntityUnderMouse(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (frameCache.matches(snapshot, mouseX, mouseY)) {
            return frameCache.targetEntity();
        }
        return directTargetEntityUnderMouse(mouseX, mouseY, snapshot);
    }

    private int directTargetEntityUnderMouse(double mouseX, double mouseY, BattleSnapshot snapshot) {
        int entryTarget = combatantEntryUnderMouse(mouseX, mouseY, snapshot);
        if (entryTarget != -1) {
            return entryTarget;
        }
        return worldTargetUnderMouse(mouseX, mouseY, snapshot);
    }

    private int directExplicitTargetUnderMouse(CardInstance card, double mouseX, double mouseY, BattleSnapshot snapshot) {
        int target = directTargetEntityUnderMouse(mouseX, mouseY, snapshot);
        return validDraggedTarget(card, target, snapshot) ? target : -1;
    }

    private boolean targetingBlockedByUiAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (frameCache.matches(snapshot, mouseX, mouseY)) {
            return frameCache.targetingBlocked();
        }
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        return pileOverlay != null
                || dragState != null
                || directVisibleHandIndexAt(mouseX, mouseY, visibleCards, handLayout(visibleCards)) >= 0
                || handPreviewAt(mouseX, mouseY, snapshot)
                || drawPileAt(mouseX, mouseY)
                || discardPileAt(mouseX, mouseY)
                || visibleExhaustPileAt(mouseX, mouseY, snapshot)
                || endTurnButtonAt(mouseX, mouseY)
                || monsterIntentCardAt(mouseX, mouseY, snapshot);
    }

    private boolean handPreviewAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (frameCache.matches(snapshot, mouseX, mouseY)) {
            return frameCache.previewIndex() >= 0;
        }
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        if (hoveredHandIndex < 0 || hoveredHandIndex >= visibleCards.size()) {
            return false;
        }
        HandLayout layout = handLayout(visibleCards);
        return previewBounds(layout.card(hoveredHandIndex)).contains(mouseX, mouseY);
    }

    private boolean monsterIntentCardAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        return monsterIntentSmallCardAt(mouseX, mouseY, snapshot) || monsterIntentPreviewAt(mouseX, mouseY, snapshot);
    }

    private boolean monsterIntentSmallCardAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        List<CardInstance> intentCards = intentCardsFor(snapshot, currentIntentEntityId(snapshot));
        if (intentCards.isEmpty() || !hasSelectedOrHoveredIntentCombatant(snapshot)) {
            return false;
        }
        MoonSpireUiRect rect = layout().resolve("monster_intent", width, height);
        int spacing = 54;
        int total = (intentCards.size() - 1) * spacing + CardRenderHelper.SMALL_CARD_WIDTH;
        int x = rect.x() + rect.width() / 2 - total / 2;
        int y = rect.y();
        for (int i = 0; i < intentCards.size(); i++) {
            int cardX = x + i * spacing;
            if (mouseX >= cardX && mouseX <= cardX + CardRenderHelper.SMALL_CARD_WIDTH && mouseY >= y && mouseY <= y + CardRenderHelper.SMALL_CARD_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    private boolean monsterIntentPreviewAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        IntentPreviewBounds preview = monsterIntentPreviewBounds(mouseX, mouseY, snapshot);
        return preview != null && preview.contains(mouseX, mouseY);
    }

    private IntentPreviewBounds monsterIntentPreviewBounds(double mouseX, double mouseY, BattleSnapshot snapshot) {
        int intentEntityId = currentIntentEntityId(snapshot);
        List<CardInstance> intentCards = intentCardsFor(snapshot, intentEntityId);
        if (intentCards.isEmpty() || hoveredMonsterIntentEntityId != intentEntityId || hoveredMonsterIntentIndex < 0 || hoveredMonsterIntentIndex >= intentCards.size()) {
            return null;
        }
        MoonSpireUiRect rect = layout().resolve("monster_intent", width, height);
        int spacing = 54;
        int total = (intentCards.size() - 1) * spacing + CardRenderHelper.SMALL_CARD_WIDTH;
        int x = rect.x() + rect.width() / 2 - total / 2;
        int y = rect.y();
        int cardX = x + hoveredMonsterIntentIndex * spacing;
        return intentPreviewBoundsForSmallCard(cardX, y);
    }

    private IntentPreviewBounds intentPreviewBoundsForSmallCard(int cardX, int cardY) {
        int previewW = Math.round(CardRenderHelper.CARD_WIDTH * HAND_PREVIEW_SCALE);
        int previewH = Math.round(CardRenderHelper.CARD_HEIGHT * HAND_PREVIEW_SCALE);
        int previewX = Math.round(cardX + (CardRenderHelper.SMALL_CARD_WIDTH - previewW) / 2.0F);
        int previewY = Math.round(cardY + (CardRenderHelper.SMALL_CARD_HEIGHT - previewH) / 2.0F);
        previewX = Math.max(8, Math.min(width - previewW - 8, previewX));
        previewY = Math.max(8, Math.min(height - previewH - 8, previewY));
        float previewScale = previewW / (float) CardRenderHelper.SMALL_CARD_WIDTH;
        return new IntentPreviewBounds(previewX, previewY, previewW, previewH, previewScale);
    }

    private boolean drawPileAt(double mouseX, double mouseY) {
        return layout().resolve("draw_pile", width, height).contains(mouseX, mouseY);
    }

    private boolean discardPileAt(double mouseX, double mouseY) {
        return layout().resolve("discard_pile", width, height).contains(mouseX, mouseY);
    }

    private boolean exhaustPileAt(double mouseX, double mouseY) {
        return layout().resolve("exhaust_pile", width, height).contains(mouseX, mouseY);
    }

    private boolean scrollEntries(double mouseX, double mouseY, double scrollY, BattleSnapshot snapshot) {
        if (scrollY == 0.0D) {
            return false;
        }
        MoonSpireUiRect playerBase = layout().resolve("player_entry", width, height);
        if (entryAreaRect(playerBase).contains(mouseX, mouseY) && maxEntryScroll(liveCombatants(snapshot.players()).size(), playerBase) > 0.0D) {
            playerEntryScroll = clamp(playerEntryScroll - scrollY * ENTRY_SCROLL_STEP, 0.0D, maxEntryScroll(liveCombatants(snapshot.players()).size(), playerBase));
            return true;
        }
        MoonSpireUiRect enemyBase = layout().resolve("monster_entry", width, height);
        if (entryAreaRect(enemyBase).contains(mouseX, mouseY) && maxEntryScroll(liveCombatants(snapshot.enemies()).size(), enemyBase) > 0.0D) {
            enemyEntryScroll = clamp(enemyEntryScroll - scrollY * ENTRY_SCROLL_STEP, 0.0D, maxEntryScroll(liveCombatants(snapshot.enemies()).size(), enemyBase));
            return true;
        }
        return false;
    }

    private double clampEntryScroll(int entryCount, MoonSpireUiRect baseRect, boolean enemyArea) {
        double max = maxEntryScroll(entryCount, baseRect);
        if (enemyArea) {
            enemyEntryScroll = clamp(enemyEntryScroll, 0.0D, max);
            return enemyEntryScroll;
        }
        playerEntryScroll = clamp(playerEntryScroll, 0.0D, max);
        return playerEntryScroll;
    }

    private double maxEntryScroll(int entryCount, MoonSpireUiRect baseRect) {
        int contentHeight = entryCount <= 0 ? 0 : entryCount * ENTRY_OCCUPIED_HEIGHT + Math.max(0, entryCount - 1) * ENTRY_GAP;
        return Math.max(0.0D, contentHeight - entryAreaRect(baseRect).height());
    }

    private static List<BattleCombatantSnapshot> liveCombatants(List<BattleCombatantSnapshot> combatants) {
        List<BattleCombatantSnapshot> live = new ArrayList<>();
        for (BattleCombatantSnapshot combatant : combatants) {
            if (!combatant.fakeDead()) {
                live.add(combatant);
            }
        }
        return live;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean visibleExhaustPileAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        return snapshot.exhaustPile() > 0 && exhaustPileAt(mouseX, mouseY);
    }

    private boolean endTurnButtonAt(double mouseX, double mouseY) {
        return layout().resolve("end_turn", width, height).contains(mouseX, mouseY);
    }

    private int worldTargetUnderMouse(double mouseX, double mouseY, BattleSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gameRenderer == null || width <= 0 || height <= 0) {
            return -1;
        }
        var camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return -1;
        }
        float planeX = (float) (mouseX / (double) width * 2.0D - 1.0D);
        float planeY = (float) (1.0D - mouseY / (double) height * 2.0D);
        planeX = Math.max(-1.0F, Math.min(1.0F, planeX));
        planeY = Math.max(-1.0F, Math.min(1.0F, planeY));
        Vec3 start = camera.getPosition();
        Vec3 nearPoint = camera.getNearPlane().getPointOnPlane(planeX, planeY);
        if (nearPoint.lengthSqr() < 1.0E-7D) {
            return -1;
        }
        Vec3 end = start.add(nearPoint.normalize().scale(48.0D));
        int bestEntityId = -1;
        double bestDistance = Double.MAX_VALUE;
        for (BattleCombatantSnapshot player : snapshot.players()) {
            if (player.fakeDead()) {
                continue;
            }
            Entity entity = minecraft.level.getEntity(player.entityId());
            if (entity != null) {
                AABB box = entity.getBoundingBox().inflate(0.25D);
                var hit = box.clip(start, end);
                if (hit.isPresent()) {
                    double distance = start.distanceToSqr(hit.get());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestEntityId = player.entityId();
                    }
                }
            }
        }
        for (BattleCombatantSnapshot enemy : snapshot.enemies()) {
            if (enemy.fakeDead()) {
                continue;
            }
            Entity entity = minecraft.level.getEntity(enemy.entityId());
            if (entity != null) {
                AABB box = entity.getBoundingBox().inflate(0.25D);
                var hit = box.clip(start, end);
                if (hit.isPresent()) {
                    double distance = start.distanceToSqr(hit.get());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestEntityId = enemy.entityId();
                    }
                }
            }
        }
        return bestEntityId;
    }

    private boolean playDraggedCard(double mouseX, double mouseY) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        CardInstance card = draggedCard(snapshot);
        if (!ClientBattleState.playerTurn() || cardActionsLocked(snapshot) || card == null) {
            return false;
        }
        if (card.cost() > snapshot.player().energyLeft()) {
            return false;
        }
        int targetId = primaryTargetForCard(card, snapshot);
        if (card.requiresExplicitTarget()) {
            int pointed = targetEntityUnderMouse(mouseX, mouseY, snapshot);
            if (!validDraggedTarget(card, pointed, snapshot)) {
                return false;
            }
            targetId = pointed;
        } else {
            if (!playAreaContains(mouseX, mouseY)) {
                return false;
            }
            List<Integer> highlighted = highlightedTargetsForDraggedCard(card, snapshot, mouseX, mouseY);
            if (!highlighted.isEmpty()) {
                targetId = highlighted.get(0);
            }
        }
        float startX = (float) mouseX;
        float startY = (float) mouseY;
        if (card.requiresExplicitTarget()) {
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null) {
                startX = animation.currentX();
                startY = animation.currentY();
            } else {
                HandLayout layout = handLayout(snapshot);
                if (dragState.handIndex() >= 0 && dragState.handIndex() < layout.cards().size()) {
                    HandCardBounds bounds = layout.card(dragState.handIndex());
                    startX = bounds.centerX();
                    startY = bounds.centerY();
                }
            }
        }
        awaitingUseCardSnapshot = true;
        locallyUsedCardIds.add(card.id());
        flyingCards.add(FlyingCardAnimation.played(card, startX, startY, battlefieldCenterX(), battlefieldCenterY(), discardPileCenterX(), discardPileCenterY(), card.effects().stream().anyMatch(effect -> effect.kind() == CardEffectKind.EXHAUST)));
        PacketDistributor.sendToServer(new UseCardPayload(dragState.handIndex(), targetId));
        return true;
    }

    private static String handSelectionChooseKey(PendingHandSelectionSnapshot.Action action) {
        return switch (action) {
            case EXHAUST -> "screen.moonspire.hand_selection.choose_exhaust";
            case CONSUME_ARROW -> "screen.moonspire.hand_selection.choose_arrow";
            case DISCARD, NONE -> "screen.moonspire.hand_selection.choose_discard";
        };
    }

    private static String handSelectionConfirmKey(PendingHandSelectionSnapshot.Action action) {
        return switch (action) {
            case EXHAUST -> "screen.moonspire.hand_selection.confirm_exhaust";
            case CONSUME_ARROW -> "screen.moonspire.hand_selection.confirm_arrow";
            case DISCARD, NONE -> "screen.moonspire.hand_selection.confirm_discard";
        };
    }

    private boolean cardActionsLocked(BattleSnapshot snapshot) {
        return snapshot.resolvingEffects() || snapshot.pendingHandSelection().active() || handSelectionConfirmation.active() || awaitingUseCardSnapshot || awaitingEndTurnSnapshot || snapshot.localPlayerEndedTurn();
    }

    private CardInstance draggedCard(BattleSnapshot snapshot) {
        if (dragState == null || dragState.handIndex() < 0 || dragState.handIndex() >= snapshot.hand().size()) {
            return null;
        }
        CardInstance card = snapshot.hand().get(dragState.handIndex());
        return card.id().equals(dragState.cardId()) ? card : null;
    }

    private int primaryTargetForCard(CardInstance card, BattleSnapshot snapshot) {
        if (card.targetsEnemy() && !card.targetsSelf()) {
            for (BattleCombatantSnapshot enemy : snapshot.enemies()) {
                if (!enemy.fakeDead()) {
                    return enemy.entityId();
                }
            }
            return snapshot.monster().entityId();
        }
        return snapshot.player().entityId();
    }

    private List<Integer> highlightedTargetsForDraggedCard(CardInstance card, BattleSnapshot snapshot, double mouseX, double mouseY) {
        if (card == null || !playableDraggedCardAt(card, snapshot, mouseX, mouseY)) {
            return List.of();
        }
        return highlightedTargetsForCard(card, snapshot, targetEntityUnderMouse(mouseX, mouseY, snapshot));
    }

    private List<Integer> highlightedTargetsForCard(CardInstance card, BattleSnapshot snapshot, int pointedTarget) {
        int bestCount = 0;
        LinkedHashSet<Integer> targets = new LinkedHashSet<>();
        for (CardEffect effect : card.effects()) {
            if (effect.amount() <= 0) {
                continue;
            }
            if (!effect.kind().usesTarget()) {
                continue;
            }
            List<Integer> effectTargets = targetIdsForEffectTarget(effect.target(), snapshot, false, pointedTarget);
            if (effectTargets.size() > bestCount) {
                bestCount = effectTargets.size();
                targets.clear();
            }
            if (effectTargets.size() == bestCount) {
                targets.addAll(effectTargets);
            }
        }
        if (card.requiresExplicitTarget() && pointedTarget != -1) {
            boolean needsEnemy = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target() == CardTarget.SINGLE_ENEMY);
            boolean needsAlly = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target() == CardTarget.SINGLE_ALLY);
            if (needsEnemy && snapshot.isEnemyEntity(pointedTarget) && !combatantFakeDead(snapshot, pointedTarget)) {
                targets.removeIf(entityId -> snapshot.isEnemyEntity(entityId) && entityId != pointedTarget);
            }
            if (needsAlly && snapshot.isPlayerEntity(pointedTarget) && !combatantFakeDead(snapshot, pointedTarget)) {
                targets.removeIf(entityId -> snapshot.isPlayerEntity(entityId) && entityId != pointedTarget);
            }
        }
        return List.copyOf(targets);
    }

    private List<Integer> targetIdsForEffectTarget(CardTarget target, BattleSnapshot snapshot, boolean monsterCard) {
        return targetIdsForEffectTarget(target, snapshot, monsterCard, -1, monsterCard ? snapshot.monster().entityId() : snapshot.player().entityId());
    }

    private List<Integer> targetIdsForEffectTarget(CardTarget target, BattleSnapshot snapshot, boolean monsterCard, int pointedTarget) {
        return targetIdsForEffectTarget(target, snapshot, monsterCard, pointedTarget, monsterCard ? snapshot.monster().entityId() : snapshot.player().entityId());
    }

    private List<Integer> targetIdsForEffectTarget(CardTarget target, BattleSnapshot snapshot, boolean monsterCard, int pointedTarget, int selfEntityId) {
        List<Integer> allies = liveIds(monsterCard ? snapshot.enemies() : snapshot.players());
        List<Integer> enemies = liveIds(monsterCard ? snapshot.players() : snapshot.enemies());
        int self = allies.contains(selfEntityId) ? selfEntityId : monsterCard ? snapshot.monster().entityId() : snapshot.player().entityId();
        int opponent = enemies.contains(pointedTarget) ? pointedTarget : enemies.isEmpty() ? -1 : enemies.getFirst();
        return switch (target) {
            case SELF, ALL_ALLIES -> List.of(self);
            case SINGLE_ALLY -> List.of();
            case SINGLE_ENEMY, RANDOM_ENEMY -> opponent < 0 ? List.of() : List.of(opponent);
            case ALL_ENEMIES -> enemies;
            case ALL_UNITS -> {
                List<Integer> all = new ArrayList<>(allies);
                all.addAll(enemies);
                yield all;
            }
            case ALL_OTHER_UNITS -> enemies;
            case ALL_OTHER_ALLIES, RANDOM_ALLY -> List.of();
        };
    }

    private int primaryOpponentEntityId(BattleSnapshot snapshot, boolean enemySideActor) {
        List<Integer> opponents = liveIds(enemySideActor ? snapshot.players() : snapshot.enemies());
        return opponents.isEmpty() ? -1 : opponents.getFirst();
    }

    private List<Integer> liveIds(List<BattleCombatantSnapshot> combatants) {
        List<Integer> ids = new ArrayList<>();
        for (BattleCombatantSnapshot combatant : combatants) {
            if (!combatant.fakeDead()) {
                ids.add(combatant.entityId());
            }
        }
        return ids;
    }

    private boolean combatantFakeDead(BattleSnapshot snapshot, int entityId) {
        BattleCombatantSnapshot combatant = snapshot.combatant(entityId);
        return combatant != null && combatant.fakeDead();
    }

    private boolean validDraggedTarget(CardInstance card, int targetEntityId, BattleSnapshot snapshot) {
        if (targetEntityId == -1) {
            return false;
        }
        boolean needsEnemy = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target() == CardTarget.SINGLE_ENEMY);
        boolean needsAlly = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind().usesTarget() && effect.target() == CardTarget.SINGLE_ALLY);
        if (needsEnemy && (!snapshot.isEnemyEntity(targetEntityId) || combatantFakeDead(snapshot, targetEntityId))) {
            return false;
        }
        if (needsAlly && (!snapshot.isPlayerEntity(targetEntityId) || targetEntityId == snapshot.player().entityId() || combatantFakeDead(snapshot, targetEntityId))) {
            return false;
        }
        return !needsEnemy || !needsAlly;
    }

    private boolean hasSelectedOrHoveredIntentCombatant(BattleSnapshot snapshot) {
        int selected = ClientBattleState.selectedTargetId();
        if (snapshot.hasIntentCardsFor(selected) && !combatantFakeDead(snapshot, selected)) {
            return true;
        }
        return snapshot.players().stream().anyMatch(player -> !player.fakeDead() && player.entityId() != snapshot.localPlayerEntityId() && snapshot.hasIntentCardsFor(player.entityId()) && ClientBattleState.isHoveredEntityId(player.entityId()))
                || snapshot.enemies().stream().anyMatch(enemy -> !enemy.fakeDead() && snapshot.hasIntentCardsFor(enemy.entityId()) && ClientBattleState.isHoveredEntityId(enemy.entityId()));
    }

    private boolean shouldRenderMonsterIntent(BattleSnapshot snapshot, double mouseX, double mouseY) {
        return hasSelectedOrHoveredIntentCombatant(snapshot) && !draggingTargetedCardAtMonster(snapshot, mouseX, mouseY);
    }

    private boolean draggingTargetedCardAtMonster(BattleSnapshot snapshot, double mouseX, double mouseY) {
        CardInstance card = draggedCard(snapshot);
        int target = directTargetEntityUnderMouse(mouseX, mouseY, snapshot);
        return card != null && card.requiresExplicitTarget() && snapshot.isEnemyEntity(target) && !combatantFakeDead(snapshot, target);
    }

    private HandLayout handLayout(BattleSnapshot snapshot) {
        return handLayout(snapshot.hand());
    }

    private HandLayout handLayout(List<CardInstance> handCards) {
        int count = handCards.size();
        if (count <= 0) {
            return new HandLayout(List.of());
        }
        MoonSpireUiRect handRect = layout().resolve("hand", width, height);
        int sideMargin = Math.min(HAND_SIDE_MARGIN, Math.max(12, handRect.width() / 12));
        int spreadWidth = Math.max(CardRenderHelper.SMALL_CARD_WIDTH, handRect.width() - sideMargin * 2);
        float scale = Math.max(HAND_MIN_SCALE, Math.min(HAND_BASE_SCALE, spreadWidth / (float) (CardRenderHelper.SMALL_CARD_WIDTH + Math.max(0, count - 1) * HAND_MAX_SPACING)));
        int maxSpacingForWidth = count <= 1 ? 0 : Math.round((spreadWidth - CardRenderHelper.SMALL_CARD_WIDTH * scale) / Math.max(1.0F, count - 1));
        int spacing = count <= 1 ? 0 : Math.max(HAND_MIN_SPACING, Math.min(HAND_MAX_SPACING, maxSpacingForWidth));
        float centerX = handRect.x() + handRect.width() / 2.0F;
        float baseline = handRect.bottom() + HAND_BASELINE_OFFSET;
        float middle = (count - 1) / 2.0F;
        float flatness = count <= 4 ? 0.45F : count >= 9 ? 1.0F : 0.45F + (count - 4) * 0.11F;
        float arcDrop = HAND_ARC_DROP * flatness;
        float lift = 56.0F + Math.min(10.0F, count * 1.2F);
        float angleLimit = HAND_EDGE_ANGLE * (count <= 3 ? 0.55F : count <= 5 ? 0.72F : 1.0F);
        List<HandCardBounds> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            float offset = i - middle;
            float normalized = middle <= 0.0F ? 0.0F : offset / middle;
            float cardX = centerX + offset * spacing;
            float arc = 0.35F * Math.abs(normalized) + 0.65F * normalized * normalized;
            float cardY = baseline - lift + arc * arcDrop;
            float angle = normalized * angleLimit;
            cards.add(new HandCardBounds(cardX, cardY, angle, scale));
        }
        return new HandLayout(cards);
    }

    private List<CardInstance> visibleHandCards(BattleSnapshot snapshot) {
        List<CardInstance> visibleCards = new ArrayList<>();
        Set<UUID> hiddenIds = new HashSet<>();
        if (dragState != null) {
            hiddenIds.add(dragState.cardId());
        }
        hiddenIds.addAll(locallyUsedCardIds);
        if (snapshot.pendingHandSelection().active()) {
            hiddenIds.addAll(handSelectionOverlay.selectedIds());
        }
        hiddenIds.addAll(handSelectionConfirmation.selectedIds());
        Set<UUID> selectionVisibleIds = snapshot.pendingHandSelection().active()
                ? new HashSet<>(snapshot.pendingHandSelection().candidateCardIds())
                : Set.of();
        if (snapshot.pendingHandSelection().active()) {
            selectionVisibleIds.addAll(handSelectionOverlay.selectedIds());
        }
        for (CardInstance card : snapshot.hand()) {
            if (!hiddenIds.contains(card.id()) && (!snapshot.pendingHandSelection().active() || selectionVisibleIds.contains(card.id()))) {
                visibleCards.add(card);
            }
        }
        return visibleCards;
    }

    private int visibleCardsIndex(List<CardInstance> cards, UUID cardId) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).id().equals(cardId)) {
                return i;
            }
        }
        return -1;
    }

    private int handIndexForCardId(BattleSnapshot snapshot, UUID cardId) {
        for (int i = 0; i < snapshot.hand().size(); i++) {
            if (snapshot.hand().get(i).id().equals(cardId)) {
                return i;
            }
        }
        return -1;
    }

    private UUID selectedHandCardId(BattleSnapshot snapshot, int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= snapshot.hand().size()) {
            return null;
        }
        return snapshot.hand().get(selectedIndex).id();
    }

    private int handBottomReserve() {
        MoonSpireUiRect handRect = layout().resolve("hand", width, height);
        return Math.max(112, height - handRect.y() + 8);
    }

    private FrameCache createFrameCache(BattleSnapshot snapshot, double mouseX, double mouseY, float partialTick) {
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        HandLayout layout = handLayout(visibleCards);
        boolean draggingHandCard = draggingHandCard();
        int directHandIndex = draggingHandCard ? -1 : directVisibleHandIndexAt(mouseX, mouseY, visibleCards, layout);
        int previewIndex = -1;
        int nextHovered = draggingHandCard ? -1 : hoveredHandIndex;
        if (draggingHandCard || visibleCards.isEmpty()) {
            nextHovered = -1;
        } else if (nextHovered >= visibleCards.size()) {
            nextHovered = -1;
        } else {
            int previousHover = nextHovered;
            if (handHoverStickyAt(mouseX, mouseY, visibleCards, layout, previousHover, partialTick)) {
                previewIndex = previousHover;
            } else if (directHandIndex >= 0 && directHandIndex != previousHover) {
                nextHovered = directHandIndex;
            } else {
                nextHovered = -1;
            }
        }
        if (previewIndex < 0) {
            if (nextHovered >= 0 && handHoverStickyAt(mouseX, mouseY, visibleCards, layout, nextHovered, partialTick)) {
                previewIndex = nextHovered;
            } else if (nextHovered < 0 && directHandIndex >= 0) {
                nextHovered = directHandIndex;
            }
        }
        CardInstance dragged = draggedCard(snapshot);
        boolean draggingAttack = dragged != null && dragged.requiresExplicitTarget();
        boolean blocked = pileOverlay != null
                || dragState != null
                || directHandIndex >= 0
                || previewIndex >= 0
                || drawPileAt(mouseX, mouseY)
                || discardPileAt(mouseX, mouseY)
                || visibleExhaustPileAt(mouseX, mouseY, snapshot)
                || endTurnButtonAt(mouseX, mouseY)
                || monsterIntentCardAt(mouseX, mouseY, snapshot);
        int targetEntity;
        if (draggingAttack) {
            targetEntity = directExplicitTargetUnderMouse(dragged, mouseX, mouseY, snapshot);
        } else {
            targetEntity = !blocked ? directTargetEntityUnderMouse(mouseX, mouseY, snapshot) : -1;
        }
        return new FrameCache(frameIndex, snapshot, mouseX, mouseY, List.copyOf(visibleCards), layout, directHandIndex, previewIndex, nextHovered, targetEntity, blocked);
    }

    private MoonSpireUiLayout layout() {
        return MoonSpireBattleLayoutEditor.layout();
    }

    private Component entityName(int entityId, Component fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(entityId) != null) {
            return minecraft.level.getEntity(entityId).getDisplayName();
        }
        return fallback;
    }

    private static final class DragState {
        private final int handIndex;
        private final UUID cardId;
        private boolean wasPlayable;
        private float playablePulseTicks;

        private DragState(int handIndex, UUID cardId) {
            this.handIndex = handIndex;
            this.cardId = cardId;
        }

        private int handIndex() {
            return handIndex;
        }

        private UUID cardId() {
            return cardId;
        }

        private float updatePlayable(boolean playable, float deltaTicks) {
            if (playable && !wasPlayable) {
                playablePulseTicks = DRAG_PLAYABLE_PULSE_TICKS;
            } else if (playablePulseTicks > 0.0F) {
                playablePulseTicks = Math.max(0.0F, playablePulseTicks - deltaTicks);
            }
            wasPlayable = playable;
            return clamp(playablePulseTicks / DRAG_PLAYABLE_PULSE_TICKS, 0.0F, 1.0F);
        }
    }

    private record DragGlow(float pulse) {
    }

    private record FrameCache(long frame, BattleSnapshot snapshot, double mouseX, double mouseY, List<CardInstance> visibleCards, HandLayout layout, int handIndex, int previewIndex, int hoveredIndex, int targetEntity, boolean targetingBlocked) {
        private static FrameCache empty() {
            return new FrameCache(-1L, BattleSnapshot.inactive(), Double.NaN, Double.NaN, List.of(), new HandLayout(List.of()), -1, -1, -1, -1, false);
        }

        private boolean matches(BattleSnapshot snapshot, double mouseX, double mouseY) {
            return this.snapshot == snapshot && Double.compare(this.mouseX, mouseX) == 0 && Double.compare(this.mouseY, mouseY) == 0;
        }

        private HandLayout layout(BattleSnapshot snapshot) {
            if (this.snapshot != snapshot) {
                return new HandLayout(List.of());
            }
            return layout;
        }

        private List<CardInstance> visibleCards(BattleSnapshot snapshot) {
            return this.snapshot == snapshot ? visibleCards : List.of();
        }

        private int targetEntity(double mouseX, double mouseY, BattleSnapshot snapshot) {
            return matches(snapshot, mouseX, mouseY) ? targetEntity : -1;
        }
    }

    private record ButtonRect(int x, int y, int w, int h) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    private static final class HandSelectionOverlay {
        private PendingHandSelectionSnapshot.Action action = PendingHandSelectionSnapshot.Action.NONE;
        private List<UUID> candidates = List.of();
        private final List<UUID> selectedIds = new ArrayList<>();
        private boolean confirming;

        private static HandSelectionOverlay empty() {
            return new HandSelectionOverlay();
        }

        private void sync(PendingHandSelectionSnapshot selection, BattleSnapshot snapshot) {
            if (!selection.active()) {
                clearIfInactive();
                return;
            }
            if (action != selection.action() || !candidates.equals(selection.candidateCardIds())) {
                action = selection.action();
                candidates = List.copyOf(selection.candidateCardIds());
                selectedIds.clear();
                confirming = false;
            }
            selectedIds.removeIf(id -> !candidates.contains(id) || snapshot.hand().stream().noneMatch(card -> card.id().equals(id)));
        }

        private void clearIfInactive() {
            action = PendingHandSelectionSnapshot.Action.NONE;
            candidates = List.of();
            selectedIds.clear();
            confirming = false;
        }

        private void clearConfirmed() {
            selectedIds.clear();
            confirming = false;
        }

        private void resetForRetry(PendingHandSelectionSnapshot selection, BattleSnapshot snapshot) {
            action = selection.action();
            candidates = List.copyOf(selection.candidateCardIds());
            selectedIds.clear();
            confirming = false;
            selectedIds.removeIf(id -> !candidates.contains(id) || snapshot.hand().stream().noneMatch(card -> card.id().equals(id)));
        }

        private void toggle(UUID cardId, int requiredCount) {
            if (confirming) {
                return;
            }
            if (selectedIds.remove(cardId)) {
                return;
            }
            if (selectedIds.size() < requiredCount) {
                selectedIds.add(cardId);
            }
        }

        private boolean ready(PendingHandSelectionSnapshot selection) {
            return selectedIds.size() >= selection.requiredCount();
        }

        private boolean isSelected(UUID cardId) {
            return selectedIds.contains(cardId);
        }

        private int selectedIndex(UUID cardId) {
            return selectedIds.indexOf(cardId);
        }

        private int selectedCount() {
            return selectedIds.size();
        }

        private List<UUID> selectedIds() {
            return List.copyOf(selectedIds);
        }

        private void confirm() {
            confirming = true;
        }

        private boolean confirming() {
            return confirming;
        }
    }

    private record HandSelectionConfirmation(boolean active, PendingHandSelectionSnapshot.Action action, List<UUID> candidates, List<UUID> selectedIds) {
        private static HandSelectionConfirmation empty() {
            return new HandSelectionConfirmation(false, PendingHandSelectionSnapshot.Action.NONE, List.of(), List.of());
        }

        private static HandSelectionConfirmation of(PendingHandSelectionSnapshot selection, List<UUID> selectedIds) {
            return new HandSelectionConfirmation(true, selection.action(), List.copyOf(selection.candidateCardIds()), List.copyOf(selectedIds));
        }

        private boolean matches(PendingHandSelectionSnapshot selection) {
            return active && selection.active() && action == selection.action() && candidates.equals(selection.candidateCardIds());
        }

    }

    private record HandLayout(List<HandCardBounds> cards) {
        private HandCardBounds card(int index) {
            return cards.get(index);
        }
    }

    private record HandCardBounds(float centerX, float centerY, float angle, float scale) {
        private boolean contains(double mouseX, double mouseY) {
            float halfW = CardRenderHelper.SMALL_CARD_WIDTH * scale / 2.0F;
            float halfH = CardRenderHelper.SMALL_CARD_HEIGHT * scale / 2.0F;
            return mouseX >= centerX - halfW && mouseX <= centerX + halfW && mouseY >= centerY - halfH && mouseY <= centerY + halfH;
        }

        private HandCardBounds expanded(float margin) {
            float expandedScaleX = scale + margin * 2.0F / CardRenderHelper.SMALL_CARD_WIDTH;
            float expandedScaleY = scale + margin * 2.0F / CardRenderHelper.SMALL_CARD_HEIGHT;
            return new HandCardBounds(centerX, centerY, angle, Math.max(expandedScaleX, expandedScaleY));
        }
    }

    private HandCardScreenBounds handCardScreenBounds(HandCardAnimation animation, float partialTick, boolean selected) {
        return handLocalAreaScreenBounds(animation, partialTick, selected, 0, 0, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
    }

    private HandCardScreenBounds handSelectionCardScreenBounds(HandCardAnimation animation, float partialTick, boolean selected) {
        if (!selected) {
            return handCardScreenBounds(animation, partialTick, false);
        }
        float scale = handSelectionPreviewScale();
        float centerX = clampSmallPreviewCenterX(animation.x(partialTick), scale);
        float centerY = clampSmallPreviewCenterY(animation.y(partialTick), scale);
        float halfW = CardRenderHelper.SMALL_CARD_WIDTH * scale / 2.0F;
        float halfH = CardRenderHelper.SMALL_CARD_HEIGHT * scale / 2.0F;
        return new HandCardScreenBounds(centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH);
    }

    private HandCardScreenBounds handLocalAreaScreenBounds(HandCardAnimation animation, float partialTick, boolean selected, int localX, int localY, int localW, int localH) {
        float scale = animation.scale(partialTick) * (selected ? 1.08F : 1.0F);
        float angle = (float) Math.toRadians(animation.angle(partialTick));
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        int[][] points = {
                {localX, localY},
                {localX + localW, localY},
                {localX, localY + localH},
                {localX + localW, localY + localH}
        };
        for (int[] point : points) {
            float px = (point[0] - CardRenderHelper.SMALL_CARD_WIDTH / 2.0F) * scale;
            float py = (point[1] - CardRenderHelper.SMALL_CARD_HEIGHT / 2.0F) * scale;
            float screenX = animation.x(partialTick) + px * cos - py * sin;
            float screenY = animation.y(partialTick) + px * sin + py * cos;
            left = Math.min(left, screenX);
            top = Math.min(top, screenY);
            right = Math.max(right, screenX);
            bottom = Math.max(bottom, screenY);
        }
        return new HandCardScreenBounds(left, top, right, bottom);
    }

    private record HandCardScreenBounds(float left, float top, float right, float bottom) {
        private static HandCardScreenBounds empty() {
            return new HandCardScreenBounds(0.0F, 0.0F, 0.0F, 0.0F);
        }

        private boolean isEmpty() {
            return right <= left || bottom <= top;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
        }

        private HandCardScreenBounds expanded(float margin) {
            return new HandCardScreenBounds(left - margin, top - margin, right + margin, bottom + margin);
        }
    }

    private ScreenRect screenRect(HandCardScreenBounds bounds) {
        return screenRect(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
    }

    private ScreenRect screenRect(float left, float top, float right, float bottom) {
        int clippedLeft = Math.max(0, (int) Math.floor(left));
        int clippedTop = Math.max(0, (int) Math.floor(top));
        int clippedRight = Math.min(width, (int) Math.ceil(right));
        int clippedBottom = Math.min(height, (int) Math.ceil(bottom));
        return new ScreenRect(clippedLeft, clippedTop, clippedRight, clippedBottom);
    }

    private record ScreenRect(int left, int top, int right, int bottom) {
        private boolean isEmpty() {
            return right <= left || bottom <= top;
        }

        private boolean covers(ScreenRect other) {
            return left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom;
        }
    }

    private record PileIconBounds(float centerX, float centerY, int size) {
    }

    private enum PileOverlaySource {
        NONE,
        BATTLE_DECK,
        DRAW,
        DISCARD,
        EXHAUST,
        ENTITY_DECK;

        private boolean remote() {
            return this == BATTLE_DECK || this == DRAW || this == DISCARD || this == EXHAUST || this == ENTITY_DECK;
        }

        private BattlePileSource remoteSource() {
            return switch (this) {
                case BATTLE_DECK, ENTITY_DECK -> BattlePileSource.BATTLE_DECK;
                case DRAW -> BattlePileSource.DRAW;
                case DISCARD -> BattlePileSource.DISCARD;
                case EXHAUST -> BattlePileSource.EXHAUST;
                default -> BattlePileSource.BATTLE_DECK;
            };
        }
    }

    private record PileRequestKey(UUID battleId, BattlePileSource source, long deckVersion, int entityId) {
    }

    private record StaticPileKey(PileOverlaySource source, long version) {
    }

    private static final class PileHoverAnimation {
        private float previousScale = 1.0F;
        private float scale = 1.0F;
        private float targetScale = 1.0F;

        private void setHovered(boolean hovered) {
            targetScale = hovered ? PILE_HOVER_SCALE : 1.0F;
        }

        private void advance(float deltaTicks) {
            previousScale = scale;
            scale = approach(scale, targetScale, frameAmount(0.42F, deltaTicks));
            if (Math.abs(scale - targetScale) < 0.002F) {
                scale = targetScale;
            }
        }

        private float scale(float partialTick) {
            return lerp(previousScale, scale, partialTick);
        }
    }

    private static final class HandCardAnimation {
        private final CardInstance card;
        private float previousX;
        private float previousY;
        private float previousAngle;
        private float previousScale;
        private float previousHover;
        private float x;
        private float y;
        private float angle;
        private float scale;
        private float hover;
        private float targetX;
        private float targetY;
        private float targetAngle;
        private float targetScale;
        private float targetHover;

        private HandCardAnimation(CardInstance card) {
            this.card = card;
        }

        private void setInstant(float x, float y, float angle, float scale, float hover) {
            this.previousX = x;
            this.previousY = y;
            this.previousAngle = angle;
            this.previousScale = scale;
            this.previousHover = hover;
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.scale = scale;
            this.hover = hover;
            setTarget(x, y, angle, scale, hover);
        }

        private void setTarget(float x, float y, float angle, float scale, float hover) {
            this.targetX = x;
            this.targetY = y;
            this.targetAngle = angle;
            this.targetScale = scale;
            this.targetHover = hover;
        }

        private UUID cardId() {
            return card.id();
        }

        private void startReturn(HandCardBounds start) {
            previousX = start.centerX();
            previousY = start.centerY();
            previousAngle = start.angle();
            previousScale = start.scale();
            previousHover = 0.0F;
            x = start.centerX();
            y = start.centerY();
            angle = start.angle();
            scale = start.scale();
            hover = 0.0F;
            targetHover = 0.0F;
        }

        private void clearHoverOnly() {
            previousHover = 0.0F;
            hover = 0.0F;
            targetHover = 0.0F;
        }

        private void clearHover(HandCardBounds target) {
            previousHover = 0.0F;
            hover = 0.0F;
            targetHover = 0.0F;
            if (target != null) {
                previousX = target.centerX();
                previousY = target.centerY();
                previousAngle = target.angle();
                previousScale = target.scale();
                x = target.centerX();
                y = target.centerY();
                angle = target.angle();
                scale = target.scale();
                setTarget(target.centerX(), target.centerY(), target.angle(), target.scale(), 0.0F);
            }
        }

        private void advance(float deltaTicks) {
            previousX = x;
            previousY = y;
            previousAngle = angle;
            previousScale = scale;
            previousHover = hover;
            float positionAmount = frameAmount(0.48F, deltaTicks);
            float shapeAmount = frameAmount(0.42F, deltaTicks);
            x = approach(x, targetX, positionAmount);
            y = approach(y, targetY, positionAmount);
            angle = approach(angle, targetAngle, shapeAmount);
            scale = approach(scale, targetScale, shapeAmount);
            hover = approach(hover, targetHover, shapeAmount);
        }

        private boolean contains(double mouseX, double mouseY) {
            float halfW = CardRenderHelper.SMALL_CARD_WIDTH * scale / 2.0F;
            float halfH = CardRenderHelper.SMALL_CARD_HEIGHT * scale / 2.0F;
            return mouseX >= x - halfW && mouseX <= x + halfW && mouseY >= y - halfH && mouseY <= y + halfH;
        }

        private boolean containsExpanded(double mouseX, double mouseY, float margin) {
            float halfW = CardRenderHelper.SMALL_CARD_WIDTH * scale / 2.0F + margin;
            float halfH = CardRenderHelper.SMALL_CARD_HEIGHT * scale / 2.0F + margin;
            return mouseX >= x - halfW && mouseX <= x + halfW && mouseY >= y - halfH && mouseY <= y + halfH;
        }

        private float x(float partialTick) {
            return lerp(previousX, x, partialTick);
        }

        private float y(float partialTick) {
            return lerp(previousY, y, partialTick);
        }

        private float angle(float partialTick) {
            return lerp(previousAngle, angle, partialTick);
        }

        private float scale(float partialTick) {
            return lerp(previousScale, scale, partialTick);
        }

        private float hover(float partialTick) {
            return lerp(previousHover, hover, partialTick);
        }

        private float currentX() {
            return x;
        }

        private float currentY() {
            return y;
        }

        private float currentScale() {
            return scale;
        }

        private static float approach(float current, float target, float amount) {
            if (Math.abs(target - current) < 0.035F) {
                return target;
            }
            return current + (target - current) * amount;
        }

        private static float frameAmount(float perTickAmount, float deltaTicks) {
            return 1.0F - (float) Math.pow(1.0F - perTickAmount, Math.max(0.0F, deltaTicks));
        }
    }

    private static final class FlyingCardAnimation {
        private final CardInstance card;
        private final float fromX;
        private final float fromY;
        private final float midX;
        private final float midY;
        private final float toX;
        private final float toY;
        private final int toCenterTicks;
        private final int discardTicks;
        private final int holdTicks;
        private final boolean played;
        private final boolean fadeOut;
        private final float fixedScale;
        private boolean released;
        private float age;

        private FlyingCardAnimation(CardInstance card, float fromX, float fromY, float midX, float midY, float toX, float toY, int toCenterTicks, int holdTicks, int discardTicks, boolean played, boolean fadeOut) {
            this(card, fromX, fromY, midX, midY, toX, toY, toCenterTicks, holdTicks, discardTicks, played, fadeOut, -1.0F);
        }

        private FlyingCardAnimation(CardInstance card, float fromX, float fromY, float midX, float midY, float toX, float toY, int toCenterTicks, int holdTicks, int discardTicks, boolean played, boolean fadeOut, float fixedScale) {
            this.card = card;
            this.fromX = fromX;
            this.fromY = fromY;
            this.midX = midX;
            this.midY = midY;
            this.toX = toX;
            this.toY = toY;
            this.toCenterTicks = toCenterTicks;
            this.holdTicks = holdTicks;
            this.discardTicks = discardTicks;
            this.played = played;
            this.fadeOut = fadeOut;
            this.fixedScale = fixedScale;
        }

        private static FlyingCardAnimation played(CardInstance card, float fromX, float fromY, float midX, float midY, float toX, float toY, boolean fadeOut) {
            return new FlyingCardAnimation(card, fromX, fromY, midX, midY, toX, toY, PLAYED_CARD_TO_CENTER_TICKS, PLAYED_CARD_HOLD_TICKS, FLY_TO_DISCARD_TICKS, true, fadeOut);
        }

        private static FlyingCardAnimation played(CardInstance card, float fromX, float fromY, float midX, float midY, float toX, float toY, boolean fadeOut, int holdTicks) {
            return new FlyingCardAnimation(card, fromX, fromY, midX, midY, toX, toY, PLAYED_CARD_TO_CENTER_TICKS, Math.max(0, holdTicks), FLY_TO_DISCARD_TICKS, true, fadeOut);
        }

        private static FlyingCardAnimation toDiscard(CardInstance card, float fromX, float fromY, float toX, float toY) {
            return new FlyingCardAnimation(card, fromX, fromY, fromX, fromY, toX, toY, 0, 0, FLY_TO_DISCARD_TICKS, false, false);
        }

        private static FlyingCardAnimation exhaustInPlace(CardInstance card, float centerX, float centerY, float smallScale) {
            float detailedScale = Math.max(0.05F, smallScale * (CardRenderHelper.SMALL_CARD_WIDTH / (float) CardRenderHelper.CARD_WIDTH));
            return new FlyingCardAnimation(card, centerX, centerY, centerX, centerY, centerX, centerY, 0, 0, HAND_SELECTION_EXHAUST_TICKS, false, true, detailedScale);
        }

        private CardInstance card() {
            return card;
        }

        private boolean played() {
            return played;
        }

        private boolean released() {
            return !played || released;
        }

        private void releaseToDiscard() {
            if (played && !fadeOut) {
                released = true;
            }
        }

        private void releaseExhaust() {
            if (played && fadeOut) {
                released = true;
            }
        }

        private void release() {
            if (!played) {
                return;
            }
            if (fadeOut) {
                releaseExhaust();
            } else {
                releaseToDiscard();
            }
        }

        private void advance(float deltaTicks) {
            age += deltaTicks;
            if (played && !released && age > toCenterTicks + holdTicks) {
                age = toCenterTicks + holdTicks;
            }
        }

        private boolean done() {
            return age > totalTicks();
        }

        private float x() {
            float time = age;
            if (played) {
                if (time <= toCenterTicks) {
                    return lerp(fromX, midX, smoothStep(time / Math.max(1.0F, toCenterTicks)));
                }
                if (time <= toCenterTicks + holdTicks || !released || fadeOut) {
                    return midX;
                }
                float t = (time - toCenterTicks - holdTicks) / Math.max(1.0F, discardTicks);
                return lerp(midX, toX, smoothStep(clamp(t, 0.0F, 1.0F)));
            }
            float t = time / Math.max(1.0F, discardTicks);
            return lerp(fromX, toX, smoothStep(clamp(t, 0.0F, 1.0F)));
        }

        private float y() {
            float time = age;
            if (played) {
                if (time <= toCenterTicks) {
                    return lerp(fromY, midY, smoothStep(time / Math.max(1.0F, toCenterTicks)));
                }
                if (time <= toCenterTicks + holdTicks || !released || fadeOut) {
                    return midY;
                }
                float t = (time - toCenterTicks - holdTicks) / Math.max(1.0F, discardTicks);
                return lerp(midY, toY, smoothStep(clamp(t, 0.0F, 1.0F)));
            }
            float t = time / Math.max(1.0F, discardTicks);
            return lerp(fromY, toY, smoothStep(clamp(t, 0.0F, 1.0F)));
        }

        private float scale() {
            if (fixedScale > 0.0F) {
                return fixedScale;
            }
            if (played && age <= toCenterTicks + holdTicks) {
                return PLAYED_CARD_NORMAL_SCALE;
            }
            if (played && (fadeOut || !released)) {
                return PLAYED_CARD_NORMAL_SCALE;
            }
            return DISCARD_FLY_SCALE;
        }

        private float alpha() {
            if (!played && fadeOut) {
                float t = age / Math.max(1.0F, discardTicks);
                return 1.0F - smoothStep(clamp(t, 0.0F, 1.0F));
            }
            if (played && fadeOut && released && age > toCenterTicks + holdTicks) {
                float t = (age - toCenterTicks - holdTicks) / Math.max(1.0F, discardTicks);
                return 1.0F - smoothStep(clamp(t, 0.0F, 1.0F));
            }
            return 1.0F;
        }

        private boolean showPlayableGlow() {
            return played && age <= toCenterTicks + holdTicks;
        }

        private boolean readyToRelease() {
            return played && age >= toCenterTicks + holdTicks;
        }

        private int totalTicks() {
            if (played && !released) {
                return Integer.MAX_VALUE;
            }
            return (played ? toCenterTicks + holdTicks : 0) + discardTicks;
        }
    }

    private enum PerfBucket {
        SNAPSHOT_SYNC,
        HAND_RENDER,
        HOVER_PREVIEW,
        TARGET_CARD,
        AIM_LINE
    }

    private record BattleCardPreviewContext(BattleCombatantSnapshot attacker, boolean monsterCard) {
    }

    private record CardRenderData(String contentKey, CardRenderHelper.CardValues values) {
    }

    private record CardRenderDataCacheKey(UUID battleId, UUID cardId, long cardStateHash, int attackerEntityId, boolean monsterCard, int hoveredEntityId, boolean draggedForPreview, int previewStateHash) {
    }

    private record HandRenderEntry(CardInstance card, HandCardAnimation animation, boolean selected, CardRenderData renderData) {
    }

    private record EffectTooltip(BattleEffectType type, int amount, int x, int y) {
    }

    private record HudTooltip(Component title, List<Component> paragraphs, MoonSpireUiRect avoidRect) {
        private HudTooltip(Component title, List<Component> paragraphs) {
            this(title, paragraphs, null);
        }
    }

    private record CardPreviewBounds(int x, int y, int width, int height) {
        private float centerX() {
            return x + width / 2.0F;
        }

        private float centerY() {
            return y + height / 2.0F;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        private CardPreviewBounds expanded(int margin) {
            return new CardPreviewBounds(x - margin, y - margin, width + margin * 2, height + margin * 2);
        }
    }

    private record IntentPreviewBounds(int x, int y, int width, int height, float scale) {
        private float centerX() {
            return x + width / 2.0F;
        }

        private float centerY() {
            return y + height / 2.0F;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width
                    && mouseY >= y && mouseY <= y + height;
        }
    }

}
