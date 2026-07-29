package com.teenpatti.platform.game.turn;

import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.Deck;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandContextManager;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TurnManagementServiceTest {

    private TableRepository tableRepository;
    private HandContextManager handContextManager;
    private WebSocketEventPublisher eventPublisher;
    private UserRepository userRepository;
    private TurnManagementService turnManagementService;

    @BeforeEach
    void setUp() {
        tableRepository = mock(TableRepository.class);
        handContextManager = mock(HandContextManager.class);
        eventPublisher = mock(WebSocketEventPublisher.class);
        userRepository = mock(UserRepository.class);
        when(userRepository.findById(anyString())).thenReturn(Optional.empty());
        turnManagementService = new TurnManagementService(
                tableRepository, handContextManager, eventPublisher, userRepository);
        ReflectionTestUtils.setField(turnManagementService, "turnTimeoutSeconds", 20);
    }

    @Test
    @DisplayName("First turn seat is immediately clockwise from dealer")
    void resolveFirstTurnSeatIndex_wrapsAroundTable() {
        assertEquals(1, turnManagementService.resolveFirstTurnSeatIndex(0, 4));
        assertEquals(0, turnManagementService.resolveFirstTurnSeatIndex(3, 4));
    }

    @Test
    @DisplayName("beginTurn persists turn user, broadcasts state, and schedules timeout callback")
    void beginTurn_startsTimerAndPublishes() {
        Table table = Table.builder()
                .id("t1")
                .seatedPlayerIds(List.of("p1", "p2", "p3"))
                .dealerSeatIndex(0)
                .build();

        BettingRoundEngine engine = new BettingRoundEngine(GameEngineConfig.defaultConfig(1000L, 50000L));
        Deck deck = new Deck();
        engine.startHand(List.of("p1", "p2", "p3"), deck);
        engine.setStartingTurnPlayer("p2");

        when(tableRepository.findById("t1")).thenReturn(Optional.of(table));
        when(handContextManager.getEngine("t1")).thenReturn(Optional.of(engine));

        AtomicBoolean timedOut = new AtomicBoolean(false);
        turnManagementService.beginTurn("t1", "p2", 1, () -> timedOut.set(true));

        verify(eventPublisher).publishTurnStarted(eq("t1"), eq("p2"), anyString(), eq(1), eq(20));
        verify(eventPublisher).publishTurnState(eq("t1"), any(TurnState.class));
        verify(tableRepository).save(table);
        assertEquals("p2", table.getCurrentTurnUserId());
        assertTrue(turnManagementService.getTurnDeadline("t1").isPresent());

        ArgumentCaptor<TurnState> turnCaptor = ArgumentCaptor.forClass(TurnState.class);
        verify(eventPublisher).publishTurnState(eq("t1"), turnCaptor.capture());
        TurnState published = turnCaptor.getValue();
        assertEquals("p2", published.getCurrentTurnUserId());
        assertEquals(1, published.getCurrentTurnSeatIndex());
        assertEquals(20, published.getTurnTimeoutSeconds());
    }

    @Test
    @DisplayName("syncTableFromEngine copies player groups from engine onto table")
    void syncTableFromEngine_updatesPlayerGroups() {
        Table table = Table.builder()
                .id("t1")
                .seatedPlayerIds(List.of("p1", "p2", "p3"))
                .build();

        BettingRoundEngine engine = new BettingRoundEngine(GameEngineConfig.defaultConfig(1000L, 50000L));
        Deck deck = new Deck();
        engine.startHand(List.of("p1", "p2", "p3"), deck);

        turnManagementService.syncTableFromEngine(table, engine);

        assertEquals(3, table.getActivePlayerIds().size());
        assertEquals(3, table.getBlindPlayerIds().size());
        assertTrue(table.getSeenPlayerIds().isEmpty());
        assertTrue(table.getPackedPlayerIds().isEmpty());
        verify(tableRepository).save(table);
    }

    @Test
    @DisplayName("cancelTurn clears scheduled timer without ending turn metadata")
    void cancelTurn_doesNotPublishTurnEnded() {
        Table table = Table.builder()
                .id("t1")
                .seatedPlayerIds(List.of("p1", "p2"))
                .currentTurnUserId("p1")
                .build();
        when(tableRepository.findById("t1")).thenReturn(Optional.of(table));
        when(handContextManager.getEngine("t1")).thenReturn(Optional.empty());

        turnManagementService.beginTurn("t1", "p1", 0, () -> {});
        turnManagementService.cancelTurn("t1");

        assertTrue(turnManagementService.getTurnDeadline("t1").isPresent());
        verify(eventPublisher, never()).publishTurnEnded(anyString(), any());
    }

    @Test
    @DisplayName("endTurn clears deadline and publishes TURN_ENDED with previous user")
    void endTurn_clearsTurnState() {
        Table table = Table.builder()
                .id("t1")
                .seatedPlayerIds(List.of("p1", "p2"))
                .currentTurnUserId("p1")
                .build();
        when(tableRepository.findById("t1")).thenReturn(Optional.of(table));

        turnManagementService.beginTurn("t1", "p1", 0, () -> {});
        turnManagementService.endTurn("t1");

        assertNull(table.getCurrentTurnUserId());
        verify(eventPublisher).publishTurnEnded("t1", "p1");
        assertTrue(turnManagementService.getTurnSecondsRemaining("t1") == 0
                || turnManagementService.getTurnDeadline("t1").isEmpty());
    }

    @Test
    @DisplayName("buildTurnState includes dealer seat and remaining seconds when deadline active")
    void buildTurnState_includesDeadlineMetadata() {
        Table table = Table.builder()
                .id("t1")
                .seatedPlayerIds(List.of("p1", "p2", "p3"))
                .dealerSeatIndex(2)
                .currentTurnUserId("p1")
                .build();

        when(handContextManager.getDealerSeat("t1")).thenReturn(2);
        ReflectionTestUtils.setField(turnManagementService, "turnDeadlines",
                new java.util.concurrent.ConcurrentHashMap<>(Map.of("t1", Instant.now().plusSeconds(15))));

        TurnState state = turnManagementService.buildTurnState(table, null);

        assertEquals(2, state.getDealerSeatIndex());
        assertEquals("p1", state.getCurrentTurnUserId());
        assertTrue(state.getTurnSecondsRemaining() > 0);
        assertNotNull(state.getTurnDeadlineAt());
    }
}
