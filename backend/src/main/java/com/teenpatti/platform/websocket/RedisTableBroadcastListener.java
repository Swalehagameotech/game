package com.teenpatti.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.websocket.dto.TableBroadcastPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisTableBroadcastListener {

    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public void onMessage(String messageBody, String channel) {
        try {
            TableBroadcastPayload payload = objectMapper.readValue(messageBody, TableBroadcastPayload.class);
            String recipientUserId = payload.getRecipientUserId();

            if (sessionRegistry.isUserConnected(recipientUserId)) {
                WebSocketSession session = sessionRegistry.getWebSocketSession(recipientUserId);
                if (session != null && session.isOpen()) {
                    session.sendMessage(new TextMessage(payload.getMessageJson()));
                    log.debug("Redis PubSub listener delivered table [{}] message to local user [{}]", payload.getTableId(), recipientUserId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Redis PubSub table broadcast message: {}", e.getMessage(), e);
        }
    }
}
