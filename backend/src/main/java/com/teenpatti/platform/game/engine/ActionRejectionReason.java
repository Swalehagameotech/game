package com.teenpatti.platform.game.engine;

/**
 * Specific reasons for rejecting a player action during a betting round.
 */
public enum ActionRejectionReason {
    NOT_YOUR_TURN("It is not your turn to act."),
    ALREADY_SEEN("Player has already seen their cards."),
    MUST_BE_BLIND("Action requires player to be in BLIND status."),
    MUST_BE_SEEN("Action requires player to be in SEEN status."),
    SHOW_DISABLED("Show is currently disabled by configuration."),
    INSUFFICIENT_BET_AMOUNT("Bet amount does not meet the minimum required for player status."),
    EXCEEDS_MAX_BET("Bet amount exceeds the maximum allowed raise limit."),
    SHOW_REQUIRES_EXACTLY_TWO_PLAYERS("Show action is only valid when exactly two active players remain."),
    HAND_ALREADY_FINISHED("The hand has already finished."),
    PLAYER_NOT_IN_HAND("Player is not part of this hand or has already packed.");

    private final String description;

    ActionRejectionReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
