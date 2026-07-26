package com.teenpatti.platform.leaderboard;

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
public class LeaderboardHandSettlementConsumer {

    private final LeaderboardService leaderboardService;

    @KafkaListener(topics = KafkaConfig.HAND_COMPLETION_TOPIC, groupId = "leaderboard-settlement-group")
    public void processLeaderboardSettlement(HandCompletedEvent event) {
        log.info("Kafka LeaderboardConsumer processing HandCompletedEvent for table [{}] hand [{}] winner [{}]",
                event.getTableId(), event.getHandId(), event.getWinnerId());

        leaderboardService.recordHandResult(event);
    }
}
