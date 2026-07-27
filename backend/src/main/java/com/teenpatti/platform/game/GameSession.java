package com.teenpatti.platform.game;

import com.teenpatti.platform.table.GameVariant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Active hand state for a table. Deck and card data are server-only and must never
 * be broadcast to clients. Primary runtime store is in-memory ({@code HandContextManager});
 * this document supports audit, recovery, and multi-instance coordination when enabled.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "game_sessions")
@CompoundIndexes({
        @CompoundIndex(name = "table_status_idx", def = "{'tableId': 1, 'status': 1}"),
        @CompoundIndex(name = "table_started_idx", def = "{'tableId': 1, 'startedAt': -1}")
})
public class GameSession {

    @Id
    private String id;

    @Indexed
    private String tableId;

    private String handId;

    /** Audit id for the secure shuffle used to produce this hand's deck order. */
    private String shuffleId;

    @Builder.Default
    private GameVariant variant = GameVariant.CLASSIC;

    @Builder.Default
    private int roundNumber = 1;

    /**
     * Remaining deck order (card codes). Never expose via REST or WebSocket.
     */
    @Builder.Default
    private List<String> deck = new ArrayList<>();

    /**
     * userId -> three card codes dealt to that player.
     */
    @Builder.Default
    private Map<String, List<String>> playerHands = new HashMap<>();

    /**
     * userId -> BLIND | SEEN | PACKED | FOLDED.
     */
    @Builder.Default
    private Map<String, String> playerStatus = new HashMap<>();

    /**
     * userId -> total contribution in paise for this hand.
     */
    @Builder.Default
    private Map<String, Long> contributions = new HashMap<>();

    @Builder.Default
    private long potPaise = 0L;

    @Builder.Default
    private long currentBaseStakePaise = 0L;

    private int currentTurnIndex;

    private String currentTurnUserId;

    private int dealerSeatIndex;

    private Instant turnDeadlineAt;

    @Builder.Default
    private GameSessionStatus status = GameSessionStatus.ACTIVE;

    @CreatedDate
    private Instant startedAt;

    private Instant endedAt;
}
