package com.teenpatti.platform.game;

import com.teenpatti.platform.table.GameVariant;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Immutable record of a completed hand. Canonical collection per platform spec.
 * Legacy {@link MatchHistory} on {@code match_histories} remains readable for older data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "game_history")
@CompoundIndexes({
        @CompoundIndex(name = "table_ended_idx", def = "{'tableId': 1, 'endedAt': -1}"),
        @CompoundIndex(name = "winner_ended_idx", def = "{'winnerId': 1, 'endedAt': -1}")
})
public class GameHistory {

    @Id
    private String id;

    @Indexed
    private String tableId;

    @Indexed
    private String handId;

    @Builder.Default
    private int roundNumber = 1;

    @Builder.Default
    private GameVariant variant = GameVariant.CLASSIC;

    @Builder.Default
    private List<String> playerIds = new ArrayList<>();

    @Indexed
    private String winnerId;

    private long potAmountPaise;

    private long rakeAmountPaise;

    private long winnerPayoutPaise;

    private WinningCategory winningCategory;

    private HandSummary handSummary;

    /**
     * Optional serialized action log for dispute resolution.
     */
    @Builder.Default
    private List<Map<String, Object>> actionLog = new ArrayList<>();

    private Instant startedAt;

    private Instant endedAt;
}
