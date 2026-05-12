package com.yinfires.moonspire.client;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.MoonSpirePerfDiagnostics;
import com.yinfires.moonspire.battle.BattleCombatantSnapshot;
import com.yinfires.moonspire.battle.BattleDamageCalculator;
import com.yinfires.moonspire.battle.BattleEffectSnapshot;
import com.yinfires.moonspire.battle.BattleEffectType;
import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.CardTarget;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.card.RegisteredCardDefinition;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import com.yinfires.moonspire.developer.DeveloperCardDefinition;
import com.yinfires.moonspire.developer.DeveloperCardFace;
import com.yinfires.moonspire.developer.DeveloperData;
import com.yinfires.moonspire.developer.DeveloperDataManager;
import com.yinfires.moonspire.developer.DeveloperPaths;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CardRenderHelper {
    public static final int CARD_WIDTH = 128;
    public static final int CARD_HEIGHT = 158;
    public static final int SMALL_CARD_WIDTH = 82;
    public static final int SMALL_CARD_HEIGHT = 101;
    public static final int TIP_WIDTH = 122;
    private static final ResourceLocation CARD_BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "textures/gui/cards/card_base.png");
    private static final int KEYWORD_TEXT_COLOR = 0xD49A22;
    private static final int CARD_DESCRIPTION_TEXT_COLOR = 0xFFF2E6D2;
    private static final int INCREASED_VALUE_COLOR = 0x2F9E55;
    private static final int COST_ICON_TEXTURE_SIZE = 32;
    private static final int COST_TEXT_COLOR = 0xFFFFF6E0;
    private static final int COST_UNAFFORDABLE_TEXT_COLOR = 0xFFFF5F63;
    private static final int COST_OUTLINE_COLOR = 0xFF46393B;
    private static final float DETAILED_DESCRIPTION_SCALE = 0.62F;
    private static final float COMPACT_DESCRIPTION_SCALE = 0.44F;
    private static final float DETAILED_DESCRIPTION_MIN_SCALE = 0.32F;
    private static final float COMPACT_DESCRIPTION_MIN_SCALE = 0.28F;
    private static final float DESCRIPTION_SCALE_STEP = 0.02F;
    private static final float FITTED_TEXT_MIN_SCALE = 0.25F;
    private static final float CARD_COST_BASE_WIDTH = SMALL_CARD_WIDTH;
    private static final float COST_TEXT_VERTICAL_BIAS = 1.0F;
    private static final float ENERGY_COST_SCREEN_SCALE = 2.0F;
    private static final int ENERGY_COST_TEXT_X_OFFSET = 2;
    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 158;
    private static final int CONTENT_KEY_CACHE_LIMIT = 1024;
    private static final Map<String, TextureRef> FILE_TEXTURES = new HashMap<>();
    private static final Map<String, TextureLookup> FILE_TEXTURE_LOOKUPS = new HashMap<>();
    private static final Map<String, AsyncTextureState> ASYNC_FILE_TEXTURES = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<LoadedTexture> LOADED_FILE_TEXTURES = new ConcurrentLinkedQueue<>();
    private static final ExecutorService TEXTURE_LOAD_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MoonSpire Card Texture Loader");
        thread.setDaemon(true);
        return thread;
    });
    private static final int TEXTURE_REGISTRATIONS_PER_FRAME = 2;
    private static final Map<String, ItemStack> ART_ITEMS = new HashMap<>();
    private static final Map<String, List<FormattedCharSequence>> DESCRIPTION_WRAP_CACHE = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<FormattedCharSequence>> eldest) {
            return size() > 512;
        }
    };
    private static final Map<String, DescriptionLayout> DESCRIPTION_LAYOUT_CACHE = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, DescriptionLayout> eldest) {
            return size() > 512;
        }
    };
    private static final Map<String, Integer> TEXT_WIDTH_CACHE = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > 512;
        }
    };
    private static final Map<String, TextContent> TEXT_CONTENT_CACHE = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TextContent> eldest) {
            return size() > 512;
        }
    };
    private static final Map<String, CardRenderPlan> CARD_RENDER_PLAN_CACHE = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CardRenderPlan> eldest) {
            return size() > 512;
        }
    };
    private static final Map<ContentKeyCacheKey, String> CONTENT_KEY_CACHE = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ContentKeyCacheKey, String> eldest) {
            return size() > CONTENT_KEY_CACHE_LIMIT;
        }
    };
    private static long contentKeyCacheRevision = Long.MIN_VALUE;
    private static long descriptionCacheRevision = Long.MIN_VALUE;
    private static final ThreadLocal<CardRenderContext> FRAME_CONTEXT = new ThreadLocal<>();
    private static int textureRegistrationsThisFrame;
    private static long textureRegistrationNanosThisFrame;
    private static int textureDecodeCompletedTotal;
    private static long textureDecodeNanosTotal;

    private record TextureRef(ResourceLocation location, int width, int height) {
    }

    private record TextureLookup(TextureRef texture) {
    }

    private enum AsyncTextureState {
        LOADING,
        READY,
        FAILED
    }

    private record LoadedTexture(String key, NativeImage image) {
    }

    private record ContentKeyCacheKey(long dataVersion, long cardStateHash) {
    }

    public record CardValues(int attack, int defense, List<Integer> damageAmounts, List<Integer> blockAmounts) {
        public CardValues(int attack, int defense) {
            this(attack, defense, List.of(), List.of());
        }

        public static CardValues original(CardInstance card) {
            return new CardValues(card.enemyDirectDamageAmount(), card.selfEffectAmount(CardEffectKind.BLOCK));
        }

        public int damageAmount(int effectIndex, int fallback) {
            return effectIndex >= 0 && effectIndex < damageAmounts.size() ? damageAmounts.get(effectIndex) : fallback;
        }

        public int blockAmount(int effectIndex, int fallback) {
            return effectIndex >= 0 && effectIndex < blockAmounts.size() ? blockAmounts.get(effectIndex) : fallback;
        }
    }

    public record CardLocalArea(int x, int y, int width, int height) {
    }

    public record SmallCardTextVisibility(boolean cost, boolean name, boolean type, boolean description) {
        public static SmallCardTextVisibility all(boolean description) {
            return new SmallCardTextVisibility(true, true, true, description);
        }

        public boolean any() {
            return cost || name || type || description;
        }
    }

    public static CardRenderContext openFrameContext() {
        CardRenderContext context = new CardRenderContext(FRAME_CONTEXT.get());
        FRAME_CONTEXT.set(context);
        registerPendingFileTextures();
        return context;
    }

    public static CardRenderStats frameStats() {
        CardRenderContext context = FRAME_CONTEXT.get();
        return context == null ? CardRenderStats.EMPTY : context.stats();
    }

    public static long frameFaceBaseNanos() {
        CardRenderContext context = FRAME_CONTEXT.get();
        return context == null ? 0L : context.faceBaseNanos;
    }

    public static long frameCustomArtNanos() {
        CardRenderContext context = FRAME_CONTEXT.get();
        return context == null ? 0L : context.customArtNanos;
    }

    public static long frameItemArtNanos() {
        CardRenderContext context = FRAME_CONTEXT.get();
        return context == null ? 0L : context.itemArtNanos;
    }

    public record CardRenderStats(int textureRegistrations, long textureRegistrationNanos, int textureDecodeCompleted, long textureDecodeNanos, int fakeItemRenders, int skippedItemArt, int itemDepthClears, int descriptionCacheHits, int descriptionCacheMisses, long faceBaseNanos, long customArtNanos, long itemArtNanos, long textCostNanos, long textNameNanos, long textTypeNanos, long textDescriptionNanos) {
        private static final CardRenderStats EMPTY = new CardRenderStats(0, 0L, 0, 0L, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        public String summary() {
            return "textureRegistrations=" + textureRegistrations
                    + " textureRegisterMs=" + MoonSpirePerfDiagnostics.millis(textureRegistrationNanos)
                    + " textureDecoded=" + textureDecodeCompleted
                    + " textureDecodeMs=" + MoonSpirePerfDiagnostics.millis(textureDecodeNanos)
                    + " fakeItemRenders=" + fakeItemRenders
                    + " skippedItemArt=" + skippedItemArt
                    + " itemDepthClears=" + itemDepthClears
                    + " descCacheHits=" + descriptionCacheHits
                    + " descCacheMisses=" + descriptionCacheMisses
                    + " faceBaseMs=" + MoonSpirePerfDiagnostics.millis(faceBaseNanos)
                    + " customArtMs=" + MoonSpirePerfDiagnostics.millis(customArtNanos)
                    + " itemArtMs=" + MoonSpirePerfDiagnostics.millis(itemArtNanos)
                    + " textCostMs=" + MoonSpirePerfDiagnostics.millis(textCostNanos)
                    + " textNameMs=" + MoonSpirePerfDiagnostics.millis(textNameNanos)
                    + " textTypeMs=" + MoonSpirePerfDiagnostics.millis(textTypeNanos)
                    + " textDescMs=" + MoonSpirePerfDiagnostics.millis(textDescriptionNanos);
        }
    }

    public static String contentKey(CardInstance card) {
        long dataVersion = dataVersionKey();
        long revision = DeveloperDataManager.cacheRevision();
        ContentKeyCacheKey key = new ContentKeyCacheKey(dataVersion, card.renderStateHash());
        synchronized (CONTENT_KEY_CACHE) {
            if (contentKeyCacheRevision != revision) {
                CONTENT_KEY_CACHE.clear();
                contentKeyCacheRevision = revision;
            }
            String cached = CONTENT_KEY_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        String built = dataVersion + "|" + cardContentKey(card);
        synchronized (CONTENT_KEY_CACHE) {
            CONTENT_KEY_CACHE.put(key, built);
        }
        return built;
    }

    public static String warmupContentKey(CardInstance card) {
        return contentKey(card);
    }

    public static final class CardRenderContext implements AutoCloseable {
        private static final int MAX_CACHE_ENTRIES = 256;

        private final CardRenderContext previous;
        private final DeveloperData data;
        private final long dataVersion;
        private final Map<String, DeveloperCardFace> faceCache = boundedMap();
        private int fakeItemRenders;
        private int skippedItemArt;
        private int itemDepthClears;
        private int descriptionCacheHits;
        private int descriptionCacheMisses;
        private long faceBaseNanos;
        private long customArtNanos;
        private long itemArtNanos;
        private long textCostNanos;
        private long textNameNanos;
        private long textTypeNanos;
        private long textDescriptionNanos;
        private int textureRegistrationsAtOpen;
        private long textureRegistrationNanosAtOpen;
        private int textureDecodeCompletedAtOpen;
        private long textureDecodeNanosAtOpen;
        private boolean closed;

        private CardRenderContext(CardRenderContext previous) {
            this.previous = previous;
            this.data = DeveloperDataManager.cachedOrLoad();
            this.dataVersion = DeveloperDataManager.cachedStamp();
            this.textureRegistrationsAtOpen = textureRegistrationsThisFrame;
            this.textureRegistrationNanosAtOpen = textureRegistrationNanosThisFrame;
            this.textureDecodeCompletedAtOpen = textureDecodeCompletedTotal;
            this.textureDecodeNanosAtOpen = textureDecodeNanosTotal;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                FRAME_CONTEXT.remove();
            } else {
                previous.fakeItemRenders += fakeItemRenders;
                previous.skippedItemArt += skippedItemArt;
                previous.itemDepthClears += itemDepthClears;
                previous.descriptionCacheHits += descriptionCacheHits;
                previous.descriptionCacheMisses += descriptionCacheMisses;
                previous.faceBaseNanos += faceBaseNanos;
                previous.customArtNanos += customArtNanos;
                previous.itemArtNanos += itemArtNanos;
                previous.textCostNanos += textCostNanos;
                previous.textNameNanos += textNameNanos;
                previous.textTypeNanos += textTypeNanos;
                previous.textDescriptionNanos += textDescriptionNanos;
                FRAME_CONTEXT.set(previous);
            }
        }

        private DeveloperCardFace cardFace(CardInstance card) {
            return faceCache.computeIfAbsent(faceCacheKey(card, dataVersion), ignored -> resolveCardFace(card, data));
        }

        private void recordFakeItemRender() {
            fakeItemRenders++;
        }

        private void recordSkippedItemArt() {
            skippedItemArt++;
        }

        private void recordItemDepthClear() {
            itemDepthClears++;
        }

        private void recordDescriptionCacheHit() {
            descriptionCacheHits++;
        }

        private void recordDescriptionCacheMiss() {
            descriptionCacheMisses++;
        }

        private void recordFaceBase(long nanos) {
            faceBaseNanos += nanos;
        }

        private void recordCustomArt(long nanos) {
            customArtNanos += nanos;
        }

        private void recordItemArt(long nanos) {
            itemArtNanos += nanos;
        }

        private void recordTextCost(long nanos) {
            textCostNanos += nanos;
        }

        private void recordTextName(long nanos) {
            textNameNanos += nanos;
        }

        private void recordTextType(long nanos) {
            textTypeNanos += nanos;
        }

        private void recordTextDescription(long nanos) {
            textDescriptionNanos += nanos;
        }

        private CardRenderStats stats() {
            return new CardRenderStats(
                    Math.max(0, textureRegistrationsThisFrame - textureRegistrationsAtOpen),
                    Math.max(0L, textureRegistrationNanosThisFrame - textureRegistrationNanosAtOpen),
                    Math.max(0, textureDecodeCompletedTotal - textureDecodeCompletedAtOpen),
                    Math.max(0L, textureDecodeNanosTotal - textureDecodeNanosAtOpen),
                    fakeItemRenders,
                    skippedItemArt,
                    itemDepthClears,
                    descriptionCacheHits,
                    descriptionCacheMisses,
                    faceBaseNanos,
                    customArtNanos,
                    itemArtNanos,
                    textCostNanos,
                    textNameNanos,
                    textTypeNanos,
                    textDescriptionNanos);
        }

        private static <T> Map<String, T> boundedMap() {
            return new LinkedHashMap<>(32, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, T> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };
        }
    }

    private record TipLayout(List<FormattedCharSequence> titleLines, List<FormattedCharSequence> descLines, int height) {
    }

    public record TextLine(FormattedCharSequence text, int width) {
    }

    private record DescriptionLayout(List<TextLine> lines, float scale) {
    }

    private record TextContent(FormattedCharSequence name, int nameWidth, FormattedCharSequence type, int typeWidth) {
    }

    private record CardRenderPlan(DeveloperCardFace face, TextureRef artTexture, ItemStack artItem) {
    }

    public static void invalidateFileTexture(Path path) {
        if (path != null) {
            String key = path.toAbsolutePath().normalize().toString();
            FILE_TEXTURES.remove(key);
            FILE_TEXTURE_LOOKUPS.remove(key);
            ASYNC_FILE_TEXTURES.remove(key);
        }
    }

    public record BarSegments(int healthWidth, int blockX, int blockWidth) {
    }

    private CardRenderHelper() {
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, false, CardValues.original(card));
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean clipArt) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, false, CardValues.original(card), clipArt);
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean clipArt, String contentKey) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, false, CardValues.original(card), clipArt, null, true, true, contentKey);
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean clipArt, DeveloperData data) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, false, CardValues.original(card), clipArt, data);
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, int attackValue, boolean modifiedAttack, boolean unaffordable) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, unaffordable, new CardValues(attackValue, card.selfEffectAmount(CardEffectKind.BLOCK)));
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, int attackValue, boolean modifiedAttack, boolean unaffordable, boolean clipArt) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, unaffordable, new CardValues(attackValue, card.selfEffectAmount(CardEffectKind.BLOCK)), clipArt);
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, CardValues values, boolean unaffordable, boolean clipArt) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, unaffordable, values, clipArt);
    }

    public static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, CardValues values, boolean unaffordable, boolean clipArt, String contentKey) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, unaffordable, values, clipArt, null, true, true, contentKey);
    }

    public static void renderDetailedCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, CardValues values, boolean unaffordable, boolean clipArt) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, unaffordable, values, clipArt);
    }

    public static void renderDetailedCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, CardValues values, boolean unaffordable, boolean clipArt, String contentKey) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, unaffordable, values, clipArt, null, true, true, contentKey);
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable) {
        renderCard(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, selected, false, unaffordable, CardValues.original(card));
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable, boolean clipArt) {
        renderSmallCard(graphics, font, card, x, y, selected, unaffordable, CardValues.original(card), clipArt);
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable, boolean clipArt, DeveloperData data) {
        renderCard(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, selected, false, unaffordable, CardValues.original(card), clipArt, data);
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable, CardValues values, boolean clipArt) {
        renderCard(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, selected, false, unaffordable, values, clipArt);
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable, CardValues values, boolean clipArt, boolean showDescription) {
        renderCard(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, selected, false, unaffordable, values, clipArt, null, showDescription);
    }

    public static void renderSmallCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, boolean unaffordable, CardValues values, boolean clipArt, boolean showDescription, String contentKey) {
        renderCard(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, selected, false, unaffordable, values, clipArt, null, showDescription, true, contentKey);
    }

    public static boolean renderSmallCardBaseAndArt(GuiGraphics graphics, CardInstance card, int x, int y, boolean clipArt) {
        return renderSmallCardBaseAndArt(graphics, card, x, y, clipArt, null);
    }

    public static boolean renderSmallCardBaseAndArt(GuiGraphics graphics, CardInstance card, int x, int y, boolean clipArt, String contentKey) {
        return renderCardBaseAndArt(graphics, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, clipArt, null, true, false, contentKey);
    }

    public static void renderSmallCardBaseOnly(GuiGraphics graphics, CardInstance card, int x, int y) {
        renderCardBase(graphics, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, null);
    }

    public static boolean renderSmallCardArtOnly(GuiGraphics graphics, CardInstance card, int x, int y, boolean clipArt) {
        return renderCardArt(graphics, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, clipArt, null, true, false);
    }

    public static CardLocalArea smallArtArea(CardInstance card) {
        return artArea(card, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, null);
    }

    public static void renderSmallCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean unaffordable, CardValues values, boolean showDescription) {
        renderSmallCardText(graphics, font, card, x, y, unaffordable, values, SmallCardTextVisibility.all(showDescription));
    }

    public static void renderSmallCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean unaffordable, CardValues values, SmallCardTextVisibility visibility) {
        renderSmallCardText(graphics, font, card, x, y, unaffordable, values, visibility, null);
    }

    public static void renderSmallCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean unaffordable, CardValues values, SmallCardTextVisibility visibility, String contentKey) {
        if (!visibility.any()) {
            return;
        }
        renderCardText(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, false, unaffordable, values, null, visibility.cost(), visibility.name(), visibility.type(), visibility.description(), contentKey);
    }

    public static void renderGridCardFast(GuiGraphics graphics, Font font, CardInstance card, int x, int y, boolean selected, CardValues values) {
        renderCard(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, selected, true, false, values, true, null, true, true);
    }

    public static boolean renderGridCardBaseAndArt(GuiGraphics graphics, CardInstance card, int x, int y) {
        return renderGridCardBaseAndArt(graphics, card, x, y, null);
    }

    public static boolean renderGridCardBaseAndArt(GuiGraphics graphics, CardInstance card, int x, int y, String contentKey) {
        return renderGridCardBaseAndArt(graphics, card, x, y, true, contentKey);
    }

    public static boolean renderGridCardBaseAndArt(GuiGraphics graphics, CardInstance card, int x, int y, boolean clipItemArt, String contentKey) {
        return renderCardBaseAndArt(graphics, card, x, y, CARD_WIDTH, CARD_HEIGHT, true, true, null, true, false, contentKey);
    }

    public static void renderGridCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, CardValues values) {
        renderGridCardText(graphics, font, card, x, y, values, true);
    }

    public static void renderGridCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, CardValues values, boolean showDescription) {
        renderGridCardText(graphics, font, card, x, y, values, showDescription, null);
    }

    public static void renderGridCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, CardValues values, boolean showDescription, String contentKey) {
        renderGridCardText(graphics, font, card, x, y, values, new SmallCardTextVisibility(true, true, true, showDescription), contentKey);
    }

    public static void renderGridCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, CardValues values, SmallCardTextVisibility visibility, String contentKey) {
        renderCardText(graphics, font, card, x, y, CARD_WIDTH, CARD_HEIGHT, true, false, values, null, visibility.cost(), visibility.name(), visibility.type(), visibility.description(), false, contentKey);
    }

    public static CardLocalArea gridCardDescriptionArea(CardInstance card) {
        return descriptionArea(card, CARD_WIDTH, CARD_HEIGHT, null);
    }

    public static void clearBatchedItemArtDepth(GuiGraphics graphics) {
        graphics.flush();
        clearItemDepth();
    }

    public static void clearBatchedItemArtDepth() {
        clearItemDepth();
    }

    public static int previewAttack(CardInstance card, int attackerSpeed, int defenderSpeed, int defenderBlock) {
        return previewAttack(card, attackerSpeed, defenderSpeed, defenderBlock, 0);
    }

    public static int previewAttack(CardInstance card, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard) {
        return previewAttack(card, attackerSpeed, defenderSpeed, defenderBlock, defenderGuard, 0, false);
    }

    public static int previewAttack(CardInstance card, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard, int attackerStrength, boolean attackerWeak) {
        int incoming = card.effects().stream()
                .filter(effect -> (effect.kind() == CardEffectKind.DAMAGE || effect.kind() == CardEffectKind.CONSUME_ARROW) && effect.target().targetsEnemy())
                .mapToInt(effect -> previewDamageAmount(effect.amount(), attackerSpeed, defenderSpeed, defenderBlock, defenderGuard, attackerStrength, attackerWeak, card.hasEffect(CardEffectKind.REMOTE), false) * effect.count())
                .sum();
        return Math.max(0, incoming);
    }

    public static int previewDamageAmount(int amount, int attackerSpeed, int defenderSpeed) {
        return previewDamageAmount(amount, attackerSpeed, defenderSpeed, 0, 0);
    }

    public static int previewDamageAmount(int amount, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard) {
        return previewDamageAmount(amount, attackerSpeed, defenderSpeed, defenderBlock, defenderGuard, 0, false);
    }

    public static int previewDamageAmount(int amount, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard, int attackerStrength, boolean attackerWeak) {
        return previewDamageAmount(amount, attackerSpeed, defenderSpeed, defenderBlock, defenderGuard, attackerStrength, attackerWeak, false, false);
    }

    public static int previewDamageAmount(int amount, int attackerSpeed, int defenderSpeed, int defenderBlock, int defenderGuard, int attackerStrength, boolean attackerWeak, boolean ignoreSpeed, boolean glowingTarget) {
        return BattleDamageCalculator.directDamage(Math.max(0, amount + attackerStrength), attackerSpeed, defenderSpeed, defenderBlock, defenderGuard, attackerWeak, ignoreSpeed, glowingTarget);
    }

    public static List<Component> descriptionLines(CardInstance card, int attackValue, boolean modifiedAttack) {
        return descriptionLines(card, new CardValues(attackValue, card.selfEffectAmount(CardEffectKind.BLOCK)));
    }

    public static List<Component> descriptionLines(CardInstance card, CardValues values) {
        return buildDescriptionLines(card, values);
    }

    private static List<Component> buildDescriptionLines(CardInstance card, CardValues values) {
        List<Component> lines = new ArrayList<>();
        if (MoonSpireCardRegistry.SELF_DESTRUCT_VIEW_CARD_ID.equals(card.cardId())) {
            addEffectLine(lines, Component.translatable("card.moonspire.effect.self_destruct", CardBalance.SELF_DESTRUCT_DAMAGE));
            return List.copyOf(lines);
        }
        for (int i = 0; i < card.effects().size(); i++) {
            CardEffect effect = card.effects().get(i);
            if (effect.kind() == CardEffectKind.DAMAGE) {
                addEffectLine(lines, Component.translatable(damageDescriptionKey(effect.target()), statNumber(effect.amount(), displayedDamageAmount(effect, values, i))), effect.count());
            } else if (effect.kind() == CardEffectKind.REMOTE) {
                addEffectLine(lines, keyword(Component.translatable("keyword.moonspire.remote.name")));
            } else if (effect.kind() == CardEffectKind.CONSUME_ARROW) {
                addEffectLine(lines, Component.translatable("card.moonspire.effect.consume_arrow", keyword(Component.translatable("keyword.moonspire.arrow.name")), statNumber(effect.amount(), displayedDamageAmount(effect, values, i))));
            } else if (effect.kind() == CardEffectKind.ARROW) {
                continue;
            } else if (effect.kind() == CardEffectKind.HEAL) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "heal", effect.target()), effect.amount()), effect.count());
            } else if (effect.kind() == CardEffectKind.BLOCK) {
                addEffectLine(lines, Component.translatable(blockDescriptionKey(effect.target()), statNumber(effect.amount(), displayedBlockAmount(effect, values, i)), keyword(Component.translatable("keyword.moonspire.block.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.BLEED) {
                addEffectLine(lines, Component.translatable(bleedDescriptionKey(effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.bleed.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.GLOWING) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "glowing", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.glowing.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.GUARD) {
                addEffectLine(lines, Component.translatable(guardDescriptionKey(effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.guard.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.STRENGTH) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "strength", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.strength.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.LOSE_STRENGTH) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "lose_strength", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.strength.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.REGENERATION) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "regeneration", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.regeneration.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.HASTE) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "haste", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.haste.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.POISON) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "poison", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.poison.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.BURN) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "burn", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.burn.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.WITHER) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "wither", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.wither.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.FUSE) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "fuse", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.fuse.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.WEAKNESS) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "weakness", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.weakness.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.SLOWNESS) {
                addEffectLine(lines, Component.translatable(effectDescriptionKey(effect.kind(), "slowness", effect.target()), effect.amount(), keyword(Component.translatable("effect.moonspire.slowness.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.DRAW_CARDS) {
                addEffectLine(lines, Component.translatable(keywordDescriptionKey("draw_cards", effect.target()), effect.amount()), effect.count());
            } else if (effect.kind() == CardEffectKind.GAIN_ENERGY) {
                addEffectLine(lines, Component.translatable(keywordDescriptionKey("gain_energy", effect.target()), effect.amount(), keyword(Component.translatable("keyword.moonspire.energy.name"))), effect.count());
            } else if (effect.kind() == CardEffectKind.EXHAUST) {
                addEffectLine(lines, keyword(Component.translatable("keyword.moonspire.exhaust.name")));
            } else if (effect.kind() == CardEffectKind.INNATE) {
                addEffectLine(lines, keyword(Component.translatable("keyword.moonspire.innate.name")));
            } else if (effect.kind() == CardEffectKind.RETAIN) {
                addEffectLine(lines, keyword(Component.translatable("keyword.moonspire.retain.name")));
            } else if (effect.kind() == CardEffectKind.ETHEREAL) {
                addEffectLine(lines, keyword(Component.translatable("keyword.moonspire.ethereal.name")));
            } else if (effect.kind() == CardEffectKind.RETAIN_REDUCE_COST) {
                addEffectLine(lines, Component.translatable("card.moonspire.effect.retain_reduce_cost", keyword(Component.translatable("keyword.moonspire.retain.name")), effect.amount()));
            } else if (effect.kind() == CardEffectKind.EXHAUST_HAND) {
                addEffectLine(lines, Component.translatable(handSelectionDescriptionKey("exhaust_hand", effect.target()), effect.amount()));
            } else if (effect.kind() == CardEffectKind.DISCARD_HAND) {
                addEffectLine(lines, Component.translatable(handSelectionDescriptionKey("discard_hand", effect.target()), effect.amount()));
            }
        }
        return List.copyOf(lines);
    }

    private static int displayedDamageAmount(CardEffect effect, CardValues values, int effectIndex) {
        return values.damageAmount(effectIndex, effect.amount());
    }

    private static int displayedBlockAmount(CardEffect effect, CardValues values, int effectIndex) {
        return values.blockAmount(effectIndex, effect.amount());
    }

    private static void addEffectLine(List<Component> lines, Component line) {
        addEffectLine(lines, line, 1);
    }

    private static void addEffectLine(List<Component> lines, Component line, int count) {
        lines.add(withEffectCount(line, count));
    }

    private static Component withEffectCount(Component line, int count) {
        if (count <= 1) {
            return Component.translatable("card.moonspire.effect.period_suffix", line);
        }
        return Component.translatable("card.moonspire.effect.count_suffix", line, count);
    }

    private static String damageDescriptionKey(CardTarget target) {
        return switch (target) {
            case SELF -> "card.moonspire.effect.damage.self";
            case SINGLE_ALLY -> "card.moonspire.effect.damage.single_ally";
            case ALL_ENEMIES -> "card.moonspire.effect.damage.all_enemies";
            case ALL_ALLIES -> "card.moonspire.effect.damage.all_allies";
            case ALL_UNITS -> "card.moonspire.effect.damage.all_units";
            case ALL_OTHER_UNITS -> "card.moonspire.effect.damage.all_other_units";
            case ALL_OTHER_ALLIES -> "card.moonspire.effect.damage.all_other_allies";
            case RANDOM_ENEMY -> "card.moonspire.effect.damage.random_enemy";
            case RANDOM_ALLY -> "card.moonspire.effect.damage.random_ally";
            case SINGLE_ENEMY -> "card.moonspire.effect.damage";
        };
    }

    private static String blockDescriptionKey(CardTarget target) {
        return switch (target) {
            case SELF -> "card.moonspire.effect.block";
            case SINGLE_ALLY -> "card.moonspire.effect.block.single_ally";
            case ALL_ENEMIES -> "card.moonspire.effect.block.all_enemies";
            case ALL_ALLIES -> "card.moonspire.effect.block.all_allies";
            case ALL_UNITS -> "card.moonspire.effect.block.all_units";
            case ALL_OTHER_UNITS -> "card.moonspire.effect.block.all_other_units";
            case ALL_OTHER_ALLIES -> "card.moonspire.effect.block.all_other_allies";
            case RANDOM_ENEMY -> "card.moonspire.effect.block.random_enemy";
            case RANDOM_ALLY -> "card.moonspire.effect.block.random_ally";
            case SINGLE_ENEMY -> "card.moonspire.effect.block.give";
        };
    }

    private static String bleedDescriptionKey(CardTarget target) {
        return switch (target) {
            case SELF -> "card.moonspire.effect.bleed.self";
            case SINGLE_ALLY -> "card.moonspire.effect.bleed.single_ally";
            case ALL_ENEMIES -> "card.moonspire.effect.bleed.all_enemies";
            case ALL_ALLIES -> "card.moonspire.effect.bleed.all_allies";
            case ALL_UNITS -> "card.moonspire.effect.bleed.all_units";
            case ALL_OTHER_UNITS -> "card.moonspire.effect.bleed.all_other_units";
            case ALL_OTHER_ALLIES -> "card.moonspire.effect.bleed.all_other_allies";
            case RANDOM_ENEMY -> "card.moonspire.effect.bleed.random_enemy";
            case RANDOM_ALLY -> "card.moonspire.effect.bleed.random_ally";
            case SINGLE_ENEMY -> "card.moonspire.effect.bleed";
        };
    }

    private static String guardDescriptionKey(CardTarget target) {
        return switch (target) {
            case SELF -> "card.moonspire.effect.guard";
            case SINGLE_ALLY -> "card.moonspire.effect.guard.single_ally";
            case ALL_ENEMIES -> "card.moonspire.effect.guard.all_enemies";
            case ALL_ALLIES -> "card.moonspire.effect.guard.all_allies";
            case ALL_UNITS -> "card.moonspire.effect.guard.all_units";
            case ALL_OTHER_UNITS -> "card.moonspire.effect.guard.all_other_units";
            case ALL_OTHER_ALLIES -> "card.moonspire.effect.guard.all_other_allies";
            case RANDOM_ENEMY -> "card.moonspire.effect.guard.random_enemy";
            case RANDOM_ALLY -> "card.moonspire.effect.guard.random_ally";
            case SINGLE_ENEMY -> "card.moonspire.effect.guard.give";
        };
    }

    private static String handSelectionDescriptionKey(String effect, CardTarget target) {
        return effectDescriptionKey(effect, target);
    }

    private static String keywordDescriptionKey(String effect, CardTarget target) {
        return "card.moonspire.effect." + effect + (target == CardTarget.SELF ? "" : ".other");
    }

    private static String effectDescriptionKey(String effect, CardTarget target) {
        return "card.moonspire.effect." + effect + switch (target) {
            case SELF -> "";
            case SINGLE_ENEMY -> ".single_enemy";
            case SINGLE_ALLY -> ".single_ally";
            case ALL_ENEMIES -> ".all_enemies";
            case ALL_ALLIES -> ".all_allies";
            case ALL_UNITS -> ".all_units";
            case ALL_OTHER_UNITS -> ".all_other_units";
            case ALL_OTHER_ALLIES -> ".all_other_allies";
            case RANDOM_ENEMY -> ".random_enemy";
            case RANDOM_ALLY -> ".random_ally";
        };
    }

    private static String effectDescriptionKey(CardEffectKind kind, String effect, CardTarget target) {
        if (target == kind.defaultTarget()) {
            return "card.moonspire.effect." + effect;
        }
        return effectDescriptionKey(effect, target);
    }

    public static void renderCombatantBar(GuiGraphics graphics, Font font, BattleCombatantSnapshot entry, int x, int y, int width, int height) {
        MoonSpireUiTextures.drawHealthBar(graphics, x, y, width, height);
        int innerX = x + 2;
        int innerY = y + 2;
        int innerW = Math.max(0, width - 4);
        int innerH = Math.max(1, height - 4);
        BarSegments segments = combatantBarSegments(entry, innerW);
        renderBarFill(graphics, MoonSpireUiTextures.HEALTH_FILL, innerX, innerY, segments.healthWidth(), innerH);
        if (entry.defense() > 0) {
            renderBarFill(graphics, MoonSpireUiTextures.BLOCK_FILL, innerX + segments.blockX(), innerY, segments.blockWidth(), innerH);
        }
        Component value = entry.defense() > 0
                ? Component.translatable("screen.moonspire.health_block_value", Math.round(entry.health()), entry.defense(), Math.round(entry.maxHealth()))
                : Component.translatable("screen.moonspire.health_value", Math.round(entry.health()), Math.round(entry.maxHealth()));
        graphics.drawCenteredString(font, value, x + width / 2, y + 2, 0xFFFFFFFF);
    }

    public static int previewAttack(CardInstance card, BattleCombatantSnapshot attacker, BattleCombatantSnapshot defender) {
        int incoming = card.effects().stream()
                .filter(effect -> (effect.kind() == CardEffectKind.DAMAGE || effect.kind() == CardEffectKind.CONSUME_ARROW) && effect.target().targetsEnemy())
                .mapToInt(effect -> previewDamageAmount(effect.amount(), attacker.roundSpeed(), defender.roundSpeed(), defender.defense(), effectAmount(defender, BattleEffectType.GUARD), effectAmount(attacker, BattleEffectType.STRENGTH), effectAmount(attacker, BattleEffectType.WEAKNESS) > 0, card.hasEffect(CardEffectKind.REMOTE), effectAmount(defender, BattleEffectType.GLOWING) > 0) * effect.count())
                .sum();
        return Math.max(0, incoming);
    }

    public static CardLocalArea smallDescriptionArea(CardInstance card) {
        return descriptionArea(card, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, null);
    }

    public static CardLocalArea smallDescriptionTextArea(Font font, CardInstance card, CardValues values) {
        return descriptionTextArea(font, card, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, false, values, null, null);
    }

    public static CardLocalArea smallDescriptionTextArea(Font font, CardInstance card, CardValues values, String contentKey) {
        return descriptionTextArea(font, card, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, false, values, null, contentKey);
    }

    public static CardLocalArea smallCostArea(CardInstance card) {
        return costArea(card, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, null);
    }

    public static CardLocalArea smallNameArea(CardInstance card) {
        return nameArea(card, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, null);
    }

    public static CardLocalArea smallTypeArea(CardInstance card) {
        return typeArea(card, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, null);
    }

    public static CardLocalArea gridCostArea(CardInstance card) {
        return costArea(card, CARD_WIDTH, CARD_HEIGHT, null);
    }

    public static CardLocalArea gridNameArea(CardInstance card) {
        return nameArea(card, CARD_WIDTH, CARD_HEIGHT, null);
    }

    public static CardLocalArea gridTypeArea(CardInstance card) {
        return typeArea(card, CARD_WIDTH, CARD_HEIGHT, null);
    }

    public static void renderSmallCardDescription(GuiGraphics graphics, Font font, CardInstance card, int x, int y, CardValues values) {
        renderCardDescription(graphics, font, card, x, y, SMALL_CARD_WIDTH, SMALL_CARD_HEIGHT, false, values, null, false);
    }

    public static BarSegments combatantBarSegments(BattleCombatantSnapshot entry, int width) {
        if (width <= 0 || entry.maxHealth() <= 0.0F) {
            return new BarSegments(0, 0, 0);
        }
        float health = Math.max(0.0F, Math.min(entry.health(), entry.maxHealth()));
        int block = Math.max(0, entry.defense());
        float effectiveMax = Math.max(1.0F, entry.maxHealth() + block);
        int healthWidth = Math.min(width, Math.max(0, Math.round(width * (health / effectiveMax))));
        int blockWidth = block <= 0 ? 0 : Math.round(width * (block / effectiveMax));
        blockWidth = Math.min(Math.max(0, width - healthWidth), Math.max(blockWidth, healthWidth < width ? 1 : 0));
        return new BarSegments(healthWidth, healthWidth, blockWidth);
    }

    public static void renderKeywordTips(GuiGraphics graphics, Font font, CardInstance card, int x, int y) {
        int tipY = y;
        if (card.hasSelfEffect(CardEffectKind.BLOCK)) {
            tipY = renderTip(graphics, font, Component.translatable("keyword.moonspire.block.name"), Component.translatable("keyword.moonspire.block.description"), x, tipY);
        }
        Set<String> renderedTips = new HashSet<>();
        for (CardEffect effect : card.effects()) {
            if (effect.kind() == CardEffectKind.BLEED && renderedTips.add("bleed")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.bleed.name"), Component.translatable("effect.moonspire.bleed.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.GLOWING && renderedTips.add("glowing")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.glowing.name"), Component.translatable("effect.moonspire.glowing.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.GUARD && renderedTips.add("guard")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.guard.name"), Component.translatable("effect.moonspire.guard.description"), x, tipY);
            } else if ((effect.kind() == CardEffectKind.STRENGTH || effect.kind() == CardEffectKind.LOSE_STRENGTH) && renderedTips.add("strength")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.strength.name"), Component.translatable("effect.moonspire.strength.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.REGENERATION && renderedTips.add("regeneration")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.regeneration.name"), Component.translatable("effect.moonspire.regeneration.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.HASTE && renderedTips.add("haste")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.haste.name"), Component.translatable("effect.moonspire.haste.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.POISON && renderedTips.add("poison")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.poison.name"), Component.translatable("effect.moonspire.poison.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.BURN && renderedTips.add("burn")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.burn.name"), Component.translatable("effect.moonspire.burn.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.WITHER && renderedTips.add("wither")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.wither.name"), Component.translatable("effect.moonspire.wither.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.FUSE && renderedTips.add("fuse")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.fuse.name"), Component.translatable("effect.moonspire.fuse.description", CardBalance.SELF_DESTRUCT_DAMAGE), x, tipY);
            } else if (effect.kind() == CardEffectKind.WEAKNESS && renderedTips.add("weakness")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.weakness.name"), Component.translatable("effect.moonspire.weakness.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.SLOWNESS && renderedTips.add("slowness")) {
                tipY = renderTip(graphics, font, Component.translatable("effect.moonspire.slowness.name"), Component.translatable("effect.moonspire.slowness.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.GAIN_ENERGY && renderedTips.add("energy")) {
                tipY = renderTip(graphics, font, Component.translatable("keyword.moonspire.energy.name"), Component.translatable("keyword.moonspire.energy.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.EXHAUST && renderedTips.add("exhaust")) {
                tipY = renderTip(graphics, font, Component.translatable("keyword.moonspire.exhaust.name"), Component.translatable("keyword.moonspire.exhaust.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.REMOTE && renderedTips.add("remote")) {
                tipY = renderTip(graphics, font, Component.translatable("keyword.moonspire.remote.name"), Component.translatable("keyword.moonspire.remote.description"), x, tipY);
            } else if ((effect.kind() == CardEffectKind.ARROW || effect.kind() == CardEffectKind.CONSUME_ARROW) && renderedTips.add("arrow")) {
                tipY = renderTip(graphics, font, Component.translatable("keyword.moonspire.arrow.name"), Component.translatable("keyword.moonspire.arrow.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.INNATE && renderedTips.add("innate")) {
                tipY = renderTip(graphics, font, Component.translatable("keyword.moonspire.innate.name"), Component.translatable("keyword.moonspire.innate.description"), x, tipY);
            } else if ((effect.kind() == CardEffectKind.RETAIN || effect.kind() == CardEffectKind.RETAIN_REDUCE_COST) && renderedTips.add("retain")) {
                tipY = renderTip(graphics, font, Component.translatable("keyword.moonspire.retain.name"), Component.translatable("keyword.moonspire.retain.description"), x, tipY);
            } else if (effect.kind() == CardEffectKind.ETHEREAL && renderedTips.add("ethereal")) {
                tipY = renderTip(graphics, font, Component.translatable("keyword.moonspire.ethereal.name"), Component.translatable("keyword.moonspire.ethereal.description"), x, tipY);
            }
        }
    }

    public static void renderKeywordTipsBeside(GuiGraphics graphics, Font font, CardInstance card, int cardX, int cardY, int screenW) {
        int x = keywordTipsXBeside(cardX, CARD_WIDTH, screenW);
        renderKeywordTips(graphics, font, card, x, cardY);
    }

    public static void renderKeywordTipsBeside(GuiGraphics graphics, Font font, CardInstance card, int cardX, int cardY, int screenW, int screenH) {
        renderKeywordTipsBeside(graphics, font, card, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, screenW, screenH);
    }

    public static void renderKeywordTipsBeside(GuiGraphics graphics, Font font, CardInstance card, int cardX, int cardY, int cardW, int cardH, int screenW, int screenH) {
        int x = keywordTipsXBeside(cardX, cardW, screenW);
        int totalHeight = keywordTipsHeight(font, card);
        int y = cardY + (cardH - totalHeight) / 2;
        y = Math.max(6, Math.min(screenH - totalHeight - 6, y));
        renderKeywordTips(graphics, font, card, x, y);
    }

    private static int keywordTipsXBeside(int cardX, int cardW, int screenW) {
        int rightX = cardX + cardW + 6;
        int preferredX = rightX + TIP_WIDTH <= screenW - 6 ? rightX : cardX - TIP_WIDTH - 6;
        int maxX = Math.max(6, screenW - TIP_WIDTH - 6);
        return Math.max(6, Math.min(maxX, preferredX));
    }

    public static int renderEffectTip(GuiGraphics graphics, Font font, BattleEffectType type, int amount, int x, int y) {
        Object value = effectTipValue(type, amount);
        String descriptionKey = type == BattleEffectType.STRENGTH && amount < 0 ? "effect.moonspire.strength.negative_active_description" : type.activeDescriptionKey();
        return renderTip(graphics, font, Component.translatable("screen.moonspire.effect_tip_title", Component.translatable(type.nameKey()), amount), Component.translatable(descriptionKey, value), x, y);
    }

    private static Object effectTipValue(BattleEffectType type, int amount) {
        if (type == BattleEffectType.GUARD) {
            return BattleDamageCalculator.guardReductionPercent(amount);
        }
        if (type == BattleEffectType.STRENGTH && amount < 0) {
            return -amount;
        }
        if (type == BattleEffectType.FUSE) {
            return CardBalance.SELF_DESTRUCT_DAMAGE;
        }
        return amount;
    }

    public static int effectAmount(BattleCombatantSnapshot snapshot, BattleEffectType type) {
        for (BattleEffectSnapshot effect : snapshot.effects()) {
            if (effect.type() == type) {
                return effect.amount();
            }
        }
        return 0;
    }

    private static int keywordTipsHeight(Font font, CardInstance card) {
        int height = 0;
        if (card.hasSelfEffect(CardEffectKind.BLOCK)) {
            height += tipHeight(font, Component.translatable("keyword.moonspire.block.name"), Component.translatable("keyword.moonspire.block.description")) + 4;
        }
        Set<String> renderedTips = new HashSet<>();
        for (CardEffect effect : card.effects()) {
            if (effect.kind() == CardEffectKind.BLEED && renderedTips.add("bleed")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.bleed.name"), Component.translatable("effect.moonspire.bleed.description")) + 4;
            } else if (effect.kind() == CardEffectKind.GLOWING && renderedTips.add("glowing")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.glowing.name"), Component.translatable("effect.moonspire.glowing.description")) + 4;
            } else if (effect.kind() == CardEffectKind.GUARD && renderedTips.add("guard")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.guard.name"), Component.translatable("effect.moonspire.guard.description")) + 4;
            } else if ((effect.kind() == CardEffectKind.STRENGTH || effect.kind() == CardEffectKind.LOSE_STRENGTH) && renderedTips.add("strength")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.strength.name"), Component.translatable("effect.moonspire.strength.description")) + 4;
            } else if (effect.kind() == CardEffectKind.REGENERATION && renderedTips.add("regeneration")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.regeneration.name"), Component.translatable("effect.moonspire.regeneration.description")) + 4;
            } else if (effect.kind() == CardEffectKind.HASTE && renderedTips.add("haste")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.haste.name"), Component.translatable("effect.moonspire.haste.description")) + 4;
            } else if (effect.kind() == CardEffectKind.POISON && renderedTips.add("poison")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.poison.name"), Component.translatable("effect.moonspire.poison.description")) + 4;
            } else if (effect.kind() == CardEffectKind.BURN && renderedTips.add("burn")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.burn.name"), Component.translatable("effect.moonspire.burn.description")) + 4;
            } else if (effect.kind() == CardEffectKind.WITHER && renderedTips.add("wither")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.wither.name"), Component.translatable("effect.moonspire.wither.description")) + 4;
            } else if (effect.kind() == CardEffectKind.FUSE && renderedTips.add("fuse")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.fuse.name"), Component.translatable("effect.moonspire.fuse.description", CardBalance.SELF_DESTRUCT_DAMAGE)) + 4;
            } else if (effect.kind() == CardEffectKind.WEAKNESS && renderedTips.add("weakness")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.weakness.name"), Component.translatable("effect.moonspire.weakness.description")) + 4;
            } else if (effect.kind() == CardEffectKind.SLOWNESS && renderedTips.add("slowness")) {
                height += tipHeight(font, Component.translatable("effect.moonspire.slowness.name"), Component.translatable("effect.moonspire.slowness.description")) + 4;
            } else if (effect.kind() == CardEffectKind.GAIN_ENERGY && renderedTips.add("energy")) {
                height += tipHeight(font, Component.translatable("keyword.moonspire.energy.name"), Component.translatable("keyword.moonspire.energy.description")) + 4;
            } else if (effect.kind() == CardEffectKind.EXHAUST && renderedTips.add("exhaust")) {
                height += tipHeight(font, Component.translatable("keyword.moonspire.exhaust.name"), Component.translatable("keyword.moonspire.exhaust.description")) + 4;
            } else if (effect.kind() == CardEffectKind.REMOTE && renderedTips.add("remote")) {
                height += tipHeight(font, Component.translatable("keyword.moonspire.remote.name"), Component.translatable("keyword.moonspire.remote.description")) + 4;
            } else if ((effect.kind() == CardEffectKind.ARROW || effect.kind() == CardEffectKind.CONSUME_ARROW) && renderedTips.add("arrow")) {
                height += tipHeight(font, Component.translatable("keyword.moonspire.arrow.name"), Component.translatable("keyword.moonspire.arrow.description")) + 4;
            } else if (effect.kind() == CardEffectKind.INNATE && renderedTips.add("innate")) {
                height += tipHeight(font, Component.translatable("keyword.moonspire.innate.name"), Component.translatable("keyword.moonspire.innate.description")) + 4;
            } else if ((effect.kind() == CardEffectKind.RETAIN || effect.kind() == CardEffectKind.RETAIN_REDUCE_COST) && renderedTips.add("retain")) {
                height += tipHeight(font, Component.translatable("keyword.moonspire.retain.name"), Component.translatable("keyword.moonspire.retain.description")) + 4;
            } else if (effect.kind() == CardEffectKind.ETHEREAL && renderedTips.add("ethereal")) {
                height += tipHeight(font, Component.translatable("keyword.moonspire.ethereal.name"), Component.translatable("keyword.moonspire.ethereal.description")) + 4;
            }
        }
        return Math.max(0, height - 4);
    }

    private static int tipHeight(Font font, Component title, Component description) {
        return tipLayout(font, title, description).height();
    }

    public static int renderTip(GuiGraphics graphics, Font font, Component title, Component description, int x, int y) {
        int pad = 6;
        TipLayout layout = tipLayout(font, title, description);
        int height = layout.height();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        MoonSpireUiTextures.drawTooltip(graphics, x, y, TIP_WIDTH, height);
        int lineY = y + pad;
        for (FormattedCharSequence line : layout.titleLines()) {
            graphics.drawString(font, line, x + pad, lineY, 0xFF000000 | KEYWORD_TEXT_COLOR, false);
            lineY += 10;
        }
        lineY += 3;
        for (FormattedCharSequence line : layout.descLines()) {
            graphics.drawString(font, line, x + pad, lineY, 0xFFF2E6D2, false);
            lineY += 10;
        }
        graphics.pose().popPose();
        return y + height + 4;
    }

    private static TipLayout tipLayout(Font font, Component title, Component description) {
        return buildTipLayout(font, title, description);
    }

    private static TipLayout buildTipLayout(Font font, Component title, Component description) {
        int pad = 6;
        List<FormattedCharSequence> titleLines = font.split(title, TIP_WIDTH - pad * 2);
        List<FormattedCharSequence> descLines = font.split(description, TIP_WIDTH - pad * 2);
        return new TipLayout(List.copyOf(titleLines), List.copyOf(descLines), pad * 2 + titleLines.size() * 10 + 3 + descLines.size() * 10);
    }

    private static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean selected, boolean detailed, boolean unaffordable, CardValues values) {
        renderCard(graphics, font, card, x, y, width, height, selected, detailed, unaffordable, values, true);
    }

    private static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean selected, boolean detailed, boolean unaffordable, CardValues values, boolean clipArt) {
        renderCard(graphics, font, card, x, y, width, height, selected, detailed, unaffordable, values, clipArt, null);
    }

    private static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean selected, boolean detailed, boolean unaffordable, CardValues values, boolean clipArt, DeveloperData dataOverride) {
        renderCard(graphics, font, card, x, y, width, height, selected, detailed, unaffordable, values, clipArt, dataOverride, true);
    }

    private static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean selected, boolean detailed, boolean unaffordable, CardValues values, boolean clipArt, DeveloperData dataOverride, boolean showDescription) {
        renderCard(graphics, font, card, x, y, width, height, selected, detailed, unaffordable, values, clipArt, dataOverride, showDescription, true);
    }

    private static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean selected, boolean detailed, boolean unaffordable, CardValues values, boolean clipArt, DeveloperData dataOverride, boolean showDescription, boolean renderItemArt) {
        renderCard(graphics, font, card, x, y, width, height, selected, detailed, unaffordable, values, clipArt, dataOverride, showDescription, renderItemArt, null);
    }

    private static void renderCard(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean selected, boolean detailed, boolean unaffordable, CardValues values, boolean clipArt, DeveloperData dataOverride, boolean showDescription, boolean renderItemArt, String contentKey) {
        renderCardBaseAndArt(graphics, card, x, y, width, height, clipArt, dataOverride, renderItemArt, true, contentKey);
        renderCardText(graphics, font, card, x, y, width, height, detailed, unaffordable, values, dataOverride, showDescription, contentKey);
        graphics.flush();
    }

    private static boolean renderCardBaseAndArt(GuiGraphics graphics, CardInstance card, int x, int y, int width, int height, boolean clipArt, DeveloperData dataOverride, boolean renderItemArt, boolean clearItemDepth) {
        return renderCardBaseAndArt(graphics, card, x, y, width, height, clipArt, dataOverride, renderItemArt, clearItemDepth, null);
    }

    private static boolean renderCardBaseAndArt(GuiGraphics graphics, CardInstance card, int x, int y, int width, int height, boolean clipArt, DeveloperData dataOverride, boolean renderItemArt, boolean clearItemDepth, String contentKey) {
        return renderCardBaseAndArt(graphics, card, x, y, width, height, clipArt, clipArt, dataOverride, renderItemArt, clearItemDepth, contentKey);
    }

    private static boolean renderCardBaseAndArt(GuiGraphics graphics, CardInstance card, int x, int y, int width, int height, boolean clipItemArt, boolean clipCustomArt, DeveloperData dataOverride, boolean renderItemArt, boolean clearItemDepth, String contentKey) {
        CardRenderPlan plan = renderPlan(card, dataOverride, contentKey);
        renderCardBase(graphics, card, x, y, width, height, plan);
        return renderCardArt(graphics, card, x, y, width, height, clipItemArt, clipCustomArt, plan, renderItemArt, clearItemDepth);
    }

    private static void renderCardBase(GuiGraphics graphics, CardInstance card, int x, int y, int width, int height, CardRenderPlan plan) {
        CardRenderPlan effectivePlan = plan == null ? renderPlan(card, null, null) : plan;
        renderCardFaceBase(graphics, effectivePlan.face().imagePath(), x, y, width, height);
    }

    private static boolean renderCardArt(GuiGraphics graphics, CardInstance card, int x, int y, int width, int height, boolean clipItemArt, boolean clipCustomArt, CardRenderPlan plan, boolean renderItemArt, boolean clearItemDepth) {
        CardRenderPlan effectivePlan = plan == null ? renderPlan(card, null, null) : plan;
        DeveloperCardFace face = effectivePlan.face();
        DeveloperCardFace.Area artArea = face.artArea();
        int artX = x + sx(width, artArea.x());
        int artY = y + sy(height, artArea.y());
        int artWidth = sx(width, artArea.width());
        int artHeight = sy(height, artArea.height());
        boolean clipItemToArtArea = true;
        boolean clipCustomToArtArea = true;
        TextureRef artTexture = effectivePlan.artTexture();
        ItemStack artItem = effectivePlan.artItem();
        boolean renderedItemArt = false;
        if (!artItem.isEmpty()) {
            if (!renderItemArt) {
                recordSkippedItemArt();
            } else {
                renderCardArtItem(graphics, artItem, artX, artY, artWidth, artHeight, card.artX(), card.artY(), card.artScale(), clipItemToArtArea, clearItemDepth);
                renderedItemArt = true;
            }
        } else if (artTexture != null) {
            renderCustomCardArt(graphics, artTexture, artX, artY, artWidth, artHeight, card.artX(), card.artY(), card.artScale(), clipCustomToArtArea);
        } else if (!card.sourceStack().isEmpty()) {
            if (!renderItemArt) {
                recordSkippedItemArt();
            } else {
                renderCardArtItem(graphics, card, artX, artY, artWidth, artHeight, clipItemToArtArea, clearItemDepth);
                renderedItemArt = true;
            }
        }
        return renderedItemArt;
    }

    private static boolean renderCardArt(GuiGraphics graphics, CardInstance card, int x, int y, int width, int height, boolean clipArt, CardRenderPlan plan, boolean renderItemArt, boolean clearItemDepth) {
        return renderCardArt(graphics, card, x, y, width, height, clipArt, clipArt, plan, renderItemArt, clearItemDepth);
    }

    private static CardLocalArea artArea(CardInstance card, int width, int height, DeveloperData dataOverride) {
        DeveloperCardFace.Area artArea = cardFace(card, dataOverride).artArea();
        return new CardLocalArea(sx(width, artArea.x()), sy(height, artArea.y()), sx(width, artArea.width()), sy(height, artArea.height()));
    }

    private static void renderCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean detailed, boolean unaffordable, CardValues values, DeveloperData dataOverride, boolean showDescription) {
        renderCardText(graphics, font, card, x, y, width, height, detailed, unaffordable, values, dataOverride, showDescription, null);
    }

    private static void renderCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean detailed, boolean unaffordable, CardValues values, DeveloperData dataOverride, boolean showDescription, String contentKey) {
        renderCardText(graphics, font, card, x, y, width, height, detailed, unaffordable, values, dataOverride, true, true, true, showDescription, contentKey);
    }

    private static void renderCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean detailed, boolean unaffordable, CardValues values, DeveloperData dataOverride, boolean showCost, boolean showName, boolean showType, boolean showDescription, String contentKey) {
        renderCardText(graphics, font, card, x, y, width, height, detailed, unaffordable, values, dataOverride, showCost, showName, showType, showDescription, true, contentKey);
    }

    private static void renderCardText(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean detailed, boolean unaffordable, CardValues values, DeveloperData dataOverride, boolean showCost, boolean showName, boolean showType, boolean showDescription, boolean clipDescription, String contentKey) {
        if (!showCost && !showName && !showType && !showDescription) {
            return;
        }
        boolean diag = MoonSpirePerfDiagnostics.enabled();
        DeveloperCardFace face = cardFace(card, dataOverride);
        String effectiveContentKey = contentKey == null ? contentKey(card) : contentKey;
        TextContent textContent = showName || showType ? (dataOverride == null ? textContent(font, card, effectiveContentKey) : uncachedTextContent(font, card)) : null;
        if (showCost) {
            DeveloperCardFace.Area costArea = face.costArea();
            long segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            drawCardCostNumber(graphics, font, Integer.toString(card.cost()), x + sx(width, costArea.x()) + sx(width, costArea.width()) / 2, y + sy(height, costArea.y()) + sy(height, costArea.height()) / 2, width, unaffordable);
            recordTextCost(segmentStart);
        }

        float nameScale = detailed ? 0.86F : 0.58F;
        if (showName) {
            DeveloperCardFace.Area nameArea = face.nameArea();
            long segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            drawCenteredFit(graphics, font, textContent.name(), textContent.nameWidth(), x + sx(width, nameArea.x()), y + sy(height, nameArea.y()), sx(width, nameArea.width()), sy(height, nameArea.height()), 0xFF3A3025, nameScale);
            recordTextName(segmentStart);
        }

        if (showType) {
            DeveloperCardFace.Area typeArea = face.typeArea();
            long segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
            drawCenteredFit(graphics, font, textContent.type(), textContent.typeWidth(), x + sx(width, typeArea.x()), y + sy(height, typeArea.y()), sx(width, typeArea.width()), sy(height, typeArea.height()), 0xFF514B45, detailed ? 0.62F : 0.48F);
            recordTextType(segmentStart);
        }

        if (!showDescription) {
            return;
        }
        long segmentStart = diag ? MoonSpirePerfDiagnostics.now() : 0L;
        renderCardDescription(graphics, font, card, x, y, width, height, detailed, values, dataOverride, clipDescription, face, effectiveContentKey);
        recordTextDescription(segmentStart);
    }

    private static CardLocalArea costArea(CardInstance card, int width, int height, DeveloperData dataOverride) {
        DeveloperCardFace.Area area = cardFace(card, dataOverride).costArea();
        return new CardLocalArea(sx(width, area.x()), sy(height, area.y()), sx(width, area.width()), sy(height, area.height()));
    }

    private static CardLocalArea nameArea(CardInstance card, int width, int height, DeveloperData dataOverride) {
        DeveloperCardFace.Area area = cardFace(card, dataOverride).nameArea();
        return new CardLocalArea(sx(width, area.x()), sy(height, area.y()), sx(width, area.width()), sy(height, area.height()));
    }

    private static CardLocalArea typeArea(CardInstance card, int width, int height, DeveloperData dataOverride) {
        DeveloperCardFace.Area area = cardFace(card, dataOverride).typeArea();
        return new CardLocalArea(sx(width, area.x()), sy(height, area.y()), sx(width, area.width()), sy(height, area.height()));
    }

    private static CardLocalArea descriptionArea(CardInstance card, int width, int height, DeveloperData dataOverride) {
        DeveloperCardFace.Area descArea = cardFace(card, dataOverride).descriptionArea();
        return new CardLocalArea(sx(width, descArea.x()), sy(height, descArea.y()), sx(width, descArea.width()), sy(height, descArea.height()));
    }

    private static CardLocalArea descriptionTextArea(Font font, CardInstance card, int width, int height, boolean detailed, CardValues values, DeveloperData dataOverride, String contentKey) {
        CardLocalArea descArea = descriptionArea(card, width, height, dataOverride);
        DescriptionLayout layout = descriptionLayout(font, card, values, descArea.width(), descArea.height(), detailed, dataOverride, contentKey);
        if (layout.lines().isEmpty()) {
            return new CardLocalArea(descArea.x() + descArea.width() / 2, descArea.y() + descArea.height() / 2, 0, 0);
        }
        int maxLineWidth = 0;
        for (TextLine line : layout.lines()) {
            maxLineWidth = Math.max(maxLineWidth, line.width());
        }
        int textWidth = Math.min(descArea.width(), Math.max(1, (int) Math.ceil(maxLineWidth * layout.scale())));
        int lineHeight = scaledLineHeight(font, layout.scale());
        int textHeight = Math.min(descArea.height(), Math.max(1, layout.lines().size() * lineHeight - 2));
        int textX = descArea.x() + (descArea.width() - textWidth) / 2;
        int textY = descArea.y() + (descArea.height() - textHeight) / 2;
        return new CardLocalArea(textX, textY, textWidth, textHeight);
    }

    private static void renderCardDescription(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean detailed, CardValues values, DeveloperData dataOverride, boolean clipDescription) {
        renderCardDescription(graphics, font, card, x, y, width, height, detailed, values, dataOverride, clipDescription, cardFace(card, dataOverride), null);
    }

    private static void renderCardDescription(GuiGraphics graphics, Font font, CardInstance card, int x, int y, int width, int height, boolean detailed, CardValues values, DeveloperData dataOverride, boolean clipDescription, DeveloperCardFace face, String contentKey) {
        DeveloperCardFace.Area faceDescArea = face.descriptionArea();
        CardLocalArea descArea = new CardLocalArea(sx(width, faceDescArea.x()), sy(height, faceDescArea.y()), sx(width, faceDescArea.width()), sy(height, faceDescArea.height()));
        int descX = x + descArea.x();
        int descY = y + descArea.y();
        int descWidth = descArea.width();
        int descHeight = descArea.height();
        DescriptionLayout layout = descriptionLayout(font, card, values, descWidth, descHeight, detailed, dataOverride, contentKey);
        if (clipDescription) {
            enablePoseScissor(graphics, descX, descY, descWidth, descHeight);
        }
        drawCenteredLines(graphics, font, layout.lines(), descX, descY, descWidth, descHeight, CARD_DESCRIPTION_TEXT_COLOR, layout.scale());
        if (clipDescription) {
            graphics.disableScissor();
        }
    }

    public static void enablePoseScissor(GuiGraphics graphics, int x, int y, int width, int height) {
        Matrix4f pose = graphics.pose().last().pose();
        Vector3f topLeft = pose.transformPosition(x, y, 0.0F, new Vector3f());
        Vector3f topRight = pose.transformPosition(x + width, y, 0.0F, new Vector3f());
        Vector3f bottomLeft = pose.transformPosition(x, y + height, 0.0F, new Vector3f());
        Vector3f bottomRight = pose.transformPosition(x + width, y + height, 0.0F, new Vector3f());
        float minX = Math.min(Math.min(topLeft.x, topRight.x), Math.min(bottomLeft.x, bottomRight.x));
        float minY = Math.min(Math.min(topLeft.y, topRight.y), Math.min(bottomLeft.y, bottomRight.y));
        float maxX = Math.max(Math.max(topLeft.x, topRight.x), Math.max(bottomLeft.x, bottomRight.x));
        float maxY = Math.max(Math.max(topLeft.y, topRight.y), Math.max(bottomLeft.y, bottomRight.y));
        graphics.enableScissor((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY));
    }

    private static DescriptionLayout descriptionLayout(Font font, CardInstance card, CardValues values, int descWidth, int descHeight, boolean detailed, DeveloperData dataOverride, String contentKey) {
        if (dataOverride == null) {
            long revision = DeveloperDataManager.cacheRevision();
            if (descriptionCacheRevision != revision) {
                DESCRIPTION_WRAP_CACHE.clear();
                DESCRIPTION_LAYOUT_CACHE.clear();
                TEXT_WIDTH_CACHE.clear();
                TEXT_CONTENT_CACHE.clear();
                CARD_RENDER_PLAN_CACHE.clear();
                descriptionCacheRevision = revision;
            }
            String baseKey = contentKey == null ? CardRenderHelper.contentKey(card) : contentKey;
            String key = baseKey + "|" + valuesKey(values) + "|" + descWidth + "|" + descHeight + "|" + detailed;
            DescriptionLayout cached = DESCRIPTION_LAYOUT_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            DescriptionLayout built = buildDescriptionLayout(font, card, values, descWidth, descHeight, detailed, dataOverride, baseKey);
            DESCRIPTION_LAYOUT_CACHE.put(key, built);
            return built;
        }
        return buildDescriptionLayout(font, card, values, descWidth, descHeight, detailed, dataOverride, contentKey);
    }

    private static DescriptionLayout buildDescriptionLayout(Font font, CardInstance card, CardValues values, int descWidth, int descHeight, boolean detailed, DeveloperData dataOverride, String contentKey) {
        float baseScale = detailed ? DETAILED_DESCRIPTION_SCALE : COMPACT_DESCRIPTION_SCALE;
        float minScale = detailed ? DETAILED_DESCRIPTION_MIN_SCALE : COMPACT_DESCRIPTION_MIN_SCALE;
        for (float scale = baseScale; scale >= minScale - 0.001F; scale -= DESCRIPTION_SCALE_STEP) {
            float normalizedScale = Math.max(minScale, scale);
            List<FormattedCharSequence> lines = descriptionLinesAtScale(font, card, values, descWidth, normalizedScale, dataOverride, contentKey);
            if (descriptionFits(font, lines, normalizedScale, descHeight)) {
                return new DescriptionLayout(widthLines(font, lines), normalizedScale);
            }
        }
        List<FormattedCharSequence> fallbackLines = descriptionLinesAtScale(font, card, values, descWidth, minScale, dataOverride, contentKey);
        int maxVisibleLines = Math.max(1, (descHeight + 2) / scaledLineHeight(font, minScale));
        if (fallbackLines.size() > maxVisibleLines) {
            fallbackLines = List.copyOf(fallbackLines.subList(0, maxVisibleLines));
        }
        return new DescriptionLayout(widthLines(font, fallbackLines), minScale);
    }

    private static boolean descriptionFits(Font font, List<FormattedCharSequence> lines, float scale, int descHeight) {
        if (lines.isEmpty()) {
            return true;
        }
        return lines.size() * scaledLineHeight(font, scale) - 2 <= descHeight;
    }

    private static List<FormattedCharSequence> descriptionLinesAtScale(Font font, CardInstance card, CardValues values, int descWidth, float scale, DeveloperData dataOverride, String contentKey) {
        int wrapWidth = Math.max(1, (int) (descWidth / Math.max(0.01F, scale)));
        return dataOverride == null
                ? wrappedDescriptionLines(font, card, values, wrapWidth, contentKey)
                : buildWrappedDescription(font, card, values, wrapWidth);
    }

    private static List<FormattedCharSequence> wrappedDescriptionLines(Font font, CardInstance card, CardValues values, int width) {
        return wrappedDescriptionLines(font, card, values, width, null);
    }

    private static List<FormattedCharSequence> wrappedDescriptionLines(Font font, CardInstance card, CardValues values, int width, String contentKey) {
        long revision = DeveloperDataManager.cacheRevision();
        if (descriptionCacheRevision != revision) {
            DESCRIPTION_WRAP_CACHE.clear();
            DESCRIPTION_LAYOUT_CACHE.clear();
            TEXT_WIDTH_CACHE.clear();
            TEXT_CONTENT_CACHE.clear();
            CARD_RENDER_PLAN_CACHE.clear();
            descriptionCacheRevision = revision;
        }
        String baseKey = contentKey == null ? CardRenderHelper.contentKey(card) : contentKey;
        String key = baseKey + "|" + valuesKey(values) + "|" + width;
        List<FormattedCharSequence> cached = DESCRIPTION_WRAP_CACHE.get(key);
        CardRenderContext context = FRAME_CONTEXT.get();
        if (cached != null) {
            if (context != null) {
                context.recordDescriptionCacheHit();
            }
            return cached;
        }
        if (context != null) {
            context.recordDescriptionCacheMiss();
        }
        List<FormattedCharSequence> built = buildWrappedDescription(font, card, values, width);
        DESCRIPTION_WRAP_CACHE.put(key, built);
        return built;
    }

    private static List<FormattedCharSequence> buildWrappedDescription(Font font, CardInstance card, CardValues values, int width) {
        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        List<Component> lines = descriptionLines(card, values);
        if (lines.isEmpty()) {
            lines = List.of(card.descriptionComponent());
        }
        for (Component line : lines) {
            for (FormattedCharSequence wrapped : font.split(line, width)) {
                wrappedLines.add(wrapped);
            }
        }
        return List.copyOf(wrappedLines);
    }

    private static List<TextLine> widthLines(Font font, List<FormattedCharSequence> lines) {
        List<TextLine> widthLines = new ArrayList<>(lines.size());
        for (FormattedCharSequence line : lines) {
            widthLines.add(new TextLine(line, font.width(line)));
        }
        return List.copyOf(widthLines);
    }

    private static int cachedTextWidth(Font font, String key, String text) {
        return TEXT_WIDTH_CACHE.computeIfAbsent(key, ignored -> font.width(text));
    }

    private static TextContent textContent(Font font, CardInstance card, String contentKey) {
        return TEXT_CONTENT_CACHE.computeIfAbsent(contentKey, ignored -> uncachedTextContent(font, card));
    }

    private static TextContent uncachedTextContent(Font font, CardInstance card) {
        String name = card.nameComponent().getString();
        String type = Component.translatable(card.isAttackType() ? "card.moonspire.type.attack" : "card.moonspire.type.skill").getString();
        FormattedCharSequence nameText = FormattedCharSequence.forward(name, Style.EMPTY);
        FormattedCharSequence typeText = FormattedCharSequence.forward(type, Style.EMPTY);
        return new TextContent(nameText, font.width(nameText), typeText, font.width(typeText));
    }

    private static DeveloperCardFace cardFace(CardInstance card) {
        return cardFace(card, null);
    }

    private static DeveloperCardFace cardFace(CardInstance card, DeveloperData dataOverride) {
        if (dataOverride == null) {
            CardRenderContext context = FRAME_CONTEXT.get();
            if (context != null) {
                return context.cardFace(card);
            }
        }
        DeveloperData data = dataOverride == null ? DeveloperDataManager.load() : dataOverride;
        return resolveCardFace(card, data);
    }

    private static CardRenderPlan renderPlan(CardInstance card, DeveloperData dataOverride, String contentKey) {
        if (dataOverride != null) {
            DeveloperCardFace face = cardFace(card, dataOverride);
            return new CardRenderPlan(face, customTexture(card.artPath(), DeveloperPaths.cardArtDirectory()), artItem(card.artItemId()));
        }
        long revision = DeveloperDataManager.cacheRevision();
        if (descriptionCacheRevision != revision) {
            DESCRIPTION_WRAP_CACHE.clear();
            DESCRIPTION_LAYOUT_CACHE.clear();
            TEXT_WIDTH_CACHE.clear();
            TEXT_CONTENT_CACHE.clear();
            CARD_RENDER_PLAN_CACHE.clear();
            descriptionCacheRevision = revision;
        }
        String key = contentKey == null ? contentKey(card) : contentKey;
        CardRenderPlan cached = CARD_RENDER_PLAN_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        DeveloperCardFace face = cardFace(card);
        CardRenderPlan built = new CardRenderPlan(face, customTexture(card.artPath(), DeveloperPaths.cardArtDirectory()), artItem(card.artItemId()));
        CARD_RENDER_PLAN_CACHE.put(key, built);
        return built;
    }

    private static DeveloperCardFace resolveCardFace(CardInstance card, DeveloperData data) {
        String faceId = currentRegisteredFaceId(card, data);
        if (faceId.isBlank()) {
            faceId = card.faceId() == null || card.faceId().isBlank() ? data.activeFaceId : card.faceId();
        }
        if (faceId == null || faceId.isBlank()) {
            faceId = "default";
        }
        String resolvedFaceId = faceId;
        return data.cardFaces.stream().filter(face -> face.id().equals(resolvedFaceId)).findFirst().orElse(DeveloperCardFace.defaultFace());
    }

    private static long dataVersionKey() {
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null) {
            return context.dataVersion;
        }
        DeveloperDataManager.cachedOrLoad();
        return DeveloperDataManager.cachedStamp();
    }

    private static String faceCacheKey(CardInstance card, long dataVersion) {
        return dataVersion + "|" + card.cardId() + "|" + nullSafe(card.faceId());
    }

    private static String cardContentKey(CardInstance card) {
        StringBuilder builder = new StringBuilder(192);
        builder.append(nullSafe(card.cardId()))
                .append('|').append(nullSafe(card.nameKey()))
                .append('|').append(nullSafe(card.descriptionKey()))
                .append('|').append(card.attack())
                .append('|').append(card.defense())
                .append('|').append(card.cost())
                .append('|').append(card.baseCost())
                .append('|').append(card.battleCostReduction())
                .append('|').append(card.sourceType().name())
                .append('|').append(nullSafe(card.developerCardId()))
                .append('|').append(nullSafe(card.artPath()))
                .append('|').append(nullSafe(card.artItemId()))
                .append('|').append(card.artX())
                .append('|').append(card.artY())
                .append('|').append(card.artScale())
                .append('|').append(nullSafe(card.faceId()));
        if (!card.sourceStack().isEmpty()) {
            builder.append('|').append(BuiltInRegistries.ITEM.getKey(card.sourceStack().getItem()));
        }
        for (CardEffect effect : card.effects()) {
            builder.append('|')
                    .append(effect.kind().name())
                    .append(':').append(effect.amount())
                    .append(':').append(effect.target().name())
                    .append(':').append(effect.count());
        }
        return builder.toString();
    }

    private static String valuesKey(CardValues values) {
        return values.attack() + "|" + values.defense() + "|" + values.damageAmounts() + "|" + values.blockAmounts();
    }

    private static String nullSafe(String value) {
        return Objects.toString(value, "");
    }

    private static String currentRegisteredFaceId(CardInstance card, DeveloperData data) {
        String registeredId = registeredCardId(card);
        if (registeredId.isBlank()) {
            return "";
        }
        String appliedFaceId = data.cards.stream()
                .filter(definition -> registeredId.equals(MoonSpireCardRegistry.registeredDeveloperId(definition.id())))
                .map(DeveloperCardDefinition::faceId)
                .filter(faceId -> faceId != null && !faceId.isBlank())
                .findFirst()
                .orElse("");
        if (!appliedFaceId.isBlank()) {
            return appliedFaceId;
        }
        return MoonSpireCardRegistry.baseCard(registeredId)
                .map(RegisteredCardDefinition::faceId)
                .filter(faceId -> faceId != null && !faceId.isBlank())
                .orElse("default");
    }

    private static String registeredCardId(CardInstance card) {
        if (card.developerCardId() != null && !card.developerCardId().isBlank()) {
            return MoonSpireCardRegistry.registeredDeveloperId(card.developerCardId());
        }
        if (card.cardId() != null && !card.cardId().isBlank()) {
            return MoonSpireCardRegistry.registeredDeveloperId(card.cardId());
        }
        return "";
    }

    public static void renderCardFaceBase(GuiGraphics graphics, String imagePath, int x, int y, int width, int height) {
        long start = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        TextureRef baseTexture = customTexture(imagePath, DeveloperPaths.cardFacesDirectory());
        try {
            graphics.blit(
                    baseTexture == null ? CARD_BASE_TEXTURE : baseTexture.location(),
                    x,
                    y,
                    width,
                    height,
                    0.0F,
                    0.0F,
                    baseTexture == null ? TEXTURE_WIDTH : baseTexture.width(),
                    baseTexture == null ? TEXTURE_HEIGHT : baseTexture.height(),
                    baseTexture == null ? TEXTURE_WIDTH : baseTexture.width(),
                    baseTexture == null ? TEXTURE_HEIGHT : baseTexture.height());
        } finally {
            recordFaceBase(start);
        }
    }

    public static void renderCostIconAndNumber(GuiGraphics graphics, Font font, int cost, int x, int y, int width, int height) {
        int iconSize = Math.max(8, Math.min(width, height));
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + (height - iconSize) / 2;
        ResourceLocation texture = cost > 0 ? MoonSpireUiTextures.COST_AVAILABLE : MoonSpireUiTextures.COST_EMPTY;
        graphics.blit(texture, iconX, iconY, iconSize, iconSize, 0.0F, 0.0F, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE);
        drawCostNumber(graphics, font, Integer.toString(cost), iconX + iconSize / 2, iconY + iconSize / 2, 1.0F);
    }

    public static void renderEnergyCostDisplay(GuiGraphics graphics, Font font, int current, int maximum, int x, int y, int width, int height) {
        int iconSize = Math.max(8, Math.min(width, height));
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + (height - iconSize) / 2;
        ResourceLocation texture = current > 0 ? MoonSpireUiTextures.COST_AVAILABLE : MoonSpireUiTextures.COST_EMPTY;
        graphics.blit(texture, iconX, iconY, iconSize, iconSize, 0.0F, 0.0F, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE, COST_ICON_TEXTURE_SIZE);
        Component ratio = Component.translatable("screen.moonspire.energy_ratio", current, maximum);
        drawCostNumber(graphics, font, ratio.getString(), iconX + iconSize / 2 + ENERGY_COST_TEXT_X_OFFSET, iconY + iconSize / 2, ENERGY_COST_SCREEN_SCALE);
    }

    public static void drawCostNumber(GuiGraphics graphics, Font font, String text, int centerX, int centerY, int iconSize) {
        drawCostNumber(graphics, font, text, centerX, centerY, 1.0F);
    }

    private static void drawCardCostNumber(GuiGraphics graphics, Font font, String text, int centerX, int centerY, int cardWidth, boolean unaffordable) {
        float inheritedScale = currentPoseScale(graphics);
        float screenScale = Math.max(0.2F, (cardWidth * inheritedScale) / CARD_COST_BASE_WIDTH);
        drawOutlinedScreenText(graphics, font, text, centerX, centerY, screenScale, unaffordable ? COST_UNAFFORDABLE_TEXT_COLOR : COST_TEXT_COLOR, COST_OUTLINE_COLOR);
    }

    public static void drawCostNumber(GuiGraphics graphics, Font font, String text, int centerX, int centerY, float screenScale) {
        drawOutlinedScreenText(graphics, font, text, centerX, centerY, screenScale, COST_TEXT_COLOR, COST_OUTLINE_COLOR);
    }

    public static void drawOutlinedScreenText(GuiGraphics graphics, Font font, Component text, int centerX, int centerY, float screenScale, int textColor, int outlineColor) {
        drawOutlinedScreenText(graphics, font, text.getString(), centerX, centerY, screenScale, textColor, outlineColor);
    }

    public static void drawOutlinedScreenText(GuiGraphics graphics, Font font, String text, int centerX, int centerY, float screenScale, int textColor, int outlineColor) {
        float inheritedScale = currentPoseScale(graphics);
        float localScale = screenScale / inheritedScale;
        float outlineOffset = 1.0F / inheritedScale;
        float yBias = COST_TEXT_VERTICAL_BIAS / inheritedScale;
        int textWidth = font.width(text);
        int textHeight = font.lineHeight;
        float textX = centerX - textWidth * localScale / 2.0F;
        float textY = centerY - textHeight * localScale / 2.0F + yBias;
        drawScaledOutlined(graphics, font, text, textX, textY, localScale, outlineOffset, textColor, outlineColor);
    }

    private static float currentPoseScale(GuiGraphics graphics) {
        Matrix4f pose = graphics.pose().last().pose();
        float xScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
        float yScale = (float) Math.sqrt(pose.m10() * pose.m10() + pose.m11() * pose.m11());
        float scale = Math.max(xScale, yScale);
        return scale > 0.0001F && Float.isFinite(scale) ? scale : 1.0F;
    }

    private static TextureRef customTexture(String path, Path relativeDirectory) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        boolean windowsPath = normalized.length() >= 3 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':' && normalized.charAt(2) == '/';
        if (normalized.contains(":") && !windowsPath) {
            try {
                return new TextureRef(ResourceLocation.parse(normalized), TEXTURE_WIDTH, TEXTURE_HEIGHT);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return fileTexture(normalized, relativeDirectory);
    }

    private static TextureRef fileTexture(String path, Path relativeDirectory) {
        try {
            Path resolved = Path.of(path);
            if (!resolved.isAbsolute()) {
                resolved = relativeDirectory.resolve(path);
            }
            String key = resolved.toAbsolutePath().normalize().toString();
            TextureLookup lookup = FILE_TEXTURE_LOOKUPS.get(key);
            if (lookup != null) {
                return lookup.texture();
            }
            if (!java.nio.file.Files.isRegularFile(resolved)) {
                FILE_TEXTURE_LOOKUPS.put(key, new TextureLookup(null));
                return null;
            }
            TextureRef cached = FILE_TEXTURES.get(key);
            if (cached != null) {
                FILE_TEXTURE_LOOKUPS.put(key, new TextureLookup(cached));
                return cached;
            }
            enqueueFileTextureLoad(key, resolved);
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void enqueueFileTextureLoad(String key, Path resolved) {
        AsyncTextureState existing = ASYNC_FILE_TEXTURES.putIfAbsent(key, AsyncTextureState.LOADING);
        if (existing != null) {
            return;
        }
        TEXTURE_LOAD_EXECUTOR.execute(() -> {
            long start = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
            try (FileInputStream input = new FileInputStream(resolved.toFile())) {
                NativeImage image = NativeImage.read(input);
                if (MoonSpirePerfDiagnostics.enabled()) {
                    long elapsed = MoonSpirePerfDiagnostics.now() - start;
                    textureDecodeCompletedTotal++;
                    textureDecodeNanosTotal += elapsed;
                    MoonSpirePerfDiagnostics.mark("client.card.textureDecode", elapsed, MoonSpirePerfDiagnostics.SEGMENT_THRESHOLD_NANOS, "keyHash=" + Integer.toHexString(key.hashCode()));
                }
                LOADED_FILE_TEXTURES.add(new LoadedTexture(key, image));
            } catch (IOException | RuntimeException ignored) {
                ASYNC_FILE_TEXTURES.put(key, AsyncTextureState.FAILED);
                FILE_TEXTURE_LOOKUPS.put(key, new TextureLookup(null));
            }
        });
    }

    private static void registerPendingFileTextures() {
        for (int i = 0; i < TEXTURE_REGISTRATIONS_PER_FRAME; i++) {
            LoadedTexture loaded = LOADED_FILE_TEXTURES.poll();
            if (loaded == null) {
                return;
            }
            if (ASYNC_FILE_TEXTURES.get(loaded.key()) != AsyncTextureState.LOADING) {
                loaded.image().close();
                continue;
            }
            long start = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
            try {
                int width = Math.max(1, loaded.image().getWidth());
                int height = Math.max(1, loaded.image().getHeight());
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "developer_file/" + Integer.toHexString(loaded.key().hashCode()));
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(loaded.image()));
                if (MoonSpirePerfDiagnostics.enabled()) {
                    long elapsed = MoonSpirePerfDiagnostics.now() - start;
                    textureRegistrationsThisFrame++;
                    textureRegistrationNanosThisFrame += elapsed;
                    MoonSpirePerfDiagnostics.mark("client.card.textureRegister", elapsed, MoonSpirePerfDiagnostics.SEGMENT_THRESHOLD_NANOS, "width=" + width + " height=" + height);
                }
                TextureRef ref = new TextureRef(id, width, height);
                FILE_TEXTURES.put(loaded.key(), ref);
                FILE_TEXTURE_LOOKUPS.put(loaded.key(), new TextureLookup(ref));
                ASYNC_FILE_TEXTURES.put(loaded.key(), AsyncTextureState.READY);
            } catch (RuntimeException ignored) {
                ASYNC_FILE_TEXTURES.put(loaded.key(), AsyncTextureState.FAILED);
                FILE_TEXTURE_LOOKUPS.put(loaded.key(), new TextureLookup(null));
                loaded.image().close();
            }
        }
    }

    private static ItemStack artItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ItemStack cached = ART_ITEMS.get(itemId);
        if (cached != null) {
            return cached;
        }
        try {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            ItemStack stack = item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
            ART_ITEMS.put(itemId, stack);
            return stack;
        } catch (RuntimeException ignored) {
            ART_ITEMS.put(itemId, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
    }

    private static void renderCardArtItem(GuiGraphics graphics, ItemStack stack, int artX, int artY, int artWidth, int artHeight, int offsetX, int offsetY, float customScale, boolean clipArt) {
        renderCardArtItem(graphics, stack, artX, artY, artWidth, artHeight, offsetX, offsetY, customScale, clipArt, true);
    }

    private static void renderCardArtItem(GuiGraphics graphics, ItemStack stack, int artX, int artY, int artWidth, int artHeight, int offsetX, int offsetY, float customScale, boolean clipArt, boolean clearDepthAfterRender) {
        long start = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        int padding = Math.max(2, Math.min(artWidth, artHeight) / 12);
        int itemSize = Math.max(16, Math.min(artWidth - padding * 2, artHeight - padding * 2));
        itemSize = Math.max(1, Math.round(itemSize * Math.max(0.05F, customScale)));
        float itemScale = itemSize / 16.0F;
        int itemX = artX + (artWidth - itemSize) / 2 + offsetX;
        int itemY = artY + (artHeight - itemSize) / 2 + offsetY;
        if (clipArt) {
            graphics.flush();
            enablePoseScissor(graphics, artX, artY, artWidth, artHeight);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(itemX, itemY, 0.0F);
        graphics.pose().scale(itemScale, itemScale, 1.0F);
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null) {
            context.recordFakeItemRender();
        }
        graphics.renderFakeItem(stack, 0, 0);
        graphics.pose().popPose();
        if (clipArt) {
            graphics.flush();
            graphics.disableScissor();
        }
        if (clearDepthAfterRender) {
            if (!clipArt) {
                graphics.flush();
            }
            clearItemDepth();
        }
        recordItemArt(start);
    }

    private static void renderCustomCardArt(GuiGraphics graphics, TextureRef texture, int artX, int artY, int artWidth, int artHeight, int offsetX, int offsetY, float scale, boolean clipArt) {
        long start = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        if (clipArt) {
            graphics.flush();
            enablePoseScissor(graphics, artX, artY, artWidth, artHeight);
        }
        int drawW = Math.max(1, Math.round(artWidth * Math.max(0.05F, scale)));
        int drawH = Math.max(1, Math.round(artHeight * Math.max(0.05F, scale)));
        int drawX = artX + (artWidth - drawW) / 2 + offsetX;
        int drawY = artY + (artHeight - drawH) / 2 + offsetY;
        graphics.blit(texture.location(), drawX, drawY, drawW, drawH, 0.0F, 0.0F, texture.width(), texture.height(), texture.width(), texture.height());
        if (clipArt) {
            graphics.flush();
            graphics.disableScissor();
        }
        recordCustomArt(start);
    }

    private static void renderBarFill(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int tileW = 16;
        int tileH = 16;
        int drawn = 0;
        while (drawn < width) {
            int slice = Math.min(tileW, width - drawn);
            graphics.blit(texture, x + drawn, y, slice, height, 0.0F, 0.0F, slice, tileH, tileW, tileH);
            drawn += slice;
        }
    }

    private static void renderCardArtItem(GuiGraphics graphics, CardInstance card, int artX, int artY, int artWidth, int artHeight, boolean clipArt) {
        renderCardArtItem(graphics, card, artX, artY, artWidth, artHeight, clipArt, true);
    }

    private static void renderCardArtItem(GuiGraphics graphics, CardInstance card, int artX, int artY, int artWidth, int artHeight, boolean clipArt, boolean clearDepthAfterRender) {
        long start = MoonSpirePerfDiagnostics.enabled() ? MoonSpirePerfDiagnostics.now() : 0L;
        int padding = Math.max(2, Math.min(artWidth, artHeight) / 12);
        int itemSize = Math.max(16, Math.min(artWidth - padding * 2, artHeight - padding * 2));
        float itemScale = itemSize / 16.0F;
        int itemX = artX + (artWidth - itemSize) / 2;
        int itemY = artY + (artHeight - itemSize) / 2;
        if (clipArt) {
            graphics.flush();
            enablePoseScissor(graphics, artX, artY, artWidth, artHeight);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(itemX, itemY, 0.0F);
        graphics.pose().scale(itemScale, itemScale, 1.0F);
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null) {
            context.recordFakeItemRender();
        }
        graphics.renderFakeItem(card.sourceStack(), 0, 0);
        graphics.pose().popPose();
        if (clipArt) {
            graphics.flush();
            graphics.disableScissor();
        }
        if (clearDepthAfterRender) {
            if (!clipArt) {
                graphics.flush();
            }
            clearItemDepth();
        }
        recordItemArt(start);
    }

    private static void clearItemDepth() {
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null) {
            context.recordItemDepthClear();
        }
    }

    private static void recordSkippedItemArt() {
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null) {
            context.recordSkippedItemArt();
        }
    }

    private static void recordFaceBase(long startNanos) {
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null && startNanos != 0L) {
            context.recordFaceBase(MoonSpirePerfDiagnostics.now() - startNanos);
        }
    }

    private static void recordCustomArt(long startNanos) {
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null && startNanos != 0L) {
            context.recordCustomArt(MoonSpirePerfDiagnostics.now() - startNanos);
        }
    }

    private static void recordItemArt(long startNanos) {
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null && startNanos != 0L) {
            context.recordItemArt(MoonSpirePerfDiagnostics.now() - startNanos);
        }
    }

    private static void recordTextCost(long startNanos) {
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null && startNanos != 0L) {
            context.recordTextCost(MoonSpirePerfDiagnostics.now() - startNanos);
        }
    }

    private static void recordTextName(long startNanos) {
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null && startNanos != 0L) {
            context.recordTextName(MoonSpirePerfDiagnostics.now() - startNanos);
        }
    }

    private static void recordTextType(long startNanos) {
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null && startNanos != 0L) {
            context.recordTextType(MoonSpirePerfDiagnostics.now() - startNanos);
        }
    }

    private static void recordTextDescription(long startNanos) {
        CardRenderContext context = FRAME_CONTEXT.get();
        if (context != null && startNanos != 0L) {
            context.recordTextDescription(MoonSpirePerfDiagnostics.now() - startNanos);
        }
    }

    private static int sx(int width, int textureX) {
        return Math.round(width * (textureX / (float) TEXTURE_WIDTH));
    }

    private static int sy(int height, int textureY) {
        return Math.round(height * (textureY / (float) TEXTURE_HEIGHT));
    }

    private static Component keyword(Component component) {
        return component.copy().withStyle(style -> style.withColor(TextColor.fromRgb(KEYWORD_TEXT_COLOR)));
    }

    private static Component statNumber(int originalValue, int finalValue) {
        MutableComponent component = Component.translatable("screen.moonspire.value_number", finalValue);
        if (finalValue > originalValue) {
            return component.withStyle(style -> style.withColor(TextColor.fromRgb(INCREASED_VALUE_COLOR)));
        }
        if (finalValue < originalValue) {
            return component.withStyle(ChatFormatting.RED);
        }
        return component;
    }

    private static void drawCenteredFit(GuiGraphics graphics, Font font, String text, int x, int y, int width, int color, float maxScale) {
        drawCenteredFit(graphics, font, text, x, y, width, 10, color, maxScale);
    }

    private static void drawCenteredFit(GuiGraphics graphics, Font font, String text, int x, int y, int width, int height, int color, float maxScale) {
        drawCenteredFit(graphics, font, text, font.width(text), x, y, width, height, color, maxScale);
    }

    private static void drawCenteredFit(GuiGraphics graphics, Font font, String text, int textWidth, int x, int y, int width, int height, int color, float maxScale) {
        float widthScale = width / (float) Math.max(1, textWidth);
        float heightScale = height / (float) Math.max(1, font.lineHeight);
        float scale = Math.min(maxScale, Math.min(widthScale, heightScale));
        scale = Math.max(FITTED_TEXT_MIN_SCALE, scale);
        int scaledWidth = (int) (textWidth * scale);
        int scaledHeight = (int) (font.lineHeight * scale);
        drawScaled(graphics, font, text, x + (width - scaledWidth) / 2, y + (height - scaledHeight) / 2, color, scale);
    }

    private static void drawCenteredFit(GuiGraphics graphics, Font font, FormattedCharSequence text, int textWidth, int x, int y, int width, int height, int color, float maxScale) {
        float widthScale = width / (float) Math.max(1, textWidth);
        float heightScale = height / (float) Math.max(1, font.lineHeight);
        float scale = Math.min(maxScale, Math.min(widthScale, heightScale));
        scale = Math.max(FITTED_TEXT_MIN_SCALE, scale);
        int scaledWidth = (int) (textWidth * scale);
        int scaledHeight = (int) (font.lineHeight * scale);
        drawScaled(graphics, font, text, x + (width - scaledWidth) / 2, y + (height - scaledHeight) / 2, color, scale);
    }

    private static void drawCenteredLines(GuiGraphics graphics, Font font, List<TextLine> lines, int x, int y, int width, int height, int color, float scale) {
        if (lines.isEmpty()) {
            return;
        }
        int lineHeight = scaledLineHeight(font, scale);
        int totalHeight = lines.size() * lineHeight - 2;
        int lineY = y + (height - totalHeight) / 2;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        try {
            for (TextLine line : lines) {
                int scaledWidth = (int) (line.width() * scale);
                int lineX = x + (width - scaledWidth) / 2;
                graphics.drawString(font, line.text(), Math.round(lineX / scale), Math.round(lineY / scale), color, false);
                lineY += lineHeight;
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static int scaledLineHeight(Font font, float scale) {
        return Math.max(1, (int) (font.lineHeight * scale + 2));
    }

    private static void drawScaled(GuiGraphics graphics, Font font, String text, int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawScaled(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawScaledOutlined(GuiGraphics graphics, Font font, String text, float x, float y, float scale, float outlineOffset, int textColor, int outlineColor) {
        drawScaledAt(graphics, font, text, x - outlineOffset, y, outlineColor, scale);
        drawScaledAt(graphics, font, text, x + outlineOffset, y, outlineColor, scale);
        drawScaledAt(graphics, font, text, x, y - outlineOffset, outlineColor, scale);
        drawScaledAt(graphics, font, text, x, y + outlineOffset, outlineColor, scale);
        drawScaledAt(graphics, font, text, x, y, textColor, scale);
    }

    private static void drawScaledAt(GuiGraphics graphics, Font font, String text, float x, float y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
