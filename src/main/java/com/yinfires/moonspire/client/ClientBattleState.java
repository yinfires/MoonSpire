package com.yinfires.moonspire.client;

import com.yinfires.moonspire.battle.BattleSnapshot;

public final class ClientBattleState {
    private static BattleSnapshot snapshot = BattleSnapshot.inactive();

    private ClientBattleState() {
    }

    public static BattleSnapshot snapshot() {
        return snapshot;
    }

    public static void setSnapshot(BattleSnapshot next) {
        snapshot = next;
    }

    public static boolean active() {
        return snapshot.active();
    }
}
