package com.teenpatti.platform.notification;

import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.support.TestDataFactory;
import com.teenpatti.platform.support.TestDataFactory.TestUserContext;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.teenpatti.platform.support.TestDataFactory.bearer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private TestUserContext user;
    private String notificationId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        user = TestDataFactory.createPlayer(
                userRepository, walletRepository, jwtTokenProvider,
                "notify@test.com", "NotifyUser", 10_000L);

        Notification saved = notificationService.notify(
                user.user().getId(),
                NotificationType.DEPOSIT_SUCCESS,
                "Deposit Successful",
                "Your deposit of ₹100 was successful.",
                java.util.Map.of("amountPaise", 10_000L));
        notificationId = saved.getId();
    }

    @Test
    @DisplayName("GET /api/notifications returns paginated summary DTOs")
    void listNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("DEPOSIT_SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].displayLabel").value("Deposit Successful"))
                .andExpect(jsonPath("$.data.content[0].isRead").value(false));
    }

    @Test
    @DisplayName("POST /api/notifications/{id}/read marks single notification read")
    void markSingleRead() throws Exception {
        mockMvc.perform(post("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("POST /api/notifications/read-all clears unread count")
    void markAllRead() throws Exception {
        notificationService.notify(user.user().getId(), NotificationType.GAME, "Another alert");

        mockMvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(user.accessToken())))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }
}
