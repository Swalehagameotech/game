package com.teenpatti.platform.websocket;

import com.teenpatti.platform.common.event.HandCompletedEvent;
import com.teenpatti.platform.config.KafkaConfig;
import com.teenpatti.platform.game.GameHistoryService;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.notification.NotificationService;
import com.teenpatti.platform.leaderboard.LeaderboardService;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableRoundEndedEvent;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.user.UserService;
import com.teenpatti.platform.wallet.settlement.WalletSettlementResult;
import com.teenpatti.platform.wallet.settlement.WalletSettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;

/**
 * Orchestrates post-hand settlement: wallet credits, match history audit, table lifecycle.
 * Wallet ledger writes are delegated to {@link WalletSettlementService}.
 */
@Slf4j
@Service
public class HandSettlementService {

    private final WalletSettlementService walletSettlementService;
    private final GameHistoryService gameHistoryService;
    private final NotificationService notificationService;
    private final TableRepository tableRepository;
    private final LeaderboardService leaderboardService;
    private final UserService userService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final KafkaTemplate<String, HandCompletedEvent> kafkaTemplate;

    public HandSettlementService(
            WalletSettlementService walletSettlementService,
            GameHistoryService gameHistoryService,
            NotificationService notificationService,
            TableRepository tableRepository,
            LeaderboardService leaderboardService,
            UserService userService,
            ApplicationEventPublisher applicationEventPublisher,
            @Autowired(required = false) KafkaTemplate<String, HandCompletedEvent> kafkaTemplate) {
        this.walletSettlementService = walletSettlementService;
        this.gameHistoryService = gameHistoryService;
        this.notificationService = notificationService;
        this.tableRepository = tableRepository;
        this.leaderboardService = leaderboardService;
        this.userService = userService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.kafkaTemplate = kafkaTemplate;
    }

    public WalletSettlementResult settleCompletedHand(Table table, String handId, HandOutcome outcome, Instant startedAt) {
        if (table == null || handId == null || outcome == null) {
            throw new IllegalArgumentException("Table, handId, and outcome must not be null");
        }

        String tableId = table.getId();
        String winnerId = outcome.getWinnerId();
        long winnerPayoutPaise = outcome.getWinnerPayoutPaise();
        long rakeAmountPaise = outcome.getRakeAmountPaise();

        HandCompletedEvent event = HandCompletedEvent.builder()
                .tableId(tableId)
                .handId(handId)
                .winnerId(winnerId)
                .potAmountPaise(outcome.getPotAmountPaise())
                .rakeAmountPaise(rakeAmountPaise)
                .winnerPayoutPaise(winnerPayoutPaise)
                .winningCategory(outcome.getWinningCategory())
                .notes(outcome.getNotes())
                .participantUserIds(new ArrayList<>(table.getSeatedPlayerIds()))
                .timestamp(Instant.now())
                .build();

        WalletSettlementResult walletResult = null;
        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send(KafkaConfig.HAND_COMPLETION_TOPIC, tableId, event);
                log.info("Published HandCompletedEvent to Kafka topic [{}] for table [{}] hand [{}]",
                        KafkaConfig.HAND_COMPLETION_TOPIC, tableId, handId);
            } catch (Exception kafkaEx) {
                log.warn("Kafka publish failed, falling back to direct synchronous settlement: {}", kafkaEx.getMessage());
                walletResult = walletSettlementService.settleHand(tableId, handId, outcome);
                recordLeaderboard(table, outcome);
            }
        } else {
            walletResult = walletSettlementService.settleHand(tableId, handId, outcome);
            recordLeaderboard(table, outcome);
        }

        gameHistoryService.recordCompletedHand(table, handId, outcome, startedAt);
        notificationService.notifyHandCompleted(
                tableId,
                handId,
                winnerId,
                winnerPayoutPaise,
                outcome.getPotAmountPaise(),
                table.getSeatedPlayerIds());
        updateTableAfterHand(table);

        if (table.getSeatedPlayerIds() != null) {
            table.getSeatedPlayerIds().forEach(userService::incrementMatchesPlayed);
        }

        applicationEventPublisher.publishEvent(new TableRoundEndedEvent(tableId));
        return walletResult;
    }

    private void recordLeaderboard(Table table, HandOutcome outcome) {
        if (leaderboardService != null) {
            leaderboardService.recordHandResult(table, outcome);
        }
    }

    private void updateTableAfterHand(Table table) {
        // Status transitions (WAITING / NEXT_ROUND / CLOSED) are owned by RoundManagementService.
        table.setPotPaise(0);
        tableRepository.save(table);
    }
}
