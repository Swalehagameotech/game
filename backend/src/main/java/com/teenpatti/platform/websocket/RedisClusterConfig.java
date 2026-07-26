package com.teenpatti.platform.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisClusterConfig {

    public static final String TABLE_BROADCAST_CHANNEL = "game-table-broadcasts";

    @Bean
    public ChannelTopic tableBroadcastTopic() {
        return new ChannelTopic(TABLE_BROADCAST_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter,
            ChannelTopic tableBroadcastTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, tableBroadcastTopic);
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisTableBroadcastListener broadcastListener) {
        return new MessageListenerAdapter(broadcastListener, "onMessage");
    }
}
