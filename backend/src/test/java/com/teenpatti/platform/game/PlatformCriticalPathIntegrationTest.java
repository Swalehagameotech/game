package com.teenpatti.platform.game;

import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.game.engine.HandRankCategory;
import com.teenpatti.platform.notification.NotificationRepository;
import com.teenpatti.platform.support.TestDataFactory;
import com.teenpatti.platform.support.TestDataFactory.TestUserContext;
import com.teenpatti.platform.table.GameVariant;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.WalletRepository;
import com.teenpatti.platform.websocket.HandSettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.teenpatti.platform.support.TestDataFactory.bearer;

/**
 * End-to-end verification for Modules 17–19: settlement → history → notifications → REST.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class PlatformCriticalPathIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HandSettlementService handSettlementService;

    @Autowired
    private GameHistoryRepository gameHistoryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private TestUserContext winner;
    private TestUserContext loser;
    private Table table;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        gameHistoryRepository.deleteAll();
        tableRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        winner = TestDataFactory.createPlayer(
                userRepository, walletRepository, jwtTokenProvider,
                "winner@test.com", "Winner", 50_000L);
        loser = TestDataFactory.createPlayer(
                userRepository, walletRepository, jwtTokenProvider,
                "loser@test.com", "Loser", 50_000L);

        table = tableRepository.save(Table.builder()
                .tableName("Integration Table")
                .tableType(TableType.PUBLIC)
                .gameVariant(GameVariant.CLASSIC)
                .bootAmountPaise(1_000L)
                .minPlayers(2)
                .maxPlayers(6)
                .roundNumber(1)
                .seatedPlayerIds(List.of(winner.user().getId(), loser.user().getId()))
                .status(TableStatus.IN_PROGRESS)
                .potPaise(10_000L)
                .currentHandId("hand_int_1")
                .build());
    }

    @Test
    @DisplayName("Hand settlement writes history, notifications, and exposes REST APIs")
    void handSettlement_criticalPath() throws Exception {
        HandOutcome outcome = new HandOutcome(
                winner.user().getId(),
                10_000L,
                500L,
                9_500L,
                HandRankCategory.PAIR,
                java.util.Map.of(),
                "Integration test hand");

        handSettlementService.settleCompletedHand(table, "hand_int_1", outcome, Instant.now());

        assertTrue(gameHistoryRepository.findByHandId("hand_int_1").isPresent());
        assertEquals(2, notificationRepository.count());
        assertTrue(notificationRepository.findByUserIdOrderByCreatedAtDesc(winner.user().getId(), PageRequest.of(0, 5))
                .stream().anyMatch(n -> n.getType() == com.teenpatti.platform.notification.NotificationType.GAME));
        assertTrue(notificationRepository.findByUserIdOrderByCreatedAtDesc(loser.user().getId(), PageRequest.of(0, 5))
                .stream().anyMatch(n -> n.getType() == com.teenpatti.platform.notification.NotificationType.GAME));

        mockMvc.perform(get("/api/game/history")
                        .header("Authorization", bearer(winner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].result").value("WON"))
                .andExpect(jsonPath("$.data.content[0].winnerPayoutPaise").value(9500));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(loser.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(get("/api/home/dashboard")
                        .header("Authorization", bearer(winner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recentHistory").isArray())
                .andExpect(jsonPath("$.data.recentHistory[0].result").value("WON"));
    }
}
