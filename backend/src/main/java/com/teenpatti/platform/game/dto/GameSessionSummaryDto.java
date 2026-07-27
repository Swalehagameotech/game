package com.teenpatti.platform.game.dto;

import com.teenpatti.platform.game.GameSessionStatus;
import com.teenpatti.platform.table.GameVariant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Sanitized hand session view for REST — never exposes deck order or private card values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSessionSummaryDto {

    private String sessionId;
    private String tableId;
    private String handId;
    private GameVariant variant;
    private int roundNumber;
    private GameSessionStatus status;
    private long potPaise;
    private long currentBaseStakePaise;
    private String currentTurnUserId;
    private int dealerSeatIndex;
    private Instant startedAt;
    private Instant endedAt;
    /** userId -> BLIND | SEEN | PACKED */
    private Map<String, String> playerStatus;
}
