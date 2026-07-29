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
    /** Host ownership transferred to another seated player. */
    HOST_CHANGED,

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
    NEXT_ROUND_STARTED,
    TABLE_WAITING_FOR_PLAYERS,

    // Turn & actions
    TURN_STARTED,
    TURN_CHANGED,
    TURN_ENDED,
    PLAYER_ACTION,
    BLIND_PLAYED,
    SEEN_PLAYED,
    /** Public status-only broadcast when a player reveals their own cards (never includes card values). */
    PLAYER_SEEN_CARDS,
    RAISE_PLAYED,
    PACK_PLAYED,
    SIDE_SHOW_REQUESTED,
    SIDE_SHOW_ACCEPTED,
    SIDE_SHOW_REJECTED,
    SHOW_REQUESTED,
    /** Show requested — challenger waits for opponent to accept (alias: SHOW_REQUEST). */
    SHOW_REQUEST,
    /** Target player accepted Show; both hands will be revealed. */
    SHOW_ACCEPTED,
    /** Private event: player's own cards revealed for Show response (never opponent cards). */
    PLAYER_CARDS_REVEALED_TO_SELF,
    /** Both showdown hands revealed to the entire table after Show accept. */
    FINAL_HANDS_REVEALED,
    /** Emitted when exactly two active players remain and Show becomes available. */
    SHOW_ENABLED,
    WINNER_DECLARED,
    POT_UPDATED,
    BET_UPDATED,
    /** Alias for BET_UPDATED — current base stake / required bet changed. */
    CURRENT_BET_UPDATED,
    PLAYER_STATE_UPDATED,
    GAME_STATE_UPDATED,
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
    BETTING_CONFIGURATION_UPDATED,

    // Raw gameplay channel (also sent on /ws/game)
    STATE_UPDATE,
    ACTION_REJECTED,
    NOTIFICATION
}
