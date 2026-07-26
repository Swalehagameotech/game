package com.teenpatti.platform.common.event;

import com.teenpatti.platform.game.engine.HandRankCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandCompletedEvent implements Serializable {

    private String tableId;
    private String handId;
    private String winnerId;
    private long potAmountPaise;
    private long rakeAmountPaise;
    private long winnerPayoutPaise;
    private HandRankCategory winningCategory;
    private String notes;
    private List<String> participantUserIds;
    private Instant timestamp;
}
