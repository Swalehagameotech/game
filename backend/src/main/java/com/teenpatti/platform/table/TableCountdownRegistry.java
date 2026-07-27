package com.teenpatti.platform.table;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Holds active public-table countdown scheduled tasks so admin/leave flows can cancel
 * without depending on {@link PublicTableCountdownService} (avoids Spring bean cycles).
 */
@Component
public class TableCountdownRegistry {

    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeCountdowns = new ConcurrentHashMap<>();

    public void register(String tableId, ScheduledFuture<?> future) {
        ScheduledFuture<?> previous = activeCountdowns.put(tableId, future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public boolean cancel(String tableId) {
        ScheduledFuture<?> future = activeCountdowns.remove(tableId);
        if (future != null) {
            future.cancel(false);
            return true;
        }
        return false;
    }

    public boolean isActive(String tableId) {
        return activeCountdowns.containsKey(tableId);
    }
}
