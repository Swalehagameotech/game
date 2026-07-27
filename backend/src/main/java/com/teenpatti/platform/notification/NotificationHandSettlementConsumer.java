package com.teenpatti.platform.notification;

import com.teenpatti.platform.common.event.HandCompletedEvent;
import com.teenpatti.platform.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka listener placeholder — hand notifications are delivered synchronously from
 * {@link com.teenpatti.platform.websocket.HandSettlementService} to avoid duplicate alerts.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class NotificationHandSettlementConsumer {

    @KafkaListener(topics = KafkaConfig.HAND_COMPLETION_TOPIC, groupId = "notification-settlement-group")
    public void processNotificationSettlement(HandCompletedEvent event) {
        log.debug("Kafka notification consumer received hand [{}] on table [{}] — handled by sync settlement path",
                event.getHandId(), event.getTableId());
    }
}
