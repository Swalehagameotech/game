package com.teenpatti.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.HandContextManager;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.websocket.dto.GameServerMessage;
import com.teenpatti.platform.websocket.dto.PlayerViewGameState;
import com.teenpatti.platform.websocket.dto.TableBroadcastPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Sends per-player filtered STATE_UPDATE messages over raw WebSocket (and optional Redis pub/sub).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameBroadcastService {

    private final ObjectMapper objectMapper;
    private final SessionRegistry sessionRegistry;
    private final GameStateProjector gameStateProjector;
    private final HandContextManager handContextManager;
    private final TableRepository tableRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Lazy
    @Autowired(required = false)
    private WebSocketEventPublisher eventPublisher;

    public void broadcastTableState(String tableId) {
        Optional<Table> tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) {
            return;
        }
        Table table = tableOpt.get();
        BettingRoundEngine engine = handContextManager.getEngine(tableId).orElse(null);

        List<String> seatedIds = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
        for (String playerId : seatedIds) {
            PlayerViewGameState projection = gameStateProjector.createProjection(table, engine, playerId);
            GameServerMessage message = GameServerMessage.stateUpdate(projection);
            deliverToPlayer(tableId, playerId, message);
        }
    }

    public void deliverPrivateHand(String tableId, String playerId) {
        Optional<Table> tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) {
            return;
        }
        Table table = tableOpt.get();
        BettingRoundEngine engine = handContextManager.getEngine(tableId).orElse(null);
        PlayerViewGameState projection = gameStateProjector.createProjection(table, engine, playerId);
        deliverToPlayer(tableId, playerId, GameServerMessage.stateUpdate(projection));
    }

    /** Delivers a typed gameplay event over raw WebSocket (mirrors STOMP for reliability). */
    public void deliverEventToPlayer(String playerId, String eventType, Object payload) {
        if (playerId == null || eventType == null) {
            return;
        }
        GameServerMessage message = GameServerMessage.builder()
                .type(eventType)
                .payload(payload)
                .build();
        boolean sent = sendToSession(playerId, message);
        if (!sent && eventPublisher != null) {
            // Fallback so Show / Winner still reach the client when raw WS session is missing.
            eventPublisher.publishEvent(StompDestinations.queueGame(playerId), eventType, payload);
            log.warn("Raw WS miss for user [{}] event [{}] — fell back to STOMP user queue", playerId, eventType);
        }
    }

    /** Broadcasts a typed event to every seated player's raw WebSocket session. */
    public void broadcastEvent(String tableId, String eventType, Object payload) {
        Optional<Table> tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) {
            return;
        }
        List<String> seatedIds = tableOpt.get().getSeatedPlayerIds() != null
                ? tableOpt.get().getSeatedPlayerIds() : List.of();
        for (String playerId : seatedIds) {
            deliverEventToPlayer(playerId, eventType, payload);
        }
        // Also fan-out on the table STOMP topic so subscribed clients get it once.
        if (eventPublisher != null && tableId != null) {
            eventPublisher.publishEvent(StompDestinations.topicTable(tableId), eventType, payload);
        }
    }

    private void deliverToPlayer(String tableId, String playerId, GameServerMessage message) {
        // Always deliver locally first so Redis downtime never blocks live updates.
        boolean sent = sendToSession(playerId, message);
        if (!sent && eventPublisher != null && message.getType() != null) {
            eventPublisher.publishEvent(
                    StompDestinations.queueGame(playerId),
                    message.getType(),
                    message.getPayload());
            log.warn("Raw WS STATE miss for user [{}] on table [{}] — STOMP queue fallback", playerId, tableId);
        }

        if (redisTemplate == null) {
            return;
        }
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            TableBroadcastPayload payload = TableBroadcastPayload.builder()
                    .tableId(tableId)
                    .recipientUserId(playerId)
                    .messageJson(messageJson)
                    .build();
            redisTemplate.convertAndSend(
                    RedisClusterConfig.TABLE_BROADCAST_CHANNEL,
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.debug("Redis fan-out skipped for table [{}] player [{}]: {}", tableId, playerId, e.getMessage());
        }
    }

    /** @return true if delivered on an open raw WebSocket session */
    private boolean sendToSession(String playerId, GameServerMessage message) {
        WebSocketSession session = sessionRegistry.getWebSocketSession(playerId);
        if (session == null || !session.isOpen()) {
            log.warn("No open raw WS session for user [{}] — dropping local deliver of [{}]",
                    playerId, message != null ? message.getType() : "null");
            return false;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            return true;
        } catch (IOException e) {
            log.error("Failed to send WS message to user [{}]: {}", playerId, e.getMessage());
            return false;
        }
    }
}
