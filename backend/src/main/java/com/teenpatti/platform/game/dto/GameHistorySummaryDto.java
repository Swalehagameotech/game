package com.teenpatti.platform.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sanitized hand history row for list views (home dashboard, REST list).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameHistorySummaryDto {

    private String id;
    private String handId;
    private String tableId;
    private String tableName;
    private int roundNumber;
    private String variant;
    private String winnerId;
    private String winnerDisplayName;

    /** WON or LOST relative to the requesting user. */
    private String result;

    private long potAmountPaise;
    private long winnerPayoutPaise;
    private long rakeAmountPaise;

    /** Net amount for the requesting user (+ payout when won, 0 when lost). */
    private long netAmountPaise;

    private String winningCategory;
    private String winningHandDescription;
    private boolean foldWin;
    private int playerCount;
    private String playedAt;
}
