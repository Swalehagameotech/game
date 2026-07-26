package com.teenpatti.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Centrally manages real-time STOMP and WebSocket event broadcasting for all platform domain events:
 * User Online/Offline, Table Lifecycle (Created, Joined, Left, Started, Finished, Closed, Deleted),
 * and Wallet / Admin Audit Updates.
 */
@Slf4j
@Component
public class WebSocketEventPublisher {

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealTimeEvent {
        private String eventType;
        private Object payload;
        private Instant timestamp;
    }

    public void publishEvent(String destination, String eventType, Object payload) {
        RealTimeEvent event = RealTimeEvent.builder()
                .eventType(eventType)
                .payload(payload)
                .timestamp(Instant.now())
                .build();

        log.info("Publishing Real-Time Event [{}] to destination [{}]", eventType, destination);

        if (messagingTemplate != null) {
            try {
                messagingTemplate.convertAndSend(destination, event);
            } catch (Exception e) {
                log.warn("STOMP MessagingTemplate broadcast warning for [{}]: {}", destination, e.getMessage());
            }
        }
    }

    // Domain Event Broadcasters
    public void publishTableCreated(Object tableSummary) {
        publishEvent("/topic/tables", "TABLE_CREATED", tableSummary);
    }

    public void publishTableUpdated(String tableId, Object tableSummary) {
        publishEvent("/topic/tables", "TABLE_UPDATED", tableSummary);
        publishEvent("/topic/tables/" + tableId, "TABLE_STATUS_CHANGED", tableSummary);
    }

    public void publishTableDeleted(String tableId) {
        publishEvent("/topic/tables", "TABLE_DELETED", tableId);
        publishEvent("/topic/tables/" + tableId, "TABLE_DELETED", tableId);
    }

    public void publishPlayerJoined(String tableId, String userId, int currentCount) {
        publishEvent("/topic/tables/" + tableId, "PLAYER_JOINED", userId);
        publishEvent("/topic/tables/" + tableId, "PLAYER_COUNT_CHANGED", currentCount);
    }

    public void publishPlayerLeft(String tableId, String userId, int currentCount) {
        publishEvent("/topic/tables/" + tableId, "PLAYER_LEFT", userId);
        publishEvent("/topic/tables/" + tableId, "PLAYER_COUNT_CHANGED", currentCount);
    }

    public void publishUserStatusChanged(String userId, boolean isOnline) {
        publishEvent("/topic/users", isOnline ? "USER_ONLINE" : "USER_OFFLINE", userId);
    }

    public void publishWalletUpdated(String userId, long newBalance) {
        publishEvent("/queue/wallet/" + userId, "WALLET_UPDATED", newBalance);
        publishEvent("/topic/admin", "ADMIN_WALLET_UPDATE", userId);
    }
}
