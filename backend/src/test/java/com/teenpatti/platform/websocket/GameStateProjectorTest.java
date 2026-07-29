package com.teenpatti.platform.websocket;

import com.teenpatti.platform.game.engine.*;
import com.teenpatti.platform.game.betting.BettingLogicService;
import com.teenpatti.platform.game.betting.BettingState;
import com.teenpatti.platform.game.winner.WinnerCalculationService;
import com.teenpatti.platform.game.winner.WinnerSnapshot;
import com.teenpatti.platform.game.turn.TurnManagementService;
import com.teenpatti.platform.game.turn.TurnState;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.dto.PlayerSummaryView;
import com.teenpatti.platform.websocket.dto.PlayerViewGameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;

class GameStateProjectorTest {

    private UserRepository userRepository;
    private SessionRegistry sessionRegistry;
    private TurnManagementService turnManagementService;
    private BettingLogicService bettingLogicService;
    private WinnerCalculationService winnerCalculationService;
    private GameStateProjector projector;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        sessionRegistry = Mockito.mock(SessionRegistry.class);
        turnManagementService = Mockito.mock(TurnManagementService.class);
        bettingLogicService = Mockito.mock(BettingLogicService.class);
        winnerCalculationService = Mockito.mock(WinnerCalculationService.class);
        var handContextManager = Mockito.mock(com.teenpatti.platform.game.engine.HandContextManager.class);
        Mockito.when(handContextManager.getPendingShow(any())).thenReturn(java.util.Optional.empty());
        projector = new GameStateProjector(
                userRepository, sessionRegistry, turnManagementService, bettingLogicService,
                winnerCalculationService, handContextManager);

        Mockito.when(userRepository.findAllById(anyList())).thenReturn(List.of(
                User.builder().id("p1").displayName("PlayerOne").build(),
                User.builder().id("p2").displayName("PlayerTwo").build(),
                User.builder().id("p3").displayName("PlayerThree").build()
        ));

        Mockito.when(turnManagementService.buildTurnState(any(Table.class), any()))
                .thenAnswer(invocation -> {
                    Table table = invocation.getArgument(0);
                    BettingRoundEngine engine = invocation.getArgument(1);
                    List<String> seated = table.getSeatedPlayerIds();
                    String turnUser = engine != null ? engine.getCurrentTurnPlayerId() : table.getCurrentTurnUserId();
                    return TurnState.builder()
                            .tableId(table.getId())
                            .currentTurnUserId(turnUser)
                            .currentTurnSeatIndex(turnUser != null ? seated.indexOf(turnUser) : -1)
                            .dealerSeatIndex(table.getDealerSeatIndex())
                            .potPaise(engine != null ? engine.getPotPaise() : table.getPotPaise())
                            .currentBaseStakePaise(engine != null ? engine.getCurrentBaseStakePaise() : 0L)
                            .turnTimeoutSeconds(20)
                            .turnSecondsRemaining(0)
                            .activePlayerIds(seated)
                            .blindPlayerIds(seated)
                            .seenPlayerIds(List.of())
                            .packedPlayerIds(List.of())
                            .build();
                });

        Mockito.when(bettingLogicService.buildBettingState(any(Table.class), any(), any(String.class)))
                .thenAnswer(invocation -> {
                    BettingRoundEngine engine = invocation.getArgument(1);
                    String userId = invocation.getArgument(2);
                    long required = engine != null ? engine.getRequiredBetPaise(userId) : 0L;
                    return BettingState.builder()
                            .tableId(invocation.<Table>getArgument(0).getId())
                            .userId(userId)
                            .potPaise(engine != null ? engine.getPotPaise() : 0L)
                            .currentBaseStakePaise(engine != null ? engine.getCurrentBaseStakePaise() : 1000L)
                            .requiredBetPaise(required)
                            .minRaiseBetPaise(required * 2)
                            .maxBetPaise(50_000L)
                            .playerContributedPaise(1000L)
                            .blindSeenRatio(2)
                            .myTurn(engine != null && userId.equals(engine.getCurrentTurnPlayerId()))
                            .allowedActions(List.of("CHAAL", "PACK"))
                            .build();
                });

        Mockito.when(winnerCalculationService.buildWinnerSnapshot(any(), any(), any(), any(), any()))
                .thenReturn(WinnerSnapshot.builder()
                        .tableId("t1")
                        .winnerUserId("p1")
                        .winnerDisplayName("PlayerOne")
                        .winningCategory("FOLD_WIN")
                        .winningHandDescription("Last player standing")
                        .foldWin(true)
                        .potPaise(3000L)
                        .rakePaise(150L)
                        .payoutPaise(2850L)
                        .participants(List.of())
                        .build());
    }

    @Test
    @DisplayName("SECURITY TEST: Recipient NEVER receives another active player's actual card values, and receives own cards ONLY if SEEN")
    void createProjection_CardPrivacy_NeverLeaksOpponentCards() {
        Table table = Table.builder()
                .id("t1")
                .tableType(TableType.PUBLIC)
                .seatedPlayerIds(List.of("p1", "p2", "p3"))
                .status(TableStatus.IN_PROGRESS)
                .build();

        GameEngineConfig config = GameEngineConfig.defaultConfig(1000L, 50000L);
        BettingRoundEngine engine = new BettingRoundEngine(config);
        Deck deck = new Deck();
        engine.startHand(List.of("p1", "p2", "p3"), deck);

        // p1 sees cards, p2 remains blind, p3 packs
        engine.applyAction(PlayerAction.of("p1", PlayerActionType.SEE_CARDS));
        engine.applyAction(PlayerAction.of("p1", PlayerActionType.CHAAL, 2000L));
        engine.applyAction(PlayerAction.of("p2", PlayerActionType.PLAY_BLIND, 1000L));

        // Generate projection for p1
        PlayerViewGameState projForP1 = projector.createProjection(table, engine, "p1");

        // Assert p1 sees their own cards (status SEEN)
        PlayerSummaryView p1View = projForP1.getPlayers().stream().filter(p -> p.getUserId().equals("p1")).findFirst().orElseThrow();
        assertNotNull(p1View.getCards(), "Recipient p1 must see their own cards when SEEN");
        assertEquals(3, p1View.getCards().size());

        // Assert p1 NEVER sees p2's cards (p2 is active/blind)
        PlayerSummaryView p2View = projForP1.getPlayers().stream().filter(p -> p.getUserId().equals("p2")).findFirst().orElseThrow();
        assertNull(p2View.getCards(), "SECURITY VIOLATION: Opponent p2's cards MUST be null in p1's projection");
        assertEquals(3, p2View.getCardCount());

        // Generate projection for p2 (who is BLIND)
        PlayerViewGameState projForP2 = projector.createProjection(table, engine, "p2");
        PlayerSummaryView p2SelfView = projForP2.getPlayers().stream().filter(p -> p.getUserId().equals("p2")).findFirst().orElseThrow();
        assertNull(p2SelfView.getCards(), "Recipient p2 MUST NOT see their own cards while status is BLIND");

        PlayerSummaryView p1ViewForP2 = projForP2.getPlayers().stream().filter(p -> p.getUserId().equals("p1")).findFirst().orElseThrow();
        assertNull(p1ViewForP2.getCards(), "SECURITY VIOLATION: Opponent p1's cards MUST be null in p2's projection");
    }

    @Test
    @DisplayName("SECURITY TEST: Showdown reveals cards ONLY for showdown participants; folded players' cards are NEVER revealed")
    void createProjection_ShowdownReveal_FoldedCardsNeverLeaked() {
        Table table = Table.builder()
                .id("t1")
                .tableType(TableType.PUBLIC)
                .seatedPlayerIds(List.of("p1", "p2", "p3"))
                .status(TableStatus.IN_PROGRESS)
                .build();

        GameEngineConfig config = GameEngineConfig.defaultConfig(1000L, 50000L);
        BettingRoundEngine engine = new BettingRoundEngine(config);
        Deck deck = new Deck();
        engine.startHand(List.of("p1", "p2", "p3"), deck);

        // p1 sees cards & calls, p2 sees cards & calls, p3 packs (folds)
        engine.applyAction(PlayerAction.of("p1", PlayerActionType.SEE_CARDS));
        engine.applyAction(PlayerAction.of("p1", PlayerActionType.CHAAL, 2000L));

        engine.applyAction(PlayerAction.of("p2", PlayerActionType.SEE_CARDS));
        engine.applyAction(PlayerAction.of("p2", PlayerActionType.CHAAL, 2000L));

        engine.applyAction(PlayerAction.of("p3", PlayerActionType.PACK));

        // p1 requests SHOW against p2
        engine.applyAction(PlayerAction.of("p1", PlayerActionType.SHOW, 2000L));
        engine.resolveShowdownAfterShowAccept();

        assertTrue(engine.isHandFinished());

        // Generate projection for p3 (the folded player)
        PlayerViewGameState proj = projector.createProjection(table, engine, "p3");

        PlayerSummaryView p1View = proj.getPlayers().stream().filter(p -> p.getUserId().equals("p1")).findFirst().orElseThrow();
        PlayerSummaryView p2View = proj.getPlayers().stream().filter(p -> p.getUserId().equals("p2")).findFirst().orElseThrow();
        PlayerSummaryView p3View = proj.getPlayers().stream().filter(p -> p.getUserId().equals("p3")).findFirst().orElseThrow();

        // p1 and p2 cards revealed because they were in showdown outcome
        assertNotNull(p1View.getCards(), "Showdown participant p1's cards revealed at showdown");
        assertNotNull(p2View.getCards(), "Showdown participant p2's cards revealed at showdown");

        // Folded player p3's cards MUST be null
        assertNull(p3View.getCards(), "SECURITY VIOLATION: Folded player p3's cards MUST NEVER be revealed to anyone");
    }
}
