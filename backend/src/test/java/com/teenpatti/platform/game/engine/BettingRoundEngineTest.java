package com.teenpatti.platform.game.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BettingRoundEngineTest {

    private GameEngineConfig config;
    private BettingRoundEngine engine;
    private List<String> players;

    @BeforeEach
    void setUp() {
        // Boot: 1000 paise (₹10), Max bet: 50,000 paise (₹500), Rake: 5.0%, Ratio: 2
        config = new GameEngineConfig(1000L, 50_000L, 5.0, 2);
        engine = new BettingRoundEngine(config);
        players = List.of("player1", "player2", "player3");
    }

    @Test
    @DisplayName("Hand initialization deducts boot amount, deals 3 cards to each player, sets turn index to 0")
    void startHand_InitializesPotCardsAndTurn() {
        Deck deck = new Deck();
        engine.startHand(players, deck);

        assertFalse(engine.isHandFinished());
        assertEquals("player1", engine.getCurrentTurnPlayerId());
        assertEquals(3000L, engine.getPotPaise(), "Pot must contain boot from 3 players (3 * 1000)");
        assertEquals(1000L, engine.getCurrentBaseStakePaise());
        assertEquals(PlayerStatus.BLIND, engine.getPlayerStatus("player1"));
        assertEquals(3, engine.getPlayerCards("player1").size());
    }

    @Test
    @DisplayName("Blind player's required bet is half of a seen player's at the same point in hand")
    void getRequiredBetPaise_BlindVsSeenRatio() {
        Deck deck = new Deck();
        engine.startHand(players, deck);

        assertEquals(1000L, engine.getRequiredBetPaise("player1"), "Blind player required bet is 1000 paise");

        // Player 1 sees cards
        engine.applyAction(PlayerAction.of("player1", PlayerActionType.SEE_CARDS));
        assertEquals(PlayerStatus.SEEN, engine.getPlayerStatus("player1"));

        // Seen player required bet must be 2x current base stake = 2000 paise
        assertEquals(2000L, engine.getRequiredBetPaise("player1"));
    }

    @Test
    @DisplayName("Action out of turn is rejected with NOT_YOUR_TURN")
    void applyAction_OutOfTurn_ThrowsNotYourTurn() {
        Deck deck = new Deck();
        engine.startHand(players, deck);

        InvalidActionException ex = assertThrows(InvalidActionException.class, () ->
                engine.applyAction(PlayerAction.of("player2", PlayerActionType.CHAAL, 1000L)));

        assertEquals(ActionRejectionReason.NOT_YOUR_TURN, ex.getReason());
    }

    @Test
    @DisplayName("Bet below required minimum is rejected with INSUFFICIENT_BET_AMOUNT")
    void applyAction_BelowMinimum_ThrowsInsufficientBetAmount() {
        Deck deck = new Deck();
        engine.startHand(players, deck);

        InvalidActionException ex = assertThrows(InvalidActionException.class, () ->
                engine.applyAction(PlayerAction.of("player1", PlayerActionType.PLAY_BLIND, 500L)));

        assertEquals(ActionRejectionReason.INSUFFICIENT_BET_AMOUNT, ex.getReason());
    }

    @Test
    @DisplayName("RAISE enforces max bet limit configuration")
    void applyAction_RaiseExceedingMaxLimit_ThrowsExceedsMaxBet() {
        Deck deck = new Deck();
        engine.startHand(players, deck);

        InvalidActionException ex = assertThrows(InvalidActionException.class, () ->
                engine.applyAction(PlayerAction.of("player1", PlayerActionType.RAISE, 100_000L))); // Config max is 50,000

        assertEquals(ActionRejectionReason.EXCEEDS_MAX_BET, ex.getReason());
    }

    @Test
    @DisplayName("PACK removes player from active set while keeping their bets in pot")
    void applyAction_Pack_RemovesPlayerFromActiveSet() {
        Deck deck = new Deck();
        engine.startHand(players, deck);

        engine.applyAction(PlayerAction.of("player1", PlayerActionType.PACK));

        assertEquals(PlayerStatus.PACKED, engine.getPlayerStatus("player1"));
        assertEquals(3000L, engine.getPotPaise(), "Pot must retain packed player's boot bet");
        assertEquals("player2", engine.getCurrentTurnPlayerId());
        assertEquals(List.of("player2", "player3"), engine.getActivePlayerIds());
    }

    @Test
    @DisplayName("SHOW is rejected when more than two active players remain")
    void applyAction_ShowWithThreePlayers_ThrowsShowRequiresExactlyTwoPlayers() {
        Deck deck = new Deck();
        engine.startHand(players, deck);

        InvalidActionException ex = assertThrows(InvalidActionException.class, () ->
                engine.applyAction(PlayerAction.of("player1", PlayerActionType.SHOW, 1000L)));

        assertEquals(ActionRejectionReason.SHOW_REQUIRES_EXACTLY_TWO_PLAYERS, ex.getReason());
    }

    @Test
    @DisplayName("SHOW is allowed for BLIND player when exactly two active remain")
    void applyAction_ShowWhileBlind_WithTwoPlayers_Resolves() {
        Deck deck = new Deck();
        engine.startHand(players, deck);
        engine.applyAction(PlayerAction.of("player1", PlayerActionType.PACK));

        assertEquals(2, engine.getActivePlayerIds().size());
        assertEquals(PlayerStatus.BLIND, engine.getPlayerStatus("player2"));

        engine.applyAction(PlayerAction.of("player2", PlayerActionType.SHOW, 2000L));
        engine.resolveShowdownAfterShowAccept();

        assertTrue(engine.isHandFinished());
        assertNotNull(engine.getOutcome());
        assertNotNull(engine.getOutcome().getWinnerId());
    }

    @Test
    @DisplayName("Hand where all but one player packs awards pot to last remaining player WITHOUT reveal")
    void hand_AllButOnePack_AwardsPotWithoutReveal() {
        Deck deck = new Deck();
        engine.startHand(players, deck);

        engine.applyAction(PlayerAction.of("player1", PlayerActionType.PACK));
        engine.applyAction(PlayerAction.of("player2", PlayerActionType.PACK));

        assertTrue(engine.isHandFinished());
        HandOutcome outcome = engine.getOutcome();

        assertNotNull(outcome);
        assertEquals("player3", outcome.getWinnerId());
        assertEquals(3000L, outcome.getPotAmountPaise());
        assertEquals(150L, outcome.getRakeAmountPaise(), "5% of 3000 = 150 paise");
        assertEquals(2850L, outcome.getWinnerPayoutPaise(), "3000 - 150 = 2850 paise");
        assertTrue(outcome.getRevealedHands().isEmpty(), "No cards revealed on fold victory");
    }

    @Test
    @DisplayName("SHOW between 2 active players resolves via compareHands, awards pot minus rake, reveals hands")
    void hand_ShowBetweenTwoPlayers_ResolvesWinnerAndRake() {
        List<String> twoPlayers = List.of("p1", "p2");
        Deck deck = new Deck();
        engine.startHand(twoPlayers, deck);

        // Turn 1: p1 plays blind 1000
        engine.applyAction(PlayerAction.of("p1", PlayerActionType.PLAY_BLIND, 1000L));
        // Turn 2: p2 sees cards, then calls SHOW with 2000 paise
        engine.applyAction(PlayerAction.of("p2", PlayerActionType.SEE_CARDS));
        engine.recordShowRequest("p2", 2000L);
        engine.resolveShowdownAfterShowAccept();

        assertTrue(engine.isHandFinished());
        HandOutcome outcome = engine.getOutcome();

        assertNotNull(outcome);
        assertNotNull(outcome.getWinnerId());
        assertEquals(5000L, outcome.getPotAmountPaise(), "Initial 2000 boot + 1000 p1 + 2000 p2 show = 5000 paise");
        assertEquals(250L, outcome.getRakeAmountPaise(), "5% of 5000 = 250 paise");
        assertEquals(4750L, outcome.getWinnerPayoutPaise(), "5000 - 250 = 4750 paise");
        assertEquals(2, outcome.getRevealedHands().size(), "Both players' hands revealed on showdown");
    }

    @Test
    @DisplayName("Full simulated multi-player hand end-to-end test")
    void fullSimulatedHand_EndToEnd() {
        Deck deck = new Deck();
        engine.startHand(players, deck); // Pot = 3000 (1000 each boot)

        // Round 1:
        // Player 1 plays blind (1000) -> Pot = 4000
        engine.applyAction(PlayerAction.of("player1", PlayerActionType.PLAY_BLIND, 1000L));

        // Player 2 sees cards
        engine.applyAction(PlayerAction.of("player2", PlayerActionType.SEE_CARDS));
        // Player 2 raises base stake to 2000 (seen required bet = 4000) -> Pot = 8000
        engine.applyAction(PlayerAction.of("player2", PlayerActionType.RAISE, 4000L));

        // Player 3 packs -> Pot = 8000
        engine.applyAction(PlayerAction.of("player3", PlayerActionType.PACK));

        // Round 2 (active: player1, player2):
        // Player 1 (blind) plays blind at raised base stake 2000 -> Pot = 10000
        engine.applyAction(PlayerAction.of("player1", PlayerActionType.PLAY_BLIND, 2000L));

        // Player 2 (seen, required = 4000) requests SHOW -> Pot = 14000
        engine.applyAction(PlayerAction.of("player2", PlayerActionType.SHOW, 4000L));
        engine.resolveShowdownAfterShowAccept();

        assertTrue(engine.isHandFinished());
        HandOutcome outcome = engine.getOutcome();

        assertNotNull(outcome);
        assertEquals(14000L, outcome.getPotAmountPaise());
        assertEquals(700L, outcome.getRakeAmountPaise(), "5% of 14000 = 700 paise");
        assertEquals(13300L, outcome.getWinnerPayoutPaise(), "14000 - 700 = 13300 paise");
        assertNotNull(outcome.getWinnerId());
        assertTrue(outcome.getWinnerId().equals("player1") || outcome.getWinnerId().equals("player2"));
    }
}
