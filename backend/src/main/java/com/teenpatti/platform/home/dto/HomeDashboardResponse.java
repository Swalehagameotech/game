package com.teenpatti.platform.home.dto;

import com.teenpatti.platform.lobby.dto.TableSummaryResponse;
import com.teenpatti.platform.notification.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Unified Home Page aggregate dashboard payload returned by GET /api/home/dashboard.
 * Driven 100% by backend MongoDB database & STOMP WebSockets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeDashboardResponse {

    private UserProfileDto userProfile;
    private WalletSummaryDto wallet;
    private ActiveGameDto activeGame;
    private List<TableSummaryResponse> publicTables;
    private List<QuickPlayOptionDto> quickPlayOptions;
    private List<GameHistoryDto> recentHistory;
    private List<Notification> recentNotifications;
    private LiveStatsDto liveStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileDto {
        private String userId;
        private String displayName;
        private String email;
        private String avatarUrl;
        private boolean isOnline;
        private String role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletSummaryDto {
        private long balancePaise;
        private String formattedBalance;
        private long totalDepositedPaise;
        private long totalWithdrawnPaise;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveGameDto {
        private String tableId;
        private String tableName;
        private long bootAmountPaise;
        private String status;
        private int seatedCount;
        private int maxPlayers;
        private int userSeatIndex;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickPlayOptionDto {
        private String label;
        private long bootAmountPaise;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameHistoryDto {
        private String gameId;
        private String tableName;
        private String result; // WON, LOST, FOLDED
        private long winningAmountPaise;
        private String playedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LiveStatsDto {
        private int onlinePlayers;
        private int runningTablesCount;
        private int waitingTablesCount;
        private int totalActiveGames;
    }
}
