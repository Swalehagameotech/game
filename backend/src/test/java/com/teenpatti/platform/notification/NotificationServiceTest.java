package com.teenpatti.platform.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.common.exception.ResourceNotFoundException;
import com.teenpatti.platform.websocket.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationServiceTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private SessionRegistry sessionRegistry;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        sessionRegistry = mock(SessionRegistry.class);
        notificationService = new NotificationService(notificationRepository, sessionRegistry, objectMapper);
    }

    @Test
    @DisplayName("notify() persists notification and attempts WebSocket push if user is connected")
    void notify_ConnectedUser_PushesWebSocketMessage() throws Exception {
        String userId = "user_online_1";
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.isOpen()).thenReturn(true);
        when(sessionRegistry.isUserConnected(userId)).thenReturn(true);
        when(sessionRegistry.getWebSocketSession(userId)).thenReturn(mockSession);

        Notification n = notificationService.notify(userId, NotificationType.DEPOSIT_SUCCESS, "Deposit of ₹500 successful.");

        assertNotNull(n);
        assertNotNull(n.getId());
        assertFalse(n.isRead());
        verify(mockSession, times(1)).sendMessage(any());

        assertEquals(1, notificationRepository.count());
    }

    @Test
    @DisplayName("notify() persists notification without push if user is offline")
    void notify_OfflineUser_PersistsNotificationOnly() {
        String userId = "user_offline_1";
        when(sessionRegistry.isUserConnected(userId)).thenReturn(false);

        Notification n = notificationService.notify(userId, NotificationType.WITHDRAWAL_SUCCESS, "Withdrawal approved.");

        assertNotNull(n);
        assertFalse(n.isRead());
        assertEquals(1, notificationRepository.count());
    }

    @Test
    @DisplayName("markAsRead() updates status for owner and rejects non-owner with 404")
    void markAsRead_EnforcesUserOwnership() {
        Notification n = notificationRepository.save(Notification.builder()
                .userId("user_a")
                .type(NotificationType.SYSTEM_ANNOUNCEMENT)
                .message("Welcome!")
                .isRead(false)
                .build());

        // Marking own notification succeeds
        Notification read = notificationService.markAsRead("user_a", n.getId());
        assertTrue(read.isRead());

        // Marking another user's notification throws ResourceNotFoundException
        assertThrows(ResourceNotFoundException.class, () -> notificationService.markAsRead("user_b", n.getId()));
    }

    @Test
    @DisplayName("markAllAsRead() marks all unread notifications for target user")
    void markAllAsRead_Succeeds() {
        notificationRepository.save(Notification.builder().userId("user_c").type(NotificationType.GAME).message("Game 1").isRead(false).build());
        notificationRepository.save(Notification.builder().userId("user_c").type(NotificationType.GAME).message("Game 2").isRead(false).build());

        assertEquals(2, notificationService.getUnreadCount("user_c"));

        notificationService.markAllAsRead("user_c");

        assertEquals(0, notificationService.getUnreadCount("user_c"));
    }

    @Test
    @DisplayName("Defensive Isolation: WebSocket exception during push does NOT throw or propagate to caller")
    void notify_WebSocketError_DoesNotThrow() throws Exception {
        String userId = "user_err_1";
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.isOpen()).thenReturn(true);
        when(sessionRegistry.isUserConnected(userId)).thenReturn(true);
        when(sessionRegistry.getWebSocketSession(userId)).thenReturn(mockSession);

        doThrow(new RuntimeException("Simulated socket error")).when(mockSession).sendMessage(any());

        assertDoesNotThrow(() -> {
            Notification n = notificationService.notify(userId, NotificationType.ACCOUNT_ALERT, "Account suspended.");
            assertNotNull(n);
        });
    }
}
