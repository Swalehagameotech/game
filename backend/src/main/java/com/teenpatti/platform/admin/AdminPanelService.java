package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminDashboardResponse;
import com.teenpatti.platform.admin.dto.AdminUserDetailDto;
import com.teenpatti.platform.admin.dto.AdminUserSummaryDto;
import com.teenpatti.platform.common.exception.UserNotFoundException;
import com.teenpatti.platform.game.GameHistory;
import com.teenpatti.platform.game.GameHistoryRepository;
import com.teenpatti.platform.game.dto.GameHistorySummaryDto;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableStatusGroups;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.transaction.WithdrawalStatus;
import com.teenpatti.platform.transaction.WithdrawalRequestRepository;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.WalletRepository;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.WalletTransaction;
import com.teenpatti.platform.wallet.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPanelService {

    private final UserRepository userRepository;
    private final TableRepository tableRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final GameHistoryRepository gameHistoryRepository;

    public AdminDashboardResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long onlineUsers = userRepository.countByIsOnlineTrue();
        long totalTables = tableRepository.count();
        long runningGames = tableRepository.countByStatusIn(TableStatusGroups.runningList());
        long waitingGames = tableRepository.countByStatusIn(TableStatusGroups.waitingList());
        long closedGames = tableRepository.countByStatus(TableStatus.CLOSED);
        long pendingWithdrawals = withdrawalRequestRepository.countByStatus(WithdrawalStatus.PENDING_ADMIN_REVIEW);

        long totalWalletBalance = walletRepository.findAll().stream()
                .mapToLong(w -> w.getBalancePaise())
                .sum();

        long totalWalletTransactions = walletTransactionRepository.count();

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .onlineUsers(onlineUsers)
                .totalTables(totalTables)
                .runningGames(runningGames)
                .waitingGames(waitingGames)
                .closedGames(closedGames)
                .pendingWithdrawals(pendingWithdrawals)
                .totalWalletBalance(totalWalletBalance)
                .totalWalletTransactions(totalWalletTransactions)
                .build();
    }

    public Page<AdminUserSummaryDto> searchUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users;
        if (query != null && !query.isBlank()) {
            users = userRepository.searchByEmailOrDisplayNameOrPhoneNumber(query.trim(), pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        return users.map(this::toUserSummary);
    }

    public AdminUserSummaryDto getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        return toUserSummary(user);
    }

    public AdminUserDetailDto getUserDetails(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        AdminUserSummaryDto profile = toUserSummary(user);

        List<WalletTransaction> walletTransactions = walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(50)
                .toList();

        List<GameHistory> histories = gameHistoryRepository.findByPlayerIdsContainingOrderByEndedAtDesc(
                        userId,
                        PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "endedAt")))
                .getContent();
        Map<String, String> displayNames = userRepository.findAllById(
                        histories.stream().map(GameHistory::getWinnerId).filter(id -> id != null && !id.isBlank()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u.getDisplayName() != null ? u.getDisplayName() : "Player"));

        List<GameHistorySummaryDto> gameHistory = histories.stream()
                .map(h -> GameHistorySummaryDto.builder()
                        .id(h.getId())
                        .handId(h.getHandId())
                        .tableId(h.getTableId())
                        .tableName(h.getTableId())
                        .roundNumber(h.getRoundNumber())
                        .variant(h.getVariant() != null ? h.getVariant().name() : "CLASSIC")
                        .winnerId(h.getWinnerId())
                        .winnerDisplayName(displayNames.getOrDefault(h.getWinnerId(), "Player"))
                        .result(userId.equals(h.getWinnerId()) ? "WON" : "LOST")
                        .potAmountPaise(h.getPotAmountPaise())
                        .winnerPayoutPaise(h.getWinnerPayoutPaise())
                        .rakeAmountPaise(h.getRakeAmountPaise())
                        .netAmountPaise(userId.equals(h.getWinnerId()) ? h.getWinnerPayoutPaise() : 0L)
                        .winningCategory(h.getWinningCategory() != null ? h.getWinningCategory().name() : null)
                        .winningHandDescription(h.getHandSummary() != null ? h.getHandSummary().getWinningHandName() : null)
                        .foldWin(false)
                        .playerCount(h.getPlayerIds() != null ? h.getPlayerIds().size() : 0)
                        .playedAt(h.getEndedAt() != null ? h.getEndedAt().toString() : null)
                        .build())
                .toList();

        return AdminUserDetailDto.builder()
                .profile(profile)
                .walletTransactions(walletTransactions)
                .gameHistory(gameHistory)
                .build();
    }

    public List<WalletTransaction> getUserWalletHistory(String userId) {
        return walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private AdminUserSummaryDto toUserSummary(User user) {
        long balance = walletService.getBalance(user.getId()).getBalancePaise();
        long gamesWon = gameHistoryRepository.countByWinnerId(user.getId());
        long gamesPlayed = Math.max(user.getMatchesPlayedCount(), gameHistoryRepository.countByPlayerIdsContaining(user.getId()));
        long gamesLost = Math.max(0L, gamesPlayed - gamesWon);
        long totalWinnings = walletTransactionRepository
                .findByUserIdAndTypeIn(user.getId(), List.of(LedgerEntryType.WIN))
                .stream()
                .mapToLong(WalletTransaction::getEffectiveAmountPaise)
                .sum();
        long totalDeposits = walletTransactionRepository
                .findByUserIdAndTypeIn(user.getId(), List.of(LedgerEntryType.DEPOSIT, LedgerEntryType.BONUS, LedgerEntryType.ADMIN_ADJUSTMENT))
                .stream()
                .filter(tx -> tx.getEffectiveAmountPaise() > 0)
                .mapToLong(WalletTransaction::getEffectiveAmountPaise)
                .sum();
        long totalWithdrawals = withdrawalRequestRepository.findByUserId(user.getId()).stream()
                .filter(w -> w.getStatus() == WithdrawalStatus.APPROVED || w.getStatus() == WithdrawalStatus.PAID_OUT)
                .mapToLong(w -> Math.max(0L, w.getAmountPaise()))
                .sum();

        var activeTables = tableRepository.findBySeatedPlayerIdsContainingAndStatusIn(user.getId(), TableStatusGroups.runningList());
        var currentTable = activeTables.stream()
                .max(Comparator.comparing(t -> t.getUpdatedAt() != null ? t.getUpdatedAt() : t.getCreatedAt()))
                .orElse(null);

        return AdminUserSummaryDto.builder()
                .id(user.getId())
                .profileImage(user.getAvatarUrl())
                .username(user.getDisplayName())
                .fullName(user.getDisplayName())
                .mobileNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .accountStatus(user.getAccountStatus())
                .online(user.isOnline())
                .walletBalancePaise(balance)
                .matchesPlayedCount((int) gamesPlayed)
                .gamesWon(gamesWon)
                .gamesLost(gamesLost)
                .totalWinningsPaise(totalWinnings)
                .totalDepositsPaise(totalDeposits)
                .totalWithdrawalsPaise(totalWithdrawals)
                .currentTableId(currentTable != null ? currentTable.getId() : null)
                .currentGameId(currentTable != null ? currentTable.getCurrentHandId() : null)
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastSeenAt())
                .build();
    }
}
