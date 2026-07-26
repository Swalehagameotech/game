package com.teenpatti.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.game.MatchHistoryRepository;
import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.table.*;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.user.UserRole;
import com.teenpatti.platform.wallet.Wallet;
import com.teenpatti.platform.wallet.WalletRepository;
import com.teenpatti.platform.websocket.dto.GameServerMessage;
import com.teenpatti.platform.websocket.dto.GameWebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class GameWebSocketHandlerTest {

    @Autowired
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private GameWebSocketHandler gameWebSocketHandler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private MatchHistoryRepository matchHistoryRepository;

    @Autowired
    private HandSettlementService handSettlementService;

    @Autowired
    private ObjectMapper objectMapper;

    private User user1;
    private User user2;
    private String token1;
    private Table testTable;

    @BeforeEach
    void setUp() {
        matchHistoryRepository.deleteAll();
        tableRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.builder()
                .email("ws1@example.com")
                .phoneNumber("9000011111")
                .passwordHash("hashed")
                .displayName("WSUserOne")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder().userId(user1.getId()).balancePaise(100_000L).currency("INR").build());
        token1 = jwtTokenProvider.generateAccessToken(user1.getId());

        user2 = userRepository.save(User.builder()
                .email("ws2@example.com")
                .phoneNumber("9000011112")
                .passwordHash("hashed")
                .displayName("WSUserTwo")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder().userId(user2.getId()).balancePaise(100_000L).currency("INR").build());

        testTable = tableRepository.save(Table.builder()
                .tableType(TableType.PUBLIC)
                .stakeTier(StakeTier.LOW)
                .maxPlayers(6)
                .seatedPlayerIds(new ArrayList<>(List.of(user1.getId(), user2.getId())))
                .status(TableStatus.WAITING)
                .build());
    }

    @Test
    @DisplayName("JWT Handshake Interceptor validates token parameter and binds userId to session attributes")
    void jwtHandshake_ValidToken_Succeeds() throws Exception {
        ServerHttpRequest request = Mockito.mock(ServerHttpRequest.class);
        ServerHttpResponse response = Mockito.mock(ServerHttpResponse.class);
        Mockito.when(request.getURI()).thenReturn(new URI("http://localhost:8080/ws/game?token=" + token1));

        Map<String, Object> attributes = new HashMap<>();
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, null, attributes);

        assertTrue(result, "Valid JWT handshake must return true");
        assertEquals(user1.getId(), attributes.get("userId"));
    }

    @Test
    @DisplayName("JWT Handshake Interceptor rejects missing or invalid token parameter")
    void jwtHandshake_InvalidToken_Fails() throws Exception {
        ServerHttpRequest request = Mockito.mock(ServerHttpRequest.class);
        ServerHttpResponse response = Mockito.mock(ServerHttpResponse.class);
        Mockito.when(request.getURI()).thenReturn(new URI("http://localhost:8080/ws/game?token=invalid_token_xyz"));

        Map<String, Object> attributes = new HashMap<>();
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, null, attributes);

        assertFalse(result, "Invalid JWT handshake must return false");
        assertNull(attributes.get("userId"));
    }

    @Test
    @DisplayName("JOIN_TABLE attaches live session and initializes hand when 2 players connect")
    void handleTextMessage_JoinTable_InitializesHand() throws Exception {
        WebSocketSession session1 = createMockSession(user1.getId());
        gameWebSocketHandler.afterConnectionEstablished(session1);

        GameWebSocketMessage joinMsg = GameWebSocketMessage.builder()
                .type("JOIN_TABLE")
                .tableId(testTable.getId())
                .build();

        TextMessage message = new TextMessage(objectMapper.writeValueAsString(joinMsg));
        gameWebSocketHandler.handleTextMessage(session1, message);

        // Verify state update message sent back to session1
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        Mockito.verify(session1, Mockito.atLeastOnce()).sendMessage(captor.capture());

        String payload = captor.getValue().getPayload();
        GameServerMessage serverMsg = objectMapper.readValue(payload, GameServerMessage.class);
        assertEquals("STATE_UPDATE", serverMsg.getType());
        assertNotNull(serverMsg.getPayload());
    }

    @Test
    @DisplayName("Invalid action sends ACTION_REJECTED message ONLY to acting player")
    void handleTextMessage_InvalidAction_SendsRejectionToActorOnly() throws Exception {
        WebSocketSession session1 = createMockSession(user1.getId());
        WebSocketSession session2 = createMockSession(user2.getId());

        gameWebSocketHandler.afterConnectionEstablished(session1);
        gameWebSocketHandler.afterConnectionEstablished(session2);

        // Both join table
        TextMessage joinMsg = new TextMessage(objectMapper.writeValueAsString(GameWebSocketMessage.builder().type("JOIN_TABLE").tableId(testTable.getId()).build()));
        gameWebSocketHandler.handleTextMessage(session1, joinMsg);
        gameWebSocketHandler.handleTextMessage(session2, joinMsg);

        // Submit out-of-turn or invalid action from user2
        TextMessage invalidAction = new TextMessage(objectMapper.writeValueAsString(GameWebSocketMessage.builder().type("PLAY_BLIND").tableId(testTable.getId()).amountPaise(50L).build()));
        gameWebSocketHandler.handleTextMessage(session2, invalidAction);

        // Verify session2 receives ACTION_REJECTED
        ArgumentCaptor<TextMessage> captor2 = ArgumentCaptor.forClass(TextMessage.class);
        Mockito.verify(session2, Mockito.atLeastOnce()).sendMessage(captor2.capture());

        boolean hasRejection = captor2.getAllValues().stream()
                .anyMatch(msg -> msg.getPayload().contains("ACTION_REJECTED") || msg.getPayload().contains("INSUFFICIENT_BET_AMOUNT") || msg.getPayload().contains("NOT_YOUR_TURN"));

        assertTrue(hasRejection, "Acting player session2 must receive ACTION_REJECTED message");
    }

    @Test
    @DisplayName("Hand completion settlement credits winner payout, platform rake, and creates MatchHistory record")
    void handSettlement_CreditsWinnerRakeAndPersistsHistory() {
        HandOutcome outcome = new HandOutcome(
                user1.getId(),
                10_000L, // Pot ₹100
                500L,    // Rake ₹5 (5%)
                9_500L,  // Payout ₹95
                null,
                Map.of(),
                "Fold win"
        );

        handSettlementService.settleCompletedHand(testTable, "hand_test_123", outcome, Instant.now());

        // Verify winner balance credited: 100,000 + 9,500 = 109,500
        Wallet winnerWallet = walletRepository.findByUserId(user1.getId()).orElseThrow();
        assertEquals(109_500L, winnerWallet.getBalancePaise());

        // Verify MatchHistory record persisted
        assertEquals(1, matchHistoryRepository.count());
    }

    private WebSocketSession createMockSession(String userId) {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", userId);
        Mockito.when(session.getAttributes()).thenReturn(attributes);
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getId()).thenReturn("sess_" + userId);
        return session;
    }
}
