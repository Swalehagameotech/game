package com.teenpatti.platform.game;

import com.teenpatti.platform.table.Table;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Backward-compatible delegate to {@link GameEngineService}.
 */
@Service
@RequiredArgsConstructor
public class GameStartService {

    private final GameEngineService gameEngineService;

    public Table startGame(String hostUserId, String tableId) {
        return gameEngineService.startGame(hostUserId, tableId);
    }

    public Table startGameAutomatically(String tableId) {
        return gameEngineService.startGameAutomatically(tableId);
    }
}
