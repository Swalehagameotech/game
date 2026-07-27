package com.teenpatti.platform.websocket;

/**
 * Canonical STOMP destination paths used by {@link WebSocketEventPublisher}.
 */
public final class StompDestinations {

    private StompDestinations() {
    }

    public static final String TOPIC_TABLES = "/topic/tables";
    public static final String TOPIC_USERS = "/topic/users";
    public static final String TOPIC_ANNOUNCEMENTS = "/topic/announcements";
    public static final String TOPIC_ADMIN = "/topic/admin";

    public static String topicTable(String tableId) {
        return "/topic/tables/" + tableId;
    }

    public static String queueWallet(String userId) {
        return "/queue/wallet/" + userId;
    }

    public static String queueNotifications(String userId) {
        return "/queue/notifications/" + userId;
    }
}
