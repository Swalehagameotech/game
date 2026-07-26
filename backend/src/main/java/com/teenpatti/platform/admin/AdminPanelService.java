package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminDashboardResponse;
import com.teenpatti.platform.common.exception.InsufficientBalanceException;
import com.teenpatti.platform.common.exception.UserNotFoundException;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.WalletTransaction;
import com.teenpatti.platform.wallet.WalletTransactionRepository;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPanelService {

    private final UserRepository userRepository;
    private final TableRepository tableRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final AdminLogRepository adminLogRepository;
    private final WebSocketEventPublisher webSocketEventPublisher;

    public AdminDashboardResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long onlineUsers = userRepository.findAll().stream().filter(User::isOnline).count();
        long totalTables = tableRepository.count();
        long runningGames = tableRepository.countByStatus(TableStatus.IN_PROGRESS);
        long waitingGames = tableRepository.countByStatus(TableStatus.WAITING);

        long totalWalletBalance = userRepository.findAll().stream()
                .mapToLong(User::getWalletBalance)
                .sum();

        long totalWalletTransactions = walletTransactionRepository.count();

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .onlineUsers(onlineUsers)
                .totalTables(totalTables)
                .runningGames(runningGames)
                .waitingGames(waitingGames)
                .totalWalletBalance(totalWalletBalance)
                .totalWalletTransactions(totalWalletTransactions)
                .build();
    }

    public Page<User> searchUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (query != null && !query.isBlank()) {
            String q = query.trim();
            return userRepository.findAll(pageable); // Can be refined with Mongo regex search
        }
        return userRepository.findAll(pageable);
    }

    public User getUserProfile(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }

    public List<WalletTransaction> getUserWalletHistory(String userId) {
        return walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public User addMoneyToUserWallet(String adminId, String userId, long amountPaise, String reason) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        User user = getUserProfile(userId);
        long newBalance = user.getWalletBalance() + amountPaise;
        user.setWalletBalance(newBalance);
        User savedUser = userRepository.save(user);

        // 1. Log Wallet Transaction
        WalletTransaction tx = WalletTransaction.builder()
                .userId(userId)
                .amount(amountPaise)
                .transactionType("Credit")
                .reason(reason != null && !reason.isBlank() ? reason : "Admin Deposit")
                .balanceAfterTransaction(newBalance)
                .createdAt(Instant.now())
                .build();
        walletTransactionRepository.save(tx);

        // 2. Log Admin Audit Log
        AdminLog adminLog = AdminLog.builder()
                .adminId(adminId)
                .userId(userId)
                .action("ADD_MONEY")
                .amount(amountPaise)
                .timestamp(Instant.now())
                .build();
        adminLogRepository.save(adminLog);

        // 3. Real-Time Broadcast
        webSocketEventPublisher.publishWalletUpdated(userId, newBalance);

        log.info("Admin [{}] added {} paise to user [{}] wallet. New balance: {}", adminId, amountPaise, userId, newBalance);
        return savedUser;
    }

    public User deductMoneyFromUserWallet(String adminId, String userId, long amountPaise, String reason) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        User user = getUserProfile(userId);
        if (user.getWalletBalance() < amountPaise) {
            throw new InsufficientBalanceException("User has insufficient wallet balance. Current: " + user.getWalletBalance() + " paise");
        }

        long newBalance = user.getWalletBalance() - amountPaise;
        user.setWalletBalance(newBalance);
        User savedUser = userRepository.save(user);

        // 1. Log Wallet Transaction
        WalletTransaction tx = WalletTransaction.builder()
                .userId(userId)
                .amount(amountPaise)
                .transactionType("Debit")
                .reason(reason != null && !reason.isBlank() ? reason : "Admin Deduction")
                .balanceAfterTransaction(newBalance)
                .createdAt(Instant.now())
                .build();
        walletTransactionRepository.save(tx);

        // 2. Log Admin Audit Log
        AdminLog adminLog = AdminLog.builder()
                .adminId(adminId)
                .userId(userId)
                .action("DEDUCT_MONEY")
                .amount(amountPaise)
                .timestamp(Instant.now())
                .build();
        adminLogRepository.save(adminLog);

        // 3. Real-Time Broadcast
        webSocketEventPublisher.publishWalletUpdated(userId, newBalance);

        log.info("Admin [{}] deducted {} paise from user [{}] wallet. New balance: {}", adminId, amountPaise, userId, newBalance);
        return savedUser;
    }

    public User blockUser(String adminId, String userId) {
        User user = getUserProfile(userId);
        user.setAccountStatus(AccountStatus.BLOCKED);
        User saved = userRepository.save(user);

        AdminLog adminLog = AdminLog.builder()
                .adminId(adminId)
                .userId(userId)
                .action("BLOCK_USER")
                .amount(0L)
                .timestamp(Instant.now())
                .build();
        adminLogRepository.save(adminLog);

        log.info("Admin [{}] blocked user [{}]", adminId, userId);
        return saved;
    }

    public User unblockUser(String adminId, String userId) {
        User user = getUserProfile(userId);
        user.setAccountStatus(AccountStatus.ACTIVE);
        User saved = userRepository.save(user);

        AdminLog adminLog = AdminLog.builder()
                .adminId(adminId)
                .userId(userId)
                .action("UNBLOCK_USER")
                .amount(0L)
                .timestamp(Instant.now())
                .build();
        adminLogRepository.save(adminLog);

        log.info("Admin [{}] unblocked user [{}]", adminId, userId);
        return saved;
    }
}
