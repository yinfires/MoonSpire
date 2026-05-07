package com.yinfires.moonspire.developer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class DeveloperDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static DeveloperData cached;
    private static long cachedStamp = Long.MIN_VALUE;
    private static final Map<String, String> LIVE_DISPLAY_NAMES = new HashMap<>();

    private DeveloperDataManager() {
    }

    public static DeveloperData load() {
        Path file = dataFile();
        long stamp = safeStamp(file);
        if (cached != null && cachedStamp == stamp) {
            return cached;
        }
        cached = readData(file);
        cachedStamp = stamp;
        return cached;
    }

    public static void save(DeveloperData data) throws IOException {
        data.ensureDefaults();
        DeveloperPaths.ensureDirectories();
        Files.writeString(dataFile(), data.toJson(), StandardCharsets.UTF_8);
        writeSplitFiles(data);
        cached = data;
        cachedStamp = safeStamp(dataFile());
    }

    public static Path dataFile() {
        return DeveloperPaths.uiDirectory().resolve("developer_data.json");
    }

    public static Optional<String> displayName(String nameKey) {
        if (nameKey == null || nameKey.isBlank()) {
            return Optional.empty();
        }
        String liveName = LIVE_DISPLAY_NAMES.get(nameKey);
        if (liveName != null && !liveName.isBlank()) {
            return Optional.of(liveName);
        }
        return load().cards.stream()
                .filter(card -> nameKey.equals(card.nameKey()) && card.displayName() != null && !card.displayName().isBlank())
                .map(DeveloperCardDefinition::displayName)
                .findFirst();
    }

    public static void rememberDisplayName(String nameKey, String displayName) {
        if (nameKey == null || nameKey.isBlank()) {
            return;
        }
        if (displayName == null || displayName.isBlank()) {
            LIVE_DISPLAY_NAMES.remove(nameKey);
        } else {
            LIVE_DISPLAY_NAMES.put(nameKey, displayName);
        }
    }

    public static Path cardArtPath(String fileName) {
        return DeveloperPaths.cardArtDirectory().resolve(fileName);
    }

    public static Optional<DeveloperMonsterDefinition> monsterOverride(LivingEntity monster) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType()).toString();
        return load().monsters.stream().filter(definition -> id.equals(definition.entityTypeId())).findFirst();
    }

    public static List<CardInstance> cardsByIds(List<String> ids) {
        return MoonSpireCardRegistry.cardsByIds(ids);
    }

    public static Optional<CardInstance> cardById(String id) {
        return MoonSpireCardRegistry.cardInstance(id);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, path);
    }

    private static DeveloperData readData(Path file) {
        try {
            if (Files.exists(file)) {
                return DeveloperData.fromJson(Files.readString(file, StandardCharsets.UTF_8));
            }
            DeveloperPaths.ensureDirectories();
            DeveloperData data = DeveloperData.empty();
            Files.writeString(file, data.toJson(), StandardCharsets.UTF_8);
            return data;
        } catch (IOException | RuntimeException ignored) {
            return DeveloperData.empty();
        }
    }

    private static void writeSplitFiles(DeveloperData data) throws IOException {
        Files.writeString(DeveloperPaths.cardFacesDirectory().resolve("card_faces.json"), GSON.toJson(data.cardFaces), StandardCharsets.UTF_8);
        Files.writeString(DeveloperPaths.cardsDirectory().resolve("cards.json"), GSON.toJson(data.cards), StandardCharsets.UTF_8);
        Files.writeString(DeveloperPaths.monstersDirectory().resolve("monsters.json"), GSON.toJson(data.monsters), StandardCharsets.UTF_8);
    }

    private static long safeStamp(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
        } catch (IOException ignored) {
            return -1L;
        }
    }
}
