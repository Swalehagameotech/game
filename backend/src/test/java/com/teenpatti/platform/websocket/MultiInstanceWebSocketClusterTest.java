package com.teenpatti.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.websocket.dto.TableBroadcastPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class MultiInstanceWebSocketClusterTest {

    @Autowired
    private ObjectMapper objectMapper;

    private SessionRegistry sessionRegistry;
    private RedisTableBroadcastListener listener;

    @BeforeEach
    void setUp() {
        sessionRegistry = mock(SessionRegistry.class);
        listener = new RedisTableBroadcastListener(sessionRegistry, objectMapper);
    }

    @Test
    @DisplayName("Redis Table Broadcast Listener delivers message to local WebSocket session when player is connected to this instance")
    void onMessage_UserConnectedLocally_DeliversWebSocketMessage() throws Exception {
        String userId = "user_instance_a";
        String tableId = "table_cluster_1";
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.isOpen()).thenReturn(true);
        when(sessionRegistry.isUserConnected(userId)).thenReturn(true);
        when(sessionRegistry.getWebSocketSession(userId)).thenReturn(mockSession);

        TableBroadcastPayload payload = TableBroadcastPayload.builder()
                .tableId(tableId)
                .recipientUserId(userId)
                .messageJson("{\"type\":\"GAME_STATE_UPDATE\",\"payload\":{}}")
                .build();

        String payloadJson = objectMapper.writeValueAsString(payload);

        listener.onMessage(payloadJson, RedisClusterConfig.TABLE_BROADCAST_CHANNEL);

        verify(mockSession, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Redis Table Broadcast Listener ignores message when target player is connected to a different instance")
    void onMessage_UserNotOnThisInstance_IgnoresMessage() throws Exception {
        String userId = "user_instance_b";
        when(sessionRegistry.isUserConnected(userId)).thenReturn(false);

        TableBroadcastPayload payload = TableBroadcastPayload.builder()
                .tableId("table_cluster_1")
                .recipientUserId(userId)
                .messageJson("{\"type\":\"GAME_STATE_UPDATE\",\"payload\":{}}")
                .build();

        String payloadJson = objectMapper.writeValueAsString(payload);

        listener.onMessage(payloadJson, RedisClusterConfig.TABLE_BROADCAST_CHANNEL);

        verify(sessionRegistry, never()).getWebSocketSession(anyString());
    }
}
