package com.teenpatti.platform.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detailed hand history for a single completed hand (showdown cards only when recorded).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameHistoryDetailDto {

    private String id;
    private String handId;
    private String tableId;
    private String tableName;
    private int roundNumber;
    private String variant;
    private String winnerId;
    private String winnerDisplayName;
    private String result;
    private long potAmountPaise;
    private long winnerPayoutPaise;
    private long rakeAmountPaise;
    private long netAmountPaise;
    private String winningCategory;
    private String winningHandDescription;
    private boolean foldWin;
    private int playerCount;
    private String startedAt;
    private String endedAt;
    private String notes;

    @Builder.Default
    private List<String> playerIds = new ArrayList<>();

    /** userId -> comma-separated card codes (showdown only). */
    @Builder.Default
    private Map<String, String> revealedHands = Map.of();
}
