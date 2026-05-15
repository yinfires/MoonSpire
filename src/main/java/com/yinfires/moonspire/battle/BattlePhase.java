package com.yinfires.moonspire.battle;

public enum BattlePhase {
    PLAYER_TURN,
    PLAYER_ALLY_TURN,
    MONSTER_TURN,
    ROUND_END;

    public String translationKey() {
        return switch (this) {
            case PLAYER_TURN -> "battle_phase.moonspire.player_turn";
            case PLAYER_ALLY_TURN -> "battle_phase.moonspire.player_ally_turn";
            case MONSTER_TURN -> "battle_phase.moonspire.monster_turn";
            case ROUND_END -> "battle_phase.moonspire.round_end";
        };
    }
}
