package com.teenpatti.platform.home;

import com.teenpatti.platform.game.GameHistoryService;
import com.teenpatti.platform.home.dto.HomeDashboardResponse;
import com.teenpatti.platform.home.dto.PrivateInvitationDto;
import com.teenpatti.platform.leaderboard.LeaderboardMetric;
import com.teenpatti.platform.leaderboard.LeaderboardService;
import com.teenpatti.platform.leaderboard.LeaderboardWindow;
import com.teenpatti.platform.leaderboard.dto.LeaderboardItemResponse;
import com.teenpatti.platform.lobby.LobbyService;
import com.teenpatti.platform.lobby.dto.TableSummaryResponse;
import com.teenpatti.platform.notification.Notification;
import com.teenpatti.platform.notification.NotificationRepository;
import com.teenpatti.platform.notification.NotificationType;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableStatusGroups;
import com.teenpatti.platform.table.TableType;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.LedgerEntryRepository;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the login / home session aggregate from MongoDB — single source of truth for the home page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionAggregateService {

    private static final List<HomeDashboardResponse.QuickPlayOptionDto> QUICK_PLAY = List.of(
            new HomeDashboardResponse.QuickPlayOptionDto("₹10", 1_000L),
            new HomeDashboardResponse.QuickPlayOptionDto("₹50", 5_000L),
            new HomeDashboardResponse.QuickPlayOptionDto("₹100", 10_000L),
            new HomeDashboardResponse.QuickPlayOptionDto("₹500", 50_000L)
    );

    private final UserRepository userRepository;
    private final TableRepository tableRepository;
    private final LobbyService lobbyService;
    private final WalletService walletService;
    private final NotificationRepository notificationRepository;
    private final GameHistoryService gameHistoryService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LeaderboardService leaderboardService;

    // Constructor injection with TableRepository - lombok RequiredArgsConstructor handles all final fields

    public HomeDashboardResponse buildSessionAggregate(String userId) {
        log.debug("Building session aggregate for user [{}]", userId);

        User user = userRepository.findById(userId).orElse(null);
        WalletBalanceResponse walletBalance = walletService.getBalance(userId);

        HomeDashboardResponse.UserProfileDto profile = buildProfile(user, userId);
        HomeDashboardResponse.WalletSummaryDto wallet = buildWalletSummary(userId, walletBalance);

        List<Table> openPublicTables = tableRepository.findByTableTypeAndStatusNot(TableType.PUBLIC, TableStatus.CLOSED);
        Map<String, String> hostNames = resolveHostNames(openPublicTables);

        Optional<Table> activeTable = findActiveTableForUser(userId);
        HomeDashboardResponse.ActiveGameDto activeGame = activeTable
                .map(t -> toActiveGameDto(t, userId))
                .orElse(null);

        List<TableSummaryResponse> publicTables = lobbyService.getPublicTables();
        enrichHostNames(publicTables, hostNames);

        List<TableSummaryResponse> runningGames = openPublicTables.stream()
                .filter(t -> TableStatusGroups.isRunning(t.getStatus()))
                .map(t -> lobbyService.toTableSummary(t, hostNames.get(t.getHostId())))
                .toList();

        List<TableSummaryResponse> waitingGames = openPublicTables.stream()
                .filter(t -> TableStatusGroups.isWaiting(t.getStatus()))
                .map(t -> lobbyService.toTableSummary(t, hostNames.get(t.getHostId())))
                .toList();

        List<PrivateInvitationDto> privateInvitations = buildPrivateInvitations(userId, hostNames);
        List<HomeDashboardResponse.GameHistoryDto> recentHistory = buildRecentHistory(userId);
        List<Notification> recentNotifications = fetchRecentNotifications(userId);
        List<LeaderboardItemResponse> leaderboardTop = fetchLeaderboardTop();

        HomeDashboardResponse.LiveStatsDto liveStats = buildLiveStats();

        return HomeDashboardResponse.builder()
                .userProfile(profile)
                .wallet(wallet)
                .activeGame(activeGame)
                .publicTables(publicTables)
                .privateInvitations(privateInvitations)
                .runningGames(runningGames)
                .waitingGames(waitingGames)
                .quickPlayOptions(QUICK_PLAY)
                .recentHistory(recentHistory)
                .recentNotifications(recentNotifications)
                .leaderboardTop(leaderboardTop)
                .liveStats(liveStats)
                .build();
    }

    private HomeDashboardResponse.UserProfileDto buildProfile(User user, String userId) {
        return HomeDashboardResponse.UserProfileDto.builder()
                .userId(userId)
                .displayName(user != null ? user.getDisplayName() : "Player")
                .email(user != null ? user.getEmail() : "")
                .avatarUrl(user != null && user.getAvatarUrl() != null
                        ? user.getAvatarUrl()
                        : "https://api.dicebear.com/7.x/avataaars/svg?seed=" + userId)
                .isOnline(user == null || user.isOnline())
                .role(user != null && user.getRole() != null ? user.getRole().name() : "PLAYER")
                .build();
    }

    private HomeDashboardResponse.WalletSummaryDto buildWalletSummary(String userId, WalletBalanceResponse balance) {
        long balancePaise = balance.getBalancePaise();
        long deposited = sumLedgerByType(userId, LedgerEntryType.DEPOSIT, LedgerEntryType.BONUS, LedgerEntryType.ADMIN_ADJUSTMENT);
        long withdrawn = sumLedgerByType(userId, LedgerEntryType.WITHDRAWAL);

        return HomeDashboardResponse.WalletSummaryDto.builder()
                .balancePaise(balancePaise)
                .formattedBalance(String.format("₹%.2f", balancePaise / 100.0))
                .totalDepositedPaise(deposited)
                .totalWithdrawnPaise(withdrawn)
                .build();
    }

    private long sumLedgerByType(String userId, LedgerEntryType... types) {
        Set<LedgerEntryType> typeSet = Set.of(types);
        return ledgerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 500))
                .stream()
                .filter(entry -> entry.getType() != null && typeSet.contains(entry.getType()))
                .mapToLong(entry -> {
                    if (entry.getType() == LedgerEntryType.WITHDRAWAL) {
                        return Math.abs(entry.getAmountPaise());
                    }
                    return Math.max(entry.getAmountPaise(), 0L);
                })
                .sum();
    }

    private Optional<Table> findActiveTableForUser(String userId) {
        List<Table> seated = tableRepository.findBySeatedPlayerIdsContainingAndStatusIn(
                userId, TableStatusGroups.userActiveList());
        return seated.stream()
                .filter(t -> t.getStatus() != TableStatus.CLOSED)
                .findFirst();
    }

    private HomeDashboardResponse.ActiveGameDto toActiveGameDto(Table table, String userId) {
        int seatIndex = table.getSeatedPlayerIds() != null
                ? table.getSeatedPlayerIds().indexOf(userId)
                : -1;
        return HomeDashboardResponse.ActiveGameDto.builder()
                .tableId(table.getId())
                .tableName(table.getTableName() != null ? table.getTableName() : "Table")
                .bootAmountPaise(table.getBootAmountPaise())
                .status(table.getStatus() != null ? table.getStatus().name() : TableStatus.WAITING.name())
                .seatedCount(table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0)
                .maxPlayers(table.getMaxPlayers())
                .userSeatIndex(seatIndex)
                .tableType(table.getTableType() != null ? table.getTableType().name() : TableType.PUBLIC.name())
                .isHost(userId.equals(table.getHostId()))
                .build();
    }

    private List<PrivateInvitationDto> buildPrivateInvitations(String userId, Map<String, String> hostNames) {
        List<Notification> invites = notificationRepository
                .findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(userId, NotificationType.GAME_INVITE);

        List<PrivateInvitationDto> result = new ArrayList<>();
        for (Notification notification : invites) {
            Map<String, Object> payload = notification.getPayload() != null
                    ? notification.getPayload()
                    : Map.of();
            String tableId = stringVal(payload.get("tableId"));
            String inviteCode = stringVal(payload.get("inviteCode"));

            Table table = null;
            if (tableId != null) {
                table = tableRepository.findById(tableId).orElse(null);
            } else if (inviteCode != null) {
                table = tableRepository.findByInviteCode(inviteCode).orElse(null);
            }

            if (table == null || table.getStatus() == TableStatus.CLOSED) {
                continue;
            }
            if (table.getSeatedPlayerIds() != null && table.getSeatedPlayerIds().contains(userId)) {
                continue;
            }

            String hostId = table.getHostId();
            result.add(PrivateInvitationDto.builder()
                    .notificationId(notification.getId())
                    .tableId(table.getId())
                    .tableName(table.getTableName())
                    .inviteCode(table.getInviteCode())
                    .hostId(hostId)
                    .hostDisplayName(hostNames.getOrDefault(hostId, "Host"))
                    .bootAmountPaise(table.getBootAmountPaise())
                    .gameVariant(table.getGameVariant() != null ? table.getGameVariant().name() : "CLASSIC")
                    .currentPlayerCount(table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0)
                    .maxPlayers(table.getMaxPlayers())
                    .status(table.getStatus() != null ? table.getStatus().name() : TableStatus.WAITING.name())
                    .invitedAt(notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : "")
                    .build());
        }
        return result;
    }

    private List<HomeDashboardResponse.GameHistoryDto> buildRecentHistory(String userId) {
        try {
            return gameHistoryService.getRecentForHomeDashboard(userId, 10);
        } catch (Exception ex) {
            log.warn("Could not fetch game history for user [{}]: {}", userId, ex.getMessage());
            return List.of();
        }
    }

    private List<Notification> fetchRecentNotifications(String userId) {
        try {
            return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10)).getContent();
        } catch (Exception ex) {
            log.warn("Could not fetch notifications for user [{}]: {}", userId, ex.getMessage());
            return List.of();
        }
    }

    private List<LeaderboardItemResponse> fetchLeaderboardTop() {
        try {
            return leaderboardService
                    .getLeaderboard(LeaderboardWindow.WEEKLY, LeaderboardMetric.WINNINGS, PageRequest.of(0, 5))
                    .getContent();
        } catch (Exception ex) {
            log.debug("Leaderboard top fetch note: {}", ex.getMessage());
            return List.of();
        }
    }

    private HomeDashboardResponse.LiveStatsDto buildLiveStats() {
        long onlinePlayers = userRepository.countByIsOnlineTrue();
        if (onlinePlayers == 0) {
            onlinePlayers = 1;
        }
        int running = (int) tableRepository.countByStatusIn(TableStatusGroups.runningList());
        int waiting = (int) tableRepository.countByStatusIn(TableStatusGroups.waitingList());

        return HomeDashboardResponse.LiveStatsDto.builder()
                .onlinePlayers((int) onlinePlayers)
                .runningTablesCount(running)
                .waitingTablesCount(waiting)
                .totalActiveGames(running + waiting)
                .build();
    }

    private Map<String, String> resolveHostNames(List<Table> tables) {
        Set<String> hostIds = tables.stream()
                .map(Table::getHostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> names = new HashMap<>();
        for (String hostId : hostIds) {
            userRepository.findById(hostId).ifPresent(u -> names.put(hostId, u.getDisplayName()));
        }
        return names;
    }

    private void enrichHostNames(List<TableSummaryResponse> tables, Map<String, String> hostNames) {
        for (TableSummaryResponse summary : tables) {
            if (summary.getHostId() != null) {
                summary.setHostDisplayName(hostNames.getOrDefault(summary.getHostId(), "Host"));
            }
        }
    }

    private static String stringVal(Object value) {
        return value != null ? value.toString() : null;
    }
}
