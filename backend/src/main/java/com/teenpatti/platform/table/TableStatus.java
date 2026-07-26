package com.teenpatti.platform.table;

/**
 * Status of a Teen Patti game table instance.
 */
public enum TableStatus {
    WAITING,
    COUNTDOWN,
    DEALING,
    PLAYING,
    IN_PROGRESS,
    SHOW,
    ROUND_END,
    NEXT_ROUND,
    CLOSED
}
