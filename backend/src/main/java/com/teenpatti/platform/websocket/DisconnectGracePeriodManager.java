package com.teenpatti.platform.websocket;

import com.teenpatti.platform.table.TableService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages WebSocket disconnection grace periods.
 * Between hands: remove player and transfer host on expiry.
 * During active hand: auto-pack on expiry and keep seat.
 */
@Slf4j
@Component
public class DisconnectGracePeriodManager {

    private final TableService tableService;
    private final SessionRegistry sessionRegistry;
    private final int gracePeriodSeconds;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    private final Map<String, ScheduledFuture<?>> pendingGraceTasks = new ConcurrentHashMap<>();
    private final Map<String, String> disconnectedUserTableMap = new ConcurrentHashMap<>();

    public DisconnectGracePeriodManager(
            TableService tableService,
            SessionRegistry sessionRegistry,
            @Value("${app.game.disconnect-grace-period-seconds:30}") int gracePeriodSeconds) {
        this.tableService = tableService;
        this.sessionRegistry = sessionRegistry;
        this.gracePeriodSeconds = gracePeriodSeconds;
    }

    public int getGracePeriodSeconds() {
        return gracePeriodSeconds;
    }

    public void handleDisconnect(String userId, String tableId, Runnable turnAutoFoldAction) {
        if (userId == null || tableId == null) {
            return;
        }

        log.info("Player [{}] disconnected from table [{}]. Starting {}s grace period.",
                userId, tableId, gracePeriodSeconds);
        disconnectedUserTableMap.put(userId, tableId);
        tableService.markPlayerDisconnected(userId, tableId, gracePeriodSeconds);

        ScheduledFuture<?> task = scheduler.schedule(
                () -> onGracePeriodExpired(userId, tableId, turnAutoFoldAction),
                gracePeriodSeconds,
                TimeUnit.SECONDS);

        ScheduledFuture<?> prev = pendingGraceTasks.put(userId, task);
        if (prev != null) {
            prev.cancel(false);
        }
    }

    public boolean handleReconnect(String userId, String tableId) {
        if (userId == null) {
            return false;
        }
        ScheduledFuture<?> task = pendingGraceTasks.remove(userId);
        disconnectedUserTableMap.remove(userId);
        if (task != null) {
            task.cancel(false);
            if (tableId != null) {
                tableService.clearPlayerDisconnected(userId, tableId);
            }
            log.info("Player [{}] reconnected within grace period. Grace period cancelled.", userId);
            return true;
        }
        return false;
    }

    public boolean isUserInGracePeriod(String userId) {
        return pendingGraceTasks.containsKey(userId);
    }

    private void onGracePeriodExpired(String userId, String tableId, Runnable turnAutoFoldAction) {
        pendingGraceTasks.remove(userId);
        disconnectedUserTableMap.remove(userId);
        log.warn("Grace period of {}s expired for player [{}] at table [{}].",
                gracePeriodSeconds, userId, tableId);

        try {
            if (turnAutoFoldAction != null) {
                turnAutoFoldAction.run();
            }
            sessionRegistry.detachUserFromTable(userId, tableId);
            tableService.handleDisconnectGraceExpired(userId, tableId);
        } catch (Exception e) {
            log.error("Failed to handle grace period expiration for user [{}]: {}", userId, e.getMessage(), e);
        }
    }
}
