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
    private final com.teenpatti.platform.websocket.WebSocketEventPublisher webSocketEventPublisher;
    private final com.teenpatti.platform.game.GameEngineService gameEngineService;
    private final com.teenpatti.platform.game.engine.HandContextManager handContextManager;
    private final com.teenpatti.platform.game.betting.BettingLogicService bettingLogicService;
    private final com.teenpatti.platform.websocket.GameStateProjector gameStateProjector;
    private final PublicTableCountdownService publicTableCountdownService;
    private final TableCountdownRegistry countdownRegistry;
    private final PublicTableService publicTableService;
    private final PrivateTableService privateTableService;
    private final HostManagementService hostManagementService;
    private final com.teenpatti.platform.websocket.GameBroadcastService gameBroadcastService;

    public List<Table> getTablesByStatus(TableStatus status) {
        return tableRepository.findByStatus(status);
    }

    public List<Table> getTablesByTypeAndStatus(TableType type, TableStatus status) {
        return tableRepository.findByTableTypeAndStatus(type, status);
    }

    public List<Table> getActiveJoinableTables() {
        return tableRepository.findAll().stream()
                .filter(t -> t.getStatus() != TableStatus.CLOSED)
                .toList();
    }

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

        if (initialTable.getTableType() == TableType.PUBLIC) {
            publicTableService.assertJoinable(initialTable);
        } else if (initialTable.getTableType() == TableType.PRIVATE) {
            privateTableService.assertJoinable(initialTable);
        }

        // Idempotency: If user is already seated, return existing seat details
        if (initialTable.getSeatedPlayerIds().contains(userId)) {
            log.info("IDEMPOTENT JOIN: User [{}] is already seated at table [{}]", userId, tableId);
            int seatIndex = initialTable.getSeatedPlayerIds().indexOf(userId);
            long bootPaise = resolveBootPaise(initialTable);
            return JoinTableResponse.builder()
                    .tableId(tableId)
                    .seatIndex(seatIndex)
                    .heldBuyInPaise(bootPaise)
                    .tableDetail(buildTableDetailResponse(initialTable))
                    .build();
        }

        long bootPaise = resolveBootPaise(initialTable);

        // Pre-validate balance for boot (collected when host starts game)
        WalletBalanceResponse balanceResponse = walletService.getBalance(userId);
        if (balanceResponse.getBalancePaise() < bootPaise) {
            throw new InsufficientBalanceException(
                    "Insufficient wallet balance. Required boot: " + bootPaise +
                            " paise, available: " + balanceResponse.getBalancePaise() + " paise."
            );
        }

        // 2. Atomic Seat Claim Loop via Optimistic Locking
        Table claimedTable = executeAtomicSeatClaim(userId, tableId);

        int seatIndex = claimedTable.getSeatedPlayerIds().indexOf(userId);
        log.info("User [{}] successfully seated at table [{}] at seat index [{}]",
                userId, tableId, seatIndex);

        webSocketEventPublisher.publishPlayerJoined(tableId, userId, claimedTable.getSeatedPlayerIds().size());

        int minReq = claimedTable.getMinPlayers() > 0 ? claimedTable.getMinPlayers() : 3;
        gameEngineService.handlePlayerSeated(tableId, claimedTable.getSeatedPlayerIds().size(), minReq);
        publicTableService.afterPublicTableMutation(tableId);

        return JoinTableResponse.builder()
                .tableId(tableId)
                .seatIndex(seatIndex)
                .heldBuyInPaise(bootPaise)
                .tableDetail(buildTableDetailResponse(claimedTable))
                .build();
    }

    /**
     * Host-only manual start: shuffle, deal, collect boot, begin RUNNING hand.
     */
    public TableDetailResponse startGame(String userId, String tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));
        if (table.getTableType() == TableType.PRIVATE) {
            privateTableService.assertHostCanStart(table, userId);
        } else {
            publicTableService.assertHostStartAllowed(table);
        }
        Table saved = gameEngineService.startGame(userId, tableId);
        return buildTableDetailResponse(saved);
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
        boolean wasHost = userId.equals(initialTable.getHostId());

        if (isActiveHandStatus(currentStatus)) {
            gameEngineService.handlePlayerLeftMidHand(tableId, userId);
        }
        executeAtomicLeave(userId, tableId);

        Table refreshed = tableRepository.findById(tableId).orElse(null);
        int updatedCount = refreshed != null && refreshed.getSeatedPlayerIds() != null
                ? refreshed.getSeatedPlayerIds().size() : 0;

        webSocketEventPublisher.publishPlayerLeft(tableId, userId, updatedCount);

        if (refreshed != null) {
            // Host transfer only between hands — during RUNNING host privileges are irrelevant.
            if (wasHost && !isActiveHandStatus(currentStatus)) {
                hostManagementService.transferHostAfterDeparture(tableId, userId);
            }

            webSocketEventPublisher.publishTableUpdated(tableId, buildTableDetailResponse(
                    tableRepository.findById(tableId).orElse(refreshed)));
        }

        int minReq = initialTable.getMinPlayers() > 0 ? initialTable.getMinPlayers() : 3;
        gameEngineService.handlePlayerLeft(tableId, updatedCount, minReq);
        publicTableService.afterPublicTableMutation(tableId);

        if (updatedCount == 0) {
            closeEmptyTable(tableId);
        }

        if (isActiveHandStatus(currentStatus)) {
            log.info("User [{}] left active hand on table [{}].", userId, tableId);
            return LeaveTableResponse.builder()
                    .tableId(tableId)
                    .refunded(false)
                    .refundAmountPaise(0L)
                    .message("Left table mid-hand.")
                    .build();
        }

        log.info("User [{}] left WAITING table [{}].", userId, tableId);
        return LeaveTableResponse.builder()
                .tableId(tableId)
                .refunded(false)
                .refundAmountPaise(0L)
                .message("Successfully left table.")
                .build();
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
     * Personalized live hand projection (same shape as WS STATE_UPDATE) for refresh/reconnect.
     */
    public com.teenpatti.platform.websocket.dto.PlayerViewGameState getLivePlayerView(String userId, String tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

        if (table.getSeatedPlayerIds() == null || !table.getSeatedPlayerIds().contains(userId)) {
            throw new PlayerNotSeatedException("Player is not seated at table: " + tableId);
        }

        com.teenpatti.platform.game.engine.BettingRoundEngine engine =
                gameEngineService.ensureActiveEngine(tableId).orElse(null);
        return gameStateProjector.createProjection(table, engine, userId);
    }

    /**
     * Dedicated server-authoritative betting payload for current player.
     */
    public com.teenpatti.platform.game.betting.BettingState getBettingState(String userId, String tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

        if (table.getSeatedPlayerIds() == null || !table.getSeatedPlayerIds().contains(userId)) {
            throw new PlayerNotSeatedException("Player is not seated at table: " + tableId);
        }

        com.teenpatti.platform.game.engine.BettingRoundEngine engine = gameEngineService.ensureActiveEngine(tableId)
                .orElseThrow(() -> new IllegalStateException("No active hand currently in progress"));
        return bettingLogicService.buildBettingState(table, engine, userId);
    }

    /**
     * Blind → Seen for the authenticated seated player. Returns only their three cards.
     */
    public com.teenpatti.platform.game.dto.SeeCardsResponse seeCards(String userId, String tableId) {
        return gameEngineService.seeCards(userId, tableId);
    }

    /**
     * REST fallback for Blind / Chaal / Raise / Pack / Show / Side Show.
     * Returns updated betting state for the acting player.
     */
    public com.teenpatti.platform.game.betting.BettingState processPlayerAction(
            String userId, String tableId, String actionType, long amountPaise) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));
        if (table.getSeatedPlayerIds() == null || !table.getSeatedPlayerIds().contains(userId)) {
            throw new PlayerNotSeatedException("Player is not seated at table: " + tableId);
        }

        String rejection = gameEngineService.processAction(tableId, userId, actionType, amountPaise);
        if (rejection != null) {
            throw new IllegalStateException(rejection);
        }

        com.teenpatti.platform.game.engine.BettingRoundEngine engine = handContextManager.getEngine(tableId)
                .orElseGet(() -> gameEngineService.ensureActiveEngine(tableId).orElse(null));
        if (engine == null) {
            // Hand may have finished after SHOW/PACK — return last known empty actions.
            return com.teenpatti.platform.game.betting.BettingState.builder()
                    .tableId(tableId)
                    .userId(userId)
                    .playerState("PACKED")
                    .potPaise(0)
                    .myTurn(false)
                    .allowedActions(java.util.List.of())
                    .build();
        }
        Table refreshed = tableRepository.findById(tableId).orElse(table);
        return bettingLogicService.buildBettingState(refreshed, engine, userId);
    }

    /**
     * Returns sanitized active hand metadata — no deck or private card data.
     */
    public com.teenpatti.platform.game.dto.GameSessionSummaryDto getActiveGameSession(String userId, String tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

        if (table.getTableType() == TableType.PRIVATE && !table.getSeatedPlayerIds().contains(userId)) {
            throw new TableNotFoundException("Private table not found or user not authorized.");
        }
        if (!table.getSeatedPlayerIds().contains(userId)) {
            throw new com.teenpatti.platform.common.exception.PlayerNotSeatedException(
                    "Player is not seated at table: " + tableId);
        }

        return gameEngineService.getActiveSession(tableId)
                .orElseThrow(() -> new TableNotFoundException("No active game session on this table"));
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

    public void deleteTable(String userId, String tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

        if (table.getHostId() != null && !table.getHostId().equals(userId)) {
            throw new IllegalStateException("Only the table creator can delete this table.");
        }

        if (table.getStatus() == TableStatus.IN_PROGRESS
                || table.getStatus() == TableStatus.PLAYING
                || table.getStatus() == TableStatus.RUNNING
                || table.getStatus() == TableStatus.STARTING) {
            throw new IllegalStateException("Cannot delete a table while a hand is in progress.");
        }

        tableRepository.deleteById(tableId);
        log.info("Creator/Host [{}] deleted table [{}]", userId, tableId);
        webSocketEventPublisher.publishTableDeleted(tableId);
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

    private Table executeAtomicSeatClaim(String userId, String tableId) {
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

                TableSeatHelper.assignSeat(table, userId);
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
                if (table.getSeatedPlayerIds().contains(userId)) {
                    TableSeatHelper.removeSeat(table, userId);
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

                TableSeatHelper.removeSeat(table, userId);
                if (table.getDisconnectedPlayerIds() != null) {
                    table.getDisconnectedPlayerIds().remove(userId);
                }
                if (isActiveHandStatus(table.getStatus())) {
                    if (!table.getLeftMidHandPlayerIds().contains(userId)) {
                        table.getLeftMidHandPlayerIds().add(userId);
                    }
                }

                int minReq = table.getMinPlayers() > 0 ? table.getMinPlayers() : 3;
                if (table.getSeatedPlayerIds().size() < minReq
                        && table.getStatus() == TableStatus.ROUND_END) {
                    table.setStatus(TableStatus.WAITING);
                }

                if (table.getSeatedPlayerIds().isEmpty()) {
                    table.setStatus(TableStatus.CLOSED);
                    table.setCountdownSeconds(0);
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

    private void closeEmptyTable(String tableId) {
        countdownRegistry.cancel(tableId);
        tableRepository.findById(tableId).ifPresent(table -> {
            table.setStatus(TableStatus.CLOSED);
            table.setCountdownSeconds(0);
            tableRepository.save(table);
            webSocketEventPublisher.publishTableClosed(tableId);
            log.info("Table [{}] closed — all players left", tableId);
        });
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
                .tableName(table.getTableName())
                .hostId(table.getHostId())
                .tableType(table.getTableType())
                .stakeTier(table.getStakeTier())
                .minPlayers(table.getMinPlayers() > 0 ? table.getMinPlayers() : 3)
                .maxPlayers(table.getMaxPlayers())
                .currentPlayerCount(seatedPlayers.size())
                .status(table.getStatus())
                .potPaise(table.getPotPaise())
                .bootAmountPaise(resolveBootPaise(table))
                .currentHandId(table.getCurrentHandId())
                .currentTurnUserId(table.getCurrentTurnUserId())
                .countdownSeconds(table.getCountdownSeconds())
                .inviteCode(table.getInviteCode())
                .seatedPlayers(seatedPlayers)
                .leftMidHandPlayerIds(table.getLeftMidHandPlayerIds() != null ? table.getLeftMidHandPlayerIds() : List.of())
                .disconnectedPlayerIds(table.getDisconnectedPlayerIds() != null ? table.getDisconnectedPlayerIds() : List.of())
                .build();
    }

    /**
     * Marks a seated player as disconnected (grace period). Does not remove the seat.
     */
    public void markPlayerDisconnected(String userId, String tableId, int graceSeconds) {
        int attempts = 0;
        while (attempts++ < MAX_OPTIMISTIC_LOCK_RETRIES) {
            try {
                Table table = tableRepository.findById(tableId)
                        .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));
                if (!table.getSeatedPlayerIds().contains(userId)) {
                    return;
                }
                if (table.getDisconnectedPlayerIds() == null) {
                    table.setDisconnectedPlayerIds(new ArrayList<>());
                }
                if (!table.getDisconnectedPlayerIds().contains(userId)) {
                    table.getDisconnectedPlayerIds().add(userId);
                    tableRepository.save(table);
                }
                webSocketEventPublisher.publishPlayerDisconnected(tableId, userId, graceSeconds);
                webSocketEventPublisher.publishTableUpdated(tableId, buildTableDetailResponse(table));
                gameBroadcastService.broadcastTableState(tableId);
                return;
            } catch (OptimisticLockingFailureException ex) {
                backoffDelay();
            }
        }
    }

    /**
     * Clears disconnected flag after successful reconnect.
     */
    public void clearPlayerDisconnected(String userId, String tableId) {
        int attempts = 0;
        while (attempts++ < MAX_OPTIMISTIC_LOCK_RETRIES) {
            try {
                Optional<Table> opt = tableRepository.findById(tableId);
                if (opt.isEmpty()) {
                    return;
                }
                Table table = opt.get();
                if (clearDisconnectedInternal(table, userId)) {
                    tableRepository.save(table);
                    webSocketEventPublisher.publishPlayerReconnected(tableId, userId);
                    webSocketEventPublisher.publishTableUpdated(tableId, buildTableDetailResponse(table));
                    gameBroadcastService.broadcastTableState(tableId);
                }
                return;
            } catch (OptimisticLockingFailureException ex) {
                backoffDelay();
            }
        }
    }

    /**
     * Grace period expired: pack during active hand, leave + host transfer between hands.
     */
    public void handleDisconnectGraceExpired(String userId, String tableId) {
        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null || !table.getSeatedPlayerIds().contains(userId)) {
            return;
        }
        clearPlayerDisconnected(userId, tableId);

        if (isActiveHandStatus(table.getStatus())) {
            log.info("Grace expired for [{}] on table [{}] during hand — auto-packing", userId, tableId);
            gameEngineService.handlePlayerLeftMidHand(tableId, userId);
            gameBroadcastService.broadcastTableState(tableId);
            return;
        }

        log.info("Grace expired for [{}] on table [{}] between hands — removing from table", userId, tableId);
        leaveTable(userId, tableId);
    }

    private boolean clearDisconnectedInternal(Table table, String userId) {
        if (table.getDisconnectedPlayerIds() == null) {
            return false;
        }
        return table.getDisconnectedPlayerIds().remove(userId);
    }

    private long resolveBootPaise(Table table) {
        if (table.getBootAmountPaise() > 0) {
            return table.getBootAmountPaise();
        }
        return stakeTierConfig.getMinBuyInPaise(table.getStakeTier());
    }

    private boolean isActiveHandStatus(TableStatus status) {
        return status == TableStatus.IN_PROGRESS
                || status == TableStatus.PLAYING
                || status == TableStatus.RUNNING
                || status == TableStatus.STARTING
                || status == TableStatus.SHOW;
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
