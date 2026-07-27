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

    private void deliverToPlayer(String tableId, String playerId, GameServerMessage message) {
        if (redisTemplate != null) {
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
                log.error("Redis broadcast failed for table [{}] player [{}]: {}", tableId, playerId, e.getMessage());
                sendToSession(playerId, message);
            }
        } else {
            sendToSession(playerId, message);
        }
    }

    private void sendToSession(String playerId, GameServerMessage message) {
        WebSocketSession session = sessionRegistry.getWebSocketSession(playerId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException e) {
            log.error("Failed to send WS message to user [{}]: {}", playerId, e.getMessage());
        }
    }
}
