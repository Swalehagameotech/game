package com.teenpatti.platform.notification;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.notification.dto.UnreadCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<Notification>> getNotifications(
            @CurrentUser String userId,
            Pageable pageable) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, pageable));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Notification> markAsRead(
            @CurrentUser String userId,
            @PathVariable String notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(userId, notificationId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@CurrentUser String userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(@CurrentUser String userId) {
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(new UnreadCountResponse(count));
    }
}
