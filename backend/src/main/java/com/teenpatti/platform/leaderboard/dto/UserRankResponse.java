package com.teenpatti.platform.leaderboard.dto;

import com.teenpatti.platform.leaderboard.LeaderboardMetric;
import com.teenpatti.platform.leaderboard.LeaderboardWindow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRankResponse {
    private String userId;
    private String displayName;
    private LeaderboardWindow window;
    private String windowKey;
    private LeaderboardMetric metric;
    private boolean ranked;
    private Integer rank; // Null if not ranked
    private int handsWon;
    private int handsPlayed;
    private long totalWinningsPaise;
    private long statValue;
}
