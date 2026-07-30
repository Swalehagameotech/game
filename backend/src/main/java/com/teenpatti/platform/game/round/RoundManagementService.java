package com.teenpatti.platform.game.round;

import com.teenpatti.platform.game.GameStartService;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import com.teenpatti.platform.websocket.GameBroadcastService;
import com.teenpatti.platform.websocket.RealTimeEventType;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Commercial Teen Patti round lifecycle: table persists across rounds;
 * after each hand, either start a next-round countdown or return to WAITING / CLOSED.
 */
@Slf4j
@Service
public class RoundManagementService {

    private final TableRepository tableRepository;
    private final GameStartService gameStartService;
    private final WebSocketEventPublisher eventPublisher;
    private final GameBroadcastService gameBroadcastService;
    private final int nextRoundDelaySeconds;
    private final int winnerDisplaySeconds;
    private final com.teenpatti.platform.table.HostManagementService hostManagementService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> nextRoundTasks = new ConcurrentHashMap<>();

    public RoundManagementService(
            TableRepository tableRepository,
            @Lazy GameStartService gameStartService,
            WebSocketEventPublisher eventPublisher,
            GameBroadcastService gameBroadcastService,
            com.teenpatti.platform.table.HostManagementService hostManagementService,
            @Value("${app.game.next-round-delay-seconds:60}") int nextRoundDelaySeconds,
            @Value("${app.game.winner-display-seconds:5}") int winnerDisplaySeconds) {
        this.tableRepository = tableRepository;
        this.gameStartService = gameStartService;
        this.eventPublisher = eventPublisher;
        this.gameBroadcastService = gameBroadcastService;
        this.hostManagementService = hostManagementService;
        this.nextRoundDelaySeconds = Math.max(1, nextRoundDelaySeconds);
        this.winnerDisplaySeconds = Math.max(0, winnerDisplaySeconds);
    }

    /**
     * Called after hand settlement. Does NOT delete the table.
     * Shows winner for {@code winnerDisplaySeconds}, then starts the next-round countdown.
     */
    public void afterRoundFinished(String tableId) {
        cancelNextRound(tableId);

        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null || table.getStatus() == TableStatus.CLOSED) {
            return;
        }

        int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        int minRequired = table.getMinPlayers() > 0 ? table.getMinPlayers() : 3;

        // Stay on ROUND_END (winner banner) — countdown starts only after winner-display delay
        table.setStatus(TableStatus.ROUND_END);
        table.setCountdownSeconds(0);
        table.setUpdatedAt(java.time.Instant.now());
        tableRepository.save(table);

        eventPublisher.publishRoundFinished(tableId, seated >= minRequired ? nextRoundDelaySeconds : 0);
        gameBroadcastService.broadcastTableState(tableId);

        if (seated <= 0) {
            closeTable(table);
            return;
        }

        // Reassign host if the previous host left/packed before the next round.
        hostManagementService.ensureValidHost(tableId);

        if (seated < minRequired) {
            transitionToWaiting(table, "Not enough players for the next round");
            return;
        }

        if (winnerDisplaySeconds <= 0) {
            scheduleNextRound(tableId, nextRoundDelaySeconds);
            return;
        }

        ScheduledFuture<?> delayFuture = scheduler.schedule(
                () -> {
                    try {
                        scheduleNextRound(tableId, nextRoundDelaySeconds);
                    } catch (Exception e) {
                        log.error("Failed to start next-round countdown after winner display on [{}]: {}",
                                tableId, e.getMessage());
                    }
                },
                winnerDisplaySeconds,
                TimeUnit.SECONDS
        );
        nextRoundTasks.put(tableId, delayFuture);
        log.info("Winner display for {}s on table [{}], then {}s next-round countdown",
                winnerDisplaySeconds, tableId, nextRoundDelaySeconds);
    }

    /**
     * Player left while table is between hands (ROUND_END / NEXT_ROUND / WAITING).
     * Rechecks whether next round can still run.
     */
    public void onPlayerLeftBetweenHands(String tableId) {
        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null) {
            return;
        }

        TableStatus status = table.getStatus();
        if (status != TableStatus.ROUND_END
                && status != TableStatus.NEXT_ROUND
                && status != TableStatus.WAITING) {
            return;
        }

        int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        int minRequired = table.getMinPlayers() > 0 ? table.getMinPlayers() : 3;

        if (seated <= 0) {
            cancelNextRound(tableId);
            closeTable(table);
            return;
        }

        hostManagementService.ensureValidHost(tableId);

        if (seated < minRequired) {
            cancelNextRound(tableId);
            transitionToWaiting(table, "Player left — waiting for more players");
        }
    }

    public void cancelNextRound(String tableId) {
        ScheduledFuture<?> future = nextRoundTasks.remove(tableId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void scheduleNextRound(String tableId, int delaySeconds) {
        // Drop completed winner-display delay (if any) without treating it as an active tick
        ScheduledFuture<?> prior = nextRoundTasks.remove(tableId);
        if (prior != null && !prior.isDone()) {
            prior.cancel(false);
        }

        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null) {
            return;
        }

        table.setStatus(TableStatus.NEXT_ROUND);
        table.setCountdownSeconds(delaySeconds);
        table.setUpdatedAt(java.time.Instant.now());
        tableRepository.save(table);

        eventPublisher.publishNextRoundCountdown(tableId, delaySeconds);
        eventPublisher.publishEvent(
                com.teenpatti.platform.websocket.StompDestinations.topicTable(tableId),
                RealTimeEventType.NEXT_ROUND_COUNTDOWN.name(),
                Map.of(
                        "tableId", tableId,
                        "secondsRemaining", delaySeconds,
                        "roundNumber", table.getRoundNumber(),
                        "message", "Next round starting..."
                )
        );
        eventPublisher.publishTableUpdated(tableId, table);
        gameBroadcastService.broadcastTableState(tableId);

        ScheduledFuture<?> tickFuture = scheduler.scheduleAtFixedRate(
                () -> tickNextRound(tableId),
                1,
                1,
                TimeUnit.SECONDS
        );
        nextRoundTasks.put(tableId, tickFuture);
        log.info("Next-round countdown started on table [{}] for {}s (after round {})",
                tableId, delaySeconds, table.getRoundNumber());
    }

    private void tickNextRound(String tableId) {
        try {
            Table table = tableRepository.findById(tableId).orElse(null);
            if (table == null || table.getStatus() != TableStatus.NEXT_ROUND) {
                cancelNextRound(tableId);
                return;
            }

            int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
            int minRequired = table.getMinPlayers() > 0 ? table.getMinPlayers() : 3;
            if (seated < minRequired) {
                cancelNextRound(tableId);
                transitionToWaiting(table, "Not enough players for the next round");
                return;
            }

            int next = Math.max(0, table.getCountdownSeconds() - 1);
            table.setCountdownSeconds(next);
            tableRepository.save(table);

            eventPublisher.publishNextRoundCountdown(tableId, next);
            eventPublisher.publishEvent(
                    com.teenpatti.platform.websocket.StompDestinations.topicTable(tableId),
                    RealTimeEventType.NEXT_ROUND_COUNTDOWN.name(),
                    Map.of(
                            "tableId", tableId,
                            "secondsRemaining", next,
                            "roundNumber", table.getRoundNumber(),
                            "message", next > 0 ? "Next round starting..." : "Starting next round"
                    )
            );
            gameBroadcastService.broadcastTableState(tableId);

            if (next <= 0) {
                cancelNextRound(tableId);
                startNextRound(tableId);
            }
        } catch (Exception ex) {
            log.error("Next-round tick failed for table [{}]: {}", tableId, ex.getMessage(), ex);
            cancelNextRound(tableId);
        }
    }

    private void startNextRound(String tableId) {
        try {
            Table table = tableRepository.findById(tableId).orElse(null);
            if (table == null) {
                return;
            }

            int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
            int minRequired = table.getMinPlayers() > 0 ? table.getMinPlayers() : 3;
            if (seated < minRequired) {
                transitionToWaiting(table, "Not enough players for the next round");
                return;
            }

            // Private tables also auto-continue when enough players remain (commercial behaviour).
            // Initial start from WAITING still requires host for private tables.
            Table started = gameStartService.startGameAutomatically(tableId);

            eventPublisher.publishEvent(
                    com.teenpatti.platform.websocket.StompDestinations.topicTable(tableId),
                    RealTimeEventType.NEXT_ROUND_STARTED.name(),
                    Map.of(
                            "tableId", tableId,
                            "roundNumber", started.getRoundNumber(),
                            "handId", started.getCurrentHandId() != null ? started.getCurrentHandId() : "",
                            "message", "Next round started"
                    )
            );
            log.info("Next round started on table [{}] as round {}", tableId, started.getRoundNumber());
        } catch (Exception ex) {
            log.warn("Failed to auto-start next round on table [{}]: {}", tableId, ex.getMessage());
            tableRepository.findById(tableId).ifPresent(t ->
                    transitionToWaiting(t, "Could not start next round: " + ex.getMessage()));
        }
    }

    private void transitionToWaiting(Table table, String reason) {
        hostManagementService.ensureValidHost(table.getId());
        table = tableRepository.findById(table.getId()).orElse(table);

        table.setStatus(TableStatus.WAITING);
        table.setCountdownSeconds(0);
        table.setCurrentTurnUserId(null);
        table.setPotPaise(0);
        table.setUpdatedAt(java.time.Instant.now());
        tableRepository.save(table);

        eventPublisher.publishEvent(
                com.teenpatti.platform.websocket.StompDestinations.topicTable(table.getId()),
                RealTimeEventType.TABLE_WAITING_FOR_PLAYERS.name(),
                Map.of(
                        "tableId", table.getId(),
                        "reason", reason != null ? reason : "Waiting for players",
                        "seatedCount", table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0,
                        "minPlayers", table.getMinPlayers() > 0 ? table.getMinPlayers() : 3,
                        "tableType", table.getTableType() != null ? table.getTableType().name() : TableType.PUBLIC.name()
                )
        );
        eventPublisher.publishTableUpdated(table.getId(), table);
        gameBroadcastService.broadcastTableState(table.getId());
        log.info("Table [{}] returned to WAITING: {}", table.getId(), reason);
    }

    private void closeTable(Table table) {
        table.setStatus(TableStatus.CLOSED);
        table.setCountdownSeconds(0);
        table.setUpdatedAt(java.time.Instant.now());
        tableRepository.save(table);
        eventPublisher.publishTableClosed(table.getId());
        eventPublisher.publishTableUpdated(table.getId(), table);
        log.info("Table [{}] CLOSED — no players remaining", table.getId());
    }
}
