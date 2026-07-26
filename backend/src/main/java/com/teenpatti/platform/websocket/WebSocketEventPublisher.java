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

    // Teen Patti Real-Time Game Engine Event Broadcasters
    public void publishCountdownStarted(String tableId, int durationSeconds) {
        publishEvent("/topic/tables/" + tableId, "COUNTDOWN_STARTED", durationSeconds);
    }

    public void publishCountdownCancelled(String tableId, String reason) {
        publishEvent("/topic/tables/" + tableId, "COUNTDOWN_CANCELLED", reason);
    }

    public void publishGameStarted(String tableId, Object payload) {
        publishEvent("/topic/tables/" + tableId, "GAME_STARTED", payload);
    }

    public void publishDealerSelected(String tableId, int dealerSeatIndex) {
        publishEvent("/topic/tables/" + tableId, "DEALER_SELECTED", dealerSeatIndex);
    }

    public void publishCardsDistributed(String tableId, Object payload) {
        publishEvent("/topic/tables/" + tableId, "CARDS_DISTRIBUTED", payload);
    }

    public void publishTurnStarted(String tableId, String activeUserId, int seatIndex, int durationSeconds) {
        publishEvent("/topic/tables/" + tableId, "TURN_STARTED", java.util.Map.of(
                "activeUserId", activeUserId,
                "seatIndex", seatIndex,
                "durationSeconds", durationSeconds
        ));
    }

    public void publishTurnEnded(String tableId, String previousUserId) {
        publishEvent("/topic/tables/" + tableId, "TURN_ENDED", previousUserId);
    }

    public void publishBlindPlayed(String tableId, String userId, long amountPaise, long potTotal) {
        publishEvent("/topic/tables/" + tableId, "BLIND_PLAYED", java.util.Map.of(
                "userId", userId,
                "amountPaise", amountPaise,
                "potTotal", potTotal
        ));
    }

    public void publishSeenPlayed(String tableId, String userId) {
        publishEvent("/topic/tables/" + tableId, "SEEN_PLAYED", userId);
    }

    public void publishRaisePlayed(String tableId, String userId, long amountPaise, long potTotal) {
        publishEvent("/topic/tables/" + tableId, "RAISE_PLAYED", java.util.Map.of(
                "userId", userId,
                "amountPaise", amountPaise,
                "potTotal", potTotal
        ));
    }

    public void publishPackPlayed(String tableId, String userId, boolean autoPacked) {
        publishEvent("/topic/tables/" + tableId, "PACK_PLAYED", java.util.Map.of(
                "userId", userId,
                "autoPacked", autoPacked
        ));
    }

    public void publishSideShowRequested(String tableId, String requesterUserId, String targetUserId) {
        publishEvent("/topic/tables/" + tableId, "SIDE_SHOW_REQUESTED", java.util.Map.of(
                "requesterUserId", requesterUserId,
                "targetUserId", targetUserId
        ));
    }

    public void publishSideShowAccepted(String tableId, String requesterUserId, String targetUserId, String loserUserId) {
        publishEvent("/topic/tables/" + tableId, "SIDE_SHOW_ACCEPTED", java.util.Map.of(
                "requesterUserId", requesterUserId,
                "targetUserId", targetUserId,
                "loserUserId", loserUserId
        ));
    }

    public void publishSideShowRejected(String tableId, String requesterUserId, String targetUserId) {
        publishEvent("/topic/tables/" + tableId, "SIDE_SHOW_REJECTED", java.util.Map.of(
                "requesterUserId", requesterUserId,
                "targetUserId", targetUserId
        ));
    }

    public void publishShowRequested(String tableId, Object payload) {
        publishEvent("/topic/tables/" + tableId, "SHOW_REQUESTED", payload);
    }

    public void publishWinnerDeclared(String tableId, Object payload) {
        publishEvent("/topic/tables/" + tableId, "WINNER_DECLARED", payload);
    }

    public void publishPotUpdated(String tableId, long potTotalPaise) {
        publishEvent("/topic/tables/" + tableId, "POT_UPDATED", potTotalPaise);
    }

    public void publishRoundFinished(String tableId, int nextRoundInSeconds) {
        publishEvent("/topic/tables/" + tableId, "ROUND_FINISHED", nextRoundInSeconds);
    }

    public void publishNextRoundCountdown(String tableId, int durationSeconds) {
        publishEvent("/topic/tables/" + tableId, "NEXT_ROUND_COUNTDOWN", durationSeconds);
    }

    public void publishPlayerDisconnected(String tableId, String userId, int graceSeconds) {
        publishEvent("/topic/tables/" + tableId, "PLAYER_DISCONNECTED", java.util.Map.of(
                "userId", userId,
                "graceSeconds", graceSeconds
        ));
    }

    public void publishPlayerReconnected(String tableId, String userId) {
        publishEvent("/topic/tables/" + tableId, "PLAYER_RECONNECTED", userId);
    }
}
