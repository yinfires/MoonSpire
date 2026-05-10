package com.yinfires.moonspire.client.developer;

import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardSourceType;
import com.yinfires.moonspire.card.CardTarget;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.card.RegisteredCardDefinition;
import com.yinfires.moonspire.battle.MonsterDeckProfile;
import com.yinfires.moonspire.client.CardRenderHelper;
import com.yinfires.moonspire.client.NoBlurScreen;
import com.yinfires.moonspire.client.ui.MoonSpireModalLayer;
import com.yinfires.moonspire.client.ui.MoonSpireTextureButton;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.developer.DeveloperCardDefinition;
import com.yinfires.moonspire.developer.DeveloperCardEffect;
import com.yinfires.moonspire.developer.DeveloperCardFace;
import com.yinfires.moonspire.developer.DeveloperData;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import com.yinfires.moonspire.developer.DeveloperPaths;
import com.yinfires.moonspire.developer.DeveloperMonsterDefinition;
import com.yinfires.moonspire.network.GiveDeveloperCardPayload;
import com.yinfires.moonspire.network.SaveDeveloperDataPayload;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class DeveloperCenterScreen extends NoBlurScreen {
    private static DeveloperData lastData = DeveloperData.empty();
    private static Tab lastTab = Tab.CARDS;
    private static String lastSelectedCardId = "";
    private static String lastSelectedMonsterId = "";
    private static double lastMonsterListScrollOffset;
    private static final int SIDEBAR_W = 116;
    private static final int FORM_W = 270;
    private static final int MIN_LIST_W = 220;
    private static final int GAP = 12;
    private static final int TOP = 34;
    private static final int LABEL_TO_BOX = 11;
    private static final int FIELD_PITCH = 33;
    static final int TAB_BUTTON_W = 90;
    static final int TAB_BUTTON_H = 20;
    static final int BOTTOM_BUTTON_W = 64;
    static final int BOTTOM_BUTTON_H = 20;
    static final int BOTTOM_BUTTON_GAP = 8;
    private static final int BUTTON_Y_OFFSET = 28;
    private static final int CONTENT_BOTTOM_RESERVE = 42;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int SCROLLBAR_HIT_WIDTH = 20;
    private static final int GRID_SCROLL_STEP = 34;
    private static final int CARD_GRID_MAX_COLUMNS = 5;
    private static final int CARD_GRID_GAP_X = 18;
    private static final int CARD_GRID_GAP_Y = 20;
    private static final int CARD_ID_LABEL_H = 18;
    private static final int CARD_GRID_SCROLL_RESERVE = 48;
    private static final int CARD_GRID_PAD_TOP = 6;
    private static final int CARD_GRID_PAD_BOTTOM = 10;
    private static final int CARD_LIST_TOGGLE_W = 24;
    private static final int CARD_LIST_TOGGLE_H = 56;
    private static final int EFFECT_INSET = 10;
    private static final int EFFECT_TARGET_W = 40;
    private static final int EFFECT_AMOUNT_W = 26;
    private static final int EFFECT_COUNT_W = 26;
    private static final int EFFECT_AMOUNT_LABEL_W = 18;
    private static final int EFFECT_COUNT_LABEL_W = 18;
    private static final int EFFECT_CONTROL_GAP = 2;
    private static final int EFFECT_LABEL_INPUT_GAP = 0;
    private static final int EFFECT_NAME_TARGET_GAP = 8;
    private static final int EFFECT_BOTTOM_PAD = 14;
    private static final int ITEM_SLOT = 18;
    private static final int ITEM_GAP = 8;
    private static final List<CardSourceType> EDIT_SOURCE_TYPES = List.of(CardSourceType.CUSTOM, CardSourceType.MOD, CardSourceType.MONSTER, CardSourceType.WEAPON, CardSourceType.ARMOR, CardSourceType.TOOL, CardSourceType.UNKNOWN);
    private static double lastCardScrollOffset;
    private static double lastItemScrollOffset;
    private static double lastListScrollOffset;
    private DeveloperData data;
    private DeveloperData savedData;
    private Tab tab = Tab.CARDS;
    private EditBox searchBox;
    private EditBox idBox;
    private EditBox nameKeyBox;
    private EditBox costBox;
    private EditBox artPathBox;
    private EditBox entityIdBox;
    private EditBox healthBox;
    private EditBox speedBox;
    private EditBox monsterEnergyBox;
    private EditBox itemSearchBox;
    private EditBox effectSearchBox;
    private EditBox targetSearchBox;
    private final List<EditBox> effectAmountBoxes = new ArrayList<>();
    private final List<EditBox> effectCountBoxes = new ArrayList<>();
    private final List<FaceAreaKind> faceAreaKinds = List.of(FaceAreaKind.COST, FaceAreaKind.NAME, FaceAreaKind.ART, FaceAreaKind.TYPE, FaceAreaKind.DESCRIPTION);
    private String selectedCardId = "";
    private String selectedMonsterId = "";
    private String selectedFaceId = "default";
    private String selectedArtItemId = "";
    private FaceAreaKind selectedFaceAreaKind = FaceAreaKind.COST;
    private int sourceTypeIndex;
    private int cardFilterIndex;
    private double cardScrollOffset;
    private double itemScrollOffset;
    private double listScrollOffset;
    private double effectScrollOffset;
    private double effectPickerScrollOffset;
    private double targetPickerScrollOffset;
    private ScrollArea draggingScrollArea = ScrollArea.NONE;
    private int scrollbarGrabOffset;
    private Component status = Component.empty();
    private Component cardFaceSaveFailureStatus = Component.empty();
    private int statusTicks;
    private boolean cardListExpanded;
    private boolean itemPickerOpen;
    private boolean effectPickerOpen;
    private boolean targetPickerOpen;
    private boolean confirmDelete;
    private int targetPickerEffectIndex = -1;
    private String effectAmountBoxesCardId = "";
    private boolean draggingArt;
    private boolean draggingFaceArea;
    private volatile boolean choosingLocalImage;
    private final Map<String, Path> pendingFaceImageSources = new HashMap<>();
    private int artGrabX;
    private int artGrabY;
    private int faceGrabX;
    private int faceGrabY;
    private int lastMouseX;
    private int lastMouseY;
    private String filteredCardsCacheKey = "";
    private List<DeveloperCardDefinition> filteredCardsCache = List.of();
    private String filteredItemsCacheKey = "";
    private List<ItemRow> filteredItemsCache = List.of();
    private String filteredMonstersCacheKey = "";
    private List<MonsterRow> filteredMonstersCache = List.of();

    public DeveloperCenterScreen(DeveloperData data) {
        super(Component.translatable("screen.moonspire.developer_center"));
        this.data = data;
        this.data.ensureDefaults();
        this.savedData = copyData(this.data);
        this.tab = lastTab;
        selectedCardId = resolveInitialCardId();
        selectedFaceId = this.data.activeFaceId;
        selectedMonsterId = resolveInitialMonsterId();
        if (this.tab == Tab.MONSTERS) {
            listScrollOffset = lastMonsterListScrollOffset;
        } else {
            listScrollOffset = lastListScrollOffset;
        }
        cardScrollOffset = lastCardScrollOffset;
        itemScrollOffset = lastItemScrollOffset;
    }

    public static void open(boolean allowed, String json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (!allowed) {
            DeveloperDataManager.setClientData(DeveloperData.fromJson(json));
            minecraft.player.displayClientMessage(Component.translatable("debug.moonspire.developer_center.no_permission"), true);
            return;
        }
        lastData = DeveloperData.fromJson(json);
        if (minecraft.screen instanceof DeveloperCenterScreen screen) {
            screen.rememberOpenState();
        }
        minecraft.setScreen(new DeveloperCenterScreen(lastData));
    }

    private String resolveInitialMonsterId() {
        if (!lastSelectedMonsterId.isBlank()) {
            return lastSelectedMonsterId;
        }
        if (!data.monsters.isEmpty()) {
            return data.monsters.getFirst().entityTypeId();
        }
        return "minecraft:zombie";
    }

    private String resolveInitialCardId() {
        if (lastSelectedCardId.isBlank()) {
            return "";
        }
        String registeredId = MoonSpireCardRegistry.registeredDeveloperId(lastSelectedCardId);
        return filteredCards().stream()
                .filter(card -> registeredId.equals(MoonSpireCardRegistry.registeredDeveloperId(card.id())))
                .findFirst()
                .map(DeveloperCardDefinition::id)
                .orElse("");
    }

    @Override
    protected void init() {
        clearWidgets();
        effectAmountBoxes.clear();
        effectCountBoxes.clear();
        Layout layout = layout();
        searchBox = addBox(layout.searchX(tab), layout.searchBoxY(), layout.searchW(tab), Component.translatable("debug.moonspire.search"));
        idBox = addBox(layout.formX(), layout.fieldBoxY(0), layout.formW(), Component.translatable("debug.moonspire.field.id"));
        nameKeyBox = addBox(layout.formX(), layout.fieldBoxY(1), layout.formW(), Component.translatable("debug.moonspire.field.card_name"));
        costBox = addBox(layout.formX(), layout.fieldBoxY(3), 54, Component.translatable("debug.moonspire.field.cost"));
        artPathBox = addBox(layout.formX(), layout.fieldBoxY(tab == Tab.FACES ? 2 : 4), layout.formW(), Component.translatable("debug.moonspire.field.image_path"));
        entityIdBox = addBox(layout.formX(), layout.fieldBoxY(0), layout.formW(), Component.translatable("debug.moonspire.field.entity_id"));
        int monsterStatW = (layout.formW() - GAP * 2) / 3;
        healthBox = addBox(layout.formX(), layout.fieldBoxY(1), monsterStatW, Component.translatable("debug.moonspire.field.health"));
        speedBox = addBox(layout.formX() + monsterStatW + GAP, layout.fieldBoxY(1), monsterStatW, Component.translatable("debug.moonspire.field.speed"));
        monsterEnergyBox = addBox(layout.formX() + (monsterStatW + GAP) * 2, layout.fieldBoxY(1), monsterStatW, Component.translatable("debug.moonspire.field.energy"));
        itemSearchBox = addBox(layout.itemX(), layout.searchBoxY(), Math.max(1, layout.itemW()), Component.translatable("debug.moonspire.field.item_search"));
        effectSearchBox = addBox(layout.formX() + 6, effectPickerY(layout, selectedEffectCount()) + 5, Math.max(1, layout.formW() - 12), Component.translatable("debug.moonspire.effect_search"));
        targetSearchBox = addBox(layout.formX() + 6, targetPickerY(layout, selectedEffectCount()) + 5, Math.max(1, layout.formW() - 12), Component.translatable("debug.moonspire.target_search"));
        createEffectAmountBoxes(layout);
        hideIrrelevantBoxes();
        int tabX = 4 + (SIDEBAR_W - TAB_BUTTON_W) / 2;
        addRenderableWidget(new MoonSpireTextureButton(tabX, layout.top(), TAB_BUTTON_W, TAB_BUTTON_H, Component.translatable("debug.moonspire.tab.cards"), button -> switchTab(Tab.CARDS)));
        addRenderableWidget(new MoonSpireTextureButton(tabX, layout.top() + 24, TAB_BUTTON_W, TAB_BUTTON_H, Component.translatable("debug.moonspire.tab.faces"), button -> switchTab(Tab.FACES)));
        addRenderableWidget(new MoonSpireTextureButton(tabX, layout.top() + 48, TAB_BUTTON_W, TAB_BUTTON_H, Component.translatable("debug.moonspire.tab.monsters"), button -> switchTab(Tab.MONSTERS)));
        addRenderableWidget(new MoonSpireTextureButton(tabX, layout.top() + 72, TAB_BUTTON_W, TAB_BUTTON_H, Component.translatable("debug.moonspire.tab.layout"), button -> switchTab(Tab.LAYOUT)));
        int globalButtonsX = width - 16 - bottomButtonGroupWidth(2);
        addTabActionButtons(layout, globalButtonsX - GAP);
        addBottomButton(globalButtonsX, layout.buttonY(), Component.translatable("debug.moonspire.save"), button -> save());
        addBottomButton(bottomButtonX(globalButtonsX, 1), layout.buttonY(), Component.translatable("gui.done"), button -> onClose());
        refreshFields();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Layout layout = layout();
        renderPanels(graphics, layout);
        graphics.drawString(font, title, 10, 10, 0xFFFFD166, false);
        if (tab == Tab.CARDS) {
            renderCards(graphics, layout);
        } else if (tab == Tab.FACES) {
            renderFaces(graphics, layout);
        } else if (tab == Tab.MONSTERS) {
            renderMonsters(graphics, layout);
        } else {
            renderLayoutInfo(graphics, layout);
        }
        if (statusTicks > 0) {
            drawTrimmed(graphics, status, layout.formX(), layout.buttonY() - 14, layout.contentRight() - layout.formX(), 0xFFB8E6FF);
            statusTicks--;
        }
        renderFieldLabels(graphics, layout);
        if (effectPickerOpen || targetPickerOpen) {
            hideEffectAmountBoxes();
            hideEffectCountBoxes();
        }
        boolean modalOpen = confirmDelete || itemPickerOpen || effectPickerOpen || targetPickerOpen;
        if (!modalOpen) {
            renderWidgetsWithClippedEffectBoxes(graphics, layout, mouseX, mouseY, partialTick);
        }
        if (!confirmDelete && itemPickerOpen) {
            renderItemPickerPopup(graphics, layout);
        }
        if (!confirmDelete && effectPickerOpen) {
            renderEffectPickerPopup(graphics, layout, mouseX, mouseY, partialTick);
        }
        if (!confirmDelete && targetPickerOpen) {
            renderTargetPickerPopup(graphics, layout, mouseX, mouseY, partialTick);
        }
        if (confirmDelete) {
            renderDeleteConfirm(graphics);
        }
    }

    private void renderWidgetsWithClippedEffectBoxes(GuiGraphics graphics, Layout layout, int mouseX, int mouseY, float partialTick) {
        List<EditBox> effectBoxes = new ArrayList<>();
        effectBoxes.addAll(effectAmountBoxes);
        effectBoxes.addAll(effectCountBoxes);
        List<Boolean> visible = effectBoxes.stream().map(box -> box.visible).toList();
        for (EditBox box : effectBoxes) {
            box.visible = false;
        }
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < effectBoxes.size(); i++) {
            effectBoxes.get(i).visible = visible.get(i);
        }
        renderEffectBoxesClipped(graphics, layout, mouseX, mouseY, partialTick);
    }

    private void renderEffectBoxesClipped(GuiGraphics graphics, Layout layout, int mouseX, int mouseY, float partialTick) {
        if (tab != Tab.CARDS) {
            return;
        }
        DeveloperCardDefinition card = selectedCard();
        if (card == null) {
            return;
        }
        GridLayout grid = effectEditorGrid(layout, card.normalizedEffects().size() + 1);
        graphics.enableScissor(grid.viewX(), grid.viewY(), grid.viewX() + grid.viewW(), grid.viewY() + grid.viewH());
        for (EditBox box : effectAmountBoxes) {
            if (box.visible) {
                box.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        for (EditBox box : effectCountBoxes) {
            if (box.visible) {
                box.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Layout layout = layout();
            if (confirmDelete) {
                return clickDeleteConfirm(mouseX, mouseY);
            }
            if (targetPickerOpen) {
                if (clickTargetPicker(layout, mouseX, mouseY, button)) {
                    return true;
                }
                return true;
            }
            if (effectPickerOpen) {
                if (clickEffectPicker(layout, mouseX, mouseY, button)) {
                    return true;
                }
                return true;
            }
            if (itemPickerOpen) {
                if (clickScrollbar(layout.itemGrid(filteredItems().size()), ScrollArea.ITEMS, mouseY, mouseX)) {
                    return true;
                }
                if (clickItemGrid(layout, mouseX, mouseY)) {
                    return true;
                }
                if (insideRect(mouseX, mouseY, layout.itemX(), layout.searchBoxY(), Math.max(1, layout.itemW()), 18) && itemSearchBox != null) {
                    itemSearchBox.mouseClicked(mouseX, mouseY, button);
                    return true;
                }
                if (!insideGrid(layout.itemGrid(filteredItems().size()), mouseX, mouseY) && !insideRect(mouseX, mouseY, layout.itemX() - 8, layout.top() - 8, layout.itemW() + 16, layout.buttonY() - layout.top())) {
                    itemPickerOpen = false;
                    init();
                    return true;
                }
                return true;
            }
            if (tab == Tab.CARDS && clickCardListToggle(layout, mouseX, mouseY)) {
                return true;
            }
            if (tab == Tab.CARDS && clickScrollbar(layout.cardGrid(filteredCards().size()), ScrollArea.CARDS, mouseY, mouseX)) {
                return true;
            }
            if ((tab == Tab.FACES || tab == Tab.MONSTERS) && clickScrollbar(layout.textListGrid(textListSize()), ScrollArea.LIST, mouseY, mouseX)) {
                return true;
            }
            if (tab == Tab.CARDS && clickCardGrid(layout, mouseX, mouseY)) {
                return true;
            }
            if (tab == Tab.CARDS && clickCardForm(layout, mouseX, mouseY)) {
                return true;
            }
            if (tab == Tab.FACES && clickFaceForm(layout, mouseX, mouseY)) {
                return true;
            }
            if (tab == Tab.FACES && clickFaceList(layout, mouseX, mouseY)) {
                return true;
            }
            if (tab == Tab.MONSTERS && clickMonsterForm(layout, mouseX, mouseY)) {
                return true;
            }
            if (tab == Tab.MONSTERS && clickMonsterList(layout, mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollArea != ScrollArea.NONE) {
            draggingScrollArea = ScrollArea.NONE;
            return true;
        }
        if (button == 0 && draggingArt) {
            draggingArt = false;
            return true;
        }
        if (button == 0 && draggingFaceArea) {
            draggingFaceArea = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScrollArea != ScrollArea.NONE) {
            Layout layout = layout();
            if (draggingScrollArea == ScrollArea.CARDS) {
                dragScrollbar(layout.cardGrid(filteredCards().size()), mouseY, ScrollArea.CARDS);
            } else if (draggingScrollArea == ScrollArea.ITEMS) {
                dragScrollbar(layout.itemGrid(filteredItems().size()), mouseY, ScrollArea.ITEMS);
            } else if (draggingScrollArea == ScrollArea.LIST) {
                dragScrollbar(layout.textListGrid(textListSize()), mouseY, ScrollArea.LIST);
            } else if (draggingScrollArea == ScrollArea.EFFECTS) {
                dragScrollbar(effectEditorGrid(layout, selectedEffectCount() + 1), mouseY, ScrollArea.EFFECTS);
            } else if (draggingScrollArea == ScrollArea.EFFECT_PICKER) {
                PickerBounds picker = effectPickerBounds(layout, selectedEffectCount());
                dragScrollbar(effectPickerGrid(picker, filteredEffectKinds().size()), mouseY, ScrollArea.EFFECT_PICKER);
            } else if (draggingScrollArea == ScrollArea.TARGET_PICKER) {
                PickerBounds picker = targetPickerBounds(layout, selectedEffectCount());
                dragScrollbar(targetPickerGrid(picker, filteredTargets().size()), mouseY, ScrollArea.TARGET_PICKER);
            }
            return true;
        }
        if (button == 0 && draggingArt && tab == Tab.CARDS) {
            DeveloperCardDefinition card = selectedCard();
            if (card != null) {
                replaceCard(card, new DeveloperCardDefinition(card.id(), card.displayName(), card.nameKey(), card.descriptionKey(), card.cost(), card.attack(), card.defense(), card.bleed(), card.normalizedEffects(), card.sourceType(), card.artPath(), card.artItemId(), (int) mouseX - artGrabX, (int) mouseY - artGrabY, card.artScale(), card.faceId()));
                refreshFields();
            }
            return true;
        }
        if (button == 0 && draggingFaceArea && tab == Tab.FACES) {
            moveSelectedFaceArea((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.CARDS && scrollY != 0.0D) {
            Layout layout = layout();
            GridLayout cardGrid = layout.cardGrid(filteredCards().size());
            GridLayout itemGrid = layout.itemGrid(filteredItems().size());
            GridLayout effectGrid = effectEditorGrid(layout, selectedEffectCount() + 1);
            PickerBounds effectPicker = effectPickerBounds(layout, selectedEffectCount());
            GridLayout effectPickerGrid = effectPickerGrid(effectPicker, filteredEffectKinds().size());
            if (effectPickerOpen && insideGrid(effectPickerGrid, mouseX, mouseY) && hasScrollbar(effectPickerGrid)) {
                effectPickerScrollOffset = clampScroll(effectPickerScrollOffset - scrollY * 16, effectPickerGrid);
                return true;
            }
            PickerBounds targetPicker = targetPickerBounds(layout, selectedEffectCount());
            GridLayout targetPickerGrid = targetPickerGrid(targetPicker, filteredTargets().size());
            if (targetPickerOpen && insideGrid(targetPickerGrid, mouseX, mouseY) && hasScrollbar(targetPickerGrid)) {
                targetPickerScrollOffset = clampScroll(targetPickerScrollOffset - scrollY * 16, targetPickerGrid);
                return true;
            }
            if (effectPickerOpen && insideRect(mouseX, mouseY, effectPicker.x(), effectPicker.y(), effectPicker.w(), effectPicker.h())) {
                return true;
            }
            if (targetPickerOpen && insideRect(mouseX, mouseY, targetPicker.x(), targetPicker.y(), targetPicker.w(), targetPicker.h())) {
                return true;
            }
            if (itemPickerOpen || effectPickerOpen || targetPickerOpen) {
                return true;
            }
            if (!cardListExpanded && insideRect(mouseX, mouseY, layout.previewX(), layout.previewY(), CardRenderHelper.CARD_WIDTH, CardRenderHelper.CARD_HEIGHT)) {
                adjustArtScale(scrollY);
                return true;
            }
            if (insideGrid(cardGrid, mouseX, mouseY) && hasScrollbar(cardGrid)) {
                cardScrollOffset = clampScroll(cardScrollOffset - scrollY * GRID_SCROLL_STEP, cardGrid);
                lastCardScrollOffset = cardScrollOffset;
                return true;
            }
            if (itemPickerOpen && insideGrid(itemGrid, mouseX, mouseY) && hasScrollbar(itemGrid)) {
                itemScrollOffset = clampScroll(itemScrollOffset - scrollY * GRID_SCROLL_STEP, itemGrid);
                lastItemScrollOffset = itemScrollOffset;
                return true;
            }
            if (insideGrid(effectGrid, mouseX, mouseY) && hasScrollbar(effectGrid)) {
                effectScrollOffset = clampScroll(effectScrollOffset - scrollY * GRID_SCROLL_STEP, effectGrid);
                return true;
            }
        } else if (tab == Tab.FACES && scrollY != 0.0D) {
            Layout layout = layout();
            if (insideRect(mouseX, mouseY, layout.facePreviewX(), layout.facePreviewY(), layout.facePreviewW(), layout.facePreviewH())) {
                adjustSelectedFaceArea(scrollY, Screen.hasShiftDown(), Screen.hasControlDown());
                return true;
            }
            GridLayout listGrid = layout.textListGrid(textListSize());
            if (insideGrid(listGrid, mouseX, mouseY) && hasScrollbar(listGrid)) {
                listScrollOffset = clampScroll(listScrollOffset - scrollY * GRID_SCROLL_STEP, listGrid);
                lastListScrollOffset = listScrollOffset;
                rememberMonsterListScrollOffset();
                return true;
            }
        } else if (tab == Tab.MONSTERS && scrollY != 0.0D) {
            Layout layout = layout();
            GridLayout listGrid = layout.textListGrid(textListSize());
            if (insideGrid(listGrid, mouseX, mouseY) && hasScrollbar(listGrid)) {
                listScrollOffset = clampScroll(listScrollOffset - scrollY * GRID_SCROLL_STEP, listGrid);
                rememberMonsterListScrollOffset();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (targetPickerOpen && targetSearchBox != null) {
            if (keyCode == 256) {
                closeTargetPicker();
                init();
                return true;
            }
            return targetSearchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (effectPickerOpen && effectSearchBox != null) {
            if (keyCode == 256) {
                effectPickerOpen = false;
                init();
                return true;
            }
            return effectSearchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (itemPickerOpen && itemSearchBox != null) {
            if (keyCode == 256) {
                itemPickerOpen = false;
                init();
                return true;
            }
            return itemSearchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            applyFields();
            return true;
        }
        if (tab == Tab.FACES && handleFaceAreaKey(keyCode, modifiers)) {
            return true;
        }
        if (keyCode == 83 && (modifiers & 0x2) != 0) {
            save();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (targetPickerOpen && targetSearchBox != null) {
            return targetSearchBox.charTyped(codePoint, modifiers);
        }
        if (effectPickerOpen && effectSearchBox != null) {
            return effectSearchBox.charTyped(codePoint, modifiers);
        }
        if (itemPickerOpen && itemSearchBox != null) {
            return itemSearchBox.charTyped(codePoint, modifiers);
        }
        boolean handled = super.charTyped(codePoint, modifiers);
        applyFields();
        return handled;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private EditBox addBox(int x, int y, int w, Component hint) {
        EditBox box = new EditBox(font, x, y, w, 18, hint);
        box.setHint(hint);
        addRenderableWidget(box);
        return box;
    }

    private void createEffectAmountBoxes(Layout layout) {
        if (tab != Tab.CARDS) {
            return;
        }
        DeveloperCardDefinition card = selectedCard();
        if (card == null) {
            return;
        }
        List<DeveloperCardEffect> effects = card.normalizedEffects();
        for (int i = 0; i < effects.size(); i++) {
            int rowY = layout.effectsY() + i * 24;
            EditBox box = addBox(layout.formX() + layout.formW() - 58, rowY, EFFECT_AMOUNT_W, Component.translatable("screen.moonspire.value_number", effects.get(i).amount()));
            box.setValue(Integer.toString(effects.get(i).amount()));
            effectAmountBoxes.add(box);
            EditBox countBox = addBox(layout.formX() + layout.formW() - 58, rowY, EFFECT_COUNT_W, Component.translatable("debug.moonspire.effect_count"));
            countBox.setValue(Integer.toString(effects.get(i).count()));
            effectCountBoxes.add(countBox);
        }
    }

    private void addTabActionButtons(Layout layout, int rightLimit) {
        if (tab == Tab.CARDS) {
            int x = bottomButtonGroupX(layout.formX(), rightLimit, 5);
            addBottomButton(x, layout.buttonY(), Component.translatable("debug.moonspire.new"), button -> createCurrent());
            addBottomButton(bottomButtonX(x, 1), layout.buttonY(), Component.translatable("debug.moonspire.delete"), button -> requestDelete());
            addBottomButton(bottomButtonX(x, 2), layout.buttonY(), Component.translatable("debug.moonspire.reset"), button -> resetCurrentCardToSaved());
            addBottomButton(bottomButtonX(x, 3), layout.buttonY(), Component.translatable("debug.moonspire.next_filter"), button -> nextFilter());
            addBottomButton(bottomButtonX(x, 4), layout.buttonY(), Component.translatable("debug.moonspire.give"), button -> giveSelectedCard());
        } else if (tab == Tab.FACES) {
            int x = bottomButtonGroupX(layout.formX(), rightLimit, 4);
            addBottomButton(x, layout.buttonY(), Component.translatable("debug.moonspire.new"), button -> createCurrent());
            addBottomButton(bottomButtonX(x, 1), layout.buttonY(), Component.translatable("debug.moonspire.delete"), button -> requestDelete());
            addBottomButton(bottomButtonX(x, 2), layout.buttonY(), Component.translatable("debug.moonspire.reset"), button -> resetCurrentFaceToSaved());
            addBottomButton(bottomButtonX(x, 3), layout.buttonY(), Component.translatable("debug.moonspire.apply"), button -> openFaceApplicationScreen());
        } else if (tab == Tab.MONSTERS) {
            int x = bottomButtonGroupX(layout.formX(), rightLimit, 2);
            addBottomButton(x, layout.buttonY(), Component.translatable("debug.moonspire.reset"), button -> resetCurrentMonsterToSaved());
            addBottomButton(bottomButtonX(x, 1), layout.buttonY(), Component.translatable("debug.moonspire.delete"), button -> requestDelete());
        }
    }

    private void addBottomButton(int x, int y, Component label, MoonSpireTextureButton.PressAction action) {
        addRenderableWidget(new MoonSpireTextureButton(x, y, BOTTOM_BUTTON_W, BOTTOM_BUTTON_H, label, action));
    }

    static int bottomButtonGroupWidth(int count) {
        return count * BOTTOM_BUTTON_W + Math.max(0, count - 1) * BOTTOM_BUTTON_GAP;
    }

    static int bottomButtonX(int startX, int index) {
        return startX + index * (BOTTOM_BUTTON_W + BOTTOM_BUTTON_GAP);
    }

    static int bottomButtonGroupX(int left, int right, int count) {
        int width = bottomButtonGroupWidth(count);
        return Math.max(left, left + Math.max(0, right - left - width) / 2);
    }

    private void switchTab(Tab next) {
        applyFields();
        if (tab == next) {
            return;
        }
        rememberOpenState();
        rememberMonsterListScrollOffset();
        tab = next;
        lastTab = next;
        cardScrollOffset = 0.0D;
        itemScrollOffset = 0.0D;
        listScrollOffset = 0.0D;
        if (next == Tab.MONSTERS) {
            listScrollOffset = lastMonsterListScrollOffset;
        }
        effectScrollOffset = 0.0D;
        effectPickerScrollOffset = 0.0D;
        targetPickerScrollOffset = 0.0D;
        targetPickerEffectIndex = -1;
        draggingScrollArea = ScrollArea.NONE;
        itemPickerOpen = false;
        effectPickerOpen = false;
        targetPickerOpen = false;
        init();
    }

    private void hideIrrelevantBoxes() {
        boolean cards = tab == Tab.CARDS;
        boolean faces = tab == Tab.FACES;
        boolean monsters = tab == Tab.MONSTERS;
        boolean cardSelected = cards && selectedCard() != null;
        List<EditBox> boxes = new ArrayList<>(List.of(searchBox, idBox, nameKeyBox, costBox, artPathBox, entityIdBox, healthBox, speedBox, monsterEnergyBox, itemSearchBox, effectSearchBox, targetSearchBox));
        boxes.addAll(effectAmountBoxes);
        boxes.addAll(effectCountBoxes);
        for (EditBox box : boxes) {
            if (box != null) {
                box.visible = false;
                box.active = false;
            }
        }
        if (searchBox != null) {
            searchBox.visible = cards || monsters || faces;
            searchBox.active = searchBox.visible;
        }
        setVisible(idBox, faces);
        setVisible(nameKeyBox, cardSelected);
        setVisible(costBox, cardSelected);
        setVisible(artPathBox, false);
        setVisible(entityIdBox, monsters);
        setVisible(healthBox, monsters);
        setVisible(speedBox, monsters);
        setVisible(monsterEnergyBox, monsters);
        setVisible(itemSearchBox, cardSelected && itemPickerOpen);
        setVisible(effectSearchBox, cardSelected && effectPickerOpen);
        setVisible(targetSearchBox, cardSelected && targetPickerOpen);
        for (EditBox box : effectAmountBoxes) {
            setVisible(box, false);
        }
        for (EditBox box : effectCountBoxes) {
            setVisible(box, false);
        }
    }

    private void setVisible(EditBox box, boolean visible) {
        if (box != null) {
            box.visible = visible;
            box.active = visible;
        }
    }

    private void renderCards(GuiGraphics graphics, Layout layout) {
        drawTrimmed(graphics, Component.translatable("debug.moonspire.filter", filterName()), layout.listX(), layout.top(), layout.listW(), 0xFFE3C48C);
        DeveloperCardDefinition card = selectedCard();
        if (card == null) {
            renderCardGrid(graphics, layout);
            renderCardListToggle(graphics, layout);
            return;
        }
        String id = card == null ? "" : MoonSpireCardRegistry.registeredDeveloperId(card.id());
        drawTrimmed(graphics, Component.translatable("debug.moonspire.card_id", id), layout.formX(), layout.fieldBoxY(1) + 24, layout.formW(), 0xFFE3C48C);
        drawTrimmed(graphics, Component.translatable("debug.moonspire.card_type", sourceTypeName()), layout.formX(), layout.fieldBoxY(1) + 38, layout.formW(), 0xFFE3C48C);
        if (!cardListExpanded) {
            renderCardPreview(graphics, layout);
        }
        renderCardImageControls(graphics, layout);
        renderCardEffectEditor(graphics, layout);
        renderCardGrid(graphics, layout);
        renderCardListToggle(graphics, layout);
    }

    private void renderCardPreview(GuiGraphics graphics, Layout layout) {
        DeveloperCardDefinition card = selectedCard();
        if (card == null) {
            return;
        }
        drawHeader(graphics, Component.translatable("debug.moonspire.card_preview"), layout.previewX(), layout.top());
        CardRenderHelper.renderCard(graphics, font, card.toCardInstance(), layout.previewX(), layout.previewY(), false, true, data);
    }

    private void renderCardImageControls(GuiGraphics graphics, Layout layout) {
        int y = layout.imageControlsY();
        drawButtonLike(graphics, layout.formX(), y, 76, 18, Component.translatable("debug.moonspire.local_image"));
        drawButtonLike(graphics, layout.formX() + 82, y, 76, 18, Component.translatable("debug.moonspire.item_icon"));
        drawButtonLike(graphics, layout.formX() + 164, y, 54, 18, Component.translatable("debug.moonspire.reset"));
    }

    private void renderCardEffectEditor(GuiGraphics graphics, Layout layout) {
        DeveloperCardDefinition card = selectedCard();
        if (card == null) {
            return;
        }
        List<DeveloperCardEffect> effects = card.normalizedEffects();
        GridLayout grid = effectEditorGrid(layout, effects.size() + 1);
        effectScrollOffset = clampScroll(effectScrollOffset, grid);
        syncEffectAmountBoxes(layout, effects, grid);
        graphics.enableScissor(grid.viewX(), grid.viewY(), grid.viewX() + grid.viewW(), grid.viewY() + grid.viewH());
        for (int i = firstVisibleIndex(grid); i < Math.min(effects.size() + 1, lastVisibleIndex(grid)); i++) {
            GridCell cell = cell(grid, i);
            int rowY = cell.y();
            if (i < effects.size()) {
                DeveloperCardEffect effect = effects.get(i);
                drawButtonLike(graphics, grid.viewX(), rowY, 20, 18, Component.translatable("debug.moonspire.remove_effect"));
                drawTrimmed(graphics, effectName(effect.kind()), effectNameX(grid), rowY + 5, Math.max(1, effectNameWidth(grid)), 0xFFEDE8FF);
                int targetX = effectTargetX(grid);
                if (effect.canChangeTarget()) {
                    drawButtonLike(graphics, targetX, rowY, EFFECT_TARGET_W, 18, Component.translatable("debug.moonspire.target_picker"));
                } else {
                    drawTrimmed(graphics, targetName(effect.target()), targetX, rowY + 5, EFFECT_TARGET_W, 0xFF8F879E);
                }
                if (effect.kind().usesAmount()) {
                    drawTrimmed(graphics, Component.translatable("debug.moonspire.effect_amount"), effectAmountLabelX(grid), rowY + 5, EFFECT_AMOUNT_LABEL_W, 0xFFD8CEB5);
                }
                if (effect.canChangeCount()) {
                    drawTrimmed(graphics, Component.translatable("debug.moonspire.effect_count"), effectCountLabelX(grid), rowY + 5, EFFECT_COUNT_LABEL_W, 0xFFD8CEB5);
                }
            } else {
                drawButtonLike(graphics, grid.viewX(), rowY, 20, 18, Component.translatable("debug.moonspire.add_effect"));
            }
        }
        graphics.disableScissor();
        renderGridScrollbar(graphics, grid);
    }

    private void renderCardListToggle(GuiGraphics graphics, Layout layout) {
        ToggleBounds bounds = cardListToggleBounds(layout);
        MoonSpireUiTextures.drawCardListToggle(graphics, bounds.x(), bounds.y(), bounds.w(), bounds.h(), insideRect(lastMouseX, lastMouseY, bounds.x(), bounds.y(), bounds.w(), bounds.h()));
        Component arrow = Component.translatable(cardListExpanded ? "debug.moonspire.collapse_arrow" : "debug.moonspire.expand_arrow");
        drawCenteredFitted(graphics, arrow.getString(), bounds.x() + 4, bounds.y() + (bounds.h() - font.lineHeight) / 2, bounds.w() - 8, font.lineHeight, 0xFFFFFFFF);
    }

    private void drawButtonLike(GuiGraphics graphics, int x, int y, int w, int h, Component text) {
        MoonSpireUiTextures.drawButton(graphics, x, y, w, h, insideRect(lastMouseX, lastMouseY, x, y, w, h), true);
        drawCenteredFitted(graphics, text.getString(), x + 4, y + (h - font.lineHeight) / 2, w - 8, font.lineHeight, 0xFFFFFFFF);
    }

    private Component effectName(DeveloperCardEffect.Kind kind) {
        return Component.translatable("debug.moonspire.effect." + kind.name().toLowerCase(Locale.ROOT));
    }

    private Component targetName(CardTarget target) {
        return Component.translatable("debug.moonspire.target." + target.name().toLowerCase(Locale.ROOT));
    }

    private void openTargetPicker(int effectIndex) {
        targetPickerEffectIndex = effectIndex;
        targetPickerOpen = true;
        effectPickerOpen = false;
        itemPickerOpen = false;
        targetPickerScrollOffset = 0.0D;
        effectPickerScrollOffset = 0.0D;
        init();
    }

    private void renderFaces(GuiGraphics graphics, Layout layout) {
        drawHeader(graphics, Component.translatable("debug.moonspire.faces"), layout.listX(), layout.top());
        drawWrappedLimited(graphics, Component.translatable("debug.moonspire.face_hint"), layout.formX(), layout.formInfoY(4, 0), layout.formW(), layout.bottom(), 0xFFC9C2DD);
        renderFaceImageControls(graphics, layout);
        renderFaceAreaPicker(graphics, layout);
        renderFacePreview(graphics, layout);
        GridLayout grid = layout.textListGrid(data.cardFaces.size());
        listScrollOffset = clampScroll(listScrollOffset, grid);
        graphics.enableScissor(grid.viewX(), grid.viewY(), grid.viewX() + grid.viewW(), grid.viewY() + grid.viewH());
        for (int i = firstVisibleIndex(grid); i < Math.min(data.cardFaces.size(), lastVisibleIndex(grid)); i++) {
            GridCell cell = cell(grid, i);
            DeveloperCardFace face = data.cardFaces.get(i);
            boolean selected = face.id().equals(selectedFaceId);
            drawListRow(graphics, layout.listX(), cell.y(), layout.listW() - SCROLLBAR_HIT_WIDTH, Component.translatable("debug.moonspire.id_row", face.id()), selected);
        }
        graphics.disableScissor();
        renderGridScrollbar(graphics, grid);
    }

    private void renderFaceImageControls(GuiGraphics graphics, Layout layout) {
        int y = layout.fieldBoxY(1);
        drawButtonLike(graphics, layout.formX(), y, 76, 18, Component.translatable("debug.moonspire.local_image"));
        drawButtonLike(graphics, layout.formX() + 82, y, 54, 18, Component.translatable("debug.moonspire.reset"));
        String imagePath = selectedFace().imagePath();
        Component current = imagePath.isBlank()
                ? Component.translatable("debug.moonspire.face_image_empty")
                : Component.translatable("debug.moonspire.face_image_current", imagePath);
        drawTrimmed(graphics, current, layout.formX(), y + 22, layout.formW(), 0xFFC9C2DD);
    }

    private void renderFaceAreaPicker(GuiGraphics graphics, Layout layout) {
        int x = layout.formX();
        int y = layout.fieldBoxY(3);
        int buttonW = Math.max(42, (layout.formW() - 16) / faceAreaKinds.size());
        for (int i = 0; i < faceAreaKinds.size(); i++) {
            FaceAreaKind kind = faceAreaKinds.get(i);
            int bx = x + i * (buttonW + 4);
            if (bx + buttonW > x + layout.formW()) {
                buttonW = Math.max(28, x + layout.formW() - bx);
            }
            boolean selected = kind == selectedFaceAreaKind;
            MoonSpireUiTextures.drawButton(graphics, bx, y, buttonW, 18, selected || insideRect(lastMouseX, lastMouseY, bx, y, buttonW, 18), true);
            drawCenteredFitted(graphics, faceAreaName(kind).getString(), bx + 3, y + (18 - font.lineHeight) / 2, buttonW - 6, font.lineHeight, selected ? 0xFFFFF3BF : 0xFFFFFFFF);
        }
    }

    private void renderFacePreview(GuiGraphics graphics, Layout layout) {
        DeveloperCardFace face = selectedFace();
        int x = layout.facePreviewX();
        int y = layout.facePreviewY();
        drawHeader(graphics, Component.translatable("debug.moonspire.card_face_preview"), x, layout.top());
        CardRenderHelper.renderCardFaceBase(graphics, face.imagePath(), x, y, layout.facePreviewW(), layout.facePreviewH());
        for (FaceAreaKind kind : faceAreaKinds) {
            DeveloperCardFace.Area area = faceArea(face, kind);
            int ax = x + scaleFaceX(area.x());
            int ay = y + scaleFaceY(area.y());
            int aw = scaleFaceX(area.width());
            int ah = scaleFaceY(area.height());
            int color = kind == selectedFaceAreaKind ? 0xFFFFFF00 : 0x998BD3FF;
            graphics.renderOutline(ax, ay, aw, ah, color);
            graphics.fill(ax + aw - 5, ay + ah - 5, ax + aw, ay + ah, color);
            drawCenteredFitted(graphics, faceAreaPreviewText(kind).getString(), ax + 2, ay + 2, Math.max(1, aw - 4), Math.max(1, ah - 4), color);
        }
    }

    private void renderMonsters(GuiGraphics graphics, Layout layout) {
        drawHeader(graphics, Component.translatable("debug.moonspire.monsters"), layout.listX(), layout.top());
        drawWrappedLimited(graphics, Component.translatable("debug.moonspire.monster_actions"), layout.formX(), layout.formInfoY(3, 0), layout.formW(), layout.bottom(), 0xFFC9C2DD);
        drawButtonLike(graphics, layout.formX(), layout.fieldBoxY(2), 112, 18, Component.translatable("debug.moonspire.open_deck"));
        drawTrimmed(graphics, Component.translatable("debug.moonspire.monster_deck_count", selectedMonsterEffective().deckCardIds().size()), layout.formX() + 120, layout.fieldBoxY(2) + 5, Math.max(1, layout.formW() - 120), 0xFFE3C48C);
        List<MonsterRow> monsters = filteredMonsters();
        GridLayout grid = layout.textListGrid(monsters.size());
        listScrollOffset = clampScroll(listScrollOffset, grid);
        graphics.enableScissor(grid.viewX(), grid.viewY(), grid.viewX() + grid.viewW(), grid.viewY() + grid.viewH());
        for (int i = firstVisibleIndex(grid); i < Math.min(monsters.size(), lastVisibleIndex(grid)); i++) {
            GridCell cell = cell(grid, i);
            MonsterRow row = monsters.get(i);
            boolean selected = row.id().equals(selectedMonsterId);
            drawListRow(graphics, layout.listX(), cell.y(), layout.listW() - SCROLLBAR_HIT_WIDTH, Component.translatable("debug.moonspire.monster_row", row.name(), row.id()), selected);
        }
        graphics.disableScissor();
        renderGridScrollbar(graphics, grid);
    }

    private void renderLayoutInfo(GuiGraphics graphics, Layout layout) {
        graphics.drawString(font, Component.translatable("debug.moonspire.layout_panel"), layout.formX(), layout.top(), 0xFFFFD166, false);
        drawWrappedLimited(graphics, Component.translatable("debug.moonspire.layout_panel_body"), layout.formX(), layout.top() + 18, layout.contentRight() - layout.formX(), layout.bottom(), 0xFFEDE8FF);
    }

    private void renderPanels(GuiGraphics graphics, Layout layout) {
        MoonSpireUiTextures.drawDarkPanel(graphics, 4, 24, SIDEBAR_W, height - 56);
        if (tab == Tab.LAYOUT) {
            MoonSpireUiTextures.drawDarkPanel(graphics, layout.formX() - 8, layout.top() - 8, layout.contentRight() - layout.formX() + 16, layout.bottom() - layout.top() + 16);
            return;
        }
        MoonSpireUiTextures.drawDarkPanel(graphics, layout.formX() - 8, layout.top() - 8, layout.formW() + 16, layout.bottom() - layout.top() + 16);
        if (tab == Tab.CARDS || tab == Tab.FACES) {
            MoonSpireUiTextures.drawDarkPanel(graphics, layout.itemX() - 8, layout.top() - 8, layout.itemW() + 16, layout.bottom() - layout.top() + 16);
        }
        MoonSpireUiTextures.drawDarkPanel(graphics, layout.listX() - 8, layout.top() - 8, layout.listW() + 16, layout.bottom() - layout.top() + 16);
    }

    private void renderFieldLabels(GuiGraphics graphics, Layout layout) {
        if (tab == Tab.CARDS) {
            if (selectedCard() != null) {
                drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.card_name"), layout.formX(), layout.fieldLabelY(1), layout.formW());
                drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.cost"), layout.formX(), layout.fieldLabelY(3), layout.formW());
                drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.card_image"), layout.formX(), layout.imageControlsY() - 13, layout.formW());
                drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.card_effects"), layout.formX(), layout.effectsY() - 13, layout.formW());
            }
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.card_search"), layout.listX(), layout.searchLabelY(), layout.listW());
        } else if (tab == Tab.FACES) {
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.id"), layout.formX(), layout.fieldLabelY(0), layout.formW());
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.card_face_image"), layout.formX(), layout.fieldLabelY(1), layout.formW());
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.card_face_area"), layout.formX(), layout.fieldLabelY(3), layout.formW());
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.search"), layout.listX(), layout.searchLabelY(), layout.listW());
        } else if (tab == Tab.MONSTERS) {
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.entity_id"), layout.formX(), layout.fieldLabelY(0), layout.formW());
            int statW = (layout.formW() - GAP * 2) / 3;
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.health"), layout.formX(), layout.fieldLabelY(1), statW);
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.speed"), layout.formX() + statW + GAP, layout.fieldLabelY(1), statW);
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.energy"), layout.formX() + (statW + GAP) * 2, layout.fieldLabelY(1), statW);
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.monster_deck"), layout.formX(), layout.fieldLabelY(2), layout.formW());
            drawFieldLabel(graphics, Component.translatable("debug.moonspire.search"), layout.listX(), layout.searchLabelY(), layout.listW());
        }
    }

    private void drawFieldLabel(GuiGraphics graphics, Component label, int x, int y, int width) {
        drawTrimmed(graphics, label, x, y, width, 0xFFB8E6FF);
    }

    private void drawHeader(GuiGraphics graphics, Component title, int x, int y) {
        graphics.drawString(font, title, x, y, 0xFFFFD166, false);
    }

    private void drawListRow(GuiGraphics graphics, int x, int y, int width, Component text, boolean selected) {
        graphics.fill(x - 3, y - 2, x + width + 3, y + 14, selected ? 0x663F7A3F : 0x33101010);
        drawTrimmed(graphics, text, x, y, width, selected ? 0xFFB8F7B8 : 0xFFEDE8FF);
    }

    private boolean insideRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void drawTrimmed(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        graphics.drawString(font, font.substrByWidth(text, Math.max(0, width)).getString(), x, y, color, false);
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        int lineY = y;
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, Math.max(20, width))) {
            graphics.drawString(font, line, x, lineY, color, false);
            lineY += font.lineHeight + 2;
        }
    }

    private void drawWrappedLimited(GuiGraphics graphics, Component text, int x, int y, int width, int bottom, int color) {
        int lineY = y;
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, Math.max(20, width))) {
            if (lineY + font.lineHeight > bottom) {
                break;
            }
            graphics.drawString(font, line, x, lineY, color, false);
            lineY += font.lineHeight + 2;
        }
    }

    private boolean clickCardGrid(Layout layout, double mouseX, double mouseY) {
        GridLayout grid = layout.cardGrid(filteredCards().size());
        if (!insideGrid(grid, mouseX, mouseY)) {
            return false;
        }
        int index = indexAt(grid, mouseX, mouseY);
        List<DeveloperCardDefinition> cards = filteredCards();
        if (index >= 0 && index < cards.size()) {
            selectedCardId = cards.get(index).id();
            lastSelectedCardId = selectedCardId;
            refreshFields();
            return true;
        }
        selectedCardId = "";
        lastSelectedCardId = "";
        refreshFields();
        return true;
    }

    private boolean clickCardForm(Layout layout, double mouseX, double mouseY) {
        DeveloperCardDefinition card = selectedCard();
        if (card == null) {
            return false;
        }
        int imageY = layout.imageControlsY();
        if (insideRect(mouseX, mouseY, layout.formX(), imageY, 76, 18)) {
            chooseLocalImage();
            return true;
        }
        if (insideRect(mouseX, mouseY, layout.formX() + 82, imageY, 76, 18)) {
            cardListExpanded = false;
            itemPickerOpen = true;
            effectPickerOpen = false;
            targetPickerOpen = false;
            targetPickerEffectIndex = -1;
            init();
            return true;
        }
        if (insideRect(mouseX, mouseY, layout.formX() + 164, imageY, 54, 18)) {
            resetCardArt();
            return true;
        }
        if (!cardListExpanded && insideRect(mouseX, mouseY, layout.previewX(), layout.previewY(), CardRenderHelper.CARD_WIDTH, CardRenderHelper.CARD_HEIGHT)) {
            draggingArt = true;
            artGrabX = (int) mouseX - card.artX();
            artGrabY = (int) mouseY - card.artY();
            return true;
        }
        int effectsY = layout.effectsY();
        List<DeveloperCardEffect> effects = card.normalizedEffects();
        GridLayout effectGrid = effectEditorGrid(layout, effects.size() + 1);
        if (clickScrollbar(effectGrid, ScrollArea.EFFECTS, mouseY, mouseX)) {
            return true;
        }
        if (insideGrid(effectGrid, mouseX, mouseY)) {
            int index = indexAt(effectGrid, mouseX, mouseY);
            if (index >= 0 && index < effects.size()) {
                GridCell cell = cell(effectGrid, index);
                if (insideRect(mouseX, mouseY, effectGrid.viewX(), cell.y(), 20, 18)) {
                    List<DeveloperCardEffect> next = new ArrayList<>(effects);
                    next.remove(index);
                    updateEffects(next);
                    return true;
                }
                if (insideRect(mouseX, mouseY, effectTargetX(effectGrid), cell.y(), EFFECT_TARGET_W, 18) && effects.get(index).canChangeTarget()) {
                    applyFields();
                    openTargetPicker(index);
                    return true;
                }
            }
            if (index == effects.size() && insideRect(mouseX, mouseY, effectGrid.viewX(), cell(effectGrid, index).y(), 20, 18)) {
                effectPickerOpen = !effectPickerOpen;
                targetPickerOpen = false;
                targetPickerEffectIndex = -1;
                effectPickerScrollOffset = 0.0D;
                init();
                return true;
            }
        }
        return false;
    }

    private boolean clickFaceForm(Layout layout, double mouseX, double mouseY) {
        int imageY = layout.fieldBoxY(1);
        if (insideRect(mouseX, mouseY, layout.formX(), imageY, 76, 18)) {
            chooseLocalImage();
            return true;
        }
        if (insideRect(mouseX, mouseY, layout.formX() + 82, imageY, 54, 18)) {
            resetFaceImage();
            return true;
        }
        int areaY = layout.fieldBoxY(3);
        int buttonW = Math.max(42, (layout.formW() - 16) / faceAreaKinds.size());
        for (int i = 0; i < faceAreaKinds.size(); i++) {
            int bx = layout.formX() + i * (buttonW + 4);
            int bw = Math.min(buttonW, Math.max(1, layout.formX() + layout.formW() - bx));
            if (insideRect(mouseX, mouseY, bx, areaY, bw, 18)) {
                selectedFaceAreaKind = faceAreaKinds.get(i);
                return true;
            }
        }
        FaceAreaKind hit = faceAreaAt(layout, mouseX, mouseY);
        if (hit != null) {
            selectedFaceAreaKind = hit;
            DeveloperCardFace.Area area = faceArea(selectedFace(), hit);
            faceGrabX = (int) mouseX - (layout.facePreviewX() + scaleFaceX(area.x()));
            faceGrabY = (int) mouseY - (layout.facePreviewY() + scaleFaceY(area.y()));
            draggingFaceArea = true;
            return true;
        }
        return false;
    }

    private FaceAreaKind faceAreaAt(Layout layout, double mouseX, double mouseY) {
        DeveloperCardFace face = selectedFace();
        for (int i = faceAreaKinds.size() - 1; i >= 0; i--) {
            FaceAreaKind kind = faceAreaKinds.get(i);
            DeveloperCardFace.Area area = faceArea(face, kind);
            int x = layout.facePreviewX() + scaleFaceX(area.x());
            int y = layout.facePreviewY() + scaleFaceY(area.y());
            int w = scaleFaceX(area.width());
            int h = scaleFaceY(area.height());
            if (insideRect(mouseX, mouseY, x, y, w, h)) {
                return kind;
            }
        }
        return insideRect(mouseX, mouseY, layout.facePreviewX(), layout.facePreviewY(), layout.facePreviewW(), layout.facePreviewH()) ? selectedFaceAreaKind : null;
    }

    private boolean clickFaceList(Layout layout, double mouseX, double mouseY) {
        GridLayout grid = layout.textListGrid(data.cardFaces.size());
        if (!insideGrid(grid, mouseX, mouseY)) {
            return false;
        }
        int index = indexAt(grid, mouseX, mouseY);
        if (index >= 0 && index < data.cardFaces.size()) {
            selectedFaceId = data.cardFaces.get(index).id();
            data.activeFaceId = selectedFaceId;
            refreshFields();
            return true;
        }
        return false;
    }

    private boolean clickMonsterForm(Layout layout, double mouseX, double mouseY) {
        if (insideRect(mouseX, mouseY, layout.formX(), layout.fieldBoxY(2), 112, 18)) {
            openMonsterDeckScreen();
            return true;
        }
        return false;
    }

    private boolean clickMonsterList(Layout layout, double mouseX, double mouseY) {
        List<MonsterRow> monsters = filteredMonsters();
        GridLayout grid = layout.textListGrid(monsters.size());
        if (!insideGrid(grid, mouseX, mouseY)) {
            return false;
        }
        int index = indexAt(grid, mouseX, mouseY);
        if (index >= 0 && index < monsters.size()) {
            selectedMonsterId = monsters.get(index).id();
            lastSelectedMonsterId = selectedMonsterId;
            rememberMonsterListScrollOffset();
            refreshFields();
            return true;
        }
        return false;
    }

    private void refreshFields() {
        if (tab == Tab.CARDS) {
            DeveloperCardDefinition card = selectedCard();
            if (card == null) {
                clearCardFields();
                selectedArtItemId = "";
                sourceTypeIndex = 0;
                itemPickerOpen = false;
                effectPickerOpen = false;
                targetPickerOpen = false;
                targetPickerEffectIndex = -1;
                hideIrrelevantBoxes();
                return;
            }
            nameKeyBox.setValue(card.displayName().isBlank() ? card.toCardInstance().nameComponent().getString() : card.displayName());
            costBox.setValue(Integer.toString(card.cost()));
            selectedArtItemId = card.artItemId();
            sourceTypeIndex = Math.max(0, EDIT_SOURCE_TYPES.indexOf(card.sourceType()));
            hideIrrelevantBoxes();
        } else if (tab == Tab.FACES) {
            DeveloperCardFace face = selectedFace();
            idBox.setValue(face.id());
            artPathBox.setValue(face.imagePath());
        } else if (tab == Tab.MONSTERS) {
            if (selectedMonsterId.isBlank()) {
                selectedMonsterId = "minecraft:zombie";
            }
            DeveloperMonsterDefinition monster = selectedMonsterEffective();
            entityIdBox.setValue(monster.entityTypeId());
            healthBox.setValue(Float.toString(monster.maxHealth()));
            speedBox.setValue(Integer.toString(monster.speed()));
            monsterEnergyBox.setValue(Integer.toString(monster.energy()));
        }
    }

    private void clearCardFields() {
        if (nameKeyBox != null) {
            nameKeyBox.setValue("");
        }
        if (costBox != null) {
            costBox.setValue("");
        }
        hideEffectAmountBoxes();
        hideEffectCountBoxes();
    }

    private void applyFields() {
        if (tab == Tab.CARDS && nameKeyBox != null) {
            DeveloperCardDefinition previous = selectedCard();
            if (previous == null) {
                return;
            }
            String displayName = valueOr(nameKeyBox, "Unnamed Card").trim();
            String id = previous.id();
            List<DeveloperCardEffect> effects = effectsFromBoxes(previous == null ? List.of() : previous.normalizedEffects());
            String nameKey = nameKeyFor(id);
            DeveloperDataManager.rememberDisplayName(nameKey, displayName);
            selectedCardId = id;
            lastSelectedCardId = id;
            DeveloperCardDefinition next = new DeveloperCardDefinition(
                    id,
                    displayName,
                    nameKey,
                    "",
                    intValue(costBox, 1),
                    0,
                    0,
                    0,
                    effects,
                    previous.sourceType(),
                    previous.artPath(),
                    selectedArtItemId,
                    previous.artX(),
                    previous.artY(),
                    previous.artScale(),
                    selectedFaceId);
            replaceCard(previous, next);
        } else if (tab == Tab.FACES && idBox != null) {
            DeveloperCardFace previous = selectedFace();
            String previousId = previous == null ? "" : previous.id();
            selectedFaceId = cleanId(idBox.getValue(), "default");
            if (!previousId.equals(selectedFaceId)) {
                Path pendingSource = pendingFaceImageSources.remove(previousId);
                if (pendingSource != null) {
                    pendingFaceImageSources.put(selectedFaceId, pendingSource);
                }
            }
            data.activeFaceId = selectedFaceId;
            DeveloperCardFace next = new DeveloperCardFace(selectedFaceId, valueOr(artPathBox, ""), previous.costArea(), previous.nameArea(), previous.artArea(), previous.typeArea(), previous.descriptionArea());
            int index = data.cardFaces.indexOf(previous);
            if (index >= 0) {
                data.cardFaces.set(index, next);
            }
        } else if (tab == Tab.MONSTERS && entityIdBox != null) {
            String previousId = selectedMonsterId;
            DeveloperMonsterDefinition previous = selectedMonsterOrDefault();
            String id = entityIdBox.getValue().isBlank() ? "minecraft:zombie" : entityIdBox.getValue();
            MonsterDefaults defaults = monsterDefaults(id);
            float health = floatValue(healthBox, defaults.health());
            int energy = intValue(monsterEnergyBox, defaults.energy());
            int speed = intValue(speedBox, defaults.speed());
            DeveloperMonsterDefinition next = new DeveloperMonsterDefinition(
                    id,
                    Math.abs(health - defaults.health()) < 0.0001F ? 0.0F : health,
                    energy == defaults.energy() ? 0 : energy,
                    speed == defaults.speed() ? 0 : speed,
                    new ArrayList<>(previous.deckCardIds()),
                    previous.hasDeckOverride());
            replaceMonster(previousId, next);
            selectedMonsterId = id;
        }
    }

    private void rememberOpenState() {
        lastTab = tab;
        lastSelectedCardId = selectedCardId.isBlank() ? "" : MoonSpireCardRegistry.registeredDeveloperId(selectedCardId);
        lastSelectedMonsterId = selectedMonsterId.isBlank() ? "" : selectedMonsterId;
        lastCardScrollOffset = cardScrollOffset;
        lastItemScrollOffset = itemScrollOffset;
        lastListScrollOffset = listScrollOffset;
        rememberMonsterListScrollOffset();
    }

    private void save() {
        applyFields();
        rememberOpenState();
        boolean faceSaved = saveCardFaceFiles();
        boolean cardArtSaved = saveCardArtFiles();
        String json = data.toJson();
        PacketDistributor.sendToServer(new SaveDeveloperDataPayload(json));
        savedData = DeveloperData.fromJson(json);
        refreshFields();
        if (!faceSaved) {
            status = cardFaceSaveFailureStatus.getString().isBlank() ? Component.translatable("debug.moonspire.card_face_save_failed") : cardFaceSaveFailureStatus;
        } else {
            status = Component.translatable(cardArtSaved ? "debug.moonspire.saved" : "debug.moonspire.card_art_save_failed");
        }
        statusTicks = 120;
    }

    private void createCurrent() {
        applyFields();
        if (tab == Tab.CARDS) {
            String id = uniqueCardId("custom_card", CardSourceType.CUSTOM, "");
            data.cards.add(DeveloperCardDefinition.defaultCard(id).withIdAndKeys(id, "", nameKeyFor(id)));
            selectedCardId = id;
        } else if (tab == Tab.FACES) {
            String id = "face_" + (data.cardFaces.size() + 1);
            DeveloperCardFace defaults = DeveloperCardFace.defaultFace();
            data.cardFaces.add(new DeveloperCardFace(id, "", defaults.costArea(), defaults.nameArea(), defaults.artArea(), defaults.typeArea(), defaults.descriptionArea()));
            selectedFaceId = id;
            data.activeFaceId = id;
        } else if (tab == Tab.MONSTERS) {
            if (selectedMonsterId.isBlank()) {
                selectedMonsterId = "minecraft:zombie";
            }
            ensureMonster(selectedMonsterId);
        }
        refreshFields();
    }

    private void nextFilter() {
        if (tab == Tab.CARDS) {
            cardFilterIndex = (cardFilterIndex + 1) % 5;
        }
    }

    private void resetCurrentCardToSaved() {
        if (tab != Tab.CARDS) {
            return;
        }
        DeveloperCardDefinition current = selectedCard();
        String currentId = current == null ? MoonSpireCardRegistry.registeredDeveloperId(selectedCardId) : MoonSpireCardRegistry.registeredDeveloperId(current.id());
        if (currentId.isBlank()) {
            return;
        }
        DeveloperCardDefinition saved = savedData.cards.stream()
                .filter(card -> currentId.equals(MoonSpireCardRegistry.registeredDeveloperId(card.id())))
                .findFirst()
                .orElse(null);
        data.cards.removeIf(card -> currentId.equals(MoonSpireCardRegistry.registeredDeveloperId(card.id())));
        if (saved != null) {
            data.cards.add(saved);
            selectedCardId = saved.id();
        } else if (MoonSpireCardRegistry.baseCard(currentId).isPresent()) {
            selectedCardId = currentId;
        } else {
            selectedCardId = filteredCards().stream().findFirst().map(DeveloperCardDefinition::id).orElse("");
        }
        lastSelectedCardId = selectedCardId;
        selectedArtItemId = "";
        confirmDelete = false;
        itemPickerOpen = false;
        effectPickerOpen = false;
        targetPickerOpen = false;
        targetPickerEffectIndex = -1;
        status = Component.translatable("debug.moonspire.reset_saved");
        statusTicks = 120;
        init();
    }

    private void giveSelectedCard() {
        applyFields();
        DeveloperCardDefinition card = selectedCard();
        if (card == null) {
            return;
        }
        PacketDistributor.sendToServer(new GiveDeveloperCardPayload(MoonSpireCardRegistry.registeredDeveloperId(card.id())));
        status = Component.translatable("debug.moonspire.card_given");
        statusTicks = 120;
    }

    private void resetCurrentFaceToSaved() {
        if (tab != Tab.FACES) {
            return;
        }
        applyFields();
        String currentId = cleanId(selectedFaceId, "default");
        pendingFaceImageSources.remove(currentId);
        DeveloperCardFace saved = savedData.cardFaces.stream()
                .filter(face -> currentId.equals(face.id()))
                .findFirst()
                .orElse(null);
        data.cardFaces.removeIf(face -> currentId.equals(face.id()));
        if (saved != null) {
            data.cardFaces.add(saved);
            selectedFaceId = saved.id();
        } else if ("default".equals(currentId)) {
            DeveloperCardFace defaults = DeveloperCardFace.defaultFace();
            data.cardFaces.add(defaults);
            selectedFaceId = defaults.id();
        } else {
            resetCardsUsingFace(currentId);
            selectedFaceId = "default";
        }
        data.activeFaceId = selectedFaceId;
        confirmDelete = false;
        itemPickerOpen = false;
        effectPickerOpen = false;
        targetPickerOpen = false;
        targetPickerEffectIndex = -1;
        status = Component.translatable("debug.moonspire.reset_saved");
        statusTicks = 120;
        init();
    }

    private void resetCurrentMonsterToSaved() {
        if (tab != Tab.MONSTERS) {
            return;
        }
        applyFields();
        String currentId = selectedMonsterId.isBlank() ? "minecraft:zombie" : selectedMonsterId;
        DeveloperMonsterDefinition saved = savedData.monsters.stream()
                .filter(monster -> currentId.equals(monster.entityTypeId()))
                .findFirst()
                .orElse(null);
        data.monsters.removeIf(monster -> currentId.equals(monster.entityTypeId()));
        if (saved != null) {
            data.monsters.add(saved);
        }
        selectedMonsterId = currentId;
        confirmDelete = false;
        itemPickerOpen = false;
        effectPickerOpen = false;
        targetPickerOpen = false;
        targetPickerEffectIndex = -1;
        status = Component.translatable("debug.moonspire.reset_saved");
        statusTicks = 120;
        init();
    }

    private DeveloperCardDefinition selectedCard() {
        if (selectedCardId.isBlank()) {
            return null;
        }
        String registeredId = MoonSpireCardRegistry.registeredDeveloperId(selectedCardId);
        DeveloperCardDefinition card = data.cards.stream()
                .filter(saved -> registeredId.equals(MoonSpireCardRegistry.registeredDeveloperId(saved.id())))
                .findFirst()
                .orElseGet(() -> MoonSpireCardRegistry.baseCard(registeredId).map(this::cardView).orElse(null));
        if (card == null) {
            return null;
        }
        boolean visible = filteredCards().stream()
                .map(DeveloperCardDefinition::id)
                .map(MoonSpireCardRegistry::registeredDeveloperId)
                .anyMatch(registeredId::equals);
        return visible ? card : null;
    }

    private DeveloperCardDefinition ensureDeveloperCard(RegisteredCardDefinition definition) {
        String id = definition.id();
        return data.cards.stream().filter(card -> id.equals(MoonSpireCardRegistry.registeredDeveloperId(card.id()))).findFirst().orElseGet(() -> {
            DeveloperCardDefinition card = new DeveloperCardDefinition(
                    id,
                    "",
                    definition.nameKey(),
                    definition.descriptionKey(),
                    definition.cost(),
                    definition.attack(),
                    definition.defense(),
                    bleedAmount(definition),
                    effectsFromDefinition(definition),
                    definition.sourceType(),
                    definition.artPath(),
                    definition.artItemId(),
                    definition.artX(),
                    definition.artY(),
                    definition.artScale(),
                    definition.faceId());
            data.cards.add(card);
            return card;
        });
    }

    private DeveloperCardDefinition cardView(RegisteredCardDefinition definition) {
        String id = definition.id();
        return data.cards.stream().filter(card -> id.equals(MoonSpireCardRegistry.registeredDeveloperId(card.id()))).findFirst().orElseGet(() -> new DeveloperCardDefinition(
                id,
                "",
                definition.nameKey(),
                definition.descriptionKey(),
                definition.cost(),
                definition.attack(),
                definition.defense(),
                bleedAmount(definition),
                effectsFromDefinition(definition),
                definition.sourceType(),
                definition.artPath(),
                definition.artItemId(),
                definition.artX(),
                definition.artY(),
                definition.artScale(),
                definition.faceId()));
    }

    private int bleedAmount(RegisteredCardDefinition definition) {
        return definition.effects().stream().filter(effect -> effect.kind() == CardEffectKind.BLEED).mapToInt(com.yinfires.moonspire.card.CardEffect::amount).sum();
    }

    private List<DeveloperCardEffect> effectsFromDefinition(RegisteredCardDefinition definition) {
        List<DeveloperCardEffect> effects = new ArrayList<>();
        if (definition.attack() > 0) {
            effects.add(DeveloperCardEffect.damage(definition.attack()));
        }
        if (definition.defense() > 0) {
            effects.add(DeveloperCardEffect.block(definition.defense()));
        }
        for (com.yinfires.moonspire.card.CardEffect effect : definition.effects()) {
            if (effect.kind() == CardEffectKind.DAMAGE && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.DAMAGE, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.REMOTE) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.REMOTE, 1));
            } else if (effect.kind() == CardEffectKind.CONSUME_ARROW && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.CONSUME_ARROW, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.ARROW) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.ARROW, 1));
            } else if (effect.kind() == CardEffectKind.HEAL && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.HEAL, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.BLOCK && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.BLOCK, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.BLEED && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.BLEED, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.GLOWING && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.GLOWING, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.GUARD && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.GUARD, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.STRENGTH && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.STRENGTH, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.LOSE_STRENGTH && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.LOSE_STRENGTH, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.REGENERATION && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.REGENERATION, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.HASTE && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.HASTE, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.POISON && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.POISON, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.BURN && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.BURN, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.WEAKNESS && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.WEAKNESS, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.SLOWNESS && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.SLOWNESS, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.DRAW_CARDS && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.DRAW_CARDS, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.GAIN_ENERGY && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.GAIN_ENERGY, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.EXHAUST) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.EXHAUST, 1));
            } else if (effect.kind() == CardEffectKind.INNATE) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.INNATE, 1));
            } else if (effect.kind() == CardEffectKind.RETAIN) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.RETAIN, 1));
            } else if (effect.kind() == CardEffectKind.ETHEREAL) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.ETHEREAL, 1));
            } else if (effect.kind() == CardEffectKind.RETAIN_REDUCE_COST && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.RETAIN_REDUCE_COST, effect.amount()));
            } else if (effect.kind() == CardEffectKind.EXHAUST_HAND && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.EXHAUST_HAND, effect.amount(), effect.target(), effect.count()));
            } else if (effect.kind() == CardEffectKind.DISCARD_HAND && effect.amount() > 0) {
                effects.add(new DeveloperCardEffect(DeveloperCardEffect.Kind.DISCARD_HAND, effect.amount(), effect.target(), effect.count()));
            }
        }
        return effects;
    }

    private DeveloperCardFace selectedFace() {
        return data.cardFaces.stream().filter(face -> face.id().equals(selectedFaceId)).findFirst().orElse(data.cardFaces.getFirst());
    }

    private DeveloperMonsterDefinition ensureMonster(String id) {
        return data.monsters.stream().filter(monster -> monster.entityTypeId().equals(id)).findFirst().orElseGet(() -> {
            DeveloperMonsterDefinition monster = DeveloperMonsterDefinition.empty(id);
            data.monsters.add(monster);
            selectedMonsterId = id;
            return monster;
        });
    }

    private java.util.Optional<DeveloperMonsterDefinition> monsterDefinition(String id) {
        return data.monsters.stream().filter(monster -> monster.entityTypeId().equals(id)).findFirst();
    }

    private DeveloperMonsterDefinition selectedMonsterOrDefault() {
        String id = selectedMonsterId.isBlank() ? "minecraft:zombie" : selectedMonsterId;
        return monsterDefinition(id).orElseGet(() -> DeveloperMonsterDefinition.empty(id));
    }

    private DeveloperMonsterDefinition selectedMonsterEffective() {
        String id = selectedMonsterId.isBlank() ? "minecraft:zombie" : selectedMonsterId;
        Optional<DeveloperMonsterDefinition> currentDefinition = monsterDefinition(id);
        DeveloperMonsterDefinition current = currentDefinition.orElseGet(() -> DeveloperMonsterDefinition.empty(id));
        MonsterDefaults defaults = monsterDefaults(id);
        List<String> defaultDeck = defaultMonsterDeckCardIds(id);
        boolean hasDeckOverride = currentDefinition.map(DeveloperMonsterDefinition::hasDeckOverride).orElse(false);
        return new DeveloperMonsterDefinition(
                current.entityTypeId(),
                current.hasHealthOverride() ? current.maxHealth() : defaults.health(),
                current.hasEnergyOverride() ? current.energy() : defaults.energy(),
                current.hasSpeedOverride() ? current.speed() : defaults.speed(),
                hasDeckOverride || !MonsterDeckProfile.hasDefaultDeck(monsterEntityType(id)) ? current.deckCardIds() : defaultDeck,
                hasDeckOverride);
    }

    private void replaceCard(DeveloperCardDefinition previous, DeveloperCardDefinition next) {
        invalidateFilteredCaches();
        if (previous == null) {
            data.cards.add(next);
            return;
        }
        int index = data.cards.indexOf(previous);
        if (index >= 0) {
            data.cards.set(index, next);
        } else {
            data.cards.add(next);
        }
    }

    private void replaceMonster(DeveloperMonsterDefinition next) {
        replaceMonster(selectedMonsterId, next);
    }

    private void replaceMonster(String previousId, DeveloperMonsterDefinition next) {
        invalidateFilteredCaches();
        if (previousId != null && !previousId.isBlank() && !previousId.equals(next.entityTypeId())) {
            data.monsters.removeIf(monster -> monster.entityTypeId().equals(previousId));
        }
        if (!hasMonsterOverride(next)) {
            data.monsters.removeIf(monster -> monster.entityTypeId().equals(next.entityTypeId()));
            return;
        }
        for (int i = 0; i < data.monsters.size(); i++) {
            if (data.monsters.get(i).entityTypeId().equals(next.entityTypeId())) {
                data.monsters.set(i, next);
                return;
            }
        }
        data.monsters.add(next);
    }

    private boolean hasMonsterOverride(DeveloperMonsterDefinition monster) {
        return monster.hasHealthOverride() || monster.hasEnergyOverride() || monster.hasSpeedOverride() || monster.hasDeckOverride();
    }

    private MonsterDefaults monsterDefaults(String entityTypeId) {
        EntityType<?> type = monsterEntityType(entityTypeId);
        LivingEntity sample = createSampleMonster(type);
        if (sample != null) {
            return new MonsterDefaults(
                    Math.max(1.0F, sample.getMaxHealth()),
                    CardBalance.fixedEnergy(),
                    nonPlayerBaseSpeed(sample));
        }
        return new MonsterDefaults(20.0F, CardBalance.fixedEnergy(), CardBalance.PLAYER_BASE_SPEED);
    }

    private LivingEntity createSampleMonster(EntityType<?> type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        Entity entity = type.create(minecraft.level);
        return entity instanceof LivingEntity living ? living : null;
    }

    private int nonPlayerBaseSpeed(LivingEntity entity) {
        double movementSpeed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return Math.max(1, Math.round((float) (movementSpeed / CardBalance.NON_PLAYER_BASELINE_MOVEMENT_SPEED * CardBalance.PLAYER_BASE_SPEED)));
    }

    private List<String> defaultMonsterDeckCardIds(String entityTypeId) {
        return MonsterDeckProfile.defaultDeckCardIds(monsterEntityType(entityTypeId));
    }

    private EntityType<?> monsterEntityType(String entityTypeId) {
        try {
            ResourceLocation id = ResourceLocation.parse(entityTypeId);
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                return BuiltInRegistries.ENTITY_TYPE.get(id);
            }
        } catch (RuntimeException ignored) {
        }
        return EntityType.ZOMBIE;
    }

    private void replaceFace(DeveloperCardFace previous, DeveloperCardFace next) {
        int index = data.cardFaces.indexOf(previous);
        if (index >= 0) {
            data.cardFaces.set(index, next);
        } else {
            data.cardFaces.add(next);
        }
        selectedFaceId = next.id();
        data.activeFaceId = next.id();
    }

    private void requestDelete() {
        if (tab == Tab.CARDS && selectedCard() != null) {
            confirmDelete = true;
        } else if (tab == Tab.FACES && selectedFace() != null) {
            confirmDelete = true;
        } else if (tab == Tab.MONSTERS && !selectedMonsterId.isBlank()) {
            confirmDelete = true;
        }
    }

    private boolean clickDeleteConfirm(double mouseX, double mouseY) {
        int w = 220;
        int h = 78;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        if (insideRect(mouseX, mouseY, x + 30, y + 50, 70, 18)) {
            if (tab == Tab.CARDS) {
                DeveloperCardDefinition card = selectedCard();
                if (card != null) {
                    String deletedId = MoonSpireCardRegistry.registeredDeveloperId(card.id());
                    data.cards.removeIf(saved -> deletedId.equals(MoonSpireCardRegistry.registeredDeveloperId(saved.id())));
                    selectedCardId = MoonSpireCardRegistry.baseCard(deletedId).isPresent() ? deletedId : filteredCards().stream().findFirst().map(DeveloperCardDefinition::id).orElse("");
                    lastSelectedCardId = selectedCardId;
                    status = Component.translatable("debug.moonspire.deleted");
                    statusTicks = 120;
                }
            } else if (tab == Tab.FACES) {
                deleteSelectedFace();
            } else if (tab == Tab.MONSTERS) {
                deleteSelectedMonster();
            }
            closeDeleteConfirm();
            return true;
        }
        if (insideRect(mouseX, mouseY, x + 120, y + 50, 70, 18)) {
            closeDeleteConfirm();
            return true;
        }
        return true;
    }

    private void closeDeleteConfirm() {
        MoonSpireModalLayer.close(() -> {
            confirmDelete = false;
            draggingScrollArea = ScrollArea.NONE;
            draggingArt = false;
            draggingFaceArea = false;
        }, this::init);
    }

    private void closeTargetPicker() {
        targetPickerOpen = false;
        targetPickerEffectIndex = -1;
        draggingScrollArea = ScrollArea.NONE;
    }

    private void deleteSelectedFace() {
        String deletedId = cleanId(selectedFaceId, "default");
        pendingFaceImageSources.remove(deletedId);
        if ("default".equals(deletedId)) {
            DeveloperCardFace defaults = DeveloperCardFace.defaultFace();
            data.cardFaces.removeIf(face -> "default".equals(face.id()));
            data.cardFaces.addFirst(defaults);
            resetCardsUsingFace("default");
        } else {
            data.cardFaces.removeIf(face -> deletedId.equals(face.id()));
            resetCardsUsingFace(deletedId);
        }
        selectedFaceId = "default";
        data.activeFaceId = "default";
        status = Component.translatable("debug.moonspire.face_deleted");
        statusTicks = 120;
    }

    private void deleteSelectedMonster() {
        String deletedId = selectedMonsterId.isBlank() ? "minecraft:zombie" : selectedMonsterId;
        data.monsters.removeIf(monster -> deletedId.equals(monster.entityTypeId()));
        selectedMonsterId = deletedId;
        status = Component.translatable("debug.moonspire.monster_deleted");
        statusTicks = 120;
    }

    private void resetCardsUsingFace(String faceId) {
        List<String> ids = data.cards.stream()
                .filter(card -> faceId.equals(card.faceId()))
                .map(card -> MoonSpireCardRegistry.registeredDeveloperId(card.id()))
                .toList();
        for (String id : ids) {
            setCardFace(id, "default");
        }
    }

    private void updateEffects(List<DeveloperCardEffect> effects) {
        DeveloperCardDefinition card = selectedCard();
        if (card == null) {
            return;
        }
        replaceCard(card, new DeveloperCardDefinition(card.id(), card.displayName(), card.nameKey(), card.descriptionKey(), card.cost(), 0, 0, 0, effects, card.sourceType(), card.artPath(), card.artItemId(), card.artX(), card.artY(), card.artScale(), card.faceId()));
        effectScrollOffset = clampScroll(effectScrollOffset, effectEditorGrid(layout(), effects.size() + 1));
        if (targetPickerEffectIndex >= effects.size()) {
            closeTargetPicker();
        }
        init();
    }

    private List<DeveloperCardEffect.Kind> filteredEffectKinds() {
        String query = effectSearchBox == null ? "" : effectSearchBox.getValue().toLowerCase(Locale.ROOT);
        return List.of(
                        DeveloperCardEffect.Kind.DAMAGE,
                        DeveloperCardEffect.Kind.REMOTE,
                        DeveloperCardEffect.Kind.CONSUME_ARROW,
                        DeveloperCardEffect.Kind.ARROW,
                        DeveloperCardEffect.Kind.HEAL,
                        DeveloperCardEffect.Kind.BLOCK,
                        DeveloperCardEffect.Kind.BLEED,
                        DeveloperCardEffect.Kind.GLOWING,
                        DeveloperCardEffect.Kind.GUARD,
                        DeveloperCardEffect.Kind.STRENGTH,
                        DeveloperCardEffect.Kind.LOSE_STRENGTH,
                        DeveloperCardEffect.Kind.REGENERATION,
                        DeveloperCardEffect.Kind.HASTE,
                        DeveloperCardEffect.Kind.POISON,
                        DeveloperCardEffect.Kind.BURN,
                        DeveloperCardEffect.Kind.WEAKNESS,
                        DeveloperCardEffect.Kind.SLOWNESS,
                        DeveloperCardEffect.Kind.DRAW_CARDS,
                        DeveloperCardEffect.Kind.GAIN_ENERGY,
                        DeveloperCardEffect.Kind.EXHAUST,
                        DeveloperCardEffect.Kind.INNATE,
                        DeveloperCardEffect.Kind.RETAIN,
                        DeveloperCardEffect.Kind.ETHEREAL,
                        DeveloperCardEffect.Kind.RETAIN_REDUCE_COST,
                        DeveloperCardEffect.Kind.EXHAUST_HAND,
                        DeveloperCardEffect.Kind.DISCARD_HAND)
                .stream()
                .filter(kind -> query.isBlank() || effectName(kind).getString().toLowerCase(Locale.ROOT).contains(query) || kind.name().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private List<CardTarget> filteredTargets() {
        String query = targetSearchBox == null ? "" : targetSearchBox.getValue().toLowerCase(Locale.ROOT);
        return List.of(CardTarget.values()).stream()
                .filter(target -> query.isBlank() || targetName(target).getString().toLowerCase(Locale.ROOT).contains(query) || target.name().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private List<DeveloperCardEffect> effectsFromBoxes(List<DeveloperCardEffect> fallback) {
        if (fallback.isEmpty()) {
            return List.of();
        }
        List<DeveloperCardEffect> effects = new ArrayList<>();
        for (int i = 0; i < fallback.size(); i++) {
            DeveloperCardEffect effect = fallback.get(i);
            int amount = i < effectAmountBoxes.size() && effect.kind().usesAmount() ? intValue(effectAmountBoxes.get(i), effect.amount()) : effect.amount();
            int count = i < effectCountBoxes.size() && effect.canChangeCount() ? positiveIntValue(effectCountBoxes.get(i), effect.count()) : effect.count();
            effects.add(new DeveloperCardEffect(effect.kind(), amount, effect.target(), count));
        }
        return effects;
    }

    private int selectedEffectCount() {
        DeveloperCardDefinition card = selectedCard();
        return card == null ? 0 : card.normalizedEffects().size();
    }

    private int effectPickerY(Layout layout, int effectCount) {
        int naturalY = layout.effectsY() + effectCount * 24 + 22;
        return Math.min(naturalY, Math.max(layout.top(), layout.buttonY() - 104));
    }

    private PickerBounds effectPickerBounds(Layout layout, int effectCount) {
        int y = effectPickerY(layout, effectCount);
        int bottom = Math.max(y + 44, layout.buttonY() - 4);
        return new PickerBounds(layout.formX(), y, layout.formW(), Math.min(92, bottom - y));
    }

    private int targetPickerY(Layout layout, int effectCount) {
        int naturalY = layout.effectsY() + Math.max(0, targetPickerEffectIndex) * 24 + 22;
        if (targetPickerEffectIndex < 0) {
            naturalY = layout.effectsY() + effectCount * 24 + 22;
        }
        return Math.min(naturalY, Math.max(layout.top(), layout.buttonY() - 124));
    }

    private PickerBounds targetPickerBounds(Layout layout, int effectCount) {
        int y = targetPickerY(layout, effectCount);
        int bottom = Math.max(y + 44, layout.buttonY() - 4);
        return new PickerBounds(layout.formX(), y, layout.formW(), Math.min(112, bottom - y));
    }

    private void adjustArtScale(double scrollY) {
        DeveloperCardDefinition card = selectedCard();
        if (card == null) {
            return;
        }
        float nextScale = Math.max(0.05F, Math.min(5.0F, card.artScale() + (float) scrollY * 0.05F));
        replaceCard(card, new DeveloperCardDefinition(card.id(), card.displayName(), card.nameKey(), card.descriptionKey(), card.cost(), card.attack(), card.defense(), card.bleed(), card.normalizedEffects(), card.sourceType(), card.artPath(), card.artItemId(), card.artX(), card.artY(), nextScale, card.faceId()));
        refreshFields();
    }

    private void resetCardArt() {
        DeveloperCardDefinition card = selectedCard();
        if (card == null) {
            return;
        }
        replaceCard(card, new DeveloperCardDefinition(card.id(), card.displayName(), card.nameKey(), card.descriptionKey(), card.cost(), card.attack(), card.defense(), card.bleed(), card.normalizedEffects(), card.sourceType(), "", "", 0, 0, 1.0F, card.faceId()));
        selectedArtItemId = "";
        refreshFields();
    }

    private void resetFaceImage() {
        DeveloperCardFace face = selectedFace();
        pendingFaceImageSources.remove(cleanId(face.id(), "default"));
        replaceFace(face, new DeveloperCardFace(face.id(), "", face.costArea(), face.nameArea(), face.artArea(), face.typeArea(), face.descriptionArea()));
        if (artPathBox != null) {
            artPathBox.setValue("");
        }
        refreshFields();
    }

    private void chooseLocalImage() {
        if (choosingLocalImage) {
            return;
        }
        choosingLocalImage = true;
        Thread thread = new Thread(() -> {
            LocalImageChoice choice = LocalImageChoice.unavailable();
            try {
                choice = openLocalImageChooser();
            } finally {
                choosingLocalImage = false;
            }
            File selected = choice.file();
            if (selected == null) {
                if (!choice.pickerOpened()) {
                    Minecraft.getInstance().execute(() -> {
                        status = Component.translatable("debug.moonspire.file_picker_unavailable");
                        statusTicks = 120;
                    });
                }
                return;
            }
            if (!selected.isFile() || !selected.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                Minecraft.getInstance().execute(() -> {
                    status = Component.translatable("debug.moonspire.file_picker_invalid");
                    statusTicks = 120;
                });
                return;
            }
            Minecraft.getInstance().execute(() -> {
                if (tab == Tab.FACES) {
                    DeveloperCardFace face = selectedFace();
                    pendingFaceImageSources.put(cleanId(face.id(), "default"), selected.toPath().toAbsolutePath().normalize());
                    replaceFace(face, new DeveloperCardFace(face.id(), selected.getAbsolutePath(), face.costArea(), face.nameArea(), face.artArea(), face.typeArea(), face.descriptionArea()));
                    refreshFields();
                } else {
                    DeveloperCardDefinition card = selectedCard();
                    if (card != null) {
                    replaceCard(card, new DeveloperCardDefinition(card.id(), card.displayName(), card.nameKey(), card.descriptionKey(), card.cost(), card.attack(), card.defense(), card.bleed(), card.normalizedEffects(), card.sourceType(), selected.getAbsolutePath(), "", 0, 0, 1.0F, card.faceId()));
                    selectedArtItemId = "";
                    refreshFields();
                    }
                }
            });
        }, "MoonSpire Local Image Chooser");
        thread.setDaemon(true);
        thread.start();
    }

    private LocalImageChoice openLocalImageChooser() {
        LocalImageChoice windowsChoice = openWindowsLocalImageChooser();
        if (windowsChoice.pickerOpened() || windowsChoice.file() != null) {
            return windowsChoice;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return LocalImageChoice.unavailable();
        }
        File[] selected = new File[1];
        boolean[] pickerOpened = new boolean[1];
        try {
            EventQueue.invokeAndWait(() -> {
                String title = Component.translatable("debug.moonspire.local_image").getString();
                Frame owner = new Frame(title);
                FileDialog dialog = null;
                try {
                    owner.setUndecorated(true);
                    owner.setAlwaysOnTop(true);
                    owner.setSize(1, 1);
                    owner.setLocationRelativeTo(null);
                    owner.setVisible(true);

                    dialog = new FileDialog(owner, title, FileDialog.LOAD);
                    dialog.setFilenameFilter((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
                    dialog.setFile("*.png");
                    dialog.setVisible(true);
                    pickerOpened[0] = true;
                    if (dialog.getFile() != null) {
                        selected[0] = new File(dialog.getDirectory(), dialog.getFile());
                    }
                } finally {
                    if (dialog != null) {
                        dialog.dispose();
                    }
                    owner.dispose();
                }
            });
        } catch (Throwable ignored) {
            return LocalImageChoice.unavailable();
        }
        return new LocalImageChoice(selected[0], pickerOpened[0]);
    }

    private LocalImageChoice openWindowsLocalImageChooser() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return LocalImageChoice.unavailable();
        }
        String title = Component.translatable("debug.moonspire.local_image").getString();
        String script = """
                [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
                Add-Type -AssemblyName System.Windows.Forms
                $owner = New-Object System.Windows.Forms.Form
                $dialog = New-Object System.Windows.Forms.OpenFileDialog
                $selected = $null
                $resultCode = 2
                try {
                  $owner.Text = __TITLE__
                  $owner.TopMost = $true
                  $owner.ShowInTaskbar = $false
                  $owner.StartPosition = 'CenterScreen'
                  $owner.Width = 1
                  $owner.Height = 1
                  $owner.Opacity = 0
                  $owner.Show()
                  $dialog.Title = __TITLE__
                  $dialog.Filter = 'PNG Images (*.png)|*.png|All Files (*.*)|*.*'
                  $dialog.Multiselect = $false
                  $dialog.CheckFileExists = $true
                  $dialog.RestoreDirectory = $true
                  if ($dialog.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) {
                    $selected = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($dialog.FileName))
                    $resultCode = 0
                  }
                } finally {
                  $dialog.Dispose()
                  $owner.Close()
                  $owner.Dispose()
                }
                if ($selected) {
                  [Console]::WriteLine('MOONSPIRE_FILE:' + $selected)
                }
                exit $resultCode
                """.replace("__TITLE__", "'" + title.replace("'", "''") + "'");
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-STA", "-EncodedCommand", encoded);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            String selected;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                selected = reader.lines()
                        .map(String::trim)
                        .filter(line -> line.startsWith("MOONSPIRE_FILE:"))
                        .map(line -> line.substring("MOONSPIRE_FILE:".length()).trim())
                        .findFirst()
                        .orElse("");
            }
            int exitCode = process.waitFor();
            if (!selected.isBlank()) {
                String path = new String(Base64.getDecoder().decode(selected), StandardCharsets.UTF_8);
                return new LocalImageChoice(new File(path), true);
            }
            if (exitCode == 2) {
                return new LocalImageChoice(null, true);
            }
        } catch (IOException ignored) {
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
        }
        return LocalImageChoice.unavailable();
    }

    private record LocalImageChoice(File file, boolean pickerOpened) {
        private static LocalImageChoice unavailable() {
        return new LocalImageChoice(null, false);
        }
    }

    private void moveSelectedFaceArea(int mouseX, int mouseY) {
        Layout layout = layout();
        DeveloperCardFace face = selectedFace();
        DeveloperCardFace.Area area = faceArea(face, selectedFaceAreaKind);
        int nextX = unscaleFaceX(mouseX - faceGrabX - layout.facePreviewX());
        int nextY = unscaleFaceY(mouseY - faceGrabY - layout.facePreviewY());
        nextX = Math.max(0, Math.min(128 - area.width(), nextX));
        nextY = Math.max(0, Math.min(158 - area.height(), nextY));
        replaceSelectedFaceArea(new DeveloperCardFace.Area(nextX, nextY, area.width(), area.height()));
    }

    private void adjustSelectedFaceArea(double scrollY, boolean shift, boolean control) {
        DeveloperCardFace face = selectedFace();
        DeveloperCardFace.Area area = faceArea(face, selectedFaceAreaKind);
        int delta = scrollY > 0.0D ? 1 : -1;
        int width = area.width();
        int height = area.height();
        if (control) {
            height += delta * 2;
        } else if (shift) {
            width += delta * 2;
        } else {
            width += delta * 2;
            height += delta * 2;
        }
        width = Math.max(1, Math.min(128 - area.x(), width));
        height = Math.max(1, Math.min(158 - area.y(), height));
        replaceSelectedFaceArea(new DeveloperCardFace.Area(area.x(), area.y(), width, height));
    }

    private boolean handleFaceAreaKey(int keyCode, int modifiers) {
        boolean shift = (modifiers & 0x1) != 0;
        boolean control = (modifiers & 0x2) != 0;
        DeveloperCardFace face = selectedFace();
        DeveloperCardFace.Area area = faceArea(face, selectedFaceAreaKind);
        int amount = shift ? 10 : 1;
        if (keyCode == 258) {
            cycleFaceArea(shift ? -1 : 1);
            return true;
        }
        if (keyCode == 262) {
            replaceSelectedFaceArea(control ? resizeFaceArea(area, amount, 0) : moveFaceArea(area, amount, 0));
            return true;
        }
        if (keyCode == 263) {
            replaceSelectedFaceArea(control ? resizeFaceArea(area, -amount, 0) : moveFaceArea(area, -amount, 0));
            return true;
        }
        if (keyCode == 264) {
            replaceSelectedFaceArea(control ? resizeFaceArea(area, 0, amount) : moveFaceArea(area, 0, amount));
            return true;
        }
        if (keyCode == 265) {
            replaceSelectedFaceArea(control ? resizeFaceArea(area, 0, -amount) : moveFaceArea(area, 0, -amount));
            return true;
        }
        return false;
    }

    private DeveloperCardFace.Area moveFaceArea(DeveloperCardFace.Area area, int dx, int dy) {
        int x = Math.max(0, Math.min(128 - area.width(), area.x() + dx));
        int y = Math.max(0, Math.min(158 - area.height(), area.y() + dy));
        return new DeveloperCardFace.Area(x, y, area.width(), area.height());
    }

    private DeveloperCardFace.Area resizeFaceArea(DeveloperCardFace.Area area, int dw, int dh) {
        int width = Math.max(1, Math.min(128 - area.x(), area.width() + dw));
        int height = Math.max(1, Math.min(158 - area.y(), area.height() + dh));
        return new DeveloperCardFace.Area(area.x(), area.y(), width, height);
    }

    private void cycleFaceArea(int delta) {
        int index = faceAreaKinds.indexOf(selectedFaceAreaKind);
        selectedFaceAreaKind = faceAreaKinds.get(Math.floorMod(index + delta, faceAreaKinds.size()));
    }

    private void replaceSelectedFaceArea(DeveloperCardFace.Area area) {
        DeveloperCardFace face = selectedFace();
        DeveloperCardFace next = switch (selectedFaceAreaKind) {
            case COST -> new DeveloperCardFace(face.id(), face.imagePath(), area, face.nameArea(), face.artArea(), face.typeArea(), face.descriptionArea());
            case NAME -> new DeveloperCardFace(face.id(), face.imagePath(), face.costArea(), area, face.artArea(), face.typeArea(), face.descriptionArea());
            case ART -> new DeveloperCardFace(face.id(), face.imagePath(), face.costArea(), face.nameArea(), area, face.typeArea(), face.descriptionArea());
            case TYPE -> new DeveloperCardFace(face.id(), face.imagePath(), face.costArea(), face.nameArea(), face.artArea(), area, face.descriptionArea());
            case DESCRIPTION -> new DeveloperCardFace(face.id(), face.imagePath(), face.costArea(), face.nameArea(), face.artArea(), face.typeArea(), area);
        };
        replaceFace(face, next);
    }

    private DeveloperCardFace.Area faceArea(DeveloperCardFace face, FaceAreaKind kind) {
        return switch (kind) {
            case COST -> face.costArea();
            case NAME -> face.nameArea();
            case ART -> face.artArea();
            case TYPE -> face.typeArea();
            case DESCRIPTION -> face.descriptionArea();
        };
    }

    private Component faceAreaName(FaceAreaKind kind) {
        return Component.translatable("debug.moonspire.face_area." + kind.name().toLowerCase(Locale.ROOT));
    }

    private Component faceAreaPreviewText(FaceAreaKind kind) {
        if (kind == FaceAreaKind.COST) {
            return Component.translatable("screen.moonspire.value_number", 1);
        }
        return faceAreaName(kind);
    }

    private static int scaleFaceX(int value) {
        return Math.max(1, Math.round(value * (CardRenderHelper.CARD_WIDTH / 128.0F)));
    }

    private static int scaleFaceY(int value) {
        return Math.max(1, Math.round(value * (CardRenderHelper.CARD_HEIGHT / 158.0F)));
    }

    private static int unscaleFaceX(int value) {
        return Math.max(0, Math.round(value * (128.0F / CardRenderHelper.CARD_WIDTH)));
    }

    private static int unscaleFaceY(int value) {
        return Math.max(0, Math.round(value * (158.0F / CardRenderHelper.CARD_HEIGHT)));
    }

    private boolean saveCardArtFiles() {
        boolean ok = true;
        Set<String> activeGeneratedFiles = new HashSet<>();
        List<DeveloperCardDefinition> savedCards = new ArrayList<>(data.cards.size());
        try {
            DeveloperPaths.ensureDirectories();
            for (DeveloperCardDefinition card : data.cards) {
                try {
                    savedCards.add(saveCardArtFile(card, activeGeneratedFiles));
                } catch (IOException | RuntimeException ignored) {
                    ok = false;
                    savedCards.add(card);
                }
            }
            data.cards.clear();
            data.cards.addAll(savedCards);
            deleteInactiveSavedCardArt(activeGeneratedFiles);
        } catch (IOException | RuntimeException ignored) {
            ok = false;
        }
        return ok;
    }

    private boolean saveCardFaceFiles() {
        boolean ok = true;
        Set<String> activeGeneratedFiles = new HashSet<>();
        List<DeveloperCardFace> savedFaces = new ArrayList<>(data.cardFaces.size());
        try {
            cardFaceSaveFailureStatus = Component.empty();
            DeveloperPaths.ensureDirectories();
            for (DeveloperCardFace face : data.cardFaces) {
                try {
                    savedFaces.add(saveCardFaceFile(face, activeGeneratedFiles));
                } catch (IOException | RuntimeException exception) {
                    ok = false;
                    rememberCardFaceSaveFailure(exception);
                    savedFaces.add(face);
                }
            }
            data.cardFaces.clear();
            data.cardFaces.addAll(savedFaces);
            deleteInactiveSavedCardFaces(activeGeneratedFiles);
        } catch (IOException | RuntimeException exception) {
            ok = false;
            rememberCardFaceSaveFailure(exception);
        }
        return ok;
    }

    private DeveloperCardDefinition saveCardArtFile(DeveloperCardDefinition card, Set<String> activeGeneratedFiles) throws IOException {
        String targetFileName = cardArtFileName(card.id());
        Path target = DeveloperDataManager.cardArtPath(targetFileName);
        if (card.artItemId() != null && !card.artItemId().isBlank() || card.artPath().isBlank() || isResourceArtPath(card.artPath())) {
            java.nio.file.Files.deleteIfExists(target);
            return card;
        }

        Path source = Path.of(card.artPath());
        if (!source.isAbsolute()) {
            source = DeveloperPaths.cardArtDirectory().resolve(card.artPath());
        }
        if (!java.nio.file.Files.isRegularFile(source)) {
            throw new IOException("Missing card art source: " + source);
        }

        if (samePath(source, target)) {
            activeGeneratedFiles.add(targetFileName);
            return withCardArtFile(card, targetFileName);
        }

        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) {
            throw new IOException("Invalid card art source: " + source);
        }
        DeveloperCardFace face = data.cardFaces.stream().filter(cardFace -> cardFace.id().equals(card.faceId())).findFirst().orElse(DeveloperCardFace.defaultFace());
        DeveloperCardFace.Area area = face.artArea();
        int targetW = Math.max(1, Math.round(CardRenderHelper.CARD_WIDTH * area.width() / 128.0F));
        int targetH = Math.max(1, Math.round(CardRenderHelper.CARD_HEIGHT * area.height() / 158.0F));
        BufferedImage finalImage = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = finalImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int drawW = Math.max(1, Math.round(targetW * Math.max(0.05F, card.artScale())));
        int drawH = Math.max(1, Math.round(targetH * Math.max(0.05F, card.artScale())));
        int drawX = (targetW - drawW) / 2 + card.artX();
        int drawY = (targetH - drawH) / 2 + card.artY();
        g.drawImage(image, drawX, drawY, drawW, drawH, null);
        g.dispose();
        ImageIO.write(finalImage, "png", target.toFile());
        activeGeneratedFiles.add(targetFileName);
        return withCardArtFile(card, targetFileName);
    }

    private DeveloperCardFace saveCardFaceFile(DeveloperCardFace face, Set<String> activeGeneratedFiles) throws IOException {
        String targetFileName = cardFaceFileName(face.id());
        Path target = DeveloperPaths.cardFacesDirectory().resolve(targetFileName).toAbsolutePath().normalize();
        String imagePath = face.imagePath();
        boolean hasPendingSource = pendingFaceImageSources.containsKey(cleanId(face.id(), "default"));
        if (!hasPendingSource && (imagePath == null || imagePath.isBlank() || isResourceArtPath(imagePath))) {
            java.nio.file.Files.deleteIfExists(target);
            CardRenderHelper.invalidateFileTexture(target);
            pendingFaceImageSources.remove(cleanId(face.id(), "default"));
            return face;
        }
        Path source = resolveCardFaceSource(face);
        if (!java.nio.file.Files.isRegularFile(source)) {
            throw new IOException("Missing card face source: " + source);
        }
        java.nio.file.Files.createDirectories(target.getParent());
        if (!samePath(source, target)) {
            java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        if (!java.nio.file.Files.isRegularFile(target)) {
            throw new IOException("Missing card face target: " + target);
        }
        CardRenderHelper.invalidateFileTexture(target);
        activeGeneratedFiles.add(targetFileName);
        pendingFaceImageSources.remove(cleanId(face.id(), "default"));
        return withCardFaceFile(face, targetFileName);
    }

    private Path resolveCardFaceSource(DeveloperCardFace face) {
        Path pendingSource = pendingFaceImageSources.get(cleanId(face.id(), "default"));
        if (pendingSource != null) {
            return pendingSource.toAbsolutePath().normalize();
        }
        if (face.imagePath() == null || face.imagePath().isBlank()) {
            return DeveloperPaths.cardFacesDirectory().resolve(cardFaceFileName(face.id())).toAbsolutePath().normalize();
        }
        Path source = Path.of(face.imagePath());
        if (!source.isAbsolute()) {
            Path existingCardFaceFile = DeveloperPaths.cardFacesDirectory().resolve(face.imagePath());
            if (java.nio.file.Files.isRegularFile(existingCardFaceFile)) {
                return existingCardFaceFile.toAbsolutePath().normalize();
            }
        }
        return source.toAbsolutePath().normalize();
    }

    private void rememberCardFaceSaveFailure(Exception exception) {
        if (cardFaceSaveFailureStatus.getString().isBlank()) {
            String message = exception.getMessage();
            cardFaceSaveFailureStatus = Component.translatable("debug.moonspire.card_face_save_failed_detail", message == null || message.isBlank() ? exception.getClass().getSimpleName() : message);
        }
    }

    private void deleteInactiveSavedCardArt(Set<String> activeGeneratedFiles) throws IOException {
        Set<String> candidates = new HashSet<>();
        for (DeveloperCardDefinition card : savedData.cards) {
            candidates.add(cardArtFileName(card.id()));
        }
        for (DeveloperCardDefinition card : data.cards) {
            candidates.add(cardArtFileName(card.id()));
        }
        for (String fileName : candidates) {
            if (!activeGeneratedFiles.contains(fileName)) {
                java.nio.file.Files.deleteIfExists(DeveloperDataManager.cardArtPath(fileName));
            }
        }
    }

    private void deleteInactiveSavedCardFaces(Set<String> activeGeneratedFiles) throws IOException {
        Set<String> candidates = new HashSet<>();
        for (DeveloperCardFace face : savedData.cardFaces) {
            candidates.add(cardFaceFileName(face.id()));
        }
        for (DeveloperCardFace face : data.cardFaces) {
            candidates.add(cardFaceFileName(face.id()));
        }
        for (String fileName : candidates) {
            if (!activeGeneratedFiles.contains(fileName)) {
                java.nio.file.Files.deleteIfExists(DeveloperPaths.cardFacesDirectory().resolve(fileName));
            }
        }
    }

    private static DeveloperCardDefinition withCardArtFile(DeveloperCardDefinition card, String fileName) {
        return new DeveloperCardDefinition(card.id(), card.displayName(), card.nameKey(), card.descriptionKey(), card.cost(), card.attack(), card.defense(), card.bleed(), card.normalizedEffects(), card.sourceType(), fileName, "", 0, 0, 1.0F, card.faceId());
    }

    private static DeveloperCardFace withCardFaceFile(DeveloperCardFace face, String fileName) {
        return new DeveloperCardFace(face.id(), fileName, face.costArea(), face.nameArea(), face.artArea(), face.typeArea(), face.descriptionArea());
    }

    static DeveloperCardDefinition withCardFace(DeveloperCardDefinition card, String faceId) {
        return new DeveloperCardDefinition(card.id(), card.displayName(), card.nameKey(), card.descriptionKey(), card.cost(), card.attack(), card.defense(), card.bleed(), card.normalizedEffects(), card.sourceType(), card.artPath(), card.artItemId(), card.artX(), card.artY(), card.artScale(), faceId);
    }

    private static String cardArtFileName(String cardId) {
        return MoonSpireCardRegistry.registeredDeveloperId(cardId).replace(':', '_') + ".png";
    }

    private static String cardFaceFileName(String faceId) {
        return cleanId(faceId, "face").replace(':', '_') + ".png";
    }

    private static boolean isResourceArtPath(String path) {
        String normalized = path.replace('\\', '/');
        boolean windowsPath = normalized.length() >= 3 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':' && normalized.charAt(2) == '/';
        return normalized.contains(":") && !windowsPath;
    }

    private static boolean samePath(Path first, Path second) {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
    }

    private List<DeveloperCardDefinition> filteredCards() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT);
        String cacheKey = cardFilterIndex + "\n" + query + "\n" + data.cards.hashCode();
        if (cacheKey.equals(filteredCardsCacheKey)) {
            return filteredCardsCache;
        }
        java.util.LinkedHashMap<String, DeveloperCardDefinition> cards = new java.util.LinkedHashMap<>();
        for (RegisteredCardDefinition definition : MoonSpireCardRegistry.baseCards()) {
            DeveloperCardDefinition card = cardView(definition);
            cards.put(MoonSpireCardRegistry.registeredDeveloperId(card.id()), card);
        }
        for (DeveloperCardDefinition card : data.cards) {
            cards.put(MoonSpireCardRegistry.registeredDeveloperId(card.id()), card);
        }
        filteredCardsCacheKey = cacheKey;
        filteredCardsCache = cards.values().stream()
                .filter(card -> query.isBlank() || MoonSpireCardRegistry.registeredDeveloperId(card.id()).toLowerCase(Locale.ROOT).contains(query) || card.nameKey().toLowerCase(Locale.ROOT).contains(query) || card.toCardInstance().nameComponent().getString().toLowerCase(Locale.ROOT).contains(query))
                .filter(card -> cardFilterIndex == 0 || (cardFilterIndex == 1 && card.sourceType() == CardSourceType.MOD) || (cardFilterIndex == 2 && card.sourceType() == CardSourceType.MONSTER) || (cardFilterIndex == 3 && isConvertedSource(card.sourceType())) || (cardFilterIndex == 4 && card.sourceType() == CardSourceType.CUSTOM))
                .toList();
        return filteredCardsCache;
    }

    List<DeveloperCardDefinition> faceApplicationCards() {
        applyFields();
        return faceApplicationCards(data);
    }

    List<DeveloperCardDefinition> deckSelectionCards() {
        applyFields();
        return faceApplicationCards(data);
    }

    List<String> selectedMonsterDeckCardIds() {
        applyFields();
        return List.copyOf(selectedMonsterEffective().deckCardIds());
    }

    List<String> savedSelectedMonsterDeckCardIds() {
        String id = selectedMonsterId.isBlank() ? "minecraft:zombie" : selectedMonsterId;
        return savedData.monsters.stream()
                .filter(monster -> id.equals(monster.entityTypeId()))
                .findFirst()
                .filter(DeveloperMonsterDefinition::hasDeckOverride)
                .map(monster -> List.copyOf(monster.deckCardIds()))
                .orElseGet(() -> defaultMonsterDeckCardIds(id));
    }

    void setSelectedMonsterDeckCardIds(List<String> deckCardIds) {
        applyFields();
        DeveloperMonsterDefinition current = selectedMonsterOrDefault();
        replaceMonster(current.entityTypeId(), new DeveloperMonsterDefinition(
                current.entityTypeId(),
                current.maxHealth(),
                current.energy(),
                current.speed(),
                new ArrayList<>(deckCardIds),
                true));
        status = Component.translatable("debug.moonspire.monster_deck_updated");
        statusTicks = 120;
        refreshFields();
    }

    DeveloperCardDefinition cardForDeckId(String id) {
        String registeredId = MoonSpireCardRegistry.registeredDeveloperId(id);
        return deckSelectionCards().stream()
                .filter(card -> registeredId.equals(MoonSpireCardRegistry.registeredDeveloperId(card.id())))
                .findFirst()
                .orElse(null);
    }

    String selectedMonsterId() {
        applyFields();
        return selectedMonsterId.isBlank() ? "minecraft:zombie" : selectedMonsterId;
    }

    private void rememberMonsterListScrollOffset() {
        if (tab == Tab.MONSTERS) {
            lastMonsterListScrollOffset = listScrollOffset;
        }
    }

    Set<String> appliedCardIdsForSelectedFace() {
        return appliedCardIdsFromData(data, selectedFaceId);
    }

    Set<String> savedAppliedCardIdsForSelectedFace() {
        return appliedCardIdsFromData(savedData, selectedFaceId);
    }

    String selectedFaceId() {
        applyFields();
        return selectedFaceId;
    }

    DeveloperData data() {
        applyFields();
        return data;
    }

    void applySelectedFaceToCards(Set<String> selectedCardIds) {
        applyFields();
        String faceId = cleanId(selectedFaceId, "default");
        List<DeveloperCardDefinition> cards = faceApplicationCards();
        for (DeveloperCardDefinition card : cards) {
            String id = MoonSpireCardRegistry.registeredDeveloperId(card.id());
            boolean selected = selectedCardIds.contains(id);
            boolean currentlyUsesFace = faceId.equals(card.faceId());
            if (selected) {
                setCardFace(id, faceId);
            } else if (currentlyUsesFace) {
                setCardFace(id, "default");
            }
        }
        status = Component.translatable("debug.moonspire.face_apply_applied");
        statusTicks = 120;
    }

    void showStatus(Component message) {
        status = message;
        statusTicks = 120;
    }

    private Set<String> appliedCardIdsFromData(DeveloperData source, String faceId) {
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (DeveloperCardDefinition card : faceApplicationCards(source)) {
            if (faceId.equals(card.faceId())) {
                ids.add(MoonSpireCardRegistry.registeredDeveloperId(card.id()));
            }
        }
        return ids;
    }

    private List<DeveloperCardDefinition> faceApplicationCards(DeveloperData source) {
        java.util.LinkedHashMap<String, DeveloperCardDefinition> cards = new java.util.LinkedHashMap<>();
        for (RegisteredCardDefinition definition : MoonSpireCardRegistry.baseCards()) {
            String registeredId = MoonSpireCardRegistry.registeredDeveloperId(definition.id());
            DeveloperCardDefinition override = source.cards.stream()
                    .filter(card -> registeredId.equals(MoonSpireCardRegistry.registeredDeveloperId(card.id())))
                    .findFirst()
                    .orElse(null);
            DeveloperCardDefinition card = override == null ? cardViewFromDefinition(definition) : override;
            cards.put(registeredId, card);
        }
        for (DeveloperCardDefinition card : source.cards) {
            cards.put(MoonSpireCardRegistry.registeredDeveloperId(card.id()), card);
        }
        return List.copyOf(cards.values());
    }

    private DeveloperCardDefinition cardViewFromDefinition(RegisteredCardDefinition definition) {
        return new DeveloperCardDefinition(
                definition.id(),
                "",
                definition.nameKey(),
                definition.descriptionKey(),
                definition.cost(),
                definition.attack(),
                definition.defense(),
                bleedAmount(definition),
                effectsFromDefinition(definition),
                definition.sourceType(),
                definition.artPath(),
                definition.artItemId(),
                definition.artX(),
                definition.artY(),
                definition.artScale(),
                definition.faceId());
    }

    private void setCardFace(String registeredId, String faceId) {
        DeveloperCardDefinition existing = data.cards.stream()
                .filter(card -> registeredId.equals(MoonSpireCardRegistry.registeredDeveloperId(card.id())))
                .findFirst()
                .orElse(null);
        if ("default".equals(faceId)) {
            if (existing != null) {
                data.cards.remove(existing);
            }
            return;
        }
        if (existing != null) {
            replaceCard(existing, withCardFace(existing, faceId));
            return;
        }
        MoonSpireCardRegistry.baseCard(registeredId)
                .filter(card -> !"default".equals(faceId))
                .map(this::cardView)
                .ifPresent(card -> data.cards.add(withCardFace(card, faceId)));
    }

    private void openFaceApplicationScreen() {
        applyFields();
        itemPickerOpen = false;
        effectPickerOpen = false;
        targetPickerOpen = false;
        targetPickerEffectIndex = -1;
        confirmDelete = false;
        Minecraft.getInstance().setScreen(new DeveloperFaceApplicationScreen(this));
    }

    private void openMonsterDeckScreen() {
        applyFields();
        itemPickerOpen = false;
        effectPickerOpen = false;
        targetPickerOpen = false;
        targetPickerEffectIndex = -1;
        confirmDelete = false;
        Minecraft.getInstance().setScreen(new DeveloperMonsterDeckScreen(this));
    }

    private boolean isConvertedSource(CardSourceType type) {
        return type == CardSourceType.WEAPON || type == CardSourceType.ARMOR || type == CardSourceType.TOOL || type == CardSourceType.UNKNOWN;
    }

    private List<ItemRow> filteredItems() {
        String query = itemSearchBox == null ? "" : itemSearchBox.getValue().toLowerCase(Locale.ROOT);
        if (query.equals(filteredItemsCacheKey)) {
            return filteredItemsCache;
        }
        filteredItemsCacheKey = query;
        filteredItemsCache = BuiltInRegistries.ITEM.entrySet().stream()
                .filter(entry -> {
                    String id = entry.getKey().location().toString();
                    String name = Component.translatable(entry.getValue().getDescriptionId()).getString();
                    return query.isBlank() || id.toLowerCase(Locale.ROOT).contains(query) || name.toLowerCase(Locale.ROOT).contains(query);
                })
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .map(entry -> new ItemRow(entry.getKey().location().toString(), Component.translatable(entry.getValue().getDescriptionId()).getString()))
                .toList();
        return filteredItemsCache;
    }

    private List<MonsterRow> filteredMonsters() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT);
        String cacheKey = query + "\n" + data.monsters.hashCode();
        if (cacheKey.equals(filteredMonstersCacheKey)) {
            return filteredMonstersCache;
        }
        List<MonsterRow> rows = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .filter(entry -> canCustomizeMonster(entry.getValue()))
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    EntityType<?> type = entry.getValue();
                    String id = entry.getKey().location().toString();
                    String name = Component.translatable(type.getDescriptionId()).getString();
                    if (query.isBlank() || id.toLowerCase(Locale.ROOT).contains(query) || name.toLowerCase(Locale.ROOT).contains(query)) {
                        rows.add(new MonsterRow(id, name));
                    }
                });
        for (DeveloperMonsterDefinition monster : data.monsters) {
            if (rows.stream().noneMatch(row -> row.id().equals(monster.entityTypeId()))) {
                rows.add(new MonsterRow(monster.entityTypeId(), monster.entityTypeId()));
            }
        }
        filteredMonstersCacheKey = cacheKey;
        filteredMonstersCache = List.copyOf(rows);
        return filteredMonstersCache;
    }

    private boolean canCustomizeMonster(EntityType<?> type) {
        return type != EntityType.PLAYER && createSampleMonster(type) != null;
    }

    private void invalidateFilteredCaches() {
        filteredCardsCacheKey = "";
        filteredItemsCacheKey = "";
        filteredMonstersCacheKey = "";
    }

    private CardSourceType sourceType() {
        return EDIT_SOURCE_TYPES.get(Math.max(0, Math.min(EDIT_SOURCE_TYPES.size() - 1, sourceTypeIndex)));
    }

    private Component sourceTypeName() {
        return Component.translatable("debug.moonspire.source_type." + sourceType().name().toLowerCase(Locale.ROOT));
    }

    private Component filterName() {
        return Component.translatable(switch (cardFilterIndex) {
            case 1 -> "debug.moonspire.filter.mod";
            case 2 -> "debug.moonspire.filter.monster";
            case 3 -> "debug.moonspire.filter.converted";
            case 4 -> "debug.moonspire.filter.custom";
            default -> "debug.moonspire.filter.all";
        });
    }

    private void renderItemPicker(GuiGraphics graphics, Layout layout) {
        drawHeader(graphics, Component.translatable("debug.moonspire.items"), layout.itemX(), layout.top());
        renderItemGrid(graphics, layout);
    }

    private void renderItemPickerPopup(GuiGraphics graphics, Layout layout) {
        MoonSpireUiTextures.drawDarkPanel(graphics, layout.itemX() - 8, layout.top() - 8, layout.itemW() + 16, layout.buttonY() - layout.top());
        renderItemPicker(graphics, layout);
        drawFieldLabel(graphics, Component.translatable("debug.moonspire.field.item_search"), layout.itemX(), layout.searchLabelY(), layout.itemW());
        if (itemSearchBox != null) {
            itemSearchBox.visible = true;
            itemSearchBox.active = true;
            itemSearchBox.setX(layout.itemX());
            itemSearchBox.setY(layout.searchBoxY());
            itemSearchBox.setWidth(Math.max(1, layout.itemW()));
            itemSearchBox.render(graphics, 0, 0, 0.0F);
        }
        drawButtonLike(graphics, layout.itemX(), layout.buttonY(), 52, 18, Component.translatable("gui.done"));
        drawButtonLike(graphics, layout.itemX() + 58, layout.buttonY(), 52, 18, Component.translatable("gui.cancel"));
    }

    private void renderEffectPickerPopup(GuiGraphics graphics, Layout layout, int mouseX, int mouseY, float partialTick) {
        PickerBounds picker = effectPickerBounds(layout, selectedEffectCount());
        graphics.fill(picker.x(), picker.y(), picker.x() + picker.w(), picker.y() + picker.h(), 0xFF100D0A);
        MoonSpireUiTextures.drawDarkPanel(graphics, picker.x(), picker.y(), picker.w(), picker.h());
        if (effectSearchBox != null) {
            effectSearchBox.visible = true;
            effectSearchBox.active = true;
            effectSearchBox.setX(picker.x() + 6);
            effectSearchBox.setY(picker.y() + 5);
            effectSearchBox.setWidth(Math.max(1, picker.w() - 12));
            effectSearchBox.render(graphics, mouseX, mouseY, partialTick);
        }
        List<DeveloperCardEffect.Kind> kinds = filteredEffectKinds();
        GridLayout grid = effectPickerGrid(picker, kinds.size());
        effectPickerScrollOffset = clampScroll(effectPickerScrollOffset, grid);
        graphics.enableScissor(grid.viewX(), grid.viewY(), grid.viewX() + grid.viewW(), grid.viewY() + grid.viewH());
        for (int i = firstVisibleIndex(grid); i < Math.min(kinds.size(), lastVisibleIndex(grid)); i++) {
            GridCell cell = cell(grid, i);
            drawTrimmed(graphics, effectName(kinds.get(i)), cell.x(), cell.y(), grid.cellW(), 0xFFEDE8FF);
        }
        graphics.disableScissor();
        renderGridScrollbar(graphics, grid);
    }

    private void renderTargetPickerPopup(GuiGraphics graphics, Layout layout, int mouseX, int mouseY, float partialTick) {
        PickerBounds picker = targetPickerBounds(layout, selectedEffectCount());
        graphics.fill(picker.x(), picker.y(), picker.x() + picker.w(), picker.y() + picker.h(), 0xFF100D0A);
        MoonSpireUiTextures.drawDarkPanel(graphics, picker.x(), picker.y(), picker.w(), picker.h());
        if (targetSearchBox != null) {
            targetSearchBox.visible = true;
            targetSearchBox.active = true;
            targetSearchBox.setX(picker.x() + 6);
            targetSearchBox.setY(picker.y() + 5);
            targetSearchBox.setWidth(Math.max(1, picker.w() - 12));
            targetSearchBox.render(graphics, mouseX, mouseY, partialTick);
        }
        List<CardTarget> targets = filteredTargets();
        GridLayout grid = targetPickerGrid(picker, targets.size());
        targetPickerScrollOffset = clampScroll(targetPickerScrollOffset, grid);
        CardTarget selectedTarget = targetPickerSelectedTarget();
        graphics.enableScissor(grid.viewX(), grid.viewY(), grid.viewX() + grid.viewW(), grid.viewY() + grid.viewH());
        for (int i = firstVisibleIndex(grid); i < Math.min(targets.size(), lastVisibleIndex(grid)); i++) {
            GridCell cell = cell(grid, i);
            CardTarget target = targets.get(i);
            drawListRow(graphics, cell.x(), cell.y(), grid.cellW(), targetName(target), target == selectedTarget);
        }
        graphics.disableScissor();
        renderGridScrollbar(graphics, grid);
    }

    private void renderDeleteConfirm(GuiGraphics graphics) {
        MoonSpireModalLayer.drawTopmostOverlay(graphics, width, height);
        int w = 220;
        int h = 78;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        MoonSpireUiTextures.drawDarkPanel(graphics, x, y, w, h);
        drawWrappedLimited(graphics, Component.translatable(deleteConfirmKey()), x + 10, y + 10, w - 20, y + 40, 0xFFEDE8FF);
        drawButtonLike(graphics, x + 30, y + 50, 70, 18, Component.translatable("debug.moonspire.delete"));
        drawButtonLike(graphics, x + 120, y + 50, 70, 18, Component.translatable("gui.cancel"));
    }

    private CardTarget targetPickerSelectedTarget() {
        DeveloperCardDefinition card = selectedCard();
        if (card == null || targetPickerEffectIndex < 0 || targetPickerEffectIndex >= card.normalizedEffects().size()) {
            return null;
        }
        return card.normalizedEffects().get(targetPickerEffectIndex).target();
    }

    private String deleteConfirmKey() {
        if (tab == Tab.FACES) {
            return "debug.moonspire.face_delete_confirm";
        }
        if (tab == Tab.MONSTERS) {
            return "debug.moonspire.monster_delete_confirm";
        }
        return "debug.moonspire.delete_confirm";
    }

    private void renderCardGrid(GuiGraphics graphics, Layout layout) {
        List<DeveloperCardDefinition> cards = filteredCards();
        GridLayout grid = layout.cardGrid(cards.size());
        cardScrollOffset = clampScroll(cardScrollOffset, grid);
        lastCardScrollOffset = cardScrollOffset;
        graphics.enableScissor(grid.viewX(), grid.viewY(), grid.viewX() + grid.viewW(), grid.viewY() + grid.viewH());
        for (int i = firstVisibleIndex(grid); i < Math.min(cards.size(), lastVisibleIndex(grid)); i++) {
            GridCell cell = cell(grid, i);
            DeveloperCardDefinition card = cards.get(i);
            boolean selected = card.id().equals(selectedCardId);
            renderScaledSmallCard(graphics, card, cell, selected);
            drawCenteredFitted(graphics, MoonSpireCardRegistry.registeredDeveloperId(card.id()), cell.x(), cell.y() + grid.cellH() + 2, grid.cellW(), CARD_ID_LABEL_H, selected ? 0xFFFFF3BF : 0xFFEDE8FF);
        }
        graphics.disableScissor();
        renderGridScrollbar(graphics, grid);
    }

    private void renderItemGrid(GuiGraphics graphics, Layout layout) {
        List<ItemRow> items = filteredItems();
        GridLayout grid = layout.itemGrid(items.size());
        itemScrollOffset = clampScroll(itemScrollOffset, grid);
        graphics.enableScissor(grid.viewX(), grid.viewY(), grid.viewX() + grid.viewW(), grid.viewY() + grid.viewH());
        for (int i = firstVisibleIndex(grid); i < Math.min(items.size(), lastVisibleIndex(grid)); i++) {
            GridCell cell = cell(grid, i);
            ItemRow row = items.get(i);
            boolean selected = row.id().equals(selectedArtItemId);
            graphics.fill(cell.x() - 1, cell.y() - 1, cell.x() + ITEM_SLOT + 1, cell.y() + ITEM_SLOT + 1, selected ? 0x663F7A3F : 0x33101010);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.id()));
            graphics.renderFakeItem(new ItemStack(item), cell.x() + (cell.cellW() - ITEM_SLOT) / 2, cell.y());
        }
        graphics.disableScissor();
        renderGridScrollbar(graphics, grid);
    }

    private void drawCenteredTrimmed(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        String trimmed = font.plainSubstrByWidth(text, Math.max(0, width));
        graphics.drawString(font, trimmed, x + (width - font.width(trimmed)) / 2, y, color, false);
    }

    private void drawCenteredFitted(GuiGraphics graphics, String text, int x, int y, int width, int height, int color) {
        int lineY = y + Math.max(0, (height - font.lineHeight) / 2);
        if (font.width(text) <= width) {
            graphics.drawString(font, text, x + (width - font.width(text)) / 2, lineY, color, false);
            return;
        }
        float scale = Math.max(0.45F, width / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x + width / 2.0F, y + Math.max(0, (height - Math.round(font.lineHeight * scale)) / 2.0F), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private void renderScaledSmallCard(GuiGraphics graphics, DeveloperCardDefinition card, GridCell cell, boolean selected) {
        float scale = cell.cellW() / (float) CardRenderHelper.SMALL_CARD_WIDTH;
        if (scale >= 0.995F) {
            CardRenderHelper.renderSmallCard(graphics, font, card.toCardInstance(), cell.x(), cell.y(), selected, false, true, data);
            if (selected) {
                renderSelectableCardOutline(graphics, cell.x(), cell.y(), CardRenderHelper.SMALL_CARD_WIDTH, CardRenderHelper.SMALL_CARD_HEIGHT);
            }
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(cell.x(), cell.y(), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        CardRenderHelper.renderSmallCard(graphics, font, card.toCardInstance(), 0, 0, selected, false, true, data);
        graphics.pose().popPose();
        if (selected) {
            renderSelectableCardOutline(graphics, cell.x(), cell.y(), Math.round(CardRenderHelper.SMALL_CARD_WIDTH * scale), Math.round(CardRenderHelper.SMALL_CARD_HEIGHT * scale));
        }
    }

    static void renderSelectableCardOutline(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.renderOutline(x - 2, y - 2, w + 4, h + 4, 0xFF4AA3FF);
        graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xCC9ED0FF);
    }

    private boolean clickScrollbar(GridLayout grid, ScrollArea area, double mouseY, double mouseX) {
        if (!hasScrollbar(grid) || !scrollbarAt(grid, mouseX, mouseY)) {
            return false;
        }
        ScrollbarThumb thumb = scrollbarThumb(grid);
        draggingScrollArea = area;
        scrollbarGrabOffset = (int) Math.max(0.0D, Math.min(thumb.height(), mouseY - thumb.y()));
        dragScrollbar(grid, mouseY, area);
        return true;
    }

    private void dragScrollbar(GridLayout grid, double mouseY, ScrollArea area) {
        if (!hasScrollbar(grid)) {
            return;
        }
        ScrollbarThumb thumb = scrollbarThumb(grid);
        int trackRange = Math.max(1, grid.viewH() - thumb.height());
        int thumbY = (int) Math.max(grid.viewY(), Math.min(grid.viewY() + trackRange, mouseY - scrollbarGrabOffset));
        double offset = (thumbY - grid.viewY()) * maxScroll(grid) / (double) trackRange;
        if (area == ScrollArea.CARDS) {
            cardScrollOffset = clampScroll(offset, grid);
            lastCardScrollOffset = cardScrollOffset;
        } else if (area == ScrollArea.ITEMS) {
            itemScrollOffset = clampScroll(offset, grid);
            lastItemScrollOffset = itemScrollOffset;
        } else if (area == ScrollArea.LIST) {
            listScrollOffset = clampScroll(offset, grid);
            lastListScrollOffset = listScrollOffset;
            rememberMonsterListScrollOffset();
        } else if (area == ScrollArea.EFFECTS) {
            effectScrollOffset = clampScroll(offset, grid);
        } else if (area == ScrollArea.EFFECT_PICKER) {
            effectPickerScrollOffset = clampScroll(offset, grid);
        } else if (area == ScrollArea.TARGET_PICKER) {
            targetPickerScrollOffset = clampScroll(offset, grid);
        }
    }

    private void renderGridScrollbar(GuiGraphics graphics, GridLayout grid) {
        if (!hasScrollbar(grid)) {
            return;
        }
        MoonSpireUiTextures.drawScrollbarTrack(graphics, grid.scrollbarX(), grid.viewY(), SCROLLBAR_WIDTH, grid.viewH());
        ScrollbarThumb thumb = scrollbarThumb(grid);
        MoonSpireUiTextures.drawScrollbarThumb(graphics, grid.scrollbarX() - 2, thumb.y(), SCROLLBAR_WIDTH + 4, thumb.height());
    }

    private boolean scrollbarAt(GridLayout grid, double mouseX, double mouseY) {
        return mouseX >= grid.scrollbarX() - (SCROLLBAR_HIT_WIDTH - SCROLLBAR_WIDTH) / 2.0D
                && mouseX <= grid.scrollbarX() + SCROLLBAR_HIT_WIDTH
                && mouseY >= grid.viewY()
                && mouseY <= grid.viewY() + grid.viewH();
    }

    private ScrollbarThumb scrollbarThumb(GridLayout grid) {
        int thumbH = Math.max(22, grid.viewH() * grid.viewH() / Math.max(1, grid.contentH()));
        int maxScroll = Math.max(1, maxScroll(grid));
        int y = grid.viewY() + (int) Math.round((grid.viewH() - thumbH) * scrollOffset(grid.area()) / maxScroll);
        return new ScrollbarThumb(y, thumbH);
    }

    private boolean insideGrid(GridLayout grid, double mouseX, double mouseY) {
        return mouseX >= grid.viewX() && mouseX <= grid.viewX() + grid.viewW() && mouseY >= grid.viewY() && mouseY <= grid.viewY() + grid.viewH();
    }

    private int indexAt(GridLayout grid, double mouseX, double mouseY) {
        int column = ((int) mouseX - grid.cellsX()) / (grid.cellW() + grid.gapX());
        int row = (int) Math.floor((mouseY - grid.viewY() + scrollOffset(grid.area()) - grid.topPad()) / grid.rowH());
        if (column < 0 || column >= grid.columns() || row < 0) {
            return -1;
        }
        int cellX = grid.cellsX() + column * (grid.cellW() + grid.gapX());
        int cellY = grid.viewY() + grid.topPad() + row * grid.rowH() - (int) Math.round(scrollOffset(grid.area()));
        if (mouseX < cellX || mouseX > cellX + grid.cellW() || mouseY < cellY || mouseY > cellY + grid.cellH()) {
            return -1;
        }
        return row * grid.columns() + column;
    }

    private GridCell cell(GridLayout grid, int index) {
        int row = index / grid.columns();
        int column = index % grid.columns();
        int x = grid.cellsX() + column * (grid.cellW() + grid.gapX());
        int y = grid.viewY() + grid.topPad() + row * grid.rowH() - (int) Math.round(scrollOffset(grid.area()));
        return new GridCell(x, y, grid.cellW());
    }

    private int firstVisibleIndex(GridLayout grid) {
        int firstRow = Math.max(0, (int) Math.floor(Math.max(0.0D, scrollOffset(grid.area()) - grid.topPad()) / grid.rowH()));
        return firstRow * grid.columns();
    }

    private int lastVisibleIndex(GridLayout grid) {
        int lastRow = Math.max(1, (int) Math.ceil(Math.max(0.0D, scrollOffset(grid.area()) + grid.viewH() - grid.topPad()) / grid.rowH()) + 1);
        return lastRow * grid.columns();
    }

    private boolean hasScrollbar(GridLayout grid) {
        return maxScroll(grid) > 0;
    }

    private int maxScroll(GridLayout grid) {
        return Math.max(0, grid.contentH() - grid.viewH());
    }

    private double clampScroll(double offset, GridLayout grid) {
        return Math.max(0.0D, Math.min(maxScroll(grid), offset));
    }

    private double scrollOffset(ScrollArea area) {
        return switch (area) {
            case CARDS -> cardScrollOffset;
            case ITEMS -> itemScrollOffset;
            case LIST -> listScrollOffset;
            case EFFECTS -> effectScrollOffset;
            case EFFECT_PICKER -> effectPickerScrollOffset;
            case TARGET_PICKER -> targetPickerScrollOffset;
            default -> 0.0D;
        };
    }

    private GridLayout effectEditorGrid(Layout layout, int itemCount) {
        int viewX = layout.formX() + EFFECT_INSET;
        int viewY = layout.effectsY();
        int viewW = Math.max(1, layout.formW() - EFFECT_INSET * 2 - SCROLLBAR_HIT_WIDTH);
        int viewH = Math.max(18, layout.buttonY() - viewY - EFFECT_BOTTOM_PAD);
        int rowH = 24;
        int contentH = itemCount <= 0 ? 0 : itemCount * rowH + EFFECT_BOTTOM_PAD - 6;
        int scrollbarX = layout.formX() + layout.formW() - EFFECT_INSET - SCROLLBAR_HIT_WIDTH / 2;
        return new GridLayout(ScrollArea.EFFECTS, viewX, viewY, viewW, viewH, viewX, scrollbarX, 1, viewW, 18, 0, rowH, contentH, 0);
    }

    private GridLayout effectPickerGrid(PickerBounds picker, int itemCount) {
        int viewX = picker.x() + 6;
        int viewY = picker.y() + 28;
        int viewW = Math.max(1, picker.w() - 12 - SCROLLBAR_HIT_WIDTH);
        int viewH = Math.max(14, picker.h() - 32);
        int rowH = 16;
        int contentH = itemCount <= 0 ? 0 : itemCount * rowH - 2;
        int scrollbarX = picker.x() + picker.w() - SCROLLBAR_HIT_WIDTH / 2;
        return new GridLayout(ScrollArea.EFFECT_PICKER, viewX, viewY, viewW, viewH, viewX, scrollbarX, 1, viewW, 14, 0, rowH, contentH, 0);
    }

    private GridLayout targetPickerGrid(PickerBounds picker, int itemCount) {
        int viewX = picker.x() + 6;
        int viewY = picker.y() + 28;
        int viewW = Math.max(1, picker.w() - 12 - SCROLLBAR_HIT_WIDTH);
        int viewH = Math.max(14, picker.h() - 32);
        int rowH = 18;
        int contentH = itemCount <= 0 ? 0 : itemCount * rowH - 2;
        int scrollbarX = picker.x() + picker.w() - SCROLLBAR_HIT_WIDTH / 2;
        return new GridLayout(ScrollArea.TARGET_PICKER, viewX, viewY, viewW, viewH, viewX, scrollbarX, 1, viewW, 14, 0, rowH, contentH, 0);
    }

    private void syncEffectAmountBoxes(Layout layout, List<DeveloperCardEffect> effects, GridLayout grid) {
        String selectedId = selectedCard() == null ? "" : selectedCard().id();
        if (!selectedId.equals(effectAmountBoxesCardId) || effectAmountBoxes.size() != effects.size() || effectCountBoxes.size() != effects.size()) {
            hideEffectAmountBoxes();
            hideEffectCountBoxes();
            effectAmountBoxes.clear();
            effectCountBoxes.clear();
            effectAmountBoxesCardId = selectedId;
            for (int i = 0; i < effects.size(); i++) {
                int rowY = layout.effectsY() + i * 24;
                EditBox box = addBox(layout.formX() + layout.formW() - 58, rowY, EFFECT_AMOUNT_W, Component.translatable("screen.moonspire.value_number", effects.get(i).amount()));
                box.setValue(Integer.toString(effects.get(i).amount()));
                effectAmountBoxes.add(box);
                EditBox countBox = addBox(layout.formX() + layout.formW() - 58, rowY, EFFECT_COUNT_W, Component.translatable("debug.moonspire.effect_count"));
                countBox.setValue(Integer.toString(effects.get(i).count()));
                effectCountBoxes.add(countBox);
            }
        }
        for (int i = 0; i < effectAmountBoxes.size(); i++) {
            EditBox box = effectAmountBoxes.get(i);
            boolean visible = tab == Tab.CARDS && i < effects.size() && effects.get(i).kind().usesAmount();
            if (visible) {
                GridCell cell = cell(grid, i);
                int rowY = cell.y();
                visible = rowY + 18 > grid.viewY() && rowY < grid.viewY() + grid.viewH();
                box.setX(effectAmountX(grid));
                box.setY(rowY);
                box.setWidth(EFFECT_AMOUNT_W);
            }
            setVisible(box, visible);
        }
        for (int i = 0; i < effectCountBoxes.size(); i++) {
            EditBox box = effectCountBoxes.get(i);
            boolean visible = tab == Tab.CARDS && i < effects.size() && effects.get(i).canChangeCount();
            if (visible) {
                GridCell cell = cell(grid, i);
                int rowY = cell.y();
                visible = rowY + 18 > grid.viewY() && rowY < grid.viewY() + grid.viewH();
                box.setX(effectCountX(grid));
                box.setY(rowY);
                box.setWidth(EFFECT_COUNT_W);
            }
            setVisible(box, visible);
        }
    }

    private void hideEffectAmountBoxes() {
        for (EditBox box : effectAmountBoxes) {
            setVisible(box, false);
        }
    }

    private void hideEffectCountBoxes() {
        for (EditBox box : effectCountBoxes) {
            setVisible(box, false);
        }
    }

    private int effectCountX(GridLayout grid) {
        return grid.viewX() + grid.viewW() - EFFECT_COUNT_W;
    }

    private int effectCountLabelX(GridLayout grid) {
        return effectCountX(grid) - EFFECT_COUNT_LABEL_W - EFFECT_LABEL_INPUT_GAP;
    }

    private int effectAmountX(GridLayout grid) {
        return effectCountLabelX(grid) - EFFECT_CONTROL_GAP - EFFECT_AMOUNT_W;
    }

    private int effectAmountLabelX(GridLayout grid) {
        return effectAmountX(grid) - EFFECT_AMOUNT_LABEL_W - EFFECT_LABEL_INPUT_GAP;
    }

    private int effectTargetX(GridLayout grid) {
        return effectAmountLabelX(grid) - EFFECT_CONTROL_GAP - EFFECT_TARGET_W;
    }

    private int effectNameX(GridLayout grid) {
        return grid.viewX() + 30;
    }

    private int effectNameWidth(GridLayout grid) {
        return Math.max(1, effectTargetX(grid) - effectNameX(grid) - EFFECT_NAME_TARGET_GAP);
    }

    private int textListSize() {
        if (tab == Tab.FACES) {
            return data.cardFaces.size();
        }
        if (tab == Tab.MONSTERS) {
            return filteredMonsters().size();
        }
        return 0;
    }

    private boolean clickItemGrid(Layout layout, double mouseX, double mouseY) {
        GridLayout grid = layout.itemGrid(filteredItems().size());
        if (!insideGrid(grid, mouseX, mouseY)) {
            return false;
        }
        int index = indexAt(grid, mouseX, mouseY);
        List<ItemRow> items = filteredItems();
        if (index >= 0 && index < items.size()) {
            selectedArtItemId = items.get(index).id();
            DeveloperCardDefinition card = selectedCard();
            if (card != null) {
                replaceCard(card, new DeveloperCardDefinition(card.id(), card.displayName(), card.nameKey(), card.descriptionKey(), card.cost(), card.attack(), card.defense(), card.bleed(), card.normalizedEffects(), card.sourceType(), "", selectedArtItemId, card.artX(), card.artY(), card.artScale(), card.faceId()));
                refreshFields();
            }
            itemPickerOpen = false;
            init();
            return true;
        }
        return false;
    }

    private boolean clickEffectPicker(Layout layout, double mouseX, double mouseY, int button) {
        PickerBounds picker = effectPickerBounds(layout, selectedEffectCount());
        if (insideRect(mouseX, mouseY, picker.x() + 6, picker.y() + 5, Math.max(1, picker.w() - 12), 18)) {
            focusPopupSearchBox(effectSearchBox, mouseX, mouseY, button);
            return true;
        }
        List<DeveloperCardEffect.Kind> kinds = filteredEffectKinds();
        GridLayout grid = effectPickerGrid(picker, kinds.size());
        if (clickScrollbar(grid, ScrollArea.EFFECT_PICKER, mouseY, mouseX)) {
            return true;
        }
        if (insideGrid(grid, mouseX, mouseY)) {
            int index = indexAt(grid, mouseX, mouseY);
            DeveloperCardDefinition card = selectedCard();
            if (card != null && index >= 0 && index < kinds.size()) {
                List<DeveloperCardEffect> next = new ArrayList<>(card.normalizedEffects());
                DeveloperCardEffect.Kind kind = kinds.get(index);
                next.add(new DeveloperCardEffect(kind, kind.usesAmount() ? 1 : 0));
                effectPickerOpen = false;
                targetPickerOpen = false;
                targetPickerEffectIndex = -1;
                updateEffects(next);
                return true;
            }
            return true;
        }
        if (!insideRect(mouseX, mouseY, picker.x(), picker.y(), picker.w(), picker.h())) {
            effectPickerOpen = false;
            init();
            return true;
        }
        return true;
    }

    private boolean clickTargetPicker(Layout layout, double mouseX, double mouseY, int button) {
        PickerBounds picker = targetPickerBounds(layout, selectedEffectCount());
        if (insideRect(mouseX, mouseY, picker.x() + 6, picker.y() + 5, Math.max(1, picker.w() - 12), 18)) {
            focusPopupSearchBox(targetSearchBox, mouseX, mouseY, button);
            return true;
        }
        List<CardTarget> targets = filteredTargets();
        GridLayout grid = targetPickerGrid(picker, targets.size());
        if (clickScrollbar(grid, ScrollArea.TARGET_PICKER, mouseY, mouseX)) {
            return true;
        }
        if (insideGrid(grid, mouseX, mouseY)) {
            int index = indexAt(grid, mouseX, mouseY);
            DeveloperCardDefinition card = selectedCard();
            List<DeveloperCardEffect> effects = card == null ? List.of() : effectsFromBoxes(card.normalizedEffects());
            if (index >= 0 && index < targets.size() && targetPickerEffectIndex >= 0 && targetPickerEffectIndex < effects.size()) {
                List<DeveloperCardEffect> next = new ArrayList<>(effects);
                DeveloperCardEffect effect = next.get(targetPickerEffectIndex);
                if (effect.canChangeTarget()) {
                    next.set(targetPickerEffectIndex, new DeveloperCardEffect(effect.kind(), effect.amount(), targets.get(index), effect.count()));
                    closeTargetPicker();
                    updateEffects(next);
                    return true;
                }
            }
            return true;
        }
        if (!insideRect(mouseX, mouseY, picker.x(), picker.y(), picker.w(), picker.h())) {
            closeTargetPicker();
            init();
            return true;
        }
        return true;
    }

    private void focusPopupSearchBox(EditBox box, double mouseX, double mouseY, int button) {
        if (box == null) {
            return;
        }
        box.mouseClicked(mouseX, mouseY, button);
        setFocused(box);
        box.setFocused(true);
    }

    private boolean clickCardListToggle(Layout layout, double mouseX, double mouseY) {
        ToggleBounds bounds = cardListToggleBounds(layout);
        if (!insideRect(mouseX, mouseY, bounds.x(), bounds.y(), bounds.w(), bounds.h())) {
            return false;
        }
        cardListExpanded = !cardListExpanded;
        itemPickerOpen = false;
        effectPickerOpen = false;
        targetPickerOpen = false;
        targetPickerEffectIndex = -1;
        init();
        return true;
    }

    private ToggleBounds cardListToggleBounds(Layout layout) {
        int w = CARD_LIST_TOGGLE_W;
        int h = CARD_LIST_TOGGLE_H;
        int centerX = layout.listPanelLeft();
        int centerY = layout.contentCenterY();
        int x = centerX - w / 2;
        int y = centerY - h / 2;
        return new ToggleBounds(x, y, w, h);
    }

    private Layout layout() {
        int formX = SIDEBAR_W + 16;
        int contentRight = Math.min(width - 16, Math.max(formX + 360, width - 16));
        int available = Math.max(360, contentRight - formX);
        int formW;
        int itemX;
        int itemW;
        int listX;
        int listW;
        if (tab == Tab.CARDS) {
            formW = Math.min(FORM_W, Math.max(246, available * 38 / 100));
            int remaining = Math.max(120, available - formW - GAP);
            int combinedListsW = Math.max(80, remaining - GAP);
            itemW = itemPickerOpen ? Math.max(180, Math.min(combinedListsW - 86, combinedListsW / 2)) : cardListExpanded ? 0 : Math.max(CardRenderHelper.CARD_WIDTH + 16, combinedListsW / 2);
            listW = cardListExpanded ? Math.max(60, combinedListsW) : Math.max(60, combinedListsW - itemW);
            listW = Math.max(72, listW);
            itemX = formX + formW + GAP;
            listX = cardListExpanded ? itemX : itemX + itemW + GAP;
            listW = Math.min(listW, Math.max(72, width - 16 - listX));
        } else if (tab == Tab.FACES) {
            formW = Math.min(FORM_W, Math.max(246, available * 34 / 100));
            itemX = formX + formW + GAP;
            int maxListW = 330;
            int minItemW = CardRenderHelper.CARD_WIDTH + 16;
            int preferredItemW = 220;
            int preferredListW = Math.min(maxListW, Math.max(MIN_LIST_W, available - formW - GAP - preferredItemW - GAP));
            listW = Math.min(preferredListW, Math.max(72, contentRight - itemX - minItemW - GAP));
            itemW = Math.max(minItemW, contentRight - itemX - listW - GAP);
            listX = itemX + itemW + GAP;
            listW = Math.min(listW, Math.max(72, contentRight - listX));
        } else {
            listW = Math.min(330, Math.max(MIN_LIST_W, available * 42 / 100));
            formW = Math.min(FORM_W, Math.max(246, available - listW - GAP));
            itemX = formX + formW + GAP;
            itemW = 0;
            listX = contentRight - listW;
        }
        int bottom = Math.max(TOP + 80, height - CONTENT_BOTTOM_RESERVE);
        int buttonY = Math.max(bottom + 10, height - BUTTON_Y_OFFSET);
        return new Layout(formX, formW, itemX, itemW, listX, listW, contentRight, bottom, buttonY);
    }

    private static String cleanId(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim().replace(' ', '_').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private static DeveloperData copyData(DeveloperData source) {
        return DeveloperData.fromJson(source.toJson());
    }

    private String uniqueCardId(String displayName, CardSourceType sourceType, String currentId) {
        String base = stableSlug(displayName, "card_" + Integer.toHexString((displayName == null ? "" : displayName).hashCode()));
        String prefix = sourceType == CardSourceType.MOD ? "builtin_moonspire_" : "custom_";
        if (!base.startsWith(prefix)) {
            base = prefix + base.replaceFirst("^custom_", "").replaceFirst("^builtin_moonspire_", "");
        }
        String candidate = base;
        int suffix = 2;
        while (cardIdExists(candidate, currentId)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private boolean cardIdExists(String id, String currentId) {
        String normalized = MoonSpireCardRegistry.registeredDeveloperId(id);
        String current = MoonSpireCardRegistry.registeredDeveloperId(currentId);
        return data.cards.stream().anyMatch(card -> normalized.equals(MoonSpireCardRegistry.registeredDeveloperId(card.id())) && !normalized.equals(current))
                || MoonSpireCardRegistry.baseCards().stream().anyMatch(card -> normalized.equals(card.id()) && !normalized.equals(current));
    }

    private static String stableSlug(String value, String fallback) {
        String cleaned = cleanId(value, "");
        if (!cleaned.isBlank() && cleaned.matches(".*[a-z0-9].*")) {
            return cleaned;
        }
        return cleanId(fallback, "card_" + Integer.toHexString((value == null ? "" : value).hashCode()));
    }

    private String nameKeyFor(String id) {
        String normalized = MoonSpireCardRegistry.registeredDeveloperId(id);
        if (normalized.startsWith("builtin_moonspire_")) {
            return "card.moonspire.builtin." + normalized.substring("builtin_moonspire_".length()) + ".name";
        }
        if (normalized.startsWith("custom_")) {
            return "card.moonspire.custom." + normalized.substring("custom_".length()) + ".name";
        }
        return "card.moonspire.override." + normalized.replace(':', '_') + ".name";
    }

    private static String valueOr(EditBox box, String fallback) {
        return box == null || box.getValue().isBlank() ? fallback : box.getValue();
    }

    private static int intValue(EditBox box, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(box.getValue().trim()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int positiveIntValue(EditBox box, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(box.getValue().trim()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float floatValue(EditBox box, float fallback) {
        try {
            return Math.max(0.0F, Float.parseFloat(box.getValue().trim()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private enum Tab {
        CARDS,
        FACES,
        MONSTERS,
        LAYOUT
    }

    private enum FaceAreaKind {
        COST,
        NAME,
        ART,
        TYPE,
        DESCRIPTION
    }

    private record ItemRow(String id, String name) {
    }

    private record MonsterRow(String id, String name) {
    }

    private record PickerBounds(int x, int y, int w, int h) {
    }

    private record Layout(int formX, int formW, int itemX, int itemW, int listX, int listW, int contentRight, int bottom, int buttonY) {
        int top() {
            return TOP;
        }

        int fieldLabelY(int row) {
            return TOP + row * FIELD_PITCH;
        }

        int fieldBoxY(int row) {
            return fieldLabelY(row) + LABEL_TO_BOX;
        }

        int searchLabelY() {
            return TOP + 14;
        }

        int searchBoxY() {
            return searchLabelY() + LABEL_TO_BOX;
        }

        int listRowsY() {
            return searchBoxY() + 23;
        }

        int searchX(Tab tab) {
            return tab == Tab.CARDS ? listX : listX;
        }

        int searchW(Tab tab) {
            return listW;
        }

        int formInfoY(int fieldRows, int row) {
            return fieldBoxY(Math.max(0, fieldRows - 1)) + 25 + row * 14;
        }

        int previewX() {
            return itemX + Math.max(0, (previewW() - CardRenderHelper.CARD_WIDTH) / 2);
        }

        int previewY() {
            return top() + 18;
        }

        int previewW() {
            return itemW;
        }

        int facePreviewX() {
            return itemX + Math.max(0, (facePreviewW() - CardRenderHelper.CARD_WIDTH) / 2);
        }

        int facePreviewY() {
            return top() + 18;
        }

        int facePreviewW() {
            return CardRenderHelper.CARD_WIDTH;
        }

        int facePreviewH() {
            return CardRenderHelper.CARD_HEIGHT;
        }

        int imageControlsY() {
            return fieldBoxY(4);
        }

        int effectsY() {
            return fieldBoxY(5) + 10;
        }

        int gridTop() {
            return listRowsY();
        }

        int listPanelLeft() {
            return listX - 8;
        }

        int contentCenterY() {
            return (top() + bottom) / 2;
        }

        GridLayout cardGrid(int itemCount) {
            int availableW = Math.max(40, listW - CARD_GRID_SCROLL_RESERVE);
            int columns = Math.max(1, Math.min(CARD_GRID_MAX_COLUMNS, (availableW + CARD_GRID_GAP_X) / (CardRenderHelper.SMALL_CARD_WIDTH + CARD_GRID_GAP_X)));
            int cellW = Math.min(CardRenderHelper.SMALL_CARD_WIDTH, Math.max(38, (availableW - (columns - 1) * CARD_GRID_GAP_X) / columns));
            int cardH = Math.max(1, Math.round(CardRenderHelper.SMALL_CARD_HEIGHT * (cellW / (float) CardRenderHelper.SMALL_CARD_WIDTH)));
            return grid(ScrollArea.CARDS, listX, listW, cellW, cardH + CARD_ID_LABEL_H + 4, CARD_GRID_GAP_X, CARD_GRID_GAP_Y, columns, itemCount, CARD_GRID_PAD_TOP, CARD_ID_LABEL_H + CARD_GRID_PAD_BOTTOM);
        }

        GridLayout itemGrid(int itemCount) {
            int cellW = ITEM_SLOT + 4;
            int fitColumns = Math.max(1, (itemW + ITEM_GAP) / (cellW + ITEM_GAP));
            int columns = Math.max(1, Math.min(8, fitColumns - 1));
            return grid(ScrollArea.ITEMS, itemX, itemW, cellW, ITEM_SLOT, ITEM_GAP, ITEM_GAP, columns, itemCount, 0, 0);
        }

        GridLayout textListGrid(int itemCount) {
            return grid(ScrollArea.LIST, listX, listW, Math.max(1, listW - SCROLLBAR_HIT_WIDTH), 16, 0, 2, 1, itemCount, 0, 0);
        }

        private GridLayout grid(ScrollArea area, int x, int width, int cellW, int cellH, int gapX, int gapY, int columns, int itemCount, int topPad, int bottomPad) {
            int viewX = x;
            int viewY = gridTop();
            int viewW = Math.max(cellW, width - SCROLLBAR_HIT_WIDTH - 4);
            int viewH = Math.max(cellH, bottom - viewY);
            int usedW = columns * cellW + (columns - 1) * gapX;
            int cellsX = viewX + Math.max(0, (viewW - usedW) / 2);
            int rowH = cellH + gapY;
            int rows = itemCount <= 0 ? 0 : (itemCount + columns - 1) / columns;
            int contentH = rows <= 0 ? 0 : topPad + rows * rowH - gapY + bottomPad;
            int scrollbarX = x + width - SCROLLBAR_HIT_WIDTH / 2 - 4;
            return new GridLayout(area, viewX, viewY, viewW, viewH, cellsX, scrollbarX, columns, cellW, cellH, gapX, rowH, contentH, topPad);
        }
    }

    private enum ScrollArea {
        NONE,
        CARDS,
        ITEMS,
        LIST,
        EFFECTS,
        EFFECT_PICKER,
        TARGET_PICKER
    }

    private record GridLayout(ScrollArea area, int viewX, int viewY, int viewW, int viewH, int cellsX, int scrollbarX, int columns, int cellW, int cellH, int gapX, int rowH, int contentH, int topPad) {
    }

    private record GridCell(int x, int y, int cellW) {
    }

    private record ScrollbarThumb(int y, int height) {
    }

    private record ToggleBounds(int x, int y, int w, int h) {
    }

    private record MonsterDefaults(float health, int energy, int speed) {
    }
}
