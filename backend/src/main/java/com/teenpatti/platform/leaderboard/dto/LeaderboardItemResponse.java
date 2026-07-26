package com.teenpatti.platform.leaderboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardItemResponse {
    private int rank;
    private String userId;
    private String displayName;
    private String avatarUrl;
    private int handsWon;
    private int handsPlayed;
    private long totalWinningsPaise;
    private long statValue; // Value corresponding to requested metric (winnings or wins)
}
