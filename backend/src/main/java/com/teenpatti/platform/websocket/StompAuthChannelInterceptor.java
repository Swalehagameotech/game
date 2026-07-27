package com.teenpatti.platform.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Rejects STOMP CONNECT when JWT handshake did not bind a userId to the WebSocket session.
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            Object userId = sessionAttributes != null ? sessionAttributes.get("userId") : null;
            if (userId == null || userId.toString().isBlank()) {
                log.warn("STOMP CONNECT rejected: missing authenticated userId on session");
                throw new IllegalArgumentException("Unauthorized STOMP connection");
            }
            accessor.setUser(() -> userId.toString());
            log.info("STOMP CONNECT authorized for user [{}]", userId);
        }

        return message;
    }
}
