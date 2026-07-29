package com.teenpatti.platform.table;

import com.teenpatti.platform.game.GameStartService;
import com.teenpatti.platform.websocket.GameBroadcastService;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

/**
 * Public tables auto-start: 5-second countdown when minimum players are seated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicTableCountdownService {

    public static final int COUNTDOWN_SECONDS = 5;

    private final TableRepository tableRepository;
    private final GameStartService gameStartService;
    private final WebSocketEventPublisher eventPublisher;
    private final GameBroadcastService gameBroadcastService;
    private final TableCountdownRegistry countdownRegistry;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * Re-evaluates whether a public table should start, continue, or cancel countdown.
     */
    public void evaluate(String tableId) {
        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null || table.getTableType() != TableType.PUBLIC) {
            return;
        }

        TableStatus status = table.getStatus();
        if (status != TableStatus.WAITING && status != TableStatus.ROUND_END) {
            return;
        }

        int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        int minRequired = table.getMinPlayers() > 0 ? table.getMinPlayers() : 3;

        if (seated >= minRequired) {
            startCountdownIfAbsent(tableId);
        } else {
            cancelCountdown(tableId, "Minimum players not met");
        }
    }

    public void cancelCountdown(String tableId, String reason) {
        countdownRegistry.cancel(tableId);

        tableRepository.findById(tableId).ifPresent(table -> {
            if (table.getStatus() == TableStatus.COUNTDOWN) {
                table.setStatus(TableStatus.WAITING);
                table.setCountdownSeconds(0);
                tableRepository.save(table);
                if (reason != null) {
                    eventPublisher.publishCountdownCancelled(tableId, reason);
                }
                eventPublisher.publishTableUpdated(tableId, table);
                gameBroadcastService.broadcastTableState(tableId);
            }
        });
    }

    private void startCountdownIfAbsent(String tableId) {
        if (countdownRegistry.isActive(tableId)) {
            return;
        }

        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null) {
            return;
        }

        table.setStatus(TableStatus.COUNTDOWN);
        table.setCountdownSeconds(COUNTDOWN_SECONDS);
        tableRepository.save(table);

        eventPublisher.publishCountdownStarted(tableId, COUNTDOWN_SECONDS);
        eventPublisher.publishTableUpdated(tableId, table);
        gameBroadcastService.broadcastTableState(tableId);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> tick(tableId),
                1,
                1,
                TimeUnit.SECONDS
        );
        countdownRegistry.register(tableId, future);
        log.info("Public countdown started on table [{}]", tableId);
    }

    @EventListener
    public void onTableRoundEnded(TableRoundEndedEvent event) {
        // Next-round auto-start is owned by RoundManagementService.
        // Public first-join countdown still uses evaluate() via afterPublicTableMutation.
        if (event != null && event.tableId() != null) {
            log.debug("TableRoundEndedEvent received for [{}] — round management owns next-round flow", event.tableId());
        }
    }

    private void tick(String tableId) {
        try {
            Table table = tableRepository.findById(tableId).orElse(null);
            if (table == null || table.getStatus() != TableStatus.COUNTDOWN) {
                cancelCountdown(tableId, null);
                return;
            }

            int next = table.getCountdownSeconds() - 1;
            if (next <= 0) {
                int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
                int minRequired = table.getMinPlayers() > 0 ? table.getMinPlayers() : 3;
                if (seated < minRequired) {
                    cancelCountdown(tableId, "Minimum players not met");
                    return;
                }

                countdownRegistry.cancel(tableId);
                table.setCountdownSeconds(0);
                tableRepository.save(table);
                eventPublisher.publishCountdownTick(tableId, 0);
                gameStartService.startGameAutomatically(tableId);
                return;
            }

            table.setCountdownSeconds(next);
            tableRepository.save(table);
            eventPublisher.publishCountdownTick(tableId, next);
            eventPublisher.publishTableUpdated(tableId, table);
            gameBroadcastService.broadcastTableState(tableId);
        } catch (Exception ex) {
            log.error("Countdown tick failed for table [{}]: {}", tableId, ex.getMessage(), ex);
            cancelCountdown(tableId, "Countdown failed: " + ex.getMessage());
        }
    }
}
