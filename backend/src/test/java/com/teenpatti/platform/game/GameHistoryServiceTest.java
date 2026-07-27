package com.teenpatti.platform.game;

import com.teenpatti.platform.game.dto.GameHistoryDetailDto;
import com.teenpatti.platform.game.dto.GameHistorySummaryDto;
import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.game.engine.HandRankCategory;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.game.engine.Suit;
import com.teenpatti.platform.table.GameVariant;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableType;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameHistoryServiceTest {

    private GameHistoryRepository gameHistoryRepository;
    private MatchHistoryRepository matchHistoryRepository;
    private UserRepository userRepository;
    private WebSocketEventPublisher eventPublisher;
    private GameHistoryService gameHistoryService;

    @BeforeEach
    void setUp() {
        gameHistoryRepository = mock(GameHistoryRepository.class);
        matchHistoryRepository = mock(MatchHistoryRepository.class);
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(WebSocketEventPublisher.class);
        gameHistoryService = new GameHistoryService(
                gameHistoryRepository, matchHistoryRepository, userRepository, eventPublisher);
    }

    @Test
    @DisplayName("recordCompletedHand persists canonical history and legacy match history")
    void recordCompletedHand_persistsBothCollections() {
        Table table = Table.builder()
                .id("table1")
                .tableName("High Stakes")
                .tableType(TableType.PUBLIC)
                .gameVariant(GameVariant.CLASSIC)
                .roundNumber(2)
                .seatedPlayerIds(List.of("u1", "u2", "u3"))
                .build();

        HandOutcome outcome = new HandOutcome(
                "u1",
                12_000L,
                600L,
                11_400L,
                HandRankCategory.PAIR,
                Map.of("u1", List.of(
                        new Card(Suit.SPADES, Rank.ACE),
                        new Card(Suit.HEARTS, Rank.ACE),
                        new Card(Suit.DIAMONDS, Rank.KING))),
                "Showdown win");

        when(gameHistoryRepository.findByHandId("hand1")).thenReturn(Optional.empty());
        when(gameHistoryRepository.save(any(GameHistory.class))).thenAnswer(inv -> {
            GameHistory saved = inv.getArgument(0);
            saved.setId("gh1");
            return saved;
        });
        when(userRepository.findById("u1")).thenReturn(Optional.of(User.builder().displayName("Alice").build()));

        GameHistory saved = gameHistoryService.recordCompletedHand(table, "hand1", outcome, Instant.parse("2026-07-27T10:00:00Z"));

        assertEquals("gh1", saved.getId());
        assertEquals("hand1", saved.getHandId());
        assertEquals(WinningCategory.PAIR, saved.getWinningCategory());
        assertEquals(11_400L, saved.getWinnerPayoutPaise());
        verify(matchHistoryRepository).save(any(MatchHistory.class));
        verify(eventPublisher, times(3)).publishGameHistoryRecorded(anyString(), any());
    }

    @Test
    @DisplayName("recordCompletedHand is idempotent by handId")
    void recordCompletedHand_idempotent() {
        GameHistory existing = GameHistory.builder().id("gh1").handId("hand1").build();
        when(gameHistoryRepository.findByHandId("hand1")).thenReturn(Optional.of(existing));

        Table table = Table.builder().id("table1").seatedPlayerIds(List.of("u1")).build();
        HandOutcome outcome = new HandOutcome("u1", 1000L, 0L, 1000L, null, Map.of(), "Fold");

        GameHistory result = gameHistoryService.recordCompletedHand(table, "hand1", outcome, Instant.now());

        assertSame(existing, result);
        verify(gameHistoryRepository, never()).save(any());
        verify(matchHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("getDetailForUser returns detail only for participants")
    void getDetailForUser_accessControl() {
        GameHistory record = GameHistory.builder()
                .id("gh1")
                .handId("hand1")
                .tableId("table1")
                .winnerId("u1")
                .playerIds(List.of("u1", "u2"))
                .potAmountPaise(5000L)
                .winnerPayoutPaise(4750L)
                .winningCategory(WinningCategory.FOLD_WIN)
                .handSummary(HandSummary.builder().winningHandName("Fold Win").build())
                .endedAt(Instant.now())
                .build();

        when(gameHistoryRepository.findById("gh1")).thenReturn(Optional.of(record));
        when(userRepository.findById("u1")).thenReturn(Optional.of(User.builder().displayName("Alice").build()));

        Optional<GameHistoryDetailDto> allowed = gameHistoryService.getDetailForUser("u1", "gh1");
        Optional<GameHistoryDetailDto> denied = gameHistoryService.getDetailForUser("u9", "gh1");

        assertTrue(allowed.isPresent());
        assertEquals("WON", allowed.get().getResult());
        assertTrue(denied.isEmpty());
    }

    @Test
    @DisplayName("getHistoryForUser maps summary rows for viewer")
    void getHistoryForUser_mapsSummary() {
        GameHistory record = GameHistory.builder()
                .id("gh1")
                .handId("hand1")
                .tableId("table1")
                .winnerId("u2")
                .playerIds(List.of("u1", "u2"))
                .potAmountPaise(8000L)
                .winnerPayoutPaise(7600L)
                .winningCategory(WinningCategory.HIGH_CARD)
                .handSummary(HandSummary.builder().winningHandName("High Card").build())
                .endedAt(Instant.now())
                .build();

        when(gameHistoryRepository.findByPlayerIdsContainingOrderByEndedAtDesc(eq("u1"), any()))
                .thenReturn(new PageImpl<>(List.of(record)));

        Page<GameHistorySummaryDto> page = gameHistoryService.getHistoryForUser("u1", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("LOST", page.getContent().get(0).getResult());
        assertEquals(0L, page.getContent().get(0).getNetAmountPaise());
    }
}
