package com.yinfires.moonspire.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.client.Minecraft;

public final class MoonSpireClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean developerMode;
    private static boolean loaded;

    private MoonSpireClientConfig() {
    }

    public static boolean developerMode() {
        ensureLoaded();
        return developerMode;
    }

    public static void reload() {
        loaded = false;
        ensureLoaded();
    }

    public static Path configFile() {
        return configFilePath();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        developerMode = false;
        try {
            Path configFile = configFilePath();
            if (Files.exists(configFile)) {
                JsonObject root = GSON.fromJson(Files.readString(configFile, StandardCharsets.UTF_8), JsonObject.class);
                if (root != null && root.has("developerMode")) {
                    developerMode = root.get("developerMode").getAsBoolean();
                }
                return;
            }
            writeDefault();
        } catch (IOException | RuntimeException ignored) {
            developerMode = false;
        }
    }

    private static void writeDefault() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("developerMode", false);
        Path configFile = configFilePath();
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static Path configFilePath() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gameDirectory != null) {
            return minecraft.gameDirectory.toPath().resolve("config").resolve("moonspire").resolve("client.json");
        }
        return Paths.get("config", "moonspire", "client.json");
    }
}
