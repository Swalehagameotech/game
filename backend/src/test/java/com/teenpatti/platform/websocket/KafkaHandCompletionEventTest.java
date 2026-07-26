package com.teenpatti.platform.websocket;

import com.teenpatti.platform.common.event.HandCompletedEvent;
import com.teenpatti.platform.game.engine.HandRankCategory;
import com.teenpatti.platform.leaderboard.LeaderboardHandSettlementConsumer;
import com.teenpatti.platform.leaderboard.LeaderboardService;
import com.teenpatti.platform.notification.NotificationHandSettlementConsumer;
import com.teenpatti.platform.notification.NotificationService;
import com.teenpatti.platform.notification.NotificationType;
import com.teenpatti.platform.wallet.WalletHandSettlementConsumer;
import com.teenpatti.platform.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class KafkaHandCompletionEventTest {

    private WalletService walletService;
    private NotificationService notificationService;
    private LeaderboardService leaderboardService;

    private WalletHandSettlementConsumer walletConsumer;
    private NotificationHandSettlementConsumer notificationConsumer;
    private LeaderboardHandSettlementConsumer leaderboardConsumer;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        notificationService = mock(NotificationService.class);
        leaderboardService = mock(LeaderboardService.class);

        walletConsumer = new WalletHandSettlementConsumer(walletService);
        notificationConsumer = new NotificationHandSettlementConsumer(notificationService);
        leaderboardConsumer = new LeaderboardHandSettlementConsumer(leaderboardService);
    }

    @Test
    @DisplayName("Kafka HandCompletedEvent triggers independent wallet, notification, and leaderboard consumers")
    void handCompletedEvent_TriggersIndependentConsumers() {
        HandCompletedEvent event = HandCompletedEvent.builder()
                .tableId("table_k1")
                .handId("hand_k1")
                .winnerId("user_winner_1")
                .potAmountPaise(10_000L)
                .rakeAmountPaise(500L)
                .winnerPayoutPaise(9_500L)
                .winningCategory(HandRankCategory.TRAIL)
                .notes("Trio win")
                .participantUserIds(List.of("user_winner_1", "user_loser_1"))
                .timestamp(Instant.now())
                .build();

        // 1. Process Wallet Consumer
        walletConsumer.processWalletSettlement(event);
        verify(walletService, times(1)).applyLedgerEntry(eq("user_winner_1"), any(), eq(9_500L), contains("win"));

        // 2. Process Notification Consumer
        notificationConsumer.processNotificationSettlement(event);
        verify(notificationService, times(1)).notify(eq("user_winner_1"), eq(NotificationType.GAME), contains("won pot ₹95"));

        // 3. Process Leaderboard Consumer
        leaderboardConsumer.processLeaderboardSettlement(event);
        verify(leaderboardService, times(1)).recordHandResult(eq(event));
    }
}
