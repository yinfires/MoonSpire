package com.yinfires.moonspire.developer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import net.neoforged.fml.loading.FMLPaths;

public final class DeveloperPaths {
    private DeveloperPaths() {
    }

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("moonspire");
    }

    public static Path uiDirectory() {
        return root().resolve("developer").resolve("ui");
    }

    public static Path cardFacesDirectory() {
        return root().resolve("developer").resolve("card_faces");
    }

    public static Path cardsDirectory() {
        return root().resolve("developer").resolve("cards");
    }

    public static Path cardArtDirectory() {
        return root().resolve("developer").resolve("card_art");
    }

    public static Path monstersDirectory() {
        return root().resolve("developer").resolve("monsters");
    }

    public static void ensureDirectories() throws IOException {
        Files.createDirectories(root());
        Files.createDirectories(uiDirectory());
        Files.createDirectories(cardFacesDirectory());
        Files.createDirectories(cardsDirectory());
        Files.createDirectories(cardArtDirectory());
        Files.createDirectories(monstersDirectory());
    }
}
