package com.teenpatti.platform.table;

/**
 * Published when a hand completes and the table transitions to ROUND_END or WAITING.
 * Consumed by {@link PublicTableCountdownService} to re-evaluate auto-start countdown.
 */
public record TableRoundEndedEvent(String tableId) {
}
