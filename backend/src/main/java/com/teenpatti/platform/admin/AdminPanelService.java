package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminDashboardResponse;
import com.teenpatti.platform.admin.dto.AdminUserSummaryDto;
import com.teenpatti.platform.common.exception.UserNotFoundException;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableStatusGroups;
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

import java.util.List;

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
            users = userRepository.searchByEmailOrDisplayName(query.trim(), pageable);
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

    public List<WalletTransaction> getUserWalletHistory(String userId) {
        return walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private AdminUserSummaryDto toUserSummary(User user) {
        long balance = walletService.getBalance(user.getId()).getBalancePaise();
        return AdminUserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .accountStatus(user.getAccountStatus())
                .online(user.isOnline())
                .walletBalancePaise(balance)
                .matchesPlayedCount(user.getMatchesPlayedCount())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
