package com.teenpatti.platform.lobby.dto;

import com.teenpatti.platform.table.StakeTier;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableSummaryResponse {
    private String tableId;
    private StakeTier stakeTier;
    private int maxPlayers;
    private int currentPlayerCount;
    private TableStatus status;
    private TableType tableType;
}
