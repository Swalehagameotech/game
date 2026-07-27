package com.teenpatti.platform.game.winner;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Canonical winner snapshot broadcast when a hand completes.
 */
@Value
@Builder
public class WinnerSnapshot {

    String tableId;
    String handId;
    String winnerUserId;
    String winnerDisplayName;
    String winningCategory;
    String winningHandDescription;
    boolean foldWin;
    long potPaise;
    long rakePaise;
    long payoutPaise;
    String notes;
    List<ShowdownParticipantView> participants;
}
