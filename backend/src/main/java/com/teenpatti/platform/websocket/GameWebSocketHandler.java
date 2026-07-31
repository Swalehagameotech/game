package com.teenpatti.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.game.GameEngineService;
import com.teenpatti.platform.game.engine.HandContextManager;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.websocket.dto.GameServerMessage;
import com.teenpatti.platform.websocket.dto.GameWebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Optional;
import java.util.Set;

/**
 * WebSocket handler for real-time Teen Patti actions. Game start is REST-only (host).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Set<String> ALLOWED_WS_ACTIONS = Set.of(
            "PLAY_BLIND", "BLIND", "SEE_CARDS", "CHAAL", "CALL", "RAISE", "PACK", "SHOW",
            "SHOW_ACCEPT", "SHOW_REJECT", "SHOW_DECLINE",
            "SIDE_SHOW_REQUEST", "SIDE_SHOW_ACCEPT", "SIDE_SHOW_REJECT",
            "DISCARD_CARD", "AUCTION_BID", "AUCTION_PASS"
    );

    private final ObjectMapper objectMapper;
    private final SessionRegistry sessionRegistry;
    private final PerTableActionExecutor perTableActionExecutor;
    private final GameEngineService gameEngineService;
    private final HandContextManager handContextManager;
    private final DisconnectGracePeriodManager disconnectGracePeriodManager;
    private final TableRepository tableRepository;
    private final GameBroadcastService gameBroadcastService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessionRegistry.registerUserSession(userId, session);
            String tableId = sessionRegistry.getTableIdForUser(userId);
            boolean reconnected = disconnectGracePeriodManager.handleReconnect(userId, tableId);
            if (reconnected) {
                log.info("User [{}] reconnected to WebSocket session.", userId);
                if (tableId != null) {
                    gameBroadcastService.broadcastTableState(tableId);
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
            sessionRegistry.unregisterUserSession(userId, session);
            // Only start disconnect grace if this user truly has no live socket left.
            if (tableId != null && !sessionRegistry.isUserConnected(userId)) {
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
                sendMessageToSession(session, GameServerMessage.actionRejected("User is not seated at table"));
                return;
            }

            sessionRegistry.attachUserToTable(userId, tableId);
            disconnectGracePeriodManager.handleReconnect(userId, tableId);

            // Rehydrate mid-hand engine after restart, then send personalized projection.
            gameEngineService.ensureActiveEngine(tableId);
            gameBroadcastService.deliverPrivateHand(tableId, userId);
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

            if (handContextManager.getEngine(tableId).isEmpty()) {
                // JVM may have restarted mid-hand — rebuild engine from durable game_sessions.
                gameEngineService.ensureActiveEngine(tableId);
            }

            if (handContextManager.getEngine(tableId).isEmpty()) {
                sendMessageToSession(session, GameServerMessage.actionRejected("No active hand — waiting for host to start"));
                return;
            }

            String normalizedType = msg.getType() != null ? msg.getType().toUpperCase() : "";
            if (!ALLOWED_WS_ACTIONS.contains(normalizedType)) {
                sendMessageToSession(session, GameServerMessage.error("Invalid action type: " + msg.getType()));
                return;
            }

            String rejection = gameEngineService.processAction(
                    tableId, userId, msg.getType(), msg.getAmountPaise(), msg.getCardIndex());
            if (rejection != null) {
                sendMessageToSession(session, GameServerMessage.actionRejected(rejection));
            }
        });
    }

    private void handleAutoFoldOnTurn(String userId, String tableId) {
        perTableActionExecutor.executeTableActionSync(tableId, () -> {
            handContextManager.getEngine(tableId).ifPresent(engine -> {
                if (!engine.isHandFinished() && userId.equals(engine.getCurrentTurnPlayerId())) {
                    gameEngineService.processAutoPack(tableId, userId);
                }
            });
        });
    }

    private void sendMessageToSession(WebSocketSession session, GameServerMessage message) {
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket message: {}", e.getMessage());
        }
    }
}
