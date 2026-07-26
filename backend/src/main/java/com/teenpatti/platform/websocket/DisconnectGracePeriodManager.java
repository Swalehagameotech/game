package com.teenpatti.platform.websocket;

import com.teenpatti.platform.table.TableService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages WebSocket disconnection grace periods.
 * Auto-folds a disconnected player if their turn arrives within the grace period,
 * and triggers Phase 9's leaveTable flow if the grace period expires without reconnection.
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
            @Value("${app.game.disconnect-grace-period-seconds:45}") int gracePeriodSeconds) {
        this.tableService = tableService;
        this.sessionRegistry = sessionRegistry;
        this.gracePeriodSeconds = gracePeriodSeconds;
    }

    public void handleDisconnect(String userId, String tableId, Runnable turnAutoFoldAction) {
        if (userId == null || tableId == null) return;

        log.info("Player [{}] disconnected from table [{}]. Starting {}s grace period.", userId, tableId, gracePeriodSeconds);
        disconnectedUserTableMap.put(userId, tableId);

        ScheduledFuture<?> task = scheduler.schedule(() -> {
            onGracePeriodExpired(userId, tableId);
        }, gracePeriodSeconds, TimeUnit.SECONDS);

        ScheduledFuture<?> prev = pendingGraceTasks.put(userId, task);
        if (prev != null) {
            prev.cancel(false);
        }
    }

    public boolean handleReconnect(String userId) {
        if (userId == null) return false;
        ScheduledFuture<?> task = pendingGraceTasks.remove(userId);
        disconnectedUserTableMap.remove(userId);
        if (task != null) {
            task.cancel(false);
            log.info("Player [{}] reconnected within grace period. Grace period cancelled.", userId);
            return true;
        }
        return false;
    }

    public boolean isUserInGracePeriod(String userId) {
        return pendingGraceTasks.containsKey(userId);
    }

    private void onGracePeriodExpired(String userId, String tableId) {
        pendingGraceTasks.remove(userId);
        disconnectedUserTableMap.remove(userId);
        log.warn("Grace period of {}s expired for player [{}] at table [{}]. Triggering leaveTable forfeiture/refund flow.",
                gracePeriodSeconds, userId, tableId);

        try {
            sessionRegistry.detachUserFromTable(userId, tableId);
            tableService.leaveTable(userId, tableId);
        } catch (Exception e) {
            log.error("Failed to execute leaveTable on grace period expiration for user [{}]: {}", userId, e.getMessage(), e);
        }
    }
}
