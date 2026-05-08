package com.yinfires.moonspire.client;

import com.mojang.math.Axis;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleCombatantSnapshot;
import com.yinfires.moonspire.battle.BattleEffectSnapshot;
import com.yinfires.moonspire.battle.BattleEffectType;
import com.yinfires.moonspire.battle.BattlePhase;
import com.yinfires.moonspire.battle.BattleSnapshot;
import com.yinfires.moonspire.battle.PendingHandSelectionSnapshot;
import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.CardTarget;
import com.yinfires.moonspire.client.ui.MoonSpireBattleLayoutEditor;
import com.yinfires.moonspire.client.ui.MoonSpireModalLayer;
import com.yinfires.moonspire.client.ui.MoonSpireUiLayout;
import com.yinfires.moonspire.client.ui.MoonSpireUiRect;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.network.CancelBattlePayload;
import com.yinfires.moonspire.network.EndTurnPayload;
import com.yinfires.moonspire.network.SelectBattleTargetPayload;
import com.yinfires.moonspire.network.SelectHandCardsPayload;
import com.yinfires.moonspire.network.UseCardPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
    private static final int CARD_GRID_BOTTOM_RESERVE = 46;
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
    private final Map<UUID, HandCardAnimation> handAnimations = new HashMap<>();
    private final List<FlyingCardAnimation> flyingCards = new ArrayList<>();
    private final PreviewCardAnimation monsterIntentPreview = new PreviewCardAnimation();
    private final PileHoverAnimation drawPileHover = new PileHoverAnimation();
    private final PileHoverAnimation discardPileHover = new PileHoverAnimation();
    private final PileHoverAnimation exhaustPileHover = new PileHoverAnimation();
    private DragState dragState;
    private CardGridPanel pileOverlay;
    private int hoveredHandIndex = -1;
    private int hoveredMonsterIntentIndex = -1;
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
    private FrameCache frameCache = FrameCache.empty();
    private boolean awaitingUseCardSnapshot;
    private final Set<UUID> locallyUsedCardIds = new HashSet<>();
    private HandSelectionOverlay handSelectionOverlay = HandSelectionOverlay.empty();

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
        currentFrameTicks = animationFrameTicks();
        if (syncedSnapshotVersion != ClientBattleState.snapshotVersion()) {
            syncSnapshotAnimations(snapshot);
            syncedSnapshotVersion = ClientBattleState.snapshotVersion();
        }
        boolean pileModalActive = pileOverlay != null;
        boolean handSelectionActive = snapshot.pendingHandSelection().active();
        frameCache = createFrameCache(snapshot, pileModalActive || handSelectionActive ? MoonSpireModalLayer.BLOCKED_MOUSE : mouseX, pileModalActive || handSelectionActive ? MoonSpireModalLayer.BLOCKED_MOUSE : mouseY);
        if (pileModalActive || handSelectionActive) {
            dragState = null;
            rotatingCamera = false;
            pendingTargetClickId = -1;
            hoveredHandIndex = -1;
            hoveredMonsterIntentIndex = -1;
            monsterIntentPreview.clear();
            ClientBattleState.setHoveredEntityId(-1);
            renderBattleBase(graphics, snapshot, MoonSpireModalLayer.BLOCKED_MOUSE, MoonSpireModalLayer.BLOCKED_MOUSE, partialTick, true);
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
        } else if (handSelectionActive) {
            hoveredTarget = -1;
            ClientBattleState.setHoveredEntityId(-1);
        } else if (hoveredTarget == -1 && monsterIntentCardAt(mouseX, mouseY, snapshot)) {
            hoveredTarget = snapshot.monster().entityId();
            ClientBattleState.setHoveredEntityId(hoveredTarget);
        } else {
            ClientBattleState.setHoveredEntityId(hoveredTarget);
        }
        renderBattleBase(graphics, snapshot, mouseX, mouseY, partialTick, false);
    }

    private void renderBattleBase(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick, boolean modalBackground) {
        if (modalBackground) {
            hoveredMonsterIntentIndex = -1;
            monsterIntentPreview.clear();
            return;
        }
        renderEntries(graphics, snapshot, mouseX, mouseY);
        if (!modalBackground && shouldRenderMonsterIntent(snapshot, mouseX, mouseY)) {
            renderMonsterIntent(graphics, snapshot, mouseX, mouseY);
        } else {
            hoveredMonsterIntentIndex = -1;
            monsterIntentPreview.clear();
        }
        renderBottomBar(graphics, snapshot, mouseX, mouseY, partialTick, !snapshot.pendingHandSelection().active());
        renderFlyingCards(graphics, snapshot, partialTick, modalBackground);
        renderDraggedCard(graphics, snapshot, mouseX, mouseY, partialTick);
        renderMonsterPlayedCard(graphics, snapshot, modalBackground);
        renderTurnBanner(graphics, partialTick);
        MoonSpireBattleLayoutEditor.render(graphics, font, width, height, mouseX, mouseY);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderExhaustTooltip(graphics, snapshot, mouseX, mouseY);
        renderHudTooltip(graphics, snapshot, mouseX, mouseY);
    }

    @Override
    public void tick() {
        uiTicks++;
        ClientBattleState.tickDamageNumbers();
        if (turnBannerTicks > 0) {
            turnBannerTicks--;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            return false;
        }
        if (snapshot.pendingHandSelection().active()) {
            return clickHandSelectionOverlay(mouseX, mouseY, button, snapshot);
        }
        if (pileOverlay != null) {
            if (button == 0 && pileOverlay.mouseClicked(width, height, CARD_GRID_BOTTOM_RESERVE, mouseX, mouseY, button)) {
                pendingTargetClickId = -1;
                return true;
            }
            if (button == 1) {
                pileOverlay = null;
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
                pileOverlay = new CardGridPanel(entityId == snapshot.monster().entityId() ? snapshot.monsterHand() : snapshot.hand(), Component.translatable("screen.moonspire.deck_view"));
                pileOverlay.warmup(width, height, CARD_GRID_BOTTOM_RESERVE, font, CardRenderHelper.CardValues::original);
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
            int handIndex = handIndexAt(mouseX, mouseY, snapshot);
            if (handIndex < 0) {
                handIndex = handPreviewIndexAt(mouseX, mouseY, snapshot);
            }
            if (handIndex >= 0) {
                ClientBattleState.selectHandIndex(handIndex);
                if (!cardActionsLocked(snapshot)) {
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
        if (ClientBattleState.snapshot().pendingHandSelection().active()) {
            dragState = null;
            rotatingCamera = false;
            pendingTargetClickId = -1;
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
            playDraggedCard(mouseX, mouseY);
            dragState = null;
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
        if (ClientBattleState.snapshot().pendingHandSelection().active()) {
            dragState = null;
            rotatingCamera = false;
            pendingTargetClickId = -1;
            return true;
        }
        if (pileOverlay != null) {
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
        if (ClientBattleState.snapshot().pendingHandSelection().active()) {
            return true;
        }
        if (pileOverlay != null && scrollY != 0.0D) {
            pileOverlay.scroll(width, height, CARD_GRID_BOTTOM_RESERVE, scrollY);
            return true;
        }
        if (MoonSpireBattleLayoutEditor.mouseScrolled(scrollY, hasShiftDown(), hasControlDown())) {
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
        if (ClientBattleState.snapshot().pendingHandSelection().active()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                Minecraft.getInstance().setScreen(new PauseScreen(true));
                return true;
            }
            return true;
        }
        if (pileOverlay != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                Minecraft.getInstance().setScreen(new PauseScreen(true));
            }
            return true;
        }
        if (MoonSpireBattleLayoutEditor.keyPressed(keyCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Q) {
            PacketDistributor.sendToServer(new EndTurnPayload());
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

    private void renderEntries(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY) {
        renderEntry(graphics, snapshot.player(), layout().resolve("player_entry", width, height), entityName(snapshot.player().entityId(), Component.translatable("screen.moonspire.player_entry")), false, mouseX, mouseY);
        renderEntry(graphics, snapshot.monster(), layout().resolve("monster_entry", width, height), entityName(snapshot.monster().entityId(), Component.translatable("screen.moonspire.monster_entry")), true, mouseX, mouseY);
    }

    private void renderEntry(GuiGraphics graphics, BattleCombatantSnapshot entry, MoonSpireUiRect rect, Component fallbackName, boolean selectable, int mouseX, int mouseY) {
        boolean hovered = ClientBattleState.isHoveredEntityId(entry.entityId());
        boolean selected = selectable && ClientBattleState.selectedTargetId() == entry.entityId();
        int x = rect.x();
        int y = rect.y();
        if (selected) {
            graphics.renderOutline(x - 1, y - 1, rect.width() + 2, rect.height() + 2, 0xDDFFD166);
        } else if (hovered) {
            graphics.renderOutline(x - 1, y - 1, rect.width() + 2, rect.height() + 2, 0xDDA8F7FF);
        }
        renderIntentSummary(graphics, entry, x - 54, y + 8);
        graphics.drawString(font, fallbackName, x + 8, y + ENTRY_HEADER_Y_OFFSET, 0xFFFFEAC2, false);
        MoonSpireUiRect speedRect = entrySpeedTextRect(entry, rect);
        graphics.drawString(font, Component.translatable("screen.moonspire.speed_short", entry.roundSpeed()), speedRect.x(), speedRect.y(), 0xFFB8E6FF, false);
        renderHealthBar(graphics, x + 8, y + 22, rect.width() - 16, 12, entry);
        renderEffects(graphics, entry.effects(), x + 8, y + rect.height() + 3, mouseX, mouseY);
    }

    private void renderIntentSummary(GuiGraphics graphics, BattleCombatantSnapshot entry, int x, int y) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (entry.entityId() != snapshot.monster().entityId() || snapshot.monsterIntentCards().isEmpty()) {
            return;
        }
        int attack = 0;
        int block = 0;
        int negativeEffects = 0;
        int positiveEffects = 0;
        for (CardInstance card : snapshot.monsterIntentCards()) {
            for (CardEffect effect : card.effects()) {
                if (effect.amount() <= 0) {
                    continue;
                }
                List<Integer> targets = targetIdsForEffectTarget(effect.target(), snapshot, true);
                int totalAmount = Math.max(0, effect.amount()) * Math.max(1, effect.count());
                if (effect.kind() == CardEffectKind.DAMAGE && targets.contains(snapshot.player().entityId())) {
                    attack += CardRenderHelper.previewDamageAmount(effect.amount(), snapshot.monster().roundSpeed(), snapshot.player().roundSpeed(), snapshot.player().defense(), CardRenderHelper.effectAmount(snapshot.player(), BattleEffectType.GUARD)) * Math.max(1, effect.count());
                } else if (effect.kind() == CardEffectKind.BLOCK && targets.contains(snapshot.monster().entityId())) {
                    block += totalAmount;
                } else if (effect.kind() == CardEffectKind.BLEED && targets.contains(snapshot.player().entityId())) {
                    negativeEffects += totalAmount;
                } else if (effect.kind() == CardEffectKind.GUARD && targets.contains(snapshot.monster().entityId())) {
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

    private void renderEffects(GuiGraphics graphics, List<BattleEffectSnapshot> effects, int x, int y, int mouseX, int mouseY) {
        BattleEffectSnapshot hovered = null;
        int hoveredX = 0;
        int hoveredY = 0;
        for (int i = 0; i < effects.size(); i++) {
            BattleEffectSnapshot effect = effects.get(i);
            int ex = x + i * 18;
            Component amount = Component.translatable("screen.moonspire.effect_short", effect.amount());
            ResourceLocation texture = effectIconTexture(effect.type());
            if (texture != null) {
                graphics.blit(texture, ex, y, 15, 15, 0.0F, 0.0F, 64, 64, 64, 64);
            } else {
                graphics.renderOutline(ex, y, 15, 15, 0xCCFFB1C0);
                Component marker = Component.translatable("screen.moonspire.effect_unknown_icon");
                graphics.drawString(font, marker, ex + (15 - font.width(marker)) / 2, y + 2, 0xFFFFB1C0, false);
            }
            graphics.drawString(font, amount, ex + 15 - font.width(amount), y + 7, 0xFFFFFFFF, true);
            if (mouseX >= ex && mouseX <= ex + 15 && mouseY >= y && mouseY <= y + 15) {
                hovered = effect;
                hoveredX = ex;
                hoveredY = y;
            }
        }
        if (hovered != null) {
            int tipX = Math.min(width - CardRenderHelper.TIP_WIDTH - 6, hoveredX);
            CardRenderHelper.renderEffectTip(graphics, font, hovered.type(), hovered.amount(), tipX, hoveredY + 18);
        }
    }

    private static ResourceLocation effectIconTexture(BattleEffectType type) {
        if (type.iconTexturePath() == null || type.iconTexturePath().isBlank()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "textures/" + type.iconTexturePath());
    }

    private void renderMonsterIntent(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY) {
        List<CardInstance> intentCards = snapshot.monsterIntentCards().isEmpty() && snapshot.monsterIntent() != null
                ? List.of(snapshot.monsterIntent())
                : snapshot.monsterIntentCards();
        if (intentCards.isEmpty()) {
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
        } else if (hoveredMonsterIntentIndex >= 0 && hoveredMonsterIntentIndex < intentCards.size()) {
            int hoveredX = x + hoveredMonsterIntentIndex * spacing;
            IntentPreviewBounds preview = intentPreviewBoundsForSmallCard(hoveredX, y);
            if (preview.contains(mouseX, mouseY)) {
                hoveredIndex = hoveredMonsterIntentIndex;
            } else {
                hoveredMonsterIntentIndex = -1;
            }
        } else {
            hoveredMonsterIntentIndex = -1;
        }
        for (int i = 0; i < intentCards.size(); i++) {
            if (i == hoveredIndex) {
                continue;
            }
            CardInstance card = intentCards.get(i);
            CardRenderHelper.renderSmallCard(graphics, font, card, x + i * spacing, y, false, false, cardValues(snapshot, card), false);
        }
        if (hoveredIndex >= 0) {
            int hoveredX = x + hoveredIndex * spacing;
            IntentPreviewBounds preview = intentPreviewBoundsForSmallCard(hoveredX, y);
            CardInstance card = intentCards.get(hoveredIndex);
            monsterIntentPreview.setOpenTarget(card.id(), hoveredX + CardRenderHelper.SMALL_CARD_WIDTH / 2.0F, y + CardRenderHelper.SMALL_CARD_HEIGHT / 2.0F,
                    preview.centerX(), preview.centerY(), CardRenderHelper.SMALL_CARD_WIDTH / (float) CardRenderHelper.CARD_WIDTH, HAND_PREVIEW_SCALE);
            monsterIntentPreview.advance(currentFrameTicks);
            renderAnimatedIntentPreview(graphics, snapshot, card, monsterIntentPreview);
            if (monsterIntentPreview.progress() > 0.86F) {
                CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, preview.x(), preview.y(), width, height);
            }
        } else {
            monsterIntentPreview.setClosingTarget();
            monsterIntentPreview.advance(currentFrameTicks);
            CardInstance closingCard = monsterIntentPreview.card(intentCards);
            if (closingCard != null && monsterIntentPreview.visible()) {
                renderAnimatedIntentPreview(graphics, snapshot, closingCard, monsterIntentPreview);
            } else {
                monsterIntentPreview.clear();
            }
        }
    }

    private void renderAnimatedIntentPreview(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, PreviewCardAnimation animation) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 120.0F);
        graphics.pose().translate(animation.centerX(), animation.centerY(), 0.0F);
        graphics.pose().scale(animation.scale(), animation.scale(), 1.0F);
        graphics.pose().translate(-CardRenderHelper.CARD_WIDTH / 2.0F, -CardRenderHelper.CARD_HEIGHT / 2.0F, 0.0F);
        renderCardBody(graphics, snapshot, card, false);
        graphics.pose().popPose();
    }

    private void renderBottomBar(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick, boolean renderHand) {
        renderEnergy(graphics, layout().resolve("energy", width, height), snapshot);
        renderPile(graphics, layout().resolve("draw_pile", width, height), snapshot.drawPile(), true, drawPileAt(mouseX, mouseY), drawPileHover, partialTick);
        renderPile(graphics, layout().resolve("discard_pile", width, height), snapshot.discardPile(), false, discardPileAt(mouseX, mouseY), discardPileHover, partialTick);
        renderExhaustPile(graphics, layout().resolve("exhaust_pile", width, height), snapshot.exhaustPile(), visibleExhaustPileAt(mouseX, mouseY, snapshot), partialTick);
        renderEndTurnButton(graphics, snapshot, layout().resolve("end_turn", width, height), mouseX, mouseY, partialTick);
        if (renderHand) {
            renderHand(graphics, snapshot, mouseX, mouseY, partialTick);
        }
    }

    private void renderPile(GuiGraphics graphics, MoonSpireUiRect rect, int count, boolean drawPile, boolean hovered, PileHoverAnimation animation, float partialTick) {
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
        renderPileBadge(graphics, rect, icon, count, drawPile);
    }

    private PileIconBounds pileIconBounds(MoonSpireUiRect rect) {
        int size = Math.max(8, Math.min(rect.width(), rect.height()));
        float centerX = rect.x() + rect.width() / 2.0F;
        float centerY = rect.y() + rect.height() / 2.0F;
        return new PileIconBounds(centerX, centerY, size);
    }

    private void renderPileBadge(GuiGraphics graphics, MoonSpireUiRect rect, PileIconBounds icon, int count, boolean drawPile) {
        int badgeX = drawPile ? Math.round(icon.centerX() + icon.size() / 2.0F - PILE_BADGE_SIZE) : Math.round(icon.centerX() - icon.size() / 2.0F);
        int badgeY = Math.round(icon.centerY() + icon.size() / 2.0F - PILE_BADGE_SIZE);
        badgeX = Math.max(rect.x(), Math.min(rect.right() - PILE_BADGE_SIZE, badgeX));
        badgeY = Math.max(rect.y(), Math.min(rect.bottom() - PILE_BADGE_SIZE, badgeY));
        graphics.blit(MoonSpireUiTextures.PILE_COUNT_BADGE, badgeX, badgeY, PILE_BADGE_SIZE, PILE_BADGE_SIZE, 0.0F, 0.0F, PILE_BADGE_TEXTURE_SIZE, PILE_BADGE_TEXTURE_SIZE, PILE_BADGE_TEXTURE_SIZE, PILE_BADGE_TEXTURE_SIZE);
        drawPileCount(graphics, Integer.toString(count), badgeX + PILE_BADGE_SIZE / 2.0F, badgeY + PILE_BADGE_SIZE / 2.0F);
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

    private void renderEnergy(GuiGraphics graphics, MoonSpireUiRect rect, BattleSnapshot snapshot) {
        CardRenderHelper.renderEnergyCostDisplay(graphics, font, snapshot.player().energyLeft(), snapshot.player().maxEnergy(), rect.x(), rect.y(), rect.width(), rect.height());
    }

    private void renderEndTurnButton(GuiGraphics graphics, BattleSnapshot snapshot, MoonSpireUiRect rect, int mouseX, int mouseY, float partialTick) {
        boolean enabled = snapshot.phase() == BattlePhase.PLAYER_TURN;
        boolean hasPlayable = enabled && hasPlayableCard(snapshot);
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        boolean highlightedNoPlay = enabled && !hasPlayable;
        if (highlightedNoPlay) {
            float pulse = 0.45F + 0.55F * (float) Math.sin((uiTicks + partialTick) * 0.22F);
            int alpha = 92 + Math.round(80.0F * pulse);
            int glow = (alpha << 24) | 0x0047F5FF;
            graphics.fill(rect.x() - 3, rect.y() - 3, rect.x() + rect.width() + 3, rect.y() + rect.height() + 3, glow);
        }
        MoonSpireUiTextures.drawButton(graphics, rect.x(), rect.y(), rect.width(), rect.height(), hovered, enabled);
        Component label = enabled
                ? Component.translatable("screen.moonspire.end_turn", snapshot.round())
                : phaseComponent(snapshot.phase());
        int textColor = highlightedNoPlay ? 0xFFFFD84D : hovered ? 0xFFFF5F63 : 0xFFFFFFFF;
        float labelScale = Math.min(1.0F, (rect.width() - 8.0F) / Math.max(1.0F, font.width(label)));
        CardRenderHelper.drawOutlinedScreenText(graphics, font, label, rect.x() + rect.width() / 2, rect.y() + rect.height() / 2, labelScale, textColor, 0xFF46393B);
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
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        int count = visibleCards.size();
        if (count <= 0) {
            hoveredHandIndex = -1;
            return;
        }
        long start = perfStart();
        HandLayout layout = handLayout(visibleCards);
        int selectedIndex = ClientBattleState.selectedHandIndex();
        int hoveredIndex = hoveredHandIndex(mouseX, mouseY, snapshot, visibleCards, layout);
        syncHandAnimationTargets(snapshot, layout, visibleCards, hoveredIndex);
        for (HandCardAnimation animation : handAnimations.values()) {
            animation.advance(currentFrameTicks);
        }
        for (int i = 0; i < count; i++) {
            CardInstance card = visibleCards.get(i);
            if (hoveredIndex == i) {
                continue;
            }
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null) {
                renderHandCard(graphics, snapshot, card, animation, partialTick, card.id().equals(selectedHandCardId(snapshot, selectedIndex)), false);
            }
        }
        renderHandDescriptions(graphics, snapshot, visibleCards, hoveredIndex, partialTick);
        if (hoveredIndex >= 0) {
            CardInstance card = visibleCards.get(hoveredIndex);
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null) {
                renderHoveredHandCard(graphics, snapshot, card, animation, layout.card(hoveredIndex), partialTick);
            }
        }
        recordPerf(PerfBucket.HAND_RENDER, start);
    }

    private void renderHandCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, float partialTick, boolean selected, boolean showDescription) {
        boolean unaffordable = card.cost() > snapshot.player().energyLeft();
        boolean playable = playable(card, snapshot);
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
        renderSmallPlayableGlow(graphics, playable, 0.0F, 0.0F, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        CardRenderHelper.enablePoseScissor(graphics, 0, 0, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        CardRenderHelper.renderSmallCard(graphics, font, card, 0, 0, selected, unaffordable, cardValues(snapshot, card), false, showDescription);
        graphics.disableScissor();
        renderSmallPlayableOutline(graphics, playable, 0.0F, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        graphics.pose().popPose();
    }

    private void renderHandDescriptions(GuiGraphics graphics, BattleSnapshot snapshot, List<CardInstance> visibleCards, int hoveredIndex, float partialTick) {
        List<HandCardScreenBounds> coverBounds = new ArrayList<>();
        int selectedIndex = ClientBattleState.selectedHandIndex();
        UUID selectedCardId = selectedHandCardId(snapshot, selectedIndex);
        for (int i = visibleCards.size() - 1; i >= 0; i--) {
            if (hoveredIndex == i) {
                continue;
            }
            CardInstance card = visibleCards.get(i);
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation == null) {
                continue;
            }
            boolean selected = card.id().equals(selectedCardId);
            HandCardScreenBounds bounds = handCardScreenBounds(animation, partialTick, selected);
            if (!coveredByPreviousCards(bounds, coverBounds)) {
                renderHandDescription(graphics, snapshot, card, animation, partialTick, selected, coverBounds);
            }
            coverBounds.add(bounds);
        }
    }

    private boolean coveredByPreviousCards(HandCardScreenBounds bounds, List<HandCardScreenBounds> coverBounds) {
        for (HandCardScreenBounds cover : coverBounds) {
            if (cover.left() <= bounds.left() && cover.right() >= bounds.right() && cover.top() <= bounds.top() && cover.bottom() >= bounds.bottom()) {
                return true;
            }
        }
        return false;
    }

    private void renderHandDescription(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, float partialTick, boolean selected, List<HandCardScreenBounds> coverBounds) {
        CardRenderHelper.CardLocalArea desc = CardRenderHelper.smallDescriptionArea(card);
        HandCardScreenBounds descBounds = handLocalAreaScreenBounds(animation, partialTick, selected, desc.x(), desc.y(), desc.width(), desc.height());
        List<Integer> cuts = new ArrayList<>();
        cuts.add((int) Math.floor(descBounds.left()));
        cuts.add((int) Math.ceil(descBounds.right()));
        for (HandCardScreenBounds cover : coverBounds) {
            float left = Math.max(descBounds.left(), cover.left());
            float right = Math.min(descBounds.right(), cover.right());
            float top = Math.max(descBounds.top(), cover.top());
            float bottom = Math.min(descBounds.bottom(), cover.bottom());
            if (right > left && bottom > top) {
                cuts.add((int) Math.floor(left));
                cuts.add((int) Math.ceil(right));
            }
        }
        Collections.sort(cuts);
        for (int i = 0; i < cuts.size() - 1; i++) {
            int left = cuts.get(i);
            int right = cuts.get(i + 1);
            if (right - left <= 1 || coveredByHandBounds(left, right, descBounds, coverBounds)) {
                continue;
            }
            graphics.enableScissor(left, (int) Math.floor(descBounds.top()), right, (int) Math.ceil(descBounds.bottom()));
            renderHandDescriptionUnclipped(graphics, snapshot, card, animation, partialTick, selected);
            graphics.disableScissor();
        }
    }

    private boolean coveredByHandBounds(int left, int right, HandCardScreenBounds descBounds, List<HandCardScreenBounds> coverBounds) {
        float centerX = (left + right) / 2.0F;
        float centerY = (descBounds.top() + descBounds.bottom()) / 2.0F;
        for (HandCardScreenBounds cover : coverBounds) {
            if (centerX >= cover.left() && centerX <= cover.right() && centerY >= cover.top() && centerY <= cover.bottom()) {
                return true;
            }
        }
        return false;
    }

    private void renderHandDescriptionUnclipped(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, float partialTick, boolean selected) {
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
        CardRenderHelper.renderSmallCardDescription(graphics, font, card, 0, 0, cardValues(snapshot, card));
        graphics.pose().popPose();
    }

    private void renderHoveredHandCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, HandCardBounds baseBounds, float partialTick) {
        long start = perfStart();
        boolean unaffordable = card.cost() > snapshot.player().energyLeft();
        float progress = smoothStep(animation.hover(partialTick));
        CardPreviewBounds preview = previewBounds(baseBounds);
        float baseScale = animation.scale(partialTick);
        float previewScale = HAND_PREVIEW_SCALE;
        float centerX = lerp(animation.x(partialTick), preview.centerX(), progress);
        float centerY = lerp(animation.y(partialTick), preview.centerY(), progress);
        float scale = lerp(baseScale * (CardRenderHelper.SMALL_CARD_WIDTH / (float) CardRenderHelper.CARD_WIDTH), previewScale, progress);
        float angle = lerp(animation.angle(partialTick), 0.0F, progress);
        renderDetailedPlayableGlow(graphics, playable(card, snapshot), centerX, centerY, scale, 0.0F);
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, true);
        renderDetailedPlayableOutline(graphics, playable(card, snapshot), centerX, centerY, scale, 0.0F);
        if (progress > 0.88F) {
            CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, preview.x(), preview.y(), width, height);
        }
        recordPerf(PerfBucket.HOVER_PREVIEW, start);
    }

    private void renderDraggedDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, boolean playableHere) {
        long start = perfStart();
        DragGlow dragGlow = dragGlowFor(card, playableHere);
        boolean basePlayable = playable(card, snapshot);
        renderDetailedPlayableGlow(graphics, basePlayable, centerX, centerY, scale, dragGlow.pulse());
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, 0.0F, card.cost() > snapshot.player().energyLeft(), false);
        renderDetailedPlayableOutline(graphics, basePlayable, centerX, centerY, scale, dragGlow.pulse());
        recordPerf(PerfBucket.TARGET_CARD, start);
    }

    private void renderTargetingHandCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, float partialTick, boolean playableHere) {
        long start = perfStart();
        boolean unaffordable = card.cost() > snapshot.player().energyLeft();
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
        CardRenderHelper.renderSmallCard(graphics, font, card, 0, 0, false, unaffordable, cardValues(snapshot, card), false, true);
        graphics.disableScissor();
        renderSmallPlayableOutline(graphics, playable(card, snapshot), dragGlow.pulse(), CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
        graphics.pose().popPose();
        recordPerf(PerfBucket.TARGET_CARD, start);
    }

    private void renderPileOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (pileOverlay == null) {
            return;
        }
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        pileOverlay.render(graphics, font, width, height, mouseX, mouseY, CARD_GRID_BOTTOM_RESERVE, card -> false, CardRenderHelper.CardValues::original,
                (previewGraphics, previewFont, card, x, y, selected) -> {
                    previewGraphics.pose().pushPose();
                    previewGraphics.pose().translate(x, y, 0.0F);
            CardRenderHelper.renderCard(previewGraphics, previewFont, card, 0, 0, false, CardRenderHelper.CardValues.original(card), false, false);
            previewGraphics.pose().popPose();
                });
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
        CardRenderHelper.CardValues values = cardValues(snapshot, card, snapshot.monster(), true);
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, MONSTER_PLAYED_CARD_SCALE, 0.0F, false, true, ClientBattleState.monsterPlayedCardAlpha(), values);
    }

    private void renderHandSelectionOverlay(GuiGraphics graphics, BattleSnapshot snapshot, int mouseX, int mouseY, float partialTick) {
        PendingHandSelectionSnapshot selection = snapshot.pendingHandSelection();
        if (!selection.active()) {
            handSelectionOverlay.clearIfInactive();
            return;
        }
        handSelectionOverlay.sync(selection, snapshot);
        if (handSelectionOverlay.confirming()) {
            return;
        }
        MoonSpireModalLayer.drawTopmostOverlay(graphics, width, height);
        renderHandSelectionCards(graphics, snapshot, mouseX, mouseY, partialTick, false);
        Component title = handSelectionOverlay.ready(selection)
                ? Component.translatable(selection.action() == PendingHandSelectionSnapshot.Action.EXHAUST ? "screen.moonspire.hand_selection.confirm_exhaust" : "screen.moonspire.hand_selection.confirm_discard")
                : Component.translatable(selection.action() == PendingHandSelectionSnapshot.Action.EXHAUST ? "screen.moonspire.hand_selection.choose_exhaust" : "screen.moonspire.hand_selection.choose_discard", selection.requiredCount());
        CardRenderHelper.drawOutlinedScreenText(graphics, font, title, width / 2, Math.max(34, height / 5), 1.45F, 0xFFFFFFFF, 0xFF101010);
        ButtonRect confirm = handSelectionConfirmButton();
        boolean ready = handSelectionOverlay.ready(selection);
        boolean hovered = ready && confirm.contains(mouseX, mouseY) && !handSelectionOverlay.confirming();
        MoonSpireUiTextures.drawButton(graphics, confirm.x(), confirm.y(), confirm.w(), confirm.h(), hovered, ready && !handSelectionOverlay.confirming());
        Component label = Component.translatable("screen.moonspire.hand_selection.confirm_button");
        CardRenderHelper.drawOutlinedScreenText(graphics, font, label, confirm.x() + confirm.w() / 2, confirm.y() + confirm.h() / 2, 1.0F, ready ? 0xFFFFFFFF : 0xFF8E989A, 0xFF313638);
        renderHandSelectionHoveredPreview(graphics, snapshot, mouseX, mouseY, partialTick);
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
            if (handSelectionOverlay.confirming() || card.id().equals(hoveredCardId)) {
                continue;
            }
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null) {
                renderHandCard(graphics, snapshot, card, animation, partialTick, false, true);
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
                renderHandCard(graphics, snapshot, card, animation, partialTick, true, true);
            }
        }
        if (drawHoveredPreview && hoveredIndex >= 0) {
            CardInstance card = snapshot.hand().get(hoveredIndex);
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null) {
                renderHandSelectionHoveredCard(graphics, snapshot, card, animation, partialTick);
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
            renderHandSelectionHoveredCard(graphics, snapshot, card, animation, partialTick);
        }
    }

    private int handSelectionHoveredCardIndex(double mouseX, double mouseY, BattleSnapshot snapshot, float partialTick) {
        if (dragState != null || handSelectionOverlay.confirming()) {
            return -1;
        }
        int directHover = handSelectionCardIndexAt(mouseX, mouseY, snapshot, partialTick);
        if (directHover >= 0 && (snapshot.pendingHandSelection().candidateCardIds().contains(snapshot.hand().get(directHover).id())
                || handSelectionOverlay.isSelected(snapshot.hand().get(directHover).id()))) {
            return directHover;
        }
        return -1;
    }

    private void renderHandSelectionHoveredCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, HandCardAnimation animation, float partialTick) {
        boolean unaffordable = card.cost() > snapshot.player().energyLeft();
        float detailedScale = HAND_PREVIEW_SCALE;
        float centerX = clampPreviewCenterX(animation.x(partialTick), detailedScale);
        float centerY = clampPreviewCenterY(animation.y(partialTick), detailedScale);
        renderDetailedPlayableGlow(graphics, playable(card, snapshot), centerX, centerY, detailedScale, 0.0F);
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, detailedScale, 0.0F, unaffordable, true);
        renderDetailedPlayableOutline(graphics, playable(card, snapshot), centerX, centerY, detailedScale, 0.0F);
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

    private boolean clickHandSelectionOverlay(double mouseX, double mouseY, int button, BattleSnapshot snapshot) {
        dragState = null;
        rotatingCamera = false;
        pendingTargetClickId = -1;
        pileOverlay = null;
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
                for (UUID cardId : handSelectionOverlay.selectedIds()) {
                    CardInstance card = cardById(snapshot.hand(), cardId);
                    HandCardAnimation animation = handAnimations.get(cardId);
                    if (card != null && animation != null) {
                        if (selection.action() == PendingHandSelectionSnapshot.Action.EXHAUST) {
                            flyingCards.add(FlyingCardAnimation.exhaustInPlace(card, animation.currentX(), animation.currentY(), animation.currentScale()));
                        } else {
                            flyingCards.add(FlyingCardAnimation.toDiscard(card, animation.currentX(), animation.currentY(), discardPileCenterX(), discardPileCenterY()));
                        }
                    }
                }
                PacketDistributor.sendToServer(new SelectHandCardsPayload(handSelectionOverlay.selectedIds()));
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
        renderDraggedDetailedCard(graphics, snapshot, card, mouseX, mouseY, DRAG_CARD_SCALE, playableHere);
    }

    private MoonSpireUiRect targetRectForCard(CardInstance card, int targetEntityId, BattleSnapshot snapshot) {
        if (targetEntityId == snapshot.player().entityId()) {
            return layout().resolve("player_entry", width, height);
        }
        return layout().resolve("monster_entry", width, height);
    }

    private long perfStart() {
        return 0L;
    }

    private void recordPerf(PerfBucket bucket, long startNanos) {
        // no-op: keep the hot render path free of per-frame diagnostics
    }

    private void syncSnapshotAnimations(BattleSnapshot snapshot) {
        long start = perfStart();
        if (!snapshot.active()) {
            handAnimations.clear();
            flyingCards.clear();
            previousSnapshot = BattleSnapshot.inactive();
            awaitingUseCardSnapshot = false;
            locallyUsedCardIds.clear();
            handSelectionOverlay = HandSelectionOverlay.empty();
            recordPerf(PerfBucket.SNAPSHOT_SYNC, start);
            return;
        }
        boolean keepLocalUsedCardsHidden = awaitingUseCardSnapshot || snapshot.resolvingEffects();
        boolean expectedPlayedRemoval = keepLocalUsedCardsHidden || previousSnapshot.resolvingEffects();
        awaitingUseCardSnapshot = false;
        if (!snapshot.pendingHandSelection().active()) {
            handSelectionOverlay.clearIfInactive();
        }
        HandLayout layout = handLayout(snapshot);
        boolean firstActiveSnapshot = !previousSnapshot.active();
        Set<UUID> currentIds = currentHandIds(snapshot.hand());
        locallyUsedCardIds.removeIf(id -> !keepLocalUsedCardsHidden || !currentIds.contains(id));
        Set<UUID> representedFlyingIds = flyingCardIds();
        Set<UUID> discardIds = currentHandIds(snapshot.discardPileCards());
        Set<UUID> exhaustedIds = currentHandIds(snapshot.exhaustPileCards());
        for (FlyingCardAnimation animation : flyingCards) {
            if (!animation.played() || animation.released()) {
                continue;
            }
            if (exhaustedIds.contains(animation.card().id())) {
                animation.releaseExhaust();
            } else if (discardIds.contains(animation.card().id())) {
                animation.releaseToDiscard();
            }
        }
        if (firstActiveSnapshot) {
            turnBannerPhase = snapshot.phase();
            turnBannerTicks = TURN_BANNER_TICKS;
        } else if (snapshot.phase() != previousSnapshot.phase()) {
            turnBannerPhase = snapshot.phase();
            turnBannerTicks = TURN_BANNER_TICKS;
        }
        for (CardInstance oldCard : previousSnapshot.hand()) {
            if (!currentIds.contains(oldCard.id()) && !representedFlyingIds.contains(oldCard.id())) {
                HandCardAnimation from = handAnimations.get(oldCard.id());
                float startX = from != null ? from.currentX() : drawPileCenterX();
                float startY = from != null ? from.currentY() : drawPileCenterY();
                if (exhaustedIds.contains(oldCard.id())) {
                    float scale = from != null ? from.currentScale() : HAND_BASE_SCALE;
                    flyingCards.add(FlyingCardAnimation.exhaustInPlace(oldCard, startX, startY, scale));
                } else if (expectedPlayedRemoval) {
                    FlyingCardAnimation animation = FlyingCardAnimation.played(oldCard, startX, startY, battlefieldCenterX(), battlefieldCenterY(), discardPileCenterX(), discardPileCenterY(), exhaustedIds.contains(oldCard.id()));
                    if (discardIds.contains(animation.card().id())) {
                        animation.releaseToDiscard();
                    }
                    flyingCards.add(animation);
                } else {
                    flyingCards.add(FlyingCardAnimation.toDiscard(oldCard, startX, startY, discardPileCenterX(), discardPileCenterY()));
                }
                representedFlyingIds.add(oldCard.id());
            }
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

    private void renderScaledDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips) {
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, suppressTips, 1.0F);
    }

    private void renderScaledDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips, float alpha) {
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, suppressTips, alpha, null);
    }

    private void renderScaledDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips, CardRenderHelper.CardValues values) {
        renderScaledDetailedCard(graphics, snapshot, card, centerX, centerY, scale, angle, unaffordable, suppressTips, 1.0F, values);
    }

    private void renderScaledDetailedCard(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, float centerX, float centerY, float scale, float angle, boolean unaffordable, boolean suppressTips, float alpha, CardRenderHelper.CardValues values) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 120.0F);
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-CardRenderHelper.CARD_WIDTH / 2.0F, -CardRenderHelper.CARD_HEIGHT / 2.0F, 0.0F);
        graphics.setColor(1.0F, 1.0F, 1.0F, clamp(alpha, 0.0F, 1.0F));
        renderCardBody(graphics, snapshot, card, unaffordable, values);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
        if (!suppressTips && scale >= 0.95F) {
            int x = Math.round(centerX - CardRenderHelper.CARD_WIDTH * scale / 2.0F);
            int y = Math.round(centerY - CardRenderHelper.CARD_HEIGHT * scale / 2.0F);
            CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, x, y, width, height);
        }
    }

    private void renderCardBody(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, boolean unaffordable) {
        renderCardBody(graphics, snapshot, card, unaffordable, null);
    }

    private void renderCardBody(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, boolean unaffordable, CardRenderHelper.CardValues values) {
        CardRenderHelper.renderDetailedCard(graphics, font, card, 0, 0, false, values == null ? cardValues(snapshot, card) : values, unaffordable, false);
    }

    private void renderCardPreview(GuiGraphics graphics, BattleSnapshot snapshot, CardInstance card, int x, int y, boolean unaffordable) {
        CardRenderHelper.renderDetailedCard(graphics, font, card, x, y, false, cardValues(snapshot, card), unaffordable, false);
        CardRenderHelper.renderKeywordTipsBeside(graphics, font, card, x, y, width, height);
    }

    private CardRenderHelper.CardValues cardValues(BattleSnapshot snapshot, CardInstance card) {
        BattleCombatantSnapshot attacker = snapshot.player();
        boolean monsterCard = snapshot.monsterHand().contains(card) || snapshot.monsterIntentCards().contains(card);
        if (snapshot.monsterHand().contains(card) || snapshot.monsterIntentCards().contains(card)) {
            attacker = snapshot.monster();
        }
        return cardValues(snapshot, card, attacker, monsterCard);
    }

    private CardRenderHelper.CardValues cardValues(BattleSnapshot snapshot, CardInstance card, BattleCombatantSnapshot attacker, BattleCombatantSnapshot defender) {
        return cardValues(snapshot, card, attacker, attacker.entityId() == snapshot.monster().entityId());
    }

    private CardRenderHelper.CardValues cardValues(BattleSnapshot snapshot, CardInstance card, BattleCombatantSnapshot attacker, boolean monsterCard) {
        int attack = card.enemyEffectAmount(CardEffectKind.DAMAGE);
        int defense = card.selfEffectAmount(CardEffectKind.BLOCK);
        List<Integer> damageAmounts = new ArrayList<>(card.effects().size());
        List<Integer> blockAmounts = new ArrayList<>(card.effects().size());
        int previewAttackTotal = 0;
        for (CardEffect effect : card.effects()) {
            int damageAmount = effect.amount();
            int blockAmount = effect.amount();
            BattleCombatantSnapshot singleTarget = singlePreviewTarget(card, effect.target(), snapshot, monsterCard);
            if (effect.kind() == CardEffectKind.DAMAGE && singleTarget.entityId() >= 0) {
                damageAmount = CardRenderHelper.previewDamageAmount(effect.amount(), attacker.roundSpeed(), singleTarget.roundSpeed(), singleTarget.defense(), CardRenderHelper.effectAmount(singleTarget, BattleEffectType.GUARD));
            }
            damageAmounts.add(damageAmount);
            blockAmounts.add(blockAmount);
            if (effect.kind() == CardEffectKind.DAMAGE && effect.target().targetsEnemy()) {
                previewAttackTotal += damageAmount * effect.count();
            }
        }
        if (previewAttackTotal > 0) {
            attack = previewAttackTotal;
        }
        return new CardRenderHelper.CardValues(attack, defense, damageAmounts, blockAmounts);
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
        return BattleCombatantSnapshot.empty();
    }

    private boolean playable(CardInstance card, BattleSnapshot snapshot) {
        return snapshot.phase() == BattlePhase.PLAYER_TURN
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
            case MONSTER_TURN -> Component.translatable("screen.moonspire.turn.monster");
            default -> Component.translatable(phase.translationKey());
        };
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
            pileOverlay = new CardGridPanel(snapshot.drawPileCards(), Component.translatable("screen.moonspire.draw_pile"));
            return true;
        }
        if (discardPileAt(mouseX, mouseY)) {
            pileOverlay = new CardGridPanel(snapshot.discardPileCards(), Component.translatable("screen.moonspire.discard_pile"));
            return true;
        }
        if (visibleExhaustPileAt(mouseX, mouseY, snapshot)) {
            pileOverlay = new CardGridPanel(snapshot.exhaustPileCards(), Component.translatable("screen.moonspire.exhaust_pile"));
            return true;
        }
        return false;
    }

    private boolean clickEndTurn(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (snapshot.phase() == BattlePhase.PLAYER_TURN && endTurnButtonAt(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new EndTurnPayload());
            return true;
        }
        return false;
    }

    private int handIndexAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (frameCache.matches(snapshot, mouseX, mouseY)) {
            return frameCache.handIndex();
        }
        return directHandIndexAt(mouseX, mouseY, snapshot);
    }

    private int directHandIndexAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        int count = snapshot.hand().size();
        if (count <= 0) {
            return -1;
        }
        for (int i = count - 1; i >= 0; i--) {
            CardInstance card = snapshot.hand().get(i);
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null && animation.contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    private int directVisibleHandIndexAt(double mouseX, double mouseY, List<CardInstance> visibleCards) {
        for (int i = visibleCards.size() - 1; i >= 0; i--) {
            CardInstance card = visibleCards.get(i);
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation != null && animation.contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    private int handPreviewIndexAt(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (frameCache.matches(snapshot, mouseX, mouseY)) {
            return frameCache.previewIndex();
        }
        List<CardInstance> visibleCards = visibleHandCards(snapshot);
        if (hoveredHandIndex < 0 || hoveredHandIndex >= visibleCards.size()) {
            return -1;
        }
        HandLayout layout = handLayout(visibleCards);
        return previewBounds(layout.card(hoveredHandIndex)).contains(mouseX, mouseY) ? hoveredHandIndex : -1;
    }

    private int handSelectionCardIndexAt(double mouseX, double mouseY, BattleSnapshot snapshot, float partialTick) {
        for (int i = snapshot.hand().size() - 1; i >= 0; i--) {
            CardInstance card = snapshot.hand().get(i);
            HandCardAnimation animation = handAnimations.get(card.id());
            if (animation == null) {
                continue;
            }
            HandCardScreenBounds bounds = handSelectionCardScreenBounds(animation, partialTick, handSelectionOverlay.isSelected(card.id()));
            if (bounds.contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    private int hoveredHandIndex(double mouseX, double mouseY, BattleSnapshot snapshot, List<CardInstance> visibleCards, HandLayout layout) {
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
        int directHover = directVisibleHandIndexAt(mouseX, mouseY, visibleCards);
        if (directHover >= 0) {
            hoveredHandIndex = directHover;
            return hoveredHandIndex;
        }
        if (hoveredHandIndex >= 0) {
            CardPreviewBounds preview = previewBounds(layout.card(hoveredHandIndex));
            if (preview.contains(mouseX, mouseY)) {
                return hoveredHandIndex;
            }
        }
        hoveredHandIndex = -1;
        return -1;
    }

    private HandCardBounds centerTargetingHandBounds(HandLayout layout) {
        float centerX = width / 2.0F;
        float scale = HAND_BASE_SCALE;
        if (!layout.cards().isEmpty()) {
            centerX = layout.card(layout.cards().size() / 2).centerX();
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
        int previewW = Math.round(CardRenderHelper.CARD_WIDTH * HAND_PREVIEW_SCALE);
        int previewH = Math.round(CardRenderHelper.CARD_HEIGHT * HAND_PREVIEW_SCALE);
        int x = Math.round(cardBounds.centerX() - previewW / 2.0F);
        x = Math.max(8, Math.min(width - previewW - 8, x));
        int bottom = Math.round(layout().resolve("hand", width, height).bottom() - 16.0F);
        int y = bottom - previewH;
        y = Math.max(48, Math.min(height - previewH - 8, y));
        return new CardPreviewBounds(x, y, previewW, previewH);
    }

    private int combatantEntryUnderMouse(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (layout().resolve("player_entry", width, height).contains(mouseX, mouseY)) {
            return snapshot.player().entityId();
        }
        if (layout().resolve("monster_entry", width, height).contains(mouseX, mouseY)) {
            return snapshot.monster().entityId();
        }
        return -1;
    }

    private int interactiveTargetUnderMouse(double mouseX, double mouseY, BattleSnapshot snapshot) {
        if (targetingBlockedByUiAt(mouseX, mouseY, snapshot)) {
            return -1;
        }
        int entryTarget = combatantEntryUnderMouse(mouseX, mouseY, snapshot);
        return entryTarget != -1 ? entryTarget : targetEntityUnderMouse(mouseX, mouseY, snapshot);
    }

    private int selectableTargetUnderMouse(double mouseX, double mouseY, BattleSnapshot snapshot) {
        int target = interactiveTargetUnderMouse(mouseX, mouseY, snapshot);
        return target == snapshot.monster().entityId() ? target : -1;
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
                || directVisibleHandIndexAt(mouseX, mouseY, visibleCards) >= 0
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
        List<CardInstance> intentCards = snapshot.monsterIntentCards().isEmpty() && snapshot.monsterIntent() != null
                ? List.of(snapshot.monsterIntent())
                : snapshot.monsterIntentCards();
        if (intentCards.isEmpty() || !hasSelectedOrHoveredMonster(snapshot)) {
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
        List<CardInstance> intentCards = snapshot.monsterIntentCards().isEmpty() && snapshot.monsterIntent() != null
                ? List.of(snapshot.monsterIntent())
                : snapshot.monsterIntentCards();
        if (intentCards.isEmpty() || hoveredMonsterIntentIndex < 0 || hoveredMonsterIntentIndex >= intentCards.size()) {
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
        return new IntentPreviewBounds(previewX, previewY, previewW, previewH);
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
        int[] entityIds = {snapshot.player().entityId(), snapshot.monster().entityId()};
        for (int entityId : entityIds) {
            Entity entity = minecraft.level.getEntity(entityId);
            if (entity == null) {
                continue;
            }
            AABB box = entity.getBoundingBox().inflate(0.25D);
            var hit = box.clip(start, end);
            if (hit.isPresent()) {
                double distance = start.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestEntityId = entityId;
                }
            }
        }
        return bestEntityId;
    }

    private void playDraggedCard(double mouseX, double mouseY) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        CardInstance card = draggedCard(snapshot);
        if (!ClientBattleState.playerTurn() || cardActionsLocked(snapshot) || card == null) {
            return;
        }
        if (card.cost() > snapshot.player().energyLeft()) {
            return;
        }
        int targetId = primaryTargetForCard(card, snapshot);
        if (card.requiresExplicitTarget()) {
            int pointed = targetEntityUnderMouse(mouseX, mouseY, snapshot);
            if (!validDraggedTarget(card, pointed, snapshot)) {
                return;
            }
            targetId = pointed;
        } else {
            if (!playAreaContains(mouseX, mouseY)) {
                return;
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
    }

    private boolean cardActionsLocked(BattleSnapshot snapshot) {
        return snapshot.resolvingEffects() || snapshot.pendingHandSelection().active() || awaitingUseCardSnapshot;
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
            return snapshot.monster().entityId();
        }
        return snapshot.player().entityId();
    }

    private List<Integer> highlightedTargetsForDraggedCard(CardInstance card, BattleSnapshot snapshot, double mouseX, double mouseY) {
        if (card == null || !playableDraggedCardAt(card, snapshot, mouseX, mouseY)) {
            return List.of();
        }
        return highlightedTargetsForCard(card, snapshot);
    }

    private List<Integer> highlightedTargetsForCard(CardInstance card, BattleSnapshot snapshot) {
        int bestCount = 0;
        LinkedHashSet<Integer> targets = new LinkedHashSet<>();
        for (CardEffect effect : card.effects()) {
            if (effect.amount() <= 0) {
                continue;
            }
            if (effect.kind() == CardEffectKind.EXHAUST) {
                continue;
            }
            List<Integer> effectTargets = targetIdsForEffectTarget(effect.target(), snapshot, false);
            if (effectTargets.size() > bestCount) {
                bestCount = effectTargets.size();
                targets.clear();
            }
            if (effectTargets.size() == bestCount) {
                targets.addAll(effectTargets);
            }
        }
        return List.copyOf(targets);
    }

    private List<Integer> targetIdsForEffectTarget(CardTarget target, BattleSnapshot snapshot, boolean monsterCard) {
        int self = monsterCard ? snapshot.monster().entityId() : snapshot.player().entityId();
        int opponent = monsterCard ? snapshot.player().entityId() : snapshot.monster().entityId();
        return switch (target) {
            case SELF, ALL_ALLIES -> List.of(self);
            case SINGLE_ALLY -> List.of();
            case SINGLE_ENEMY, ALL_ENEMIES, RANDOM_ENEMY -> List.of(opponent);
            case ALL_UNITS -> List.of(self, opponent);
            case ALL_OTHER_UNITS -> List.of(opponent);
            case ALL_OTHER_ALLIES, RANDOM_ALLY -> List.of();
        };
    }

    private boolean validDraggedTarget(CardInstance card, int targetEntityId, BattleSnapshot snapshot) {
        if (targetEntityId == -1) {
            return false;
        }
        boolean needsEnemy = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind() != CardEffectKind.EXHAUST && effect.target() == CardTarget.SINGLE_ENEMY);
        boolean needsAlly = card.effects().stream().anyMatch(effect -> effect.amount() > 0 && effect.kind() != CardEffectKind.EXHAUST && effect.target() == CardTarget.SINGLE_ALLY);
        if (needsEnemy && targetEntityId != snapshot.monster().entityId()) {
            return false;
        }
        if (needsAlly) {
            return false;
        }
        return !needsEnemy || !needsAlly;
    }

    private boolean hasSelectedOrHoveredMonster(BattleSnapshot snapshot) {
        return snapshot.selectedTargetId() == snapshot.monster().entityId() || ClientBattleState.isHoveredEntityId(snapshot.monster().entityId());
    }

    private boolean shouldRenderMonsterIntent(BattleSnapshot snapshot, double mouseX, double mouseY) {
        return hasSelectedOrHoveredMonster(snapshot) && !draggingTargetedCardAtMonster(snapshot, mouseX, mouseY);
    }

    private boolean draggingTargetedCardAtMonster(BattleSnapshot snapshot, double mouseX, double mouseY) {
        CardInstance card = draggedCard(snapshot);
        return card != null && card.requiresExplicitTarget() && directTargetEntityUnderMouse(mouseX, mouseY, snapshot) == snapshot.monster().entityId();
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
        for (CardInstance card : snapshot.hand()) {
            if (!hiddenIds.contains(card.id())) {
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

    private FrameCache createFrameCache(BattleSnapshot snapshot, double mouseX, double mouseY) {
        HandLayout layout = handLayout(snapshot);
        int directHandIndex = directHandIndexAt(mouseX, mouseY, snapshot);
        int previewIndex = -1;
        int nextHovered = hoveredHandIndex;
        if (dragState != null || snapshot.hand().isEmpty()) {
            nextHovered = -1;
        } else if (nextHovered >= snapshot.hand().size()) {
            nextHovered = -1;
        } else if (directHandIndex >= 0) {
            nextHovered = directHandIndex;
        } else if (nextHovered >= 0 && previewBounds(layout.card(nextHovered)).contains(mouseX, mouseY)) {
            previewIndex = nextHovered;
        } else {
            nextHovered = -1;
        }
        if (previewIndex < 0 && nextHovered >= 0 && directHandIndex < 0 && previewBounds(layout.card(nextHovered)).contains(mouseX, mouseY)) {
            previewIndex = nextHovered;
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
        return new FrameCache(frameIndex, snapshot, mouseX, mouseY, layout, directHandIndex, previewIndex, nextHovered, targetEntity, blocked);
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

    private record FrameCache(long frame, BattleSnapshot snapshot, double mouseX, double mouseY, HandLayout layout, int handIndex, int previewIndex, int hoveredIndex, int targetEntity, boolean targetingBlocked) {
        private static FrameCache empty() {
            return new FrameCache(-1L, BattleSnapshot.inactive(), Double.NaN, Double.NaN, new HandLayout(List.of()), -1, -1, -1, -1, false);
        }

        private boolean matches(BattleSnapshot snapshot, double mouseX, double mouseY) {
            return this.snapshot == snapshot && Double.compare(this.mouseX, mouseX) == 0 && Double.compare(this.mouseY, mouseY) == 0;
        }

        private HandLayout layout(BattleSnapshot snapshot) {
            if (this.snapshot != snapshot) {
                throw new IllegalStateException("Frame cache used with a stale battle snapshot");
            }
            return layout;
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

    private record HandLayout(List<HandCardBounds> cards) {
        private HandCardBounds card(int index) {
            return cards.get(index);
        }
    }

    private record HandCardBounds(float centerX, float centerY, float angle, float scale) {
        private boolean contains(double mouseX, double mouseY) {
            float halfW = CardRenderHelper.SMALL_CARD_WIDTH * scale / 2.0F;
            float halfH = CardRenderHelper.SMALL_CARD_HEIGHT * scale / 2.0F;
            return mouseX >= centerX - halfW && mouseX <= centerX + halfW && mouseY >= centerY - halfH - 12 && mouseY <= centerY + halfH;
        }
    }

    private HandCardScreenBounds handCardScreenBounds(HandCardAnimation animation, float partialTick, boolean selected) {
        return handLocalAreaScreenBounds(animation, partialTick, selected, 0, 0, CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
    }

    private HandCardScreenBounds handSelectionCardScreenBounds(HandCardAnimation animation, float partialTick, boolean selected) {
        if (!selected) {
            return handCardScreenBounds(animation, partialTick, false);
        }
        float centerX = clampPreviewCenterX(animation.x(partialTick), HAND_PREVIEW_SCALE);
        float centerY = clampPreviewCenterY(animation.y(partialTick), HAND_PREVIEW_SCALE);
        float halfW = CardRenderHelper.CARD_WIDTH * HAND_PREVIEW_SCALE / 2.0F;
        float halfH = CardRenderHelper.CARD_HEIGHT * HAND_PREVIEW_SCALE / 2.0F;
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
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
        }
    }

    private record PileIconBounds(float centerX, float centerY, int size) {
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
            return mouseX >= x - halfW && mouseX <= x + halfW && mouseY >= y - halfH - 12.0F && mouseY <= y + halfH;
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
                age = toCenterTicks + holdTicks;
            }
        }

        private void releaseExhaust() {
            if (played && fadeOut) {
                released = true;
                age = toCenterTicks + holdTicks;
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
    }

    private record IntentPreviewBounds(int x, int y, int width, int height) {
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

    private static final class PreviewCardAnimation {
        private UUID cardId;
        private float baseCenterX;
        private float baseCenterY;
        private float baseScale;
        private float centerX;
        private float centerY;
        private float scale;
        private float progress;
        private float targetCenterX;
        private float targetCenterY;
        private float targetScale;

        private void setOpenTarget(UUID cardId, float fromX, float fromY, float toX, float toY, float fromScale, float toScale) {
            if (!cardId.equals(this.cardId)) {
                this.cardId = cardId;
                this.baseCenterX = fromX;
                this.baseCenterY = fromY;
                this.baseScale = fromScale;
                this.centerX = fromX;
                this.centerY = fromY;
                this.scale = fromScale;
                this.progress = 0.0F;
            }
            this.baseCenterX = fromX;
            this.baseCenterY = fromY;
            this.baseScale = fromScale;
            this.targetCenterX = toX;
            this.targetCenterY = toY;
            this.targetScale = toScale;
        }

        private void setClosingTarget() {
            if (cardId == null) {
                return;
            }
            targetCenterX = baseCenterX;
            targetCenterY = baseCenterY;
            targetScale = baseScale;
        }

        private void advance(float deltaTicks) {
            float amount = frameAmount(0.52F, deltaTicks);
            centerX = approach(centerX, targetCenterX, amount);
            centerY = approach(centerY, targetCenterY, amount);
            scale = approach(scale, targetScale, amount);
            progress = approach(progress, targetScale <= baseScale + 0.01F ? 0.0F : 1.0F, amount);
        }

        private void clear() {
            cardId = null;
            progress = 0.0F;
        }

        private CardInstance card(List<CardInstance> cards) {
            if (cardId == null) {
                return null;
            }
            for (CardInstance card : cards) {
                if (card.id().equals(cardId)) {
                    return card;
                }
            }
            return null;
        }

        private float centerX() {
            return centerX;
        }

        private float centerY() {
            return centerY;
        }

        private float scale() {
            return scale;
        }

        private float progress() {
            return progress;
        }

        private boolean visible() {
            return cardId != null && (progress > 0.02F || Math.abs(scale - baseScale) > 0.015F || Math.abs(centerX - baseCenterX) > 0.5F || Math.abs(centerY - baseCenterY) > 0.5F);
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
    }

}
