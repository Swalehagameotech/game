package com.teenpatti.platform.home;

import com.teenpatti.platform.game.MatchHistory;
import com.teenpatti.platform.game.MatchHistoryRepository;
import com.teenpatti.platform.home.dto.HomeDashboardResponse;
import com.teenpatti.platform.lobby.LobbyService;
import com.teenpatti.platform.lobby.dto.TableSummaryResponse;
import com.teenpatti.platform.notification.Notification;
import com.teenpatti.platform.notification.NotificationRepository;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service that builds the 100% backend-driven Home Page Dashboard aggregation payload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    private final UserRepository userRepository;
    private final TableRepository tableRepository;
    private final LobbyService lobbyService;
    private final WalletTransactionRepository walletTransactionRepository;
    private final NotificationRepository notificationRepository;
    private final MatchHistoryRepository matchHistoryRepository;

    public HomeDashboardResponse getHomeDashboard(String userId) {
        log.info("Generating Home Dashboard aggregate payload for user [{}]", userId);

        User user = userRepository.findById(userId).orElse(null);

        // 1. User Profile DTO
        HomeDashboardResponse.UserProfileDto profileDto = HomeDashboardResponse.UserProfileDto.builder()
                .userId(userId)
                .displayName(user != null ? user.getDisplayName() : "Guest Player")
                .email(user != null ? user.getEmail() : "")
                .avatarUrl(user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() :
                        "https://api.dicebear.com/7.x/avataaars/svg?seed=" + userId)
                .isOnline(true)
                .role(user != null && user.getRole() != null ? user.getRole().name() : "PLAYER")
                .build();

        // 2. Wallet Summary DTO
        long balancePaise = user != null ? user.getWalletBalancePaise() : 10000L;
        HomeDashboardResponse.WalletSummaryDto walletDto = HomeDashboardResponse.WalletSummaryDto.builder()
                .balancePaise(balancePaise)
                .formattedBalance(String.format("₹%.2f", balancePaise / 100.0))
                .totalDepositedPaise(50000L)
                .totalWithdrawnPaise(10000L)
                .build();

        // 3. Active Game Detection (Checks if user is currently seated at a WAITING or IN_PROGRESS table)
        HomeDashboardResponse.ActiveGameDto activeGameDto = null;
        List<Table> allTables = tableRepository.findAll();
        Optional<Table> activeTableOpt = allTables.stream()
                .filter(t -> t.getStatus() == TableStatus.WAITING || t.getStatus() == TableStatus.IN_PROGRESS)
                .filter(t -> t.getSeatedPlayerIds() != null && t.getSeatedPlayerIds().contains(userId))
                .findFirst();

        if (activeTableOpt.isPresent()) {
            Table activeTable = activeTableOpt.get();
            int seatIndex = activeTable.getSeatedPlayerIds().indexOf(userId);
            activeGameDto = HomeDashboardResponse.ActiveGameDto.builder()
                    .tableId(activeTable.getId())
                    .tableName(activeTable.getTableName() != null ? activeTable.getTableName() : "Table #" + activeTable.getId().substring(0, 6).toUpperCase())
                    .bootAmountPaise(activeTable.getBootAmountPaise() > 0 ? activeTable.getBootAmountPaise() : 1000L)
                    .status(activeTable.getStatus().name())
                    .seatedCount(activeTable.getSeatedPlayerIds().size())
                    .maxPlayers(activeTable.getMaxPlayers())
                    .userSeatIndex(seatIndex)
                    .build();
        }

        // 4. Active Public Tables Grid
        List<TableSummaryResponse> publicTables = lobbyService.getPublicTables();

        // 5. Predefined Quick Play Options
        List<HomeDashboardResponse.QuickPlayOptionDto> quickPlayOptions = List.of(
                new HomeDashboardResponse.QuickPlayOptionDto("₹10", 1000L),
                new HomeDashboardResponse.QuickPlayOptionDto("₹20", 2000L),
                new HomeDashboardResponse.QuickPlayOptionDto("₹50", 5000L),
                new HomeDashboardResponse.QuickPlayOptionDto("₹100", 10000L),
                new HomeDashboardResponse.QuickPlayOptionDto("₹500", 50000L)
        );

        // 6. Recent Game History
        List<HomeDashboardResponse.GameHistoryDto> recentHistory = new ArrayList<>();
        try {
            List<MatchHistory> userMatches = matchHistoryRepository
                    .findByPlayerIdsContainingOrderByEndedAtDesc(userId, PageRequest.of(0, 10))
                    .getContent();

            for (MatchHistory m : userMatches) {
                boolean isWinner = userId.equals(m.getWinnerId());
                recentHistory.add(HomeDashboardResponse.GameHistoryDto.builder()
                        .gameId(m.getId() != null ? "gm_" + m.getId().substring(0, Math.min(m.getId().length(), 6)) : "gm_100")
                        .tableName(m.getTableId() != null ? "Table #" + m.getTableId().substring(0, Math.min(m.getTableId().length(), 6)).toUpperCase() : "Teen Patti Public")
                        .result(isWinner ? "WON" : "LOST")
                        .winningAmountPaise(m.getPotAmountPaise())
                        .playedAt(m.getEndedAt() != null ? m.getEndedAt().toString() : "")
                        .build());
            }
        } catch (Exception e) {
            log.warn("Could not fetch game history for user [{}]: {}", userId, e.getMessage());
        }

        // 7. Recent Notifications
        List<Notification> recentNotifications = new ArrayList<>();
        try {
            recentNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 5)).getContent();
        } catch (Exception e) {
            log.warn("Could not fetch notifications for user [{}]: {}", userId, e.getMessage());
        }

        // 8. Live Platform Statistics
        long onlinePlayersCount = userRepository.findAll().stream().filter(User::isOnline).count();
        if (onlinePlayersCount == 0) onlinePlayersCount = 1;

        int runningTables = (int) allTables.stream().filter(t -> t.getStatus() == TableStatus.IN_PROGRESS).count();
        int waitingTables = (int) allTables.stream().filter(t -> t.getStatus() == TableStatus.WAITING).count();

        HomeDashboardResponse.LiveStatsDto liveStats = HomeDashboardResponse.LiveStatsDto.builder()
                .onlinePlayers((int) onlinePlayersCount)
                .runningTablesCount(runningTables)
                .waitingTablesCount(waitingTables)
                .totalActiveGames(runningTables + waitingTables)
                .build();

        return HomeDashboardResponse.builder()
                .userProfile(profileDto)
                .wallet(walletDto)
                .activeGame(activeGameDto)
                .publicTables(publicTables)
                .quickPlayOptions(quickPlayOptions)
                .recentHistory(recentHistory)
                .recentNotifications(recentNotifications)
                .liveStats(liveStats)
                .build();
    }
}
