package com.yinfires.moonspire.client;

import com.yinfires.moonspire.card.PlayerCardData;

public final class ClientCardState {
    private static PlayerCardData cards = new PlayerCardData();

    private ClientCardState() {
    }

    public static PlayerCardData cards() {
        return cards;
    }

    public static void setCards(PlayerCardData data) {
        cards = data;
    }
}
