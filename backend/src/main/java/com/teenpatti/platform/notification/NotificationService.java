package com.teenpatti.platform.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.common.exception.ResourceNotFoundException;
import com.teenpatti.platform.notification.dto.NotificationSummaryDto;
import com.teenpatti.platform.websocket.SessionRegistry;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import com.teenpatti.platform.websocket.dto.GameServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized service managing notification persistence, STOMP + game WebSocket push,
 * and user read status updates.
 *
 * CRITICAL: notify(...) must NEVER throw exceptions that disrupt the calling domain service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final WebSocketEventPublisher eventPublisher;

    public Notification notify(String userId, NotificationType type, String message) {
        return notify(userId, type, null, message, null);
    }

    public Notification notify(String userId, NotificationType type, String title, String message, Map<String, Object> payload) {
        if (userId == null || type == null || message == null) {
            log.warn("Invalid parameters passed to NotificationService.notify: userId={}, type={}, message={}", userId, type, message);
            return null;
        }

        try {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .payload(payload != null ? payload : new HashMap<>())
                    .isRead(false)
                    .createdAt(Instant.now())
                    .build();

            Notification saved = notificationRepository.save(notification);
            log.info("Created notification [{}] for user [{}] of type [{}]", saved.getId(), userId, type);

            pushRealtime(saved);
            return saved;
        } catch (Exception e) {
            log.error("Defensive error isolation: Failed to deliver notification to user [{}]: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Notifies all hand participants — winner gets payout message, others get loss summary.
     */
    public void notifyHandCompleted(
            String tableId,
            String handId,
            String winnerId,
            long winnerPayoutPaise,
            long potAmountPaise,
            List<String> participantUserIds) {
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("tableId", tableId);
        payload.put("handId", handId);
        payload.put("winnerId", winnerId);
        payload.put("potAmountPaise", potAmountPaise);
        payload.put("winnerPayoutPaise", winnerPayoutPaise);

        String tableLabel = tableId != null && tableId.length() >= 6
                ? tableId.substring(0, 6).toUpperCase()
                : "TABLE";

        for (String userId : participantUserIds) {
            if (userId == null || userId.isBlank()) {
                continue;
            }
            if (userId.equals(winnerId)) {
                notify(
                        userId,
                        NotificationType.GAME,
                        "You Won!",
                        String.format("Congratulations! You won ₹%.2f from the pot.", winnerPayoutPaise / 100.0),
                        payload);
            } else {
                notify(
                        userId,
                        NotificationType.GAME,
                        "Hand Complete",
                        String.format("You lost this hand at table %s. Better luck next round!", tableLabel),
                        payload);
            }
        }
    }

    public Page<NotificationSummaryDto> getUserNotifications(String userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toSummaryDto);
    }

    public NotificationSummaryDto markAsRead(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (!userId.equals(notification.getUserId())) {
            throw new ResourceNotFoundException("Notification not found for user: " + userId);
        }

        notification.setRead(true);
        return toSummaryDto(notificationRepository.save(notification));
    }

    public void markAllAsRead(String userId) {
        List<Notification> unreadList = notificationRepository.findByUserIdAndIsReadFalse(userId);
        if (!unreadList.isEmpty()) {
            for (Notification n : unreadList) {
                n.setRead(true);
            }
            notificationRepository.saveAll(unreadList);
            log.info("Marked {} notifications as read for user [{}]", unreadList.size(), userId);
        }
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public NotificationSummaryDto toSummaryDto(Notification notification) {
        if (notification == null) {
            return null;
        }
        return NotificationSummaryDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .displayLabel(resolveDisplayLabel(notification.getType(), notification.getTitle()))
                .isRead(notification.isRead())
                .payload(notification.getPayload() != null ? notification.getPayload() : Map.of())
                .createdAt(notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : "")
                .build();
    }

    private void pushRealtime(Notification saved) {
        NotificationSummaryDto summary = toSummaryDto(saved);

        try {
            eventPublisher.publishNotification(saved.getUserId(), summary);
        } catch (Exception stompEx) {
            log.warn("STOMP notification push failed for user [{}]: {}", saved.getUserId(), stompEx.getMessage());
        }

        try {
            if (sessionRegistry.isUserConnected(saved.getUserId())) {
                WebSocketSession session = sessionRegistry.getWebSocketSession(saved.getUserId());
                if (session != null && session.isOpen()) {
                    GameServerMessage wsMessage = GameServerMessage.builder()
                            .type("NOTIFICATION")
                            .payload(summary)
                            .build();
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(wsMessage)));
                }
            }
        } catch (Exception wsEx) {
            log.warn("Game WebSocket notification push failed for user [{}]: {}", saved.getUserId(), wsEx.getMessage());
        }
    }

    static String resolveDisplayLabel(NotificationType type, String title) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        if (type == null) {
            return "Notification";
        }
        return switch (type) {
            case GAME_INVITE -> "Game Invite";
            case DEPOSIT_SUCCESS -> "Deposit Successful";
            case DEPOSIT_FAILED -> "Deposit Failed";
            case WITHDRAWAL_SUCCESS -> "Withdrawal Successful";
            case WITHDRAWAL_FAILED -> "Withdrawal Failed";
            case ACCOUNT_ALERT -> "Account Alert";
            case SYSTEM_ANNOUNCEMENT -> "System Announcement";
            case GAME -> "Game Result";
            case TRANSACTION -> "Transaction";
            case FRIEND -> "Friend";
            case SYSTEM -> "System";
        };
    }
}
