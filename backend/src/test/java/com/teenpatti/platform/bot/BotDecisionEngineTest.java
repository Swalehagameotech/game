package com.teenpatti.platform.bot;

import com.teenpatti.platform.game.betting.BettingState;
import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.game.engine.Suit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BotDecisionEngineTest {

    private BotDecisionEngine engine;
    private BotProfile profile;

    @BeforeEach
    void setUp() {
        engine = new BotDecisionEngine(new HandStrengthEvaluator());
        profile = BotProfile.builder()
                .userId("bot-1")
                .displayName("Test Bot")
                .personality(BotPersonality.BALANCED)
                .preferSeeAfterRounds(1)
                .build();
    }

    @Test
    void strongHandChoosesFromAllowedActions() {
        BettingState state = BettingState.builder()
                .tableId("t1")
                .userId("bot-1")
                .playerState("SEEN")
                .myTurn(true)
                .allowedActions(List.of("CHAAL", "RAISE", "PACK", "SHOW"))
                .raiseOptionsPaise(List.of(2000L, 4000L))
                .build();

        List<Card> trail = List.of(
                new Card(Suit.HEARTS, Rank.ACE),
                new Card(Suit.SPADES, Rank.ACE),
                new Card(Suit.DIAMONDS, Rank.ACE)
        );

        BotDecision decision = engine.decide(profile, state, trail, 2);
        assertNotNull(decision.getActionType());
        assertTrue(Set.of("CHAAL", "RAISE", "PACK", "SHOW").contains(decision.getActionType()));
    }

    @Test
    void showPromptReturnsAcceptOrReject() {
        BettingState state = BettingState.builder()
                .tableId("t1")
                .userId("bot-1")
                .playerState("SEEN")
                .myTurn(true)
                .allowedActions(List.of("SHOW_ACCEPT", "SHOW_REJECT"))
                .build();

        BotDecision decision = engine.decide(profile, state, List.of(
                new Card(Suit.HEARTS, Rank.TWO),
                new Card(Suit.SPADES, Rank.THREE),
                new Card(Suit.CLUBS, Rank.FOUR)
        ), 1);

        assertTrue(Set.of("SHOW_ACCEPT", "SHOW_REJECT").contains(decision.getActionType()));
    }

    @Test
    void emptyAllowedFallsBackToPack() {
        BettingState state = BettingState.builder()
                .tableId("t1")
                .userId("bot-1")
                .playerState("PACKED")
                .myTurn(false)
                .allowedActions(List.of())
                .build();

        BotDecision decision = engine.decide(profile, state, null, 1);
        assertEquals("PACK", decision.getActionType());
    }
}
