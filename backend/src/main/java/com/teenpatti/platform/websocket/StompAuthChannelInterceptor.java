package com.teenpatti.platform.websocket;

import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Rejects STOMP CONNECT without JWT userId and restricts table topic subscriptions to seated players.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final TableRepository tableRepository;

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

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith(StompDestinations.TOPIC_TABLES + "/")) {
                String rawTableId = destination.substring((StompDestinations.TOPIC_TABLES + "/").length());
                final String tableId = rawTableId.contains("/")
                        ? rawTableId.substring(0, rawTableId.indexOf('/'))
                        : rawTableId;
                Principal user = accessor.getUser();
                String userId = user != null ? user.getName() : null;
                if (userId == null || userId.isBlank()) {
                    throw new IllegalArgumentException("Unauthorized table subscription");
                }
                Table table = tableRepository.findById(tableId)
                        .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));
                List<String> seated = table.getSeatedPlayerIds();
                if (seated == null || !seated.contains(userId)) {
                    log.warn("STOMP SUBSCRIBE rejected: user [{}] not seated at table [{}]", userId, tableId);
                    throw new IllegalArgumentException("Not authorized to subscribe to this table");
                }
            }
        }

        return message;
    }
}
