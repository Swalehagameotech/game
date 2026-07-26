package com.teenpatti.platform.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConfig {

    public static final String HAND_COMPLETION_TOPIC = "hand-completion-events";

    @Bean
    public NewTopic handCompletionTopic() {
        return TopicBuilder.name(HAND_COMPLETION_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
