package com.teenpatti.platform.websocket.dto;

import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.table.TableStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerViewGameState {
    private String tableId;
    private TableStatus status;
    private String currentTurnPlayerId;
    private long potPaise;
    private long currentBaseStakePaise;
    private long requiredBetPaise;
    private List<PlayerSummaryView> players;
    private HandOutcome handOutcome;
}
