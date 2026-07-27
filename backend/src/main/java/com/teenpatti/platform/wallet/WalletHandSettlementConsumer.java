package com.teenpatti.platform.wallet;

import com.teenpatti.platform.common.event.HandCompletedEvent;
import com.teenpatti.platform.config.KafkaConfig;
import com.teenpatti.platform.wallet.settlement.WalletSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class WalletHandSettlementConsumer {

    private final WalletSettlementService walletSettlementService;

    @KafkaListener(topics = KafkaConfig.HAND_COMPLETION_TOPIC, groupId = "wallet-settlement-group")
    public void processWalletSettlement(HandCompletedEvent event) {
        log.info("Kafka WalletConsumer processing HandCompletedEvent for table [{}] hand [{}] winner [{}]",
                event.getTableId(), event.getHandId(), event.getWinnerId());
        walletSettlementService.settleFromEvent(event);
    }
}
