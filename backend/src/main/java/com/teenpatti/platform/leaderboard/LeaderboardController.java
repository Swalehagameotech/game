package com.teenpatti.platform.leaderboard;

import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.leaderboard.dto.LeaderboardItemResponse;
import com.teenpatti.platform.leaderboard.dto.UserRankResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping
    public ResponseEntity<Page<LeaderboardItemResponse>> getLeaderboard(
            @RequestParam(defaultValue = "ALL_TIME") LeaderboardWindow window,
            @RequestParam(defaultValue = "WINNINGS") LeaderboardMetric metric,
            Pageable pageable) {
        return ResponseEntity.ok(leaderboardService.getLeaderboard(window, metric, pageable));
    }

    @GetMapping("/me")
    public ResponseEntity<UserRankResponse> getMyRank(
            @CurrentUser String userId,
            @RequestParam(defaultValue = "ALL_TIME") LeaderboardWindow window,
            @RequestParam(defaultValue = "WINNINGS") LeaderboardMetric metric) {
        return ResponseEntity.ok(leaderboardService.getUserRank(userId, window, metric));
    }
}
