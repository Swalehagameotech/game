package com.teenpatti.platform.table.dto;

import com.teenpatti.platform.table.StakeTier;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableDetailResponse {
    private String tableId;
    private TableType tableType;
    private StakeTier stakeTier;
    private int maxPlayers;
    private int currentPlayerCount;
    private TableStatus status;
    private long potPaise;
    private String currentHandId;
    private List<SeatedPlayerResponse> seatedPlayers;
    private List<String> leftMidHandPlayerIds;
}
