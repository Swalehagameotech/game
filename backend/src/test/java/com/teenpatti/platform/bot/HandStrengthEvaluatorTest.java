package com.teenpatti.platform.bot;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.game.engine.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HandStrengthEvaluatorTest {

    private final HandStrengthEvaluator evaluator = new HandStrengthEvaluator();

    @Test
    void trailIsVeryStrong() {
        List<Card> trail = List.of(
                new Card(Suit.HEARTS, Rank.ACE),
                new Card(Suit.SPADES, Rank.ACE),
                new Card(Suit.CLUBS, Rank.ACE)
        );
        assertEquals(HandStrength.VERY_STRONG, evaluator.evaluate(trail));
    }

    @Test
    void highPairIsStrong() {
        List<Card> pair = List.of(
                new Card(Suit.HEARTS, Rank.KING),
                new Card(Suit.SPADES, Rank.KING),
                new Card(Suit.CLUBS, Rank.FIVE)
        );
        assertEquals(HandStrength.STRONG, evaluator.evaluate(pair));
    }

    @Test
    void lowHighCardIsVeryWeak() {
        List<Card> junk = List.of(
                new Card(Suit.HEARTS, Rank.SEVEN),
                new Card(Suit.SPADES, Rank.FOUR),
                new Card(Suit.CLUBS, Rank.TWO)
        );
        assertEquals(HandStrength.VERY_WEAK, evaluator.evaluate(junk));
    }
}
