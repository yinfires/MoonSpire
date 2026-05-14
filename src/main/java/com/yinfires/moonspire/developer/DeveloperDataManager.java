package com.yinfires.moonspire.developer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.MonsterDeckProfile;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.card.PlayerCardData;
import com.yinfires.moonspire.registry.ModAttachments;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class DeveloperDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static DeveloperData cached;
    private static long cachedStamp = Long.MIN_VALUE;
    private static long cacheRevision;
    private static final Map<String, String> LIVE_DISPLAY_NAMES = new HashMap<>();

    private DeveloperDataManager() {
    }

    public static DeveloperData load() {
        Path file = dataFile();
        long stamp = dataStamp();
        if (cached != null && cachedStamp == stamp) {
            return cached;
        }
        cached = readData(file);
        cachedStamp = dataStamp();
        cacheRevision++;
        return cached;
    }

    public static DeveloperData reload() {
        cached = readData(dataFile());
        cachedStamp = dataStamp();
        cacheRevision++;
        return cached;
    }

    public static long cachedStamp() {
        return cachedStamp;
    }

    public static long cacheRevision() {
        return cacheRevision;
    }

    public static DeveloperData cachedOrLoad() {
        return cached == null ? load() : cached;
    }

    public static void save(DeveloperData data) throws IOException {
        sanitizeData(data);
        DeveloperPaths.ensureDirectories();
        Files.writeString(dataFile(), data.toJson(), StandardCharsets.UTF_8);
        writeSplitFiles(data);
        cached = data;
        cachedStamp = dataStamp();
        cacheRevision++;
    }

    public static void setClientData(DeveloperData data) {
        if (data == null) {
            return;
        }
        sanitizeData(data);
        cached = data;
        cachedStamp = dataStamp();
        cacheRevision++;
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

    public static void forgetDisplayName(String nameKey) {
        rememberDisplayName(nameKey, "");
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

    public static List<ServerPlayer> cleanupOnlinePlayerCards(MinecraftServer server) {
        List<ServerPlayer> changedPlayers = new ArrayList<>();
        if (server == null) {
            return changedPlayers;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerCardData data = player.getData(ModAttachments.PLAYER_CARDS.get());
            if (data.removeUnresolvableCustomCards()) {
                player.setData(ModAttachments.PLAYER_CARDS.get(), data);
                player.syncData(ModAttachments.PLAYER_CARDS.get());
                changedPlayers.add(player);
            }
        }
        return changedPlayers;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, path);
    }

    private static DeveloperData readData(Path file) {
        try {
            DeveloperPaths.ensureDirectories();
            DeveloperData data;
            if (Files.exists(file)) {
                data = DeveloperData.fromJson(Files.readString(file, StandardCharsets.UTF_8));
            } else {
                data = DeveloperData.empty();
            }
            applySplitFiles(data);
            sanitizeData(data);
            if (!Files.exists(file)) {
                Files.writeString(file, data.toJson(), StandardCharsets.UTF_8);
            }
            return data;
        } catch (IOException | RuntimeException ignored) {
            return DeveloperData.empty();
        }
    }

    private static void applySplitFiles(DeveloperData data) throws IOException {
        Path cardFacesFile = DeveloperPaths.cardFacesDirectory().resolve("card_faces.json");
        Path cardsFile = DeveloperPaths.cardsDirectory().resolve("cards.json");
        Path monstersFile = DeveloperPaths.monstersDirectory().resolve("monsters.json");
        boolean splitDataActive = Files.exists(cardFacesFile) || Files.exists(cardsFile) || Files.exists(monstersFile);
        if (!splitDataActive) {
            data.ensureDefaults();
            return;
        }
        data.cardFaces = readSplitList(cardFacesFile, DeveloperCardFace[].class, List.of());
        data.cards = readSplitList(cardsFile, DeveloperCardDefinition[].class, List.of());
        data.monsters = readSplitList(monstersFile, DeveloperMonsterDefinition[].class, List.of());
        data.ensureDefaults();
    }

    private static <T> ArrayList<T> readSplitList(Path file, Class<T[]> arrayType, List<T> fallback) throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>(fallback);
        }
        T[] values = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), arrayType);
        if (values == null) {
            return new ArrayList<>(fallback);
        }
        return new ArrayList<>(Arrays.asList(values));
    }

    private static void sanitizeData(DeveloperData data) {
        data.ensureDefaults();
        Map<String, DeveloperCardDefinition> cardsById = new LinkedHashMap<>();
        for (DeveloperCardDefinition card : data.cards) {
            if (isUsableCardDefinition(card)) {
                cardsById.put(MoonSpireCardRegistry.registeredDeveloperId(card.id()), card);
            }
        }
        data.cards = new ArrayList<>(cardsById.values());

        Map<String, DeveloperCardFace> facesById = new LinkedHashMap<>();
        for (DeveloperCardFace face : data.cardFaces) {
            if (face != null && face.id() != null && !face.id().isBlank()) {
                facesById.put(face.id(), face);
            }
        }
        facesById.putIfAbsent("default", DeveloperCardFace.defaultFace());
        data.cardFaces = new ArrayList<>(facesById.values());
        if (!facesById.containsKey(data.activeFaceId)) {
            data.activeFaceId = "default";
        }

        Set<String> resolvableCardIds = new LinkedHashSet<>();
        for (var card : MoonSpireCardRegistry.baseCards()) {
            resolvableCardIds.add(card.id());
        }
        resolvableCardIds.addAll(cardsById.keySet());
        Map<String, DeveloperMonsterDefinition> monstersById = new LinkedHashMap<>();
        for (DeveloperMonsterDefinition monster : data.monsters) {
            if (monster == null || monster.entityTypeId() == null || monster.entityTypeId().isBlank()) {
                continue;
            }
            List<String> cleanDeck = new ArrayList<>();
            for (String id : monster.deckCardIds()) {
                String registeredId = MoonSpireCardRegistry.registeredDeveloperId(id);
                if (!registeredId.isBlank() && resolvableCardIds.contains(registeredId)) {
                    cleanDeck.add(registeredId);
                }
            }
            List<String> cleanRewards = new ArrayList<>();
            for (String id : monster.rewardCardIds()) {
                String registeredId = MoonSpireCardRegistry.registeredDeveloperId(id);
                if (!registeredId.isBlank() && resolvableCardIds.contains(registeredId) && !cleanRewards.contains(registeredId)) {
                    cleanRewards.add(registeredId);
                }
            }
            boolean deckOverride = monster.hasDeckOverride() || !monster.deckCardIds().isEmpty();
            if (deckOverride && !monster.deckCardIds().isEmpty() && cleanDeck.isEmpty() && hasDefaultMonsterDeck(monster.entityTypeId())) {
                deckOverride = false;
            }
            boolean rewardOverride = monster.hasRewardOverride() || !monster.rewardCardIds().isEmpty();
            if (rewardOverride && !monster.rewardCardIds().isEmpty() && cleanRewards.isEmpty()) {
                rewardOverride = false;
            }
            monstersById.put(monster.entityTypeId(), new DeveloperMonsterDefinition(
                    monster.entityTypeId(),
                    monster.maxHealth(),
                    monster.energy(),
                    monster.speed(),
                    monster.initialEffects(),
                    cleanDeck,
                    deckOverride,
                    cleanRewards,
                    rewardOverride));
        }
        data.monsters = new ArrayList<>(monstersById.values());
    }

    private static boolean hasDefaultMonsterDeck(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            return false;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(entityTypeId);
            return BuiltInRegistries.ENTITY_TYPE.containsKey(id) && MonsterDeckProfile.hasDefaultDeck(BuiltInRegistries.ENTITY_TYPE.get(id));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isUsableCardDefinition(DeveloperCardDefinition card) {
        if (card == null) {
            return false;
        }
        String registeredId = MoonSpireCardRegistry.registeredDeveloperId(card.id());
        if (MoonSpireCardRegistry.SELF_DESTRUCT_VIEW_CARD_ID.equals(registeredId)) {
            return false;
        }
        return !registeredId.startsWith("builtin_monster_") || MoonSpireCardRegistry.baseCard(registeredId).isPresent();
    }

    private static void writeSplitFiles(DeveloperData data) throws IOException {
        Files.writeString(DeveloperPaths.cardFacesDirectory().resolve("card_faces.json"), GSON.toJson(data.cardFaces), StandardCharsets.UTF_8);
        Files.writeString(DeveloperPaths.cardsDirectory().resolve("cards.json"), GSON.toJson(data.cards), StandardCharsets.UTF_8);
        Files.writeString(DeveloperPaths.monstersDirectory().resolve("monsters.json"), GSON.toJson(data.monsters), StandardCharsets.UTF_8);
    }

    private static long dataStamp() {
        long stamp = 17L;
        for (Path file : watchedDataFiles()) {
            stamp = stamp * 31L + safeStamp(file);
        }
        return stamp;
    }

    private static List<Path> watchedDataFiles() {
        return List.of(
                dataFile(),
                DeveloperPaths.cardFacesDirectory().resolve("card_faces.json"),
                DeveloperPaths.cardsDirectory().resolve("cards.json"),
                DeveloperPaths.monstersDirectory().resolve("monsters.json"));
    }

    private static long safeStamp(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
        } catch (IOException ignored) {
            return -1L;
        }
    }
}
