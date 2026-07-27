package com.teenpatti.platform.table;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical table status groupings used by lobby, home dashboard, and session aggregate APIs.
 */
public final class TableStatusGroups {

    private TableStatusGroups() {
    }

    public static final Set<TableStatus> USER_ACTIVE = EnumSet.of(
            TableStatus.WAITING,
            TableStatus.COUNTDOWN,
            TableStatus.STARTING,
            TableStatus.RUNNING,
            TableStatus.IN_PROGRESS,
            TableStatus.DEALING,
            TableStatus.PLAYING,
            TableStatus.SHOW,
            TableStatus.ROUND_END
    );

    public static final Set<TableStatus> RUNNING = EnumSet.of(
            TableStatus.COUNTDOWN,
            TableStatus.STARTING,
            TableStatus.RUNNING,
            TableStatus.IN_PROGRESS,
            TableStatus.DEALING,
            TableStatus.PLAYING,
            TableStatus.SHOW
    );

    public static final Set<TableStatus> WAITING = EnumSet.of(
            TableStatus.WAITING,
            TableStatus.ROUND_END
    );

    public static List<TableStatus> userActiveList() {
        return List.copyOf(USER_ACTIVE);
    }

    public static List<TableStatus> runningList() {
        return List.copyOf(RUNNING);
    }

    public static List<TableStatus> waitingList() {
        return List.copyOf(WAITING);
    }

    public static boolean isUserActive(TableStatus status) {
        return status != null && USER_ACTIVE.contains(status);
    }

    public static boolean isRunning(TableStatus status) {
        return status != null && RUNNING.contains(status);
    }

    public static boolean isWaiting(TableStatus status) {
        return status != null && WAITING.contains(status);
    }
}
