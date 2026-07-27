package com.teenpatti.platform.websocket;

import com.teenpatti.platform.common.event.HandCompletedEvent;
import com.teenpatti.platform.game.engine.HandRankCategory;
import com.teenpatti.platform.leaderboard.LeaderboardHandSettlementConsumer;
import com.teenpatti.platform.leaderboard.LeaderboardService;
import com.teenpatti.platform.notification.NotificationHandSettlementConsumer;
import com.teenpatti.platform.notification.NotificationService;
import com.teenpatti.platform.wallet.WalletHandSettlementConsumer;
import com.teenpatti.platform.wallet.settlement.WalletSettlementResult;
import com.teenpatti.platform.wallet.settlement.WalletSettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@ActiveProfiles("test")
class KafkaHandCompletionEventTest {

    private WalletSettlementService walletSettlementService;
    private NotificationService notificationService;
    private LeaderboardService leaderboardService;

    private WalletHandSettlementConsumer walletConsumer;
    private NotificationHandSettlementConsumer notificationConsumer;
    private LeaderboardHandSettlementConsumer leaderboardConsumer;

    @BeforeEach
    void setUp() {
        walletSettlementService = mock(WalletSettlementService.class);
        notificationService = mock(NotificationService.class);
        leaderboardService = mock(LeaderboardService.class);

        walletConsumer = new WalletHandSettlementConsumer(walletSettlementService);
        notificationConsumer = new NotificationHandSettlementConsumer();
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

        when(walletSettlementService.settleFromEvent(event)).thenReturn(
                WalletSettlementResult.builder()
                        .tableId("table_k1")
                        .handId("hand_k1")
                        .winnerUserId("user_winner_1")
                        .payoutPaise(9_500L)
                        .build());

        walletConsumer.processWalletSettlement(event);
        verify(walletSettlementService, times(1)).settleFromEvent(event);

        notificationConsumer.processNotificationSettlement(event);
        verifyNoInteractions(notificationService);

        leaderboardConsumer.processLeaderboardSettlement(event);
        verify(leaderboardService, times(1)).recordHandResult(eq(event));
    }
}
