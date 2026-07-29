package com.teenpatti.platform.table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Per-round winner record stored on the table for lobby/history display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableRoundResult {

    private int roundNumber;
    private String handId;
    private String winnerUserId;
    private String winnerDisplayName;
    private String winningCategory;
    private String winningHandDescription;
    private long potPaise;
    private long payoutPaise;
    private boolean foldWin;
    private Instant endedAt;
}
