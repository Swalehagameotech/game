package com.teenpatti.platform.game.winner;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.game.engine.HandRankCategory;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.game.engine.Suit;
import com.teenpatti.platform.game.variant.ClassicVariantStrategy;
import com.teenpatti.platform.game.variant.GameVariantRegistry;
import com.teenpatti.platform.table.GameVariant;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableType;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WinnerCalculationServiceTest {

    private GameVariantRegistry variantRegistry;
    private UserRepository userRepository;
    private WebSocketEventPublisher eventPublisher;
    private WinnerCalculationService winnerCalculationService;
    private VariantWinnerResolver resolver;
    private GameEngineConfig config;

    @BeforeEach
    void setUp() {
        variantRegistry = mock(GameVariantRegistry.class);
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(WebSocketEventPublisher.class);
        winnerCalculationService = new WinnerCalculationService(variantRegistry, userRepository, eventPublisher);

        ClassicVariantStrategy classic = new ClassicVariantStrategy();
        when(variantRegistry.requireStrategy(any())).thenReturn(classic);
        resolver = new VariantWinnerResolver(classic);
        config = GameEngineConfig.defaultConfig(1000L, 50_000L);
    }

    @Test
    @DisplayName("Fold win awards pot minus rake without revealing cards")
    void resolveFoldWin_noReveal() {
        HandOutcome outcome = resolver.resolveFoldWin("p3", 3000L, config);

        assertEquals("p3", outcome.getWinnerId());
        assertEquals(3000L, outcome.getPotAmountPaise());
        assertEquals(150L, outcome.getRakeAmountPaise());
        assertEquals(2850L, outcome.getWinnerPayoutPaise());
        assertNull(outcome.getWinningCategory());
        assertTrue(outcome.getRevealedHands().isEmpty());
    }

    @Test
    @DisplayName("Showdown picks higher Teen Patti hand and reveals both participants")
    void resolveShowdown_trailBeatsPair() {
        List<Card> trail = List.of(
                new Card(Suit.HEARTS, Rank.ACE),
                new Card(Suit.SPADES, Rank.ACE),
                new Card(Suit.CLUBS, Rank.ACE));
        List<Card> pair = List.of(
                new Card(Suit.HEARTS, Rank.KING),
                new Card(Suit.SPADES, Rank.KING),
                new Card(Suit.CLUBS, Rank.FIVE));

        HandOutcome outcome = resolver.resolveShowdown("p1", trail, "p2", pair, 5000L, config);

        assertEquals("p1", outcome.getWinnerId());
        assertEquals(HandRankCategory.TRAIL, outcome.getWinningCategory());
        assertEquals(2, outcome.getRevealedHands().size());
        assertTrue(outcome.getRevealedHands().containsKey("p1"));
        assertTrue(outcome.getRevealedHands().containsKey("p2"));
    }

    @Test
    @DisplayName("Winner snapshot includes display names and participant ranks")
    void buildWinnerSnapshot_showdownMetadata() {
        Table table = Table.builder()
                .id("t1")
                .tableType(TableType.PUBLIC)
                .gameVariant(GameVariant.CLASSIC)
                .seatedPlayerIds(List.of("p1", "p2"))
                .currentHandId("hand-1")
                .build();

        when(userRepository.findAllById(anyList())).thenReturn(List.of(
                User.builder().id("p1").displayName("Alice").build(),
                User.builder().id("p2").displayName("Bob").build()
        ));

        List<Card> trail = List.of(
                new Card(Suit.HEARTS, Rank.ACE),
                new Card(Suit.SPADES, Rank.ACE),
                new Card(Suit.CLUBS, Rank.ACE));
        List<Card> pair = List.of(
                new Card(Suit.HEARTS, Rank.KING),
                new Card(Suit.SPADES, Rank.KING),
                new Card(Suit.CLUBS, Rank.FIVE));

        HandOutcome outcome = resolver.resolveShowdown("p1", trail, "p2", pair, 5000L, config);

        WinnerSnapshot snapshot = winnerCalculationService.buildWinnerSnapshot(
                table, "hand-1", outcome, null, GameVariant.CLASSIC);

        assertEquals("Alice", snapshot.getWinnerDisplayName());
        assertEquals("TRAIL", snapshot.getWinningCategory());
        assertFalse(snapshot.isFoldWin());
        assertEquals(2, snapshot.getParticipants().size());
        assertEquals("Trail (Three of a Kind)", snapshot.getWinningHandDescription());
    }

    @Test
    @DisplayName("Fold-win snapshot marks FOLD_WIN without card reveal")
    void buildWinnerSnapshot_foldWin() {
        Table table = Table.builder()
                .id("t1")
                .seatedPlayerIds(List.of("p1", "p2", "p3"))
                .currentHandId("hand-2")
                .build();

        when(userRepository.findAllById(anyList())).thenReturn(List.of(
                User.builder().id("p3").displayName("Charlie").build()
        ));

        HandOutcome outcome = resolver.resolveFoldWin("p3", 3000L, config);
        WinnerSnapshot snapshot = winnerCalculationService.buildWinnerSnapshot(
                table, "hand-2", outcome, null, GameVariant.CLASSIC);

        assertTrue(snapshot.isFoldWin());
        assertEquals("FOLD_WIN", snapshot.getWinningCategory());
        assertEquals("Charlie", snapshot.getWinnerDisplayName());
        assertEquals(1, snapshot.getParticipants().size());
        assertTrue(snapshot.getParticipants().get(0).getCards().isEmpty());
    }

    @Test
    @DisplayName("publishWinnerDeclared sends snapshot over STOMP")
    void publishWinnerDeclared_broadcasts() {
        WinnerSnapshot snapshot = WinnerSnapshot.builder()
                .tableId("t1")
                .winnerUserId("p1")
                .winnerDisplayName("Alice")
                .winningCategory("PAIR")
                .payoutPaise(4750L)
                .build();

        winnerCalculationService.publishWinnerDeclared("t1", snapshot);

        verify(eventPublisher).publishWinnerDeclared(eq("t1"), eq(snapshot));
    }
}
