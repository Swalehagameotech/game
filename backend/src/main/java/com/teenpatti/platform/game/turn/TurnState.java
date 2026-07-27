package com.teenpatti.platform.game.turn;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Canonical turn snapshot broadcast on every turn change (STOMP + WebSocket projection).
 */
@Value
@Builder
public class TurnState {

    String tableId;
    String currentTurnUserId;
    int currentTurnSeatIndex;
    int dealerSeatIndex;
    long potPaise;
    long currentBaseStakePaise;
    int turnTimeoutSeconds;
    int turnSecondsRemaining;
    Instant turnDeadlineAt;
    List<String> activePlayerIds;
    List<String> blindPlayerIds;
    List<String> seenPlayerIds;
    List<String> packedPlayerIds;
}
