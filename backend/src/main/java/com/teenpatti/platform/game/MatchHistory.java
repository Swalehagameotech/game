package com.teenpatti.platform.game;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * MatchHistory document recording completed hand results and pot distributions.
 *
 * NOTE:
 * Written once per completed hand, never updated afterward — serves as the immutable audit log.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "match_histories")
public class MatchHistory {

    @Id
    private String id;

    @Indexed
    private String tableId;

    @Indexed
    private String handId;

    @Builder.Default
    private int roundNumber = 1;

    private List<String> playerIds;

    private String winnerId;

    /**
     * Total pot amount in paise.
     */
    private long potAmountPaise;

    /**
     * Rake fee collected by platform in paise.
     */
    private long rakeAmountPaise;

    private HandSummary handSummary;

    private Instant startedAt;

    private Instant endedAt;
}
