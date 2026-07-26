package com.teenpatti.platform.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.common.exception.ResourceNotFoundException;
import com.teenpatti.platform.websocket.SessionRegistry;
import com.teenpatti.platform.websocket.dto.GameServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;

/**
 * Centralized service managing notification persistence, real-time WebSocket push,
 * and user read status updates.
 *
 * CRITICAL ARCHITECTURAL REQUIREMENT:
 * notify(...) must NEVER throw exceptions that disrupt the calling domain service's primary operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    /**
     * Single entry point for creating notifications across all modules.
     * Persists notification and pushes real-time WebSocket alert if user is currently online.
     */
    public Notification notify(String userId, NotificationType type, String message) {
        if (userId == null || type == null || message == null) {
            log.warn("Invalid parameters passed to NotificationService.notify: userId={}, type={}, message={}", userId, type, message);
            return null;
        }

        try {
            // 1. Persist Notification Document
            Notification notification = Notification.builder()
                    .userId(userId)
                    .type(type)
                    .message(message)
                    .isRead(false)
                    .createdAt(Instant.now())
                    .build();

            Notification saved = notificationRepository.save(notification);
            log.info("Created notification [{}] for user [{}] of type [{}]", saved.getId(), userId, type);

            // 2. Push Real-Time WebSocket Message if User is Connected (Isolate socket push errors)
            try {
                if (sessionRegistry.isUserConnected(userId)) {
                    WebSocketSession session = sessionRegistry.getWebSocketSession(userId);
                    if (session != null && session.isOpen()) {
                        GameServerMessage wsMessage = GameServerMessage.builder()
                                .type("NOTIFICATION")
                                .payload(saved)
                                .build();
                        String json = objectMapper.writeValueAsString(wsMessage);
                        session.sendMessage(new TextMessage(json));
                        log.info("Pushed real-time notification [{}] over WebSocket to user [{}]", saved.getId(), userId);
                    }
                }
            } catch (Exception wsEx) {
                log.error("WebSocket push failed for notification [{}] to user [{}], but notification is persisted: {}", saved.getId(), userId, wsEx.getMessage());
            }

            return saved;
        } catch (Exception e) {
            // Defensive Failure Isolation: Log failure, NEVER propagate exception to caller
            log.error("Defensive error isolation: Failed to deliver notification to user [{}]: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    public Page<Notification> getUserNotifications(String userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Notification markAsRead(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (!userId.equals(notification.getUserId())) {
            throw new ResourceNotFoundException("Notification not found for user: " + userId);
        }

        notification.setRead(true);
        return notificationRepository.save(notification);
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
}
