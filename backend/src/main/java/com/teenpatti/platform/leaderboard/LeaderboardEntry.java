package com.teenpatti.platform.leaderboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "leaderboard_entries")
@CompoundIndexes({
        @CompoundIndex(name = "user_window_key_idx", def = "{'userId': 1, 'window': 1, 'windowKey': 1}", unique = true),
        @CompoundIndex(name = "window_winnings_idx", def = "{'window': 1, 'windowKey': 1, 'totalWinningsPaise': -1}"),
        @CompoundIndex(name = "window_wins_idx", def = "{'window': 1, 'windowKey': 1, 'handsWon': -1}")
})
public class LeaderboardEntry {

    @Id
    private String id;

    @Indexed
    private String userId;

    private LeaderboardWindow window;

    private String windowKey;

    @Builder.Default
    private int handsWon = 0;

    @Builder.Default
    private int handsPlayed = 0;

    @Builder.Default
    private long totalWinningsPaise = 0L;

    private Instant updatedAt;
}
