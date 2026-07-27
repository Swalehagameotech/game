package com.teenpatti.platform.game;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.game.dto.GameHistoryDetailDto;
import com.teenpatti.platform.game.dto.GameHistorySummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Player-facing REST API for completed hand history.
 */
@RestController
@RequestMapping("/api/game/history")
@RequiredArgsConstructor
public class GameHistoryController {

    private final GameHistoryService gameHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<GameHistorySummaryDto>>> listHistory(
            @CurrentUser String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<GameHistorySummaryDto> history = gameHistoryService.getHistoryForUser(
                userId, PageRequest.of(Math.max(page, 0), safeSize));
        return ResponseEntity.ok(ApiResponse.success("Game history retrieved successfully", history));
    }

    @GetMapping("/{historyId}")
    public ResponseEntity<ApiResponse<GameHistoryDetailDto>> getHistoryDetail(
            @CurrentUser String userId,
            @PathVariable String historyId) {
        return gameHistoryService.getDetailForUser(userId, historyId)
                .map(detail -> ResponseEntity.ok(ApiResponse.success("Game history detail retrieved", detail)))
                .orElse(ResponseEntity.notFound().build());
    }
}
