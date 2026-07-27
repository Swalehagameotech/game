package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminAnnouncementRequest;
import com.teenpatti.platform.notification.NotificationService;
import com.teenpatti.platform.notification.NotificationType;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnnouncementService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final WebSocketEventPublisher webSocketEventPublisher;
    private final AdminActionLogService adminActionLogService;

    public int broadcastAnnouncement(String adminUserId, AdminAnnouncementRequest request) {
        String fullMessage = request.getTitle() + ": " + request.getMessage();
        Map<String, String> payload = Map.of(
                "title", request.getTitle(),
                "message", request.getMessage()
        );

        webSocketEventPublisher.publishSystemAnnouncement(payload);

        int notified = 0;
        for (var user : userRepository.findAll()) {
            notificationService.notify(user.getId(), NotificationType.SYSTEM_ANNOUNCEMENT, fullMessage);
            notified++;
        }

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.SYSTEM_ANNOUNCEMENT,
                null,
                Map.of("title", request.getTitle(), "message", request.getMessage(), "recipientCount", notified)
        );

        log.info("Admin [{}] broadcast announcement to {} users: {}", adminUserId, notified, request.getTitle());
        return notified;
    }
}
