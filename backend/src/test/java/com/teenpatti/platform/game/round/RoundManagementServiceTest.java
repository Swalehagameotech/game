package com.teenpatti.platform.game.round;

import com.teenpatti.platform.game.GameStartService;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import com.teenpatti.platform.websocket.GameBroadcastService;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoundManagementServiceTest {

    private TableRepository tableRepository;
    private GameStartService gameStartService;
    private WebSocketEventPublisher eventPublisher;
    private GameBroadcastService gameBroadcastService;
    private RoundManagementService roundManagementService;

    @BeforeEach
    void setUp() {
        tableRepository = mock(TableRepository.class);
        gameStartService = mock(GameStartService.class);
        eventPublisher = mock(WebSocketEventPublisher.class);
        gameBroadcastService = mock(GameBroadcastService.class);
        var hostManagementService = mock(com.teenpatti.platform.table.HostManagementService.class);
        roundManagementService = new RoundManagementService(
                tableRepository, gameStartService, eventPublisher, gameBroadcastService,
                hostManagementService, 5, 0);
    }

    @Test
    @DisplayName("afterRoundFinished with enough players schedules NEXT_ROUND and does not delete table")
    void afterRoundFinished_enoughPlayers_goesToNextRound() {
        Table table = Table.builder()
                .id("t1")
                .tableType(TableType.PUBLIC)
                .status(TableStatus.ROUND_END)
                .minPlayers(3)
                .roundNumber(1)
                .seatedPlayerIds(new ArrayList<>(List.of("a", "b", "c")))
                .build();
        when(tableRepository.findById("t1")).thenReturn(Optional.of(table));
        when(tableRepository.save(any(Table.class))).thenAnswer(inv -> inv.getArgument(0));

        roundManagementService.afterRoundFinished("t1");

        assertEquals(TableStatus.NEXT_ROUND, table.getStatus());
        assertEquals(5, table.getCountdownSeconds());
        verify(eventPublisher).publishRoundFinished("t1", 5);
        verify(eventPublisher, atLeastOnce()).publishNextRoundCountdown(eq("t1"), anyInt());
        verify(tableRepository, never()).delete(any());
        roundManagementService.cancelNextRound("t1");
    }

    @Test
    @DisplayName("afterRoundFinished with too few players returns WAITING")
    void afterRoundFinished_underMin_goesWaiting() {
        Table table = Table.builder()
                .id("t2")
                .tableType(TableType.PUBLIC)
                .status(TableStatus.ROUND_END)
                .minPlayers(3)
                .roundNumber(2)
                .seatedPlayerIds(new ArrayList<>(List.of("a", "b")))
                .build();
        when(tableRepository.findById("t2")).thenReturn(Optional.of(table));
        when(tableRepository.save(any(Table.class))).thenAnswer(inv -> inv.getArgument(0));

        roundManagementService.afterRoundFinished("t2");

        assertEquals(TableStatus.WAITING, table.getStatus());
        verify(eventPublisher).publishRoundFinished("t2", 0);
        verify(eventPublisher).publishEvent(contains("/topic/tables/t2"), eq("TABLE_WAITING_FOR_PLAYERS"), any());
    }

    @Test
    @DisplayName("afterRoundFinished with zero players closes table")
    void afterRoundFinished_empty_closesTable() {
        Table table = Table.builder()
                .id("t3")
                .status(TableStatus.ROUND_END)
                .minPlayers(3)
                .seatedPlayerIds(new ArrayList<>())
                .build();
        when(tableRepository.findById("t3")).thenReturn(Optional.of(table));
        when(tableRepository.save(any(Table.class))).thenAnswer(inv -> inv.getArgument(0));

        roundManagementService.afterRoundFinished("t3");

        assertEquals(TableStatus.CLOSED, table.getStatus());
        verify(eventPublisher).publishTableClosed("t3");
    }
}
