package com.teenpatti.platform.wallet;

import com.teenpatti.platform.common.event.HandCompletedEvent;
import com.teenpatti.platform.config.KafkaConfig;
import com.teenpatti.platform.transaction.LedgerEntryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class WalletHandSettlementConsumer {

    private final WalletService walletService;

    @Value("${app.game.house-account-id:house_platform_admin}")
    private String houseAccountId;

    @KafkaListener(topics = KafkaConfig.HAND_COMPLETION_TOPIC, groupId = "wallet-settlement-group")
    public void processWalletSettlement(HandCompletedEvent event) {
        log.info("Kafka WalletConsumer processing HandCompletedEvent for table [{}] hand [{}] winner [{}]",
                event.getTableId(), event.getHandId(), event.getWinnerId());

        String winnerId = event.getWinnerId();
        long winnerPayoutPaise = event.getWinnerPayoutPaise();
        long rakeAmountPaise = event.getRakeAmountPaise();

        // 1. Credit Winner (Idempotent via referenceId)
        String refWin = "table:" + event.getTableId() + ":hand:" + event.getHandId() + ":win";
        walletService.applyLedgerEntry(winnerId, LedgerEntryType.WIN, winnerPayoutPaise, refWin);

        // 2. Credit Platform Rake (Idempotent via referenceId)
        if (rakeAmountPaise > 0) {
            String refRake = "table:" + event.getTableId() + ":hand:" + event.getHandId() + ":rake";
            walletService.applyLedgerEntry(houseAccountId, LedgerEntryType.RAKE, rakeAmountPaise, refRake);
        }
    }
}
