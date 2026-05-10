package com.yinfires.moonspire.client;

import com.yinfires.moonspire.card.PlayerCardData;

public final class ClientCardState {
    private static PlayerCardData cards = new PlayerCardData();
    private static long version;

    private ClientCardState() {
    }

    public static PlayerCardData cards() {
        return cards;
    }

    public static long version() {
        return version;
    }

    public static void setCards(PlayerCardData data) {
        cards = data;
        version++;
    }
}
