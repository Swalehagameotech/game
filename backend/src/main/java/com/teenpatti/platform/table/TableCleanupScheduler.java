package com.teenpatti.platform.table;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Isolated background scheduler that cleans up orphaned/stale WAITING tables past their timeout.
 * Re-checks table state atomically at close time to prevent closing tables that recently acquired new players.
 */
@Slf4j
@Component
public class TableCleanupScheduler {

    private final TableService tableService;
    private final int waitingTimeoutMinutes;

    public TableCleanupScheduler(
            TableService tableService,
            @Value("${app.table.waiting-timeout-minutes:10}") int waitingTimeoutMinutes) {
        this.tableService = tableService;
        this.waitingTimeoutMinutes = waitingTimeoutMinutes;
    }

    @Scheduled(fixedDelayString = "${app.table.cleanup-interval-ms:60000}")
    public void cleanupStaleTablesScheduled() {
        try {
            int closedCount = tableService.cleanupStaleWaitingTables(waitingTimeoutMinutes);
            if (closedCount > 0) {
                log.info("Scheduled table cleanup completed: closed {} stale WAITING tables", closedCount);
            }
        } catch (Exception e) {
            log.error("Error during scheduled table cleanup execution: {}", e.getMessage(), e);
        }
    }
}
