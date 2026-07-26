package com.teenpatti.platform.notification;

import com.teenpatti.platform.common.event.HandCompletedEvent;
import com.teenpatti.platform.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class NotificationHandSettlementConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaConfig.HAND_COMPLETION_TOPIC, groupId = "notification-settlement-group")
    public void processNotificationSettlement(HandCompletedEvent event) {
        log.info("Kafka NotificationConsumer processing HandCompletedEvent for table [{}] hand [{}] winner [{}]",
                event.getTableId(), event.getHandId(), event.getWinnerId());

        long rupees = event.getWinnerPayoutPaise() / 100;
        notificationService.notify(
                event.getWinnerId(),
                NotificationType.GAME,
                "Congratulations! You won pot ₹" + rupees + " (" + event.getWinnerPayoutPaise() + " paise)!"
        );
    }
}
