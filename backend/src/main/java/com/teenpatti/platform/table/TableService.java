package com.teenpatti.platform.table;

import com.teenpatti.platform.common.exception.InsufficientBalanceException;
import com.teenpatti.platform.common.exception.OptimisticLockException;
import com.teenpatti.platform.common.exception.PlayerNotSeatedException;
import com.teenpatti.platform.common.exception.TableFullException;
import com.teenpatti.platform.common.exception.TableNotFoundException;
import com.teenpatti.platform.lobby.config.StakeTierConfig;
import com.teenpatti.platform.table.dto.*;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing race-condition-free table seat claiming, buy-in debit/refund flows,
 * mid-hand leave forfeiture contracts, and table state resolution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableService {

    private static final int MAX_OPTIMISTIC_LOCK_RETRIES = 10;

    private final TableRepository tableRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final StakeTierConfig stakeTierConfig;

    /**
     * Atomically claims a seat at a table, debits the buy-in from the user's wallet,
     * and returns the table details. Implements dual rollback protection.
     */
    public JoinTableResponse joinTable(String userId, String tableId) {
        // 1. Initial Read & Validation
        Table initialTable = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

        if (initialTable.getStatus() == TableStatus.CLOSED) {
            throw new TableNotFoundException("Table is closed: " + tableId);
        }

        // Idempotency: If user is already seated, return existing seat details
        if (initialTable.getSeatedPlayerIds().contains(userId)) {
            log.info("IDEMPOTENT JOIN: User [{}] is already seated at table [{}]", userId, tableId);
            int seatIndex = initialTable.getSeatedPlayerIds().indexOf(userId);
            long buyInAmountPaise = stakeTierConfig.getMinBuyInPaise(initialTable.getStakeTier());
            return JoinTableResponse.builder()
                    .tableId(tableId)
                    .seatIndex(seatIndex)
                    .heldBuyInPaise(buyInAmountPaise)
                    .tableDetail(buildTableDetailResponse(initialTable))
                    .build();
        }

        long buyInAmountPaise = stakeTierConfig.getMinBuyInPaise(initialTable.getStakeTier());

        // Pre-validate balance
        WalletBalanceResponse balanceResponse = walletService.getBalance(userId);
        if (balanceResponse.getBalancePaise() < buyInAmountPaise) {
            throw new InsufficientBalanceException(
                    "Insufficient wallet balance. Required buy-in: " + buyInAmountPaise +
                            " paise, available: " + balanceResponse.getBalancePaise() + " paise."
            );
        }

        // 2. Atomic Seat Claim Loop via Optimistic Locking
        Table claimedTable = executeAtomicSeatClaim(userId, tableId, buyInAmountPaise);

        // 3. Debit Buy-In via Wallet Module
        String referenceId = "table:" + tableId + ":buyin:" + userId;
        try {
            walletService.applyLedgerEntry(userId, LedgerEntryType.BET, buyInAmountPaise, referenceId);
        } catch (Exception ex) {
            log.error("Debit failed for user [{}] after seat claim on table [{}]. Triggering seat claim rollback.",
                    userId, tableId, ex);
            rollbackSeatClaim(userId, tableId);
            throw ex;
        }

        int seatIndex = claimedTable.getSeatedPlayerIds().indexOf(userId);
        log.info("User [{}] successfully seated at table [{}] at seat index [{}] with held buy-in {} paise",
                userId, tableId, seatIndex, buyInAmountPaise);

        return JoinTableResponse.builder()
                .tableId(tableId)
                .seatIndex(seatIndex)
                .heldBuyInPaise(buyInAmountPaise)
                .tableDetail(buildTableDetailResponse(claimedTable))
                .build();
    }

    /**
     * Allows a seated player to leave a table. Refunds buy-in if WAITING; forfeits buy-in if IN_PROGRESS.
     */
    public LeaveTableResponse leaveTable(String userId, String tableId) {
        Table initialTable = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

        if (!initialTable.getSeatedPlayerIds().contains(userId)) {
            throw new PlayerNotSeatedException("Player " + userId + " is not seated at table " + tableId);
        }

        TableStatus currentStatus = initialTable.getStatus();
        long buyInAmountPaise = stakeTierConfig.getMinBuyInPaise(initialTable.getStakeTier());

        executeAtomicLeave(userId, tableId);

        if (currentStatus == TableStatus.WAITING) {
            // Full refund
            String referenceId = "table:" + tableId + ":refund:" + userId;
            walletService.applyLedgerEntry(userId, LedgerEntryType.REFUND, buyInAmountPaise, referenceId);
            log.info("User [{}] left WAITING table [{}]. Fully refunded buy-in {} paise", userId, tableId, buyInAmountPaise);
            return LeaveTableResponse.builder()
                    .tableId(tableId)
                    .refunded(true)
                    .refundAmountPaise(buyInAmountPaise)
                    .message("Successfully left table. Held buy-in fully refunded.")
                    .build();
        } else {
            // Mid-hand forfeiture (no refund)
            log.info("User [{}] left IN_PROGRESS table [{}]. Buy-in {} paise forfeited to pot.", userId, tableId, buyInAmountPaise);
            return LeaveTableResponse.builder()
                    .tableId(tableId)
                    .refunded(false)
                    .refundAmountPaise(0L)
                    .message("Left table mid-hand. Buy-in forfeited to table pot.")
                    .build();
        }
    }

    /**
     * Returns full detailed table state with resolved user display names.
     */
    public TableDetailResponse getTableDetails(String userId, String tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

        if (table.getTableType() == TableType.PRIVATE && !table.getSeatedPlayerIds().contains(userId)) {
            throw new TableNotFoundException("Private table not found or user not authorized.");
        }

        return buildTableDetailResponse(table);
    }

    /**
     * Scheduled cleanup helper to close stale WAITING tables older than timeout and refund seated players.
     */
    public int cleanupStaleWaitingTables(int waitingTimeoutMinutes) {
        Instant cutoffTime = Instant.now().minus(Duration.ofMinutes(waitingTimeoutMinutes));
        List<Table> staleTables = tableRepository.findAll().stream()
                .filter(t -> t.getStatus() == TableStatus.WAITING)
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isBefore(cutoffTime))
                .filter(t -> t.getSeatedPlayerIds() == null || t.getSeatedPlayerIds().size() < 2)
                .toList();

        int closedCount = 0;
        for (Table staleTable : staleTables) {
            boolean closed = processSingleStaleTableCleanup(staleTable.getId());
            if (closed) {
                closedCount++;
            }
        }
        return closedCount;
    }

    private boolean processSingleStaleTableCleanup(String tableId) {
        int attempts = 0;
        while (attempts < MAX_OPTIMISTIC_LOCK_RETRIES) {
            attempts++;
            try {
                Optional<Table> opt = tableRepository.findById(tableId);
                if (opt.isEmpty()) {
                    return false;
                }
                Table table = opt.get();
                // Re-verify condition at exact moment of closing
                if (table.getStatus() != TableStatus.WAITING || (table.getSeatedPlayerIds() != null && table.getSeatedPlayerIds().size() >= 2)) {
                    log.info("Stale table cleanup skipped for table [{}] - player joined recently.", tableId);
                    return false;
                }

                long buyInAmountPaise = stakeTierConfig.getMinBuyInPaise(table.getStakeTier());
                List<String> playersToRefund = new ArrayList<>(table.getSeatedPlayerIds());

                table.getSeatedPlayerIds().clear();
                table.setStatus(TableStatus.CLOSED);
                tableRepository.save(table);

                // Refund all seated players
                for (String playerId : playersToRefund) {
                    String refId = "table:" + tableId + ":cleanup_refund:" + playerId;
                    walletService.applyLedgerEntry(playerId, LedgerEntryType.REFUND, buyInAmountPaise, refId);
                }

                log.info("Successfully cleaned up stale WAITING table [{}], refunded {} players", tableId, playersToRefund.size());
                return true;
            } catch (OptimisticLockingFailureException ex) {
                backoffDelay();
            }
        }
        return false;
    }

    private Table executeAtomicSeatClaim(String userId, String tableId, long buyInAmountPaise) {
        int attempts = 0;
        while (attempts < MAX_OPTIMISTIC_LOCK_RETRIES) {
            attempts++;
            try {
                Table table = tableRepository.findById(tableId)
                        .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

                if (table.getStatus() == TableStatus.CLOSED) {
                    throw new TableNotFoundException("Table is closed: " + tableId);
                }

                if (table.getSeatedPlayerIds().contains(userId)) {
                    return table;
                }

                if (table.getSeatedPlayerIds().size() >= table.getMaxPlayers()) {
                    throw new TableFullException("Table is full (max players: " + table.getMaxPlayers() + ")");
                }

                table.getSeatedPlayerIds().add(userId);
                return tableRepository.save(table);
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Optimistic lock collision on seat claim attempt {}/{} for user [{}] on table [{}]",
                        attempts, MAX_OPTIMISTIC_LOCK_RETRIES, userId, tableId);
                if (attempts >= MAX_OPTIMISTIC_LOCK_RETRIES) {
                    throw new TableFullException("Table update conflict: Table became full or was modified by another player.");
                }
                backoffDelay();
            }
        }
        throw new TableFullException("Failed to claim seat due to high concurrency collisions.");
    }

    private void rollbackSeatClaim(String userId, String tableId) {
        int attempts = 0;
        while (attempts < MAX_OPTIMISTIC_LOCK_RETRIES) {
            attempts++;
            try {
                Optional<Table> opt = tableRepository.findById(tableId);
                if (opt.isEmpty()) return;
                Table table = opt.get();
                if (table.getSeatedPlayerIds().remove(userId)) {
                    tableRepository.save(table);
                    log.info("Rolled back seat claim for user [{}] on table [{}]", userId, tableId);
                }
                return;
            } catch (OptimisticLockingFailureException ex) {
                backoffDelay();
            }
        }
    }

    private void executeAtomicLeave(String userId, String tableId) {
        int attempts = 0;
        while (attempts < MAX_OPTIMISTIC_LOCK_RETRIES) {
            attempts++;
            try {
                Table table = tableRepository.findById(tableId)
                        .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

                if (!table.getSeatedPlayerIds().contains(userId)) {
                    return;
                }

                table.getSeatedPlayerIds().remove(userId);
                if (table.getStatus() == TableStatus.IN_PROGRESS) {
                    if (!table.getLeftMidHandPlayerIds().contains(userId)) {
                        table.getLeftMidHandPlayerIds().add(userId);
                    }
                }

                tableRepository.save(table);
                return;
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Optimistic lock collision on leave attempt {}/{} for user [{}] on table [{}]",
                        attempts, MAX_OPTIMISTIC_LOCK_RETRIES, userId, tableId);
                if (attempts >= MAX_OPTIMISTIC_LOCK_RETRIES) {
                    throw new OptimisticLockException("Failed to update table on leave due to concurrency collisions.");
                }
                backoffDelay();
            }
        }
    }

    private TableDetailResponse buildTableDetailResponse(Table table) {
        List<String> seatedIds = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
        Map<String, String> displayNameMap = userRepository.findAllById(seatedIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getDisplayName() != null ? u.getDisplayName() : "Player"));

        List<SeatedPlayerResponse> seatedPlayers = seatedIds.stream()
                .map(id -> SeatedPlayerResponse.builder()
                        .userId(id)
                        .displayName(displayNameMap.getOrDefault(id, "Player"))
                        .build())
                .toList();

        return TableDetailResponse.builder()
                .tableId(table.getId())
                .tableType(table.getTableType())
                .stakeTier(table.getStakeTier())
                .maxPlayers(table.getMaxPlayers())
                .currentPlayerCount(seatedPlayers.size())
                .status(table.getStatus())
                .potPaise(table.getPotPaise())
                .currentHandId(table.getCurrentHandId())
                .seatedPlayers(seatedPlayers)
                .leftMidHandPlayerIds(table.getLeftMidHandPlayerIds() != null ? table.getLeftMidHandPlayerIds() : List.of())
                .build();
    }

    private void backoffDelay() {
        try {
            Thread.sleep((long) (Math.random() * 40 + 10));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new OptimisticLockException("Thread interrupted during table operation retry");
        }
    }
}
