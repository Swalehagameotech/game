package com.teenpatti.platform.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Centrally manages real-time STOMP event broadcasting for all platform domain events.
 * Envelope: {@link RealTimeEvent} { eventType, payload, timestamp }.
 */
@Slf4j
@Component
public class WebSocketEventPublisher {

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

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

    public void publishTableCreated(Object tableSummary) {
        publishEvent(StompDestinations.TOPIC_TABLES, RealTimeEventType.TABLE_CREATED.name(), tableSummary);
    }

    public void publishTableUpdated(String tableId, Object tableSummary) {
        publishEvent(StompDestinations.TOPIC_TABLES, RealTimeEventType.TABLE_UPDATED.name(), tableSummary);
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.TABLE_STATUS_CHANGED.name(), tableSummary);
    }

    public void publishTableDeleted(String tableId) {
        publishEvent(StompDestinations.TOPIC_TABLES, RealTimeEventType.TABLE_DELETED.name(), tableId);
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.TABLE_DELETED.name(), tableId);
    }

    public void publishPlayerJoined(String tableId, String userId, int currentCount) {
        publishEvent(StompDestinations.TOPIC_TABLES, RealTimeEventType.PLAYER_JOINED.name(),
                Map.of("tableId", tableId, "userId", userId, "currentCount", currentCount));
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.PLAYER_JOINED.name(), userId);
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.PLAYER_COUNT_CHANGED.name(), currentCount);
    }

    public void publishPlayerLeft(String tableId, String userId, int currentCount) {
        publishEvent(StompDestinations.TOPIC_TABLES, RealTimeEventType.PLAYER_LEFT.name(),
                Map.of("tableId", tableId, "userId", userId, "currentCount", currentCount));
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.PLAYER_LEFT.name(), userId);
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.PLAYER_COUNT_CHANGED.name(), currentCount);
    }

    public void publishUserStatusChanged(String userId, boolean isOnline) {
        publishEvent(StompDestinations.TOPIC_USERS,
                isOnline ? RealTimeEventType.USER_ONLINE.name() : RealTimeEventType.USER_OFFLINE.name(),
                userId);
    }

    public void publishWalletUpdated(String userId, long newBalance) {
        publishEvent(StompDestinations.queueWallet(userId), RealTimeEventType.WALLET_UPDATED.name(), newBalance);
        publishEvent(StompDestinations.TOPIC_ADMIN, RealTimeEventType.ADMIN_WALLET_UPDATE.name(), userId);
    }

    public void publishHostStartedGame(String tableId, Object payload) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.HOST_STARTED_GAME.name(), payload);
    }

    public void publishGameRunning(String tableId, Object payload) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.GAME_RUNNING.name(), payload);
    }

    public void publishCountdownStarted(String tableId, int durationSeconds) {
        publishEvent(StompDestinations.TOPIC_TABLES, RealTimeEventType.COUNTDOWN_STARTED.name(),
                Map.of("tableId", tableId, "countdownSeconds", durationSeconds));
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.COUNTDOWN_STARTED.name(), durationSeconds);
    }

    public void publishCountdownCancelled(String tableId, String reason) {
        publishEvent(StompDestinations.TOPIC_TABLES, RealTimeEventType.COUNTDOWN_CANCELLED.name(),
                Map.of("tableId", tableId, "reason", reason != null ? reason : ""));
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.COUNTDOWN_CANCELLED.name(), reason);
    }

    public void publishCountdownTick(String tableId, int secondsRemaining) {
        Map<String, Object> payload = Map.of(
                "tableId", tableId,
                "countdownSeconds", secondsRemaining
        );
        publishEvent(StompDestinations.TOPIC_TABLES, RealTimeEventType.COUNTDOWN_TICK.name(), payload);
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.COUNTDOWN_TICK.name(), payload);
    }

    public void publishTableClosed(String tableId) {
        publishEvent(StompDestinations.TOPIC_TABLES, RealTimeEventType.TABLE_CLOSED.name(), tableId);
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.TABLE_CLOSED.name(), tableId);
    }

    public void publishSystemAnnouncement(Object payload) {
        publishEvent(StompDestinations.TOPIC_ANNOUNCEMENTS, RealTimeEventType.SYSTEM_ANNOUNCEMENT.name(), payload);
    }

    public void publishGameStarted(String tableId, Object payload) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.GAME_STARTED.name(), payload);
    }

    public void publishDealerSelected(String tableId, int dealerSeatIndex) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.DEALER_SELECTED.name(), dealerSeatIndex);
    }

    public void publishCardsDistributed(String tableId, Object payload) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.CARDS_DISTRIBUTED.name(), payload);
    }

    public void publishTurnStarted(String tableId, String activeUserId, int seatIndex, int durationSeconds) {
        Map<String, Object> payload = Map.of(
                "activeUserId", activeUserId,
                "seatIndex", seatIndex,
                "durationSeconds", durationSeconds
        );
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.TURN_STARTED.name(), payload);
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.TURN_CHANGED.name(), payload);
    }

    public void publishTurnState(String tableId, Object turnState) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.TURN_CHANGED.name(), turnState);
    }

    public void publishPlayerAction(String tableId, String userId, String actionType, long amountPaise, long potTotal) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.PLAYER_ACTION.name(), Map.of(
                "userId", userId,
                "actionType", actionType,
                "amountPaise", amountPaise,
                "potTotal", potTotal
        ));
    }

    public void publishTurnEnded(String tableId, String previousUserId) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.TURN_ENDED.name(), previousUserId);
    }

    public void publishBlindPlayed(String tableId, String userId, long amountPaise, long potTotal) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.BLIND_PLAYED.name(), Map.of(
                "userId", userId,
                "amountPaise", amountPaise,
                "potTotal", potTotal
        ));
    }

    public void publishSeenPlayed(String tableId, String userId) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.SEEN_PLAYED.name(), userId);
    }

    public void publishRaisePlayed(String tableId, String userId, long amountPaise, long potTotal) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.RAISE_PLAYED.name(), Map.of(
                "userId", userId,
                "amountPaise", amountPaise,
                "potTotal", potTotal
        ));
    }

    public void publishPackPlayed(String tableId, String userId, boolean autoPacked) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.PACK_PLAYED.name(), Map.of(
                "userId", userId,
                "autoPacked", autoPacked
        ));
    }

    public void publishSideShowRequested(String tableId, String requesterUserId, String targetUserId) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.SIDE_SHOW_REQUESTED.name(), Map.of(
                "requesterUserId", requesterUserId,
                "targetUserId", targetUserId
        ));
    }

    public void publishSideShowAccepted(String tableId, String requesterUserId, String targetUserId, String loserUserId) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.SIDE_SHOW_ACCEPTED.name(), Map.of(
                "requesterUserId", requesterUserId,
                "targetUserId", targetUserId,
                "loserUserId", loserUserId
        ));
    }

    public void publishSideShowRejected(String tableId, String requesterUserId, String targetUserId) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.SIDE_SHOW_REJECTED.name(), Map.of(
                "requesterUserId", requesterUserId,
                "targetUserId", targetUserId
        ));
    }

    public void publishShowRequested(String tableId, Object payload) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.SHOW_REQUESTED.name(), payload);
    }

    public void publishWinnerDeclared(String tableId, Object payload) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.WINNER_DECLARED.name(), payload);
    }

    public void publishPotUpdated(String tableId, long potTotalPaise) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.POT_UPDATED.name(), potTotalPaise);
    }

    public void publishBettingState(String tableId, Object bettingState) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.BETTING_STATE.name(), bettingState);
    }

    public void publishWalletSettled(String tableId, Object settlementResult) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.WALLET_SETTLED.name(), settlementResult);
    }

    public void publishGameHistoryRecorded(String userId, Object summary) {
        publishEvent(StompDestinations.queueWallet(userId), RealTimeEventType.GAME_HISTORY_RECORDED.name(), summary);
    }

    public void publishNotification(String userId, Object notificationSummary) {
        publishEvent(StompDestinations.queueNotifications(userId), RealTimeEventType.NOTIFICATION.name(), notificationSummary);
    }

    public void publishRoundFinished(String tableId, int nextRoundInSeconds) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.ROUND_FINISHED.name(), nextRoundInSeconds);
    }

    public void publishNextRoundCountdown(String tableId, int durationSeconds) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.NEXT_ROUND_COUNTDOWN.name(), durationSeconds);
    }

    public void publishPlayerDisconnected(String tableId, String userId, int graceSeconds) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.PLAYER_DISCONNECTED.name(), Map.of(
                "userId", userId,
                "graceSeconds", graceSeconds
        ));
    }

    public void publishPlayerReconnected(String tableId, String userId) {
        publishEvent(StompDestinations.topicTable(tableId), RealTimeEventType.PLAYER_RECONNECTED.name(), userId);
    }
}
