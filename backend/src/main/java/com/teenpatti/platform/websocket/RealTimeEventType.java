package com.teenpatti.platform.websocket;

/**
 * Canonical STOMP real-time event types broadcast via {@link WebSocketEventPublisher}.
 * Frontend should mirror these in {@code realtimeEvents.js}.
 */
public enum RealTimeEventType {

    // Table lobby
    TABLE_CREATED,
    TABLE_UPDATED,
    TABLE_DELETED,
    TABLE_CLOSED,
    TABLE_STATUS_CHANGED,
    PLAYER_JOINED,
    PLAYER_LEFT,
    PLAYER_COUNT_CHANGED,

    // Public table countdown
    COUNTDOWN_STARTED,
    COUNTDOWN_CANCELLED,
    COUNTDOWN_TICK,

    // Game lifecycle
    HOST_STARTED_GAME,
    GAME_RUNNING,
    GAME_STARTED,
    DEALER_SELECTED,
    CARDS_DISTRIBUTED,
    ROUND_FINISHED,
    NEXT_ROUND_COUNTDOWN,

    // Turn & actions
    TURN_STARTED,
    TURN_CHANGED,
    TURN_ENDED,
    PLAYER_ACTION,
    BLIND_PLAYED,
    SEEN_PLAYED,
    RAISE_PLAYED,
    PACK_PLAYED,
    SIDE_SHOW_REQUESTED,
    SIDE_SHOW_ACCEPTED,
    SIDE_SHOW_REJECTED,
    SHOW_REQUESTED,
    WINNER_DECLARED,
    POT_UPDATED,
    BETTING_STATE,
    WALLET_SETTLED,
    GAME_HISTORY_RECORDED,

    // Presence
    PLAYER_DISCONNECTED,
    PLAYER_RECONNECTED,
    USER_ONLINE,
    USER_OFFLINE,

    // Wallet & admin
    WALLET_UPDATED,
    ADMIN_WALLET_UPDATE,
    SYSTEM_ANNOUNCEMENT,

    // Raw gameplay channel (also sent on /ws/game)
    STATE_UPDATE,
    ACTION_REJECTED,
    NOTIFICATION
}
