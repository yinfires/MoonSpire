package com.yinfires.moonspire.developer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;

public class DeveloperData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int version = 1;
    public String activeFaceId = "default";
    public List<DeveloperCardFace> cardFaces = new ArrayList<>();
    public List<DeveloperCardDefinition> cards = new ArrayList<>();
    public List<DeveloperMonsterDefinition> monsters = new ArrayList<>();

    public static DeveloperData empty() {
        DeveloperData data = new DeveloperData();
        data.ensureDefaults();
        return data;
    }

    public static DeveloperData fromJson(String json) {
        try {
            DeveloperData data = GSON.fromJson(json, DeveloperData.class);
            if (data == null) {
                return empty();
            }
            data.ensureDefaults();
            return data;
        } catch (RuntimeException ignored) {
            return empty();
        }
    }

    public String toJson() {
        ensureDefaults();
        return GSON.toJson(this);
    }

    public void ensureDefaults() {
        if (cardFaces == null) {
            cardFaces = new ArrayList<>();
        }
        if (cards == null) {
            cards = new ArrayList<>();
        }
        if (monsters == null) {
            monsters = new ArrayList<>();
        }
        if (cardFaces.stream().noneMatch(face -> "default".equals(face.id()))) {
            cardFaces.addFirst(DeveloperCardFace.defaultFace());
        }
        if (activeFaceId == null || activeFaceId.isBlank()) {
            activeFaceId = "default";
        }
    }
}
