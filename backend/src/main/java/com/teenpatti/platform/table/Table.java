package com.teenpatti.platform.table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Table document representing a poker/Teen Patti table room.
 *
 * NOTE ON TRANSIENT STATE:
 * Active game hand state (cards dealt, current bet, turn order) is transient/in-memory.
 * This document tracks table configuration, limits, and seated user ID references.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tables")
@CompoundIndexes({
    @CompoundIndex(name = "table_type_status_stake_idx", def = "{'tableType': 1, 'status': 1, 'stakeTier': 1}")
})
public class Table {

    @Id
    private String id;

    private String tableName;

    private String hostId;

    @Builder.Default
    private TableType tableType = TableType.PUBLIC;

    @Builder.Default
    private String visibility = "PUBLIC";

    @Builder.Default
    private StakeTier stakeTier = StakeTier.LOW;

    @Builder.Default
    private GameVariant gameVariant = GameVariant.HIGHER;

    @Builder.Default
    private long bootAmountPaise = 1000L;

    @Builder.Default
    private int minPlayers = 3;

    @Builder.Default
    private int maxPlayers = 6;

    @Indexed(unique = true, sparse = true)
    private String inviteCode;

    @Builder.Default
    private List<String> seatedPlayerIds = new ArrayList<>();

    /**
     * List of user IDs who left the table while a hand was IN_PROGRESS.
     * Consumed by game engine to treat as folded/removed from active hand.
     */
    @Builder.Default
    private List<String> leftMidHandPlayerIds = new ArrayList<>();

    @Builder.Default
    private long potPaise = 0L;

    private String currentHandId;

    @Builder.Default
    private TableStatus status = TableStatus.WAITING;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    private Instant updatedAt;

    public String getTableId() {
        return id;
    }

    public List<String> getPlayers() {
        return seatedPlayerIds;
    }

    public int getPlayerCount() {
        return seatedPlayerIds != null ? seatedPlayerIds.size() : 0;
    }

    public long getBootAmount() {
        return bootAmountPaise;
    }
}
