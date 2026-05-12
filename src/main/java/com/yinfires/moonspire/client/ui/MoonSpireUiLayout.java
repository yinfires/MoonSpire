package com.yinfires.moonspire.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.developer.DeveloperPaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class MoonSpireUiLayout {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ResourceLocation DEFAULT_RESOURCE = ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "ui/battle_layout.json");
    private static final Map<String, MoonSpireUiElement> DEFAULTS = defaultElements();
    private static MoonSpireUiLayout cached;
    private static long cachedOverrideStamp = Long.MIN_VALUE;
    private static long cachedDefaultStamp = Long.MIN_VALUE;

    private final Map<String, MoonSpireUiElement> elements;

    private MoonSpireUiLayout(Map<String, MoonSpireUiElement> elements) {
        this.elements = new LinkedHashMap<>(elements);
    }

    public static MoonSpireUiLayout current() {
        try {
            Path overrideFile = effectiveOverrideFilePath();
            long overrideStamp = Files.exists(overrideFile) ? Files.getLastModifiedTime(overrideFile).toMillis() : -1L;
            long defaultStamp = resourceStamp();
            if (cached == null || cachedOverrideStamp != overrideStamp || cachedDefaultStamp != defaultStamp) {
                cached = load();
                cachedOverrideStamp = overrideStamp;
                cachedDefaultStamp = defaultStamp;
            }
        } catch (IOException ignored) {
            if (cached == null) {
                cached = new MoonSpireUiLayout(DEFAULTS);
            }
        }
        return cached;
    }

    public static void reload() {
        cached = load();
        cachedOverrideStamp = safeStamp(effectiveOverrideFilePath());
        cachedDefaultStamp = resourceStamp();
    }

    public static void resetOverride() throws IOException {
        Files.deleteIfExists(overrideFilePath());
        Files.deleteIfExists(legacyOverrideFilePath());
        reload();
    }

    public static void saveOverride(MoonSpireUiLayout layout) throws IOException {
        Path overrideFile = effectiveOverrideFilePath();
        Files.createDirectories(overrideFile.getParent());
        Files.writeString(overrideFile, GSON.toJson(layout.toJson()), StandardCharsets.UTF_8);
        reload();
    }

    public static Path overrideFile() {
        return overrideFilePath();
    }

    public static MoonSpireUiLayout load() {
        MoonSpireUiLayout layout = loadDefault();
        Path overrideFile = overrideFilePath();
        if (Files.exists(overrideFile)) {
            try {
                String raw = Files.readString(overrideFile, StandardCharsets.UTF_8);
                return layout.merge(fromJson(JsonParser.parseString(raw).getAsJsonObject()));
            } catch (IOException | RuntimeException ignored) {
                return layout;
            }
        }
        return layout;
    }

    public static MoonSpireUiLayout loadDefault() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getResourceManager() != null) {
            try {
                var resource = minecraft.getResourceManager().getResource(DEFAULT_RESOURCE);
                if (resource.isPresent()) {
                    try (InputStream input = resource.get().open()) {
                        String raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                        return fromJson(JsonParser.parseString(raw).getAsJsonObject());
                    }
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return new MoonSpireUiLayout(DEFAULTS);
    }

    public MoonSpireUiElement element(String id) {
        return elements.getOrDefault(id, DEFAULTS.get(id));
    }

    public MoonSpireUiRect resolve(String id, int screenWidth, int screenHeight) {
        return resolve(id, screenWidth, screenHeight, screenWidth / 2, screenHeight / 2);
    }

    public MoonSpireUiRect resolve(String id, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        MoonSpireUiElement element = element(id);
        if (element == null) {
            return new MoonSpireUiRect(0, 0, 0, 0);
        }
        int x = element.x();
        int y = element.y();
        int baseW = Math.max(1, element.width());
        int baseH = Math.max(1, element.height());
        int w = Math.max(1, Math.round(baseW * element.scale()));
        int h = Math.max(1, Math.round(baseH * element.scale()));
        switch (element.anchor()) {
            case TOP_RIGHT -> x = screenWidth - w - x;
            case TOP_CENTER -> x = screenWidth / 2 - w / 2 + x;
            case BOTTOM_LEFT -> y = screenHeight - h - y;
            case BOTTOM_RIGHT -> {
                x = screenWidth - w - x;
                y = screenHeight - h - y;
            }
            case BOTTOM_CENTER -> {
                x = screenWidth / 2 - w / 2 + x;
                y = screenHeight - h - y;
            }
            case CENTER -> {
                x = screenWidth / 2 - w / 2 + x;
                y = screenHeight / 2 - h / 2 + y;
            }
            case MOUSE -> {
                x = mouseX + x;
                y = mouseY + y;
            }
            case TOP_LEFT -> {
            }
        }
        return new MoonSpireUiRect(x, y, w, h);
    }

    public Collection<MoonSpireUiElement> elements() {
        return elements.values();
    }

    public Set<String> ids() {
        return elements.keySet();
    }

    public MoonSpireUiLayout update(MoonSpireUiElement next) {
        Map<String, MoonSpireUiElement> copy = new LinkedHashMap<>(elements);
        copy.put(next.id(), next);
        return new MoonSpireUiLayout(copy);
    }

    public MoonSpireUiLayout with(String id, MoonSpireUiElement next) {
        Map<String, MoonSpireUiElement> copy = new LinkedHashMap<>(elements);
        copy.put(id, next);
        return new MoonSpireUiLayout(copy);
    }

    public MoonSpireUiLayout merge(MoonSpireUiLayout other) {
        Map<String, MoonSpireUiElement> copy = new LinkedHashMap<>(elements);
        for (MoonSpireUiElement element : other.elements()) {
            copy.put(element.id(), element);
        }
        return new MoonSpireUiLayout(copy);
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject elementsJson = new JsonObject();
        for (MoonSpireUiElement element : elements.values()) {
            JsonObject json = new JsonObject();
            json.addProperty("anchor", element.anchor().serializedName());
            json.addProperty("x", element.x());
            json.addProperty("y", element.y());
            json.addProperty("width", element.width());
            json.addProperty("height", element.height());
            json.addProperty("scale", element.scale());
            elementsJson.add(element.id(), json);
        }
        root.add("elements", elementsJson);
        return root;
    }

    public static MoonSpireUiLayout fromJson(JsonObject root) {
        Map<String, MoonSpireUiElement> loaded = new LinkedHashMap<>(DEFAULTS);
        if (root == null || !root.has("elements")) {
            return new MoonSpireUiLayout(loaded);
        }
        JsonObject elements = root.getAsJsonObject("elements");
        for (String id : elements.keySet()) {
            JsonElement raw = elements.get(id);
            if (!raw.isJsonObject()) {
                continue;
            }
            JsonObject json = raw.getAsJsonObject();
            MoonSpireUiElement fallback = DEFAULTS.get(id);
            if (fallback == null) {
                continue;
            }
            MoonSpireUiAnchor anchor = fallback == null ? MoonSpireUiAnchor.TOP_LEFT : fallback.anchor();
            int x = fallback == null ? 0 : fallback.x();
            int y = fallback == null ? 0 : fallback.y();
            int width = fallback == null ? 1 : fallback.width();
            int height = fallback == null ? 1 : fallback.height();
            float scale = fallback == null ? 1.0F : fallback.scale();
            if (json.has("anchor")) {
                anchor = MoonSpireUiAnchor.fromString(json.get("anchor").getAsString());
            }
            if (json.has("x")) {
                x = json.get("x").getAsInt();
            }
            if (json.has("y")) {
                y = json.get("y").getAsInt();
            }
            if (json.has("width")) {
                width = json.get("width").getAsInt();
            }
            if (json.has("height")) {
                height = json.get("height").getAsInt();
            }
            if (json.has("scale")) {
                scale = json.get("scale").getAsFloat();
            }
            loaded.put(id, new MoonSpireUiElement(id, anchor, x, y, width, height, scale));
        }
        return new MoonSpireUiLayout(loaded);
    }

    public MoonSpireUiElement defaultElement(String id) {
        return DEFAULTS.get(id);
    }

    public MoonSpireUiLayout copy() {
        return new MoonSpireUiLayout(elements);
    }

    private static Map<String, MoonSpireUiElement> defaultElements() {
        Map<String, MoonSpireUiElement> defaults = new LinkedHashMap<>();
        defaults.put("player_entry", new MoonSpireUiElement("player_entry", MoonSpireUiAnchor.TOP_LEFT, 12, 12, 184, 42, 1.0F));
        defaults.put("monster_entry", new MoonSpireUiElement("monster_entry", MoonSpireUiAnchor.TOP_RIGHT, 12, 12, 184, 42, 1.0F));
        defaults.put("monster_intent", new MoonSpireUiElement("monster_intent", MoonSpireUiAnchor.TOP_CENTER, 0, 12, 290, 132, 1.0F));
        defaults.put("energy", new MoonSpireUiElement("energy", MoonSpireUiAnchor.BOTTOM_LEFT, 96, 57, 58, 58, 1.0F));
        defaults.put("draw_pile", new MoonSpireUiElement("draw_pile", MoonSpireUiAnchor.BOTTOM_LEFT, 18, 22, 58, 68, 1.0F));
        defaults.put("discard_pile", new MoonSpireUiElement("discard_pile", MoonSpireUiAnchor.BOTTOM_RIGHT, 18, 22, 58, 68, 1.0F));
        defaults.put("exhaust_pile", new MoonSpireUiElement("exhaust_pile", MoonSpireUiAnchor.BOTTOM_RIGHT, 18, 96, 58, 68, 1.0F));
        defaults.put("end_turn", new MoonSpireUiElement("end_turn", MoonSpireUiAnchor.TOP_RIGHT, 42, 48, 128, 26, 1.0F));
        defaults.put("hand", new MoonSpireUiElement("hand", MoonSpireUiAnchor.BOTTOM_CENTER, 0, -21, 820, 170, 1.0F));
        defaults.put("play_area", new MoonSpireUiElement("play_area", MoonSpireUiAnchor.CENTER, -68, 68, 300, 230, 1.0F));
        defaults.put("card_preview", new MoonSpireUiElement("card_preview", MoonSpireUiAnchor.MOUSE, 18, -82, 128, 158, 1.0F));
        defaults.put("tooltip", new MoonSpireUiElement("tooltip", MoonSpireUiAnchor.MOUSE, 18, 18, 122, 90, 1.0F));
        return defaults;
    }

    private static long resourceStamp() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getResourceManager() != null && minecraft.getResourceManager().getResource(DEFAULT_RESOURCE).isPresent()) {
                return minecraft.getResourceManager().getResource(DEFAULT_RESOURCE).get().sourcePackId().hashCode();
            }
        } catch (RuntimeException ignored) {
        }
        return 0L;
    }

    private static long safeStamp(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
        } catch (IOException ignored) {
            return -1L;
        }
    }

    private static Path overrideFilePath() {
        return DeveloperPaths.uiDirectory().resolve("battle_layout_override.json");
    }

    private static Path effectiveOverrideFilePath() {
        Path current = overrideFilePath();
        if (Files.exists(current)) {
            return current;
        }
        Path legacy = legacyOverrideFilePath();
        return Files.exists(legacy) ? legacy : current;
    }

    private static Path legacyOverrideFilePath() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gameDirectory != null) {
            return minecraft.gameDirectory.toPath().resolve("config").resolve("moonspire").resolve("battle_layout_override.json");
        }
        return Paths.get("config", "moonspire", "battle_layout_override.json");
    }
}
