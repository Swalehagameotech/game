package com.teenpatti.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.game.engine.*;
import com.teenpatti.platform.lobby.config.StakeTierConfig;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.websocket.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket TextHandler managing real-time Teen Patti action routing, per-table serialized execution,
 * recipient-filtered broadcasts, and disconnect/reconnect flows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SessionRegistry sessionRegistry;
    private final PerTableActionExecutor perTableActionExecutor;
    private final GameStateProjector gameStateProjector;
    private final HandSettlementService handSettlementService;
    private final DisconnectGracePeriodManager disconnectGracePeriodManager;
    private final TableRepository tableRepository;
    private final StakeTierConfig stakeTierConfig;

    // Active Engine State per Table (Runtime In-Memory State)
    private final Map<String, BettingRoundEngine> activeEngines = new ConcurrentHashMap<>();
    private final Map<String, Instant> handStartTimeMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessionRegistry.registerUserSession(userId, session);
            boolean reconnected = disconnectGracePeriodManager.handleReconnect(userId);
            if (reconnected) {
                log.info("User [{}] reconnected to WebSocket session within grace period.", userId);
                String tableId = sessionRegistry.getTableIdForUser(userId);
                if (tableId != null) {
                    broadcastPlayerProjections(tableId);
                }
            }
        } else {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unauthenticated connection"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        GameWebSocketMessage msg;
        try {
            msg = objectMapper.readValue(message.getPayload(), GameWebSocketMessage.class);
        } catch (Exception e) {
            sendMessageToSession(session, GameServerMessage.error("Malformed JSON payload"));
            return;
        }

        String tableId = msg.getTableId();
        if (tableId == null || tableId.isBlank()) {
            sendMessageToSession(session, GameServerMessage.error("tableId is required"));
            return;
        }

        String type = msg.getType();
        if ("JOIN_TABLE".equalsIgnoreCase(type)) {
            handleJoinTable(userId, tableId, session);
        } else {
            handleGameAction(userId, tableId, msg, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            String tableId = sessionRegistry.getTableIdForUser(userId);
            sessionRegistry.unregisterUserSession(userId);
            if (tableId != null) {
                disconnectGracePeriodManager.handleDisconnect(userId, tableId, () -> handleAutoFoldOnTurn(userId, tableId));
            }
        }
    }

    private void handleJoinTable(String userId, String tableId, WebSocketSession session) {
        perTableActionExecutor.executeTableActionSync(tableId, () -> {
            Optional<Table> tableOpt = tableRepository.findById(tableId);
            if (tableOpt.isEmpty()) {
                sendMessageToSession(session, GameServerMessage.error("Table not found"));
                return;
            }

            Table table = tableOpt.get();
            if (!table.getSeatedPlayerIds().contains(userId)) {
                log.warn("User [{}] attempted to attach WS session to table [{}] without being seated", userId, tableId);
                sendMessageToSession(session, GameServerMessage.actionRejected("User is not seated at table"));
                return;
            }

            sessionRegistry.attachUserToTable(userId, tableId);
            disconnectGracePeriodManager.handleReconnect(userId);

            // If table has >= 2 players and no active engine, initialize and start hand
            if (table.getSeatedPlayerIds().size() >= 2 && !activeEngines.containsKey(tableId)) {
                startNewHand(table);
            }

            // Broadcast state projection
            broadcastPlayerProjections(tableId);
        });
    }

    private void handleGameAction(String userId, String tableId, GameWebSocketMessage msg, WebSocketSession session) {
        perTableActionExecutor.executeTableActionSync(tableId, () -> {
            Optional<Table> tableOpt = tableRepository.findById(tableId);
            if (tableOpt.isEmpty()) {
                sendMessageToSession(session, GameServerMessage.error("Table not found"));
                return;
            }
            Table table = tableOpt.get();

            if (!table.getSeatedPlayerIds().contains(userId)) {
                sendMessageToSession(session, GameServerMessage.actionRejected("User is not seated at table"));
                return;
            }

            BettingRoundEngine engine = activeEngines.get(tableId);
            if (engine == null) {
                sendMessageToSession(session, GameServerMessage.actionRejected("No active hand currently in progress"));
                return;
            }

            PlayerActionType actionType;
            try {
                actionType = PlayerActionType.valueOf(msg.getType().toUpperCase());
            } catch (Exception e) {
                sendMessageToSession(session, GameServerMessage.error("Invalid action type: " + msg.getType()));
                return;
            }

            PlayerAction action = PlayerAction.of(userId, actionType, msg.getAmountPaise());

            try {
                engine.applyAction(action);
            } catch (InvalidActionException ex) {
                log.warn("Action [{}] rejected for user [{}] on table [{}]: {}", actionType, userId, tableId, ex.getMessage());
                // Send rejection ONLY to acting player
                sendMessageToSession(session, GameServerMessage.actionRejected(ex.getMessage()));
                return;
            }

            // Action Succeeded -> Check Hand Completion
            if (engine.isHandFinished()) {
                HandOutcome outcome = engine.getOutcome();
                String handId = UUID.randomUUID().toString();
                Instant startedAt = handStartTimeMap.getOrDefault(tableId, Instant.now());

                handSettlementService.settleCompletedHand(table, handId, outcome, startedAt);
                activeEngines.remove(tableId);
                handStartTimeMap.remove(tableId);
            }

            // Broadcast updated projections to all connected players at table
            broadcastPlayerProjections(tableId);
        });
    }

    private void startNewHand(Table table) {
        String tableId = table.getId();
        long minBuyIn = stakeTierConfig.getMinBuyInPaise(table.getStakeTier());
        long maxBet = minBuyIn * 50;

        GameEngineConfig engineConfig = GameEngineConfig.defaultConfig(minBuyIn, maxBet);
        BettingRoundEngine engine = new BettingRoundEngine(engineConfig);

        Deck deck = new Deck();
        deck.shuffle();

        engine.startHand(new ArrayList<>(table.getSeatedPlayerIds()), deck);
        activeEngines.put(tableId, engine);
        handStartTimeMap.put(tableId, Instant.now());

        table.setStatus(TableStatus.IN_PROGRESS);
        tableRepository.save(table);

        log.info("Started new Teen Patti hand on table [{}] with {} players", tableId, table.getSeatedPlayerIds().size());
    }

    private void handleAutoFoldOnTurn(String userId, String tableId) {
        perTableActionExecutor.executeTableActionSync(tableId, () -> {
            BettingRoundEngine engine = activeEngines.get(tableId);
            if (engine != null && !engine.isHandFinished() && userId.equals(engine.getCurrentTurnPlayerId())) {
                log.info("Auto-folding player [{}] on table [{}] due to disconnect turn arrival", userId, tableId);
                try {
                    engine.applyAction(PlayerAction.of(userId, PlayerActionType.PACK));
                    if (engine.isHandFinished()) {
                        Optional<Table> tableOpt = tableRepository.findById(tableId);
                        if (tableOpt.isPresent()) {
                            Table table = tableOpt.get();
                            handSettlementService.settleCompletedHand(table, UUID.randomUUID().toString(), engine.getOutcome(), Instant.now());
                            activeEngines.remove(tableId);
                        }
                    }
                    broadcastPlayerProjections(tableId);
                } catch (Exception e) {
                    log.error("Failed auto-fold for user [{}]: {}", userId, e.getMessage(), e);
                }
            }
        });
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private void broadcastPlayerProjections(String tableId) {
        Optional<Table> tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) return;
        Table table = tableOpt.get();
        BettingRoundEngine engine = activeEngines.get(tableId);

        List<String> seatedIds = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
        for (String pid : seatedIds) {
            PlayerViewGameState projection = gameStateProjector.createProjection(table, engine, pid);
            GameServerMessage msg = GameServerMessage.stateUpdate(projection);

            if (redisTemplate != null) {
                try {
                    String messageJson = objectMapper.writeValueAsString(msg);
                    TableBroadcastPayload payload = TableBroadcastPayload.builder()
                            .tableId(tableId)
                            .recipientUserId(pid)
                            .messageJson(messageJson)
                            .build();
                    redisTemplate.convertAndSend(RedisClusterConfig.TABLE_BROADCAST_CHANNEL, objectMapper.writeValueAsString(payload));
                } catch (Exception e) {
                    log.error("Redis PubSub publish error for table [{}], recipient [{}]: {}", tableId, pid, e.getMessage());
                    WebSocketSession session = sessionRegistry.getWebSocketSession(pid);
                    if (session != null && session.isOpen()) {
                        sendMessageToSession(session, msg);
                    }
                }
            } else {
                WebSocketSession session = sessionRegistry.getWebSocketSession(pid);
                if (session != null && session.isOpen()) {
                    sendMessageToSession(session, msg);
                }
            }
        }
    }

    private void sendMessageToSession(WebSocketSession session, GameServerMessage message) {
        try {
            if (session != null && session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("Failed to send WebSocket message to session [{}]: {}", session.getId(), e.getMessage());
        }
    }

    public BettingRoundEngine getActiveEngineForTable(String tableId) {
        return activeEngines.get(tableId);
    }
}
