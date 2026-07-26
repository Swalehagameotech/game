package com.teenpatti.platform.websocket;

import com.teenpatti.platform.game.HandSummary;
import com.teenpatti.platform.game.MatchHistory;
import com.teenpatti.platform.game.MatchHistoryRepository;
import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.wallet.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.teenpatti.platform.wallet.Wallet;
import com.teenpatti.platform.wallet.WalletRepository;

import com.teenpatti.platform.leaderboard.LeaderboardService;

import com.teenpatti.platform.common.event.HandCompletedEvent;
import com.teenpatti.platform.config.KafkaConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Service managing financial settlement, platform rake collection, and immutable
 * MatchHistory audit logging upon hand completion.
 */
@Slf4j
@Service
public class HandSettlementService {

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final TableRepository tableRepository;
    private final LeaderboardService leaderboardService;
    private final KafkaTemplate<String, HandCompletedEvent> kafkaTemplate;
    private final String houseAccountId;

    public HandSettlementService(
            WalletService walletService,
            WalletRepository walletRepository,
            MatchHistoryRepository matchHistoryRepository,
            TableRepository tableRepository,
            LeaderboardService leaderboardService,
            @Autowired(required = false) KafkaTemplate<String, HandCompletedEvent> kafkaTemplate,
            @Value("${app.game.house-account-id:house_platform_admin}") String houseAccountId) {
        this.walletService = walletService;
        this.walletRepository = walletRepository;
        this.matchHistoryRepository = matchHistoryRepository;
        this.tableRepository = tableRepository;
        this.leaderboardService = leaderboardService;
        this.kafkaTemplate = kafkaTemplate;
        this.houseAccountId = houseAccountId;
    }

    private void ensureHouseWalletExists() {
        if (walletRepository.findByUserId(houseAccountId).isEmpty()) {
            walletRepository.save(Wallet.builder()
                    .userId(houseAccountId)
                    .balancePaise(0L)
                    .currency("INR")
                    .build());
            log.info("Initialized platform house wallet for [{}]", houseAccountId);
        }
    }

    public void settleCompletedHand(Table table, String handId, HandOutcome outcome, Instant startedAt) {
        if (table == null || handId == null || outcome == null) {
            throw new IllegalArgumentException("Table, handId, and outcome must not be null");
        }

        String tableId = table.getId();
        String winnerId = outcome.getWinnerId();
        long winnerPayoutPaise = outcome.getWinnerPayoutPaise();
        long rakeAmountPaise = outcome.getRakeAmountPaise();

        // Construct HandCompletedEvent for Kafka Decoupled Consumption (Phase 16)
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

        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send(KafkaConfig.HAND_COMPLETION_TOPIC, tableId, event);
                log.info("Published HandCompletedEvent to Kafka topic [{}] for table [{}] hand [{}]", KafkaConfig.HAND_COMPLETION_TOPIC, tableId, handId);
            } catch (Exception kafkaEx) {
                log.warn("Kafka publish failed, falling back to direct synchronous settlement: {}", kafkaEx.getMessage());
                executeDirectSynchronousSettlement(table, handId, outcome, winnerId, winnerPayoutPaise, rakeAmountPaise, tableId);
            }
        } else {
            executeDirectSynchronousSettlement(table, handId, outcome, winnerId, winnerPayoutPaise, rakeAmountPaise, tableId);
        }

        // 3. Construct and Save MatchHistory Audit Record
        Map<String, String> cardStringMap = new HashMap<>();
        if (outcome.getRevealedHands() != null) {
            for (Map.Entry<String, List<Card>> entry : outcome.getRevealedHands().entrySet()) {
                String cardsStr = entry.getValue().stream()
                        .map(Card::toShortString)
                        .collect(Collectors.joining(","));
                cardStringMap.put(entry.getKey(), cardsStr);
            }
        }

        HandSummary handSummary = HandSummary.builder()
                .winningHandName(outcome.getWinningCategory() != null ? outcome.getWinningCategory().name() : "FOLD_WIN")
                .winningRank(outcome.getWinningCategory() != null ? outcome.getWinningCategory().getPriority() : 0)
                .playerHandsMap(cardStringMap)
                .notes(outcome.getNotes())
                .build();

        MatchHistory history = MatchHistory.builder()
                .tableId(tableId)
                .playerIds(new ArrayList<>(table.getSeatedPlayerIds()))
                .winnerId(winnerId)
                .potAmountPaise(outcome.getPotAmountPaise())
                .rakeAmountPaise(rakeAmountPaise)
                .handSummary(handSummary)
                .startedAt(startedAt != null ? startedAt : Instant.now())
                .endedAt(Instant.now())
                .build();

        MatchHistory savedHistory = matchHistoryRepository.save(history);
        log.info("Persisted MatchHistory [{}] for table [{}], winner [{}] payout {} paise, rake {} paise",
                savedHistory.getId(), tableId, winnerId, winnerPayoutPaise, rakeAmountPaise);

        // 4. Update Table Status
        if (table.getSeatedPlayerIds() == null || table.getSeatedPlayerIds().size() < 2) {
            table.setStatus(TableStatus.WAITING);
            tableRepository.save(table);
        }
    }

    private void executeDirectSynchronousSettlement(Table table, String handId, HandOutcome outcome, String winnerId, long winnerPayoutPaise, long rakeAmountPaise, String tableId) {
        String refWin = "table:" + tableId + ":hand:" + handId + ":win";
        walletService.applyLedgerEntry(winnerId, LedgerEntryType.WIN, winnerPayoutPaise, refWin);

        if (rakeAmountPaise > 0) {
            ensureHouseWalletExists();
            String refRake = "table:" + tableId + ":hand:" + handId + ":rake";
            walletService.applyLedgerEntry(houseAccountId, LedgerEntryType.RAKE, rakeAmountPaise, refRake);
        }

        if (leaderboardService != null) {
            leaderboardService.recordHandResult(table, outcome);
        }
    }
}
