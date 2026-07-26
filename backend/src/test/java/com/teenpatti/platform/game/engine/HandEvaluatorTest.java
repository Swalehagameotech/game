package com.teenpatti.platform.game.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HandEvaluatorTest {

    @Test
    @DisplayName("TRAIL correctly identified and correctly ranked against other TRAILs (AAA beats KKK, KKK beats 222)")
    void evaluateHand_Trail_IdentifiedAndRanked() {
        List<Card> aaa = List.of(new Card(Suit.HEARTS, Rank.ACE), new Card(Suit.SPADES, Rank.ACE), new Card(Suit.CLUBS, Rank.ACE));
        List<Card> kkk = List.of(new Card(Suit.HEARTS, Rank.KING), new Card(Suit.SPADES, Rank.KING), new Card(Suit.CLUBS, Rank.KING));
        List<Card> t222 = List.of(new Card(Suit.HEARTS, Rank.TWO), new Card(Suit.SPADES, Rank.TWO), new Card(Suit.CLUBS, Rank.TWO));

        HandResult resultAAA = HandEvaluator.evaluateHand(aaa);
        HandResult resultKKK = HandEvaluator.evaluateHand(kkk);
        HandResult result222 = HandEvaluator.evaluateHand(t222);

        assertEquals(HandRankCategory.TRAIL, resultAAA.getCategory());
        assertEquals(HandRankCategory.TRAIL, resultKKK.getCategory());
        assertEquals(HandRankCategory.TRAIL, result222.getCategory());

        assertTrue(HandEvaluator.compareHands(resultAAA, resultKKK) > 0, "AAA must beat KKK");
        assertTrue(HandEvaluator.compareHands(resultKKK, result222) > 0, "KKK must beat 222");
    }

    @Test
    @DisplayName("PURE_SEQUENCE correctly identified for a normal case (4-5-6 of Hearts)")
    void evaluateHand_PureSequence_NormalCase() {
        List<Card> cards = List.of(new Card(Suit.HEARTS, Rank.FOUR), new Card(Suit.HEARTS, Rank.FIVE), new Card(Suit.HEARTS, Rank.SIX));
        HandResult result = HandEvaluator.evaluateHand(cards);

        assertEquals(HandRankCategory.PURE_SEQUENCE, result.getCategory());
        assertEquals(List.of(6), result.getTiebreakers());
    }

    @Test
    @DisplayName("A-2-3 same suit correctly identified as PURE_SEQUENCE (the low-ace edge case) and ranks below 2-3-4")
    void evaluateHand_PureSequence_LowAceSpecialCase() {
        List<Card> a23 = List.of(new Card(Suit.HEARTS, Rank.ACE), new Card(Suit.HEARTS, Rank.TWO), new Card(Suit.HEARTS, Rank.THREE));
        List<Card> c234 = List.of(new Card(Suit.HEARTS, Rank.TWO), new Card(Suit.HEARTS, Rank.THREE), new Card(Suit.HEARTS, Rank.FOUR));

        HandResult resultA23 = HandEvaluator.evaluateHand(a23);
        HandResult result234 = HandEvaluator.evaluateHand(c234);

        assertEquals(HandRankCategory.PURE_SEQUENCE, resultA23.getCategory());
        assertEquals(List.of(3), resultA23.getTiebreakers(), "A-2-3 sequence tiebreaker value must be 3");

        assertTrue(HandEvaluator.compareHands(result234, resultA23) > 0, "2-3-4 (tiebreaker 4) must beat A-2-3 (tiebreaker 3)");
    }

    @Test
    @DisplayName("Q-K-A same suit correctly identified as the HIGHEST PURE_SEQUENCE")
    void evaluateHand_PureSequence_HighAceQKA() {
        List<Card> qka = List.of(new Card(Suit.DIAMONDS, Rank.QUEEN), new Card(Suit.DIAMONDS, Rank.KING), new Card(Suit.DIAMONDS, Rank.ACE));
        List<Card> kqj = List.of(new Card(Suit.DIAMONDS, Rank.KING), new Card(Suit.DIAMONDS, Rank.QUEEN), new Card(Suit.DIAMONDS, Rank.JACK));

        HandResult resultQKA = HandEvaluator.evaluateHand(qka);
        HandResult resultKQJ = HandEvaluator.evaluateHand(kqj);

        assertEquals(HandRankCategory.PURE_SEQUENCE, resultQKA.getCategory());
        assertEquals(List.of(14), resultQKA.getTiebreakers());
        assertTrue(HandEvaluator.compareHands(resultQKA, resultKQJ) > 0, "Q-K-A must beat K-Q-J");
    }

    @Test
    @DisplayName("SEQUENCE (mixed suit) correctly identified, including the A-2-3 mixed-suit case")
    void evaluateHand_Sequence_MixedSuit() {
        List<Card> seqNormal = List.of(new Card(Suit.HEARTS, Rank.FOUR), new Card(Suit.CLUBS, Rank.FIVE), new Card(Suit.SPADES, Rank.SIX));
        List<Card> seqA23 = List.of(new Card(Suit.HEARTS, Rank.ACE), new Card(Suit.CLUBS, Rank.TWO), new Card(Suit.SPADES, Rank.THREE));

        HandResult resNormal = HandEvaluator.evaluateHand(seqNormal);
        HandResult resA23 = HandEvaluator.evaluateHand(seqA23);

        assertEquals(HandRankCategory.SEQUENCE, resNormal.getCategory());
        assertEquals(HandRankCategory.SEQUENCE, resA23.getCategory());
        assertEquals(List.of(3), resA23.getTiebreakers());
        assertTrue(HandEvaluator.compareHands(resNormal, resA23) > 0, "4-5-6 sequence must beat A-2-3 sequence");
    }

    @Test
    @DisplayName("COLOR correctly identified and correctly tie-broken by highest, then next, then lowest card")
    void evaluateHand_Color_FlushTiebreaking() {
        List<Card> colorHigh = List.of(new Card(Suit.SPADES, Rank.KING), new Card(Suit.SPADES, Rank.NINE), new Card(Suit.SPADES, Rank.TWO));
        List<Card> colorLow = List.of(new Card(Suit.SPADES, Rank.KING), new Card(Suit.SPADES, Rank.EIGHT), new Card(Suit.SPADES, Rank.SEVEN));

        HandResult resHigh = HandEvaluator.evaluateHand(colorHigh);
        HandResult resLow = HandEvaluator.evaluateHand(colorLow);

        assertEquals(HandRankCategory.COLOR, resHigh.getCategory());
        assertEquals(HandRankCategory.COLOR, resLow.getCategory());
        assertTrue(HandEvaluator.compareHands(resHigh, resLow) > 0, "K-9-2 color must beat K-8-7 color");
    }

    @Test
    @DisplayName("PAIR correctly identified and correctly tie-broken by pair rank then kicker")
    void evaluateHand_Pair_TiebrokenByPairRankThenKicker() {
        List<Card> pairK_A = List.of(new Card(Suit.HEARTS, Rank.KING), new Card(Suit.SPADES, Rank.KING), new Card(Suit.CLUBS, Rank.ACE));
        List<Card> pairK_Q = List.of(new Card(Suit.DIAMONDS, Rank.KING), new Card(Suit.CLUBS, Rank.KING), new Card(Suit.HEARTS, Rank.QUEEN));
        List<Card> pairQ_A = List.of(new Card(Suit.HEARTS, Rank.QUEEN), new Card(Suit.SPADES, Rank.QUEEN), new Card(Suit.CLUBS, Rank.ACE));

        HandResult resK_A = HandEvaluator.evaluateHand(pairK_A);
        HandResult resK_Q = HandEvaluator.evaluateHand(pairK_Q);
        HandResult resQ_A = HandEvaluator.evaluateHand(pairQ_A);

        assertEquals(HandRankCategory.PAIR, resK_A.getCategory());
        assertTrue(HandEvaluator.compareHands(resK_A, resK_Q) > 0, "Pair of Kings with Ace kicker beats Pair of Kings with Queen kicker");
        assertTrue(HandEvaluator.compareHands(resK_Q, resQ_A) > 0, "Pair of Kings beats Pair of Queens");
    }

    @Test
    @DisplayName("HIGH_CARD correctly tie-broken card by card")
    void evaluateHand_HighCard_TiebrokenCardByCard() {
        List<Card> hc1 = List.of(new Card(Suit.HEARTS, Rank.ACE), new Card(Suit.CLUBS, Rank.KING), new Card(Suit.SPADES, Rank.FIVE));
        List<Card> hc2 = List.of(new Card(Suit.DIAMONDS, Rank.ACE), new Card(Suit.SPADES, Rank.KING), new Card(Suit.CLUBS, Rank.FOUR));

        HandResult res1 = HandEvaluator.evaluateHand(hc1);
        HandResult res2 = HandEvaluator.evaluateHand(hc2);

        assertEquals(HandRankCategory.HIGH_CARD, res1.getCategory());
        assertTrue(HandEvaluator.compareHands(res1, res2) > 0, "A-K-5 beats A-K-4");
    }

    @Test
    @DisplayName("compareHands correctly ranks TRAIL > PURE_SEQUENCE > SEQUENCE > COLOR > PAIR > HIGH_CARD across category boundaries")
    void compareHands_CategoryHierarchyBoundaries() {
        List<Card> trail = List.of(new Card(Suit.HEARTS, Rank.TWO), new Card(Suit.SPADES, Rank.TWO), new Card(Suit.CLUBS, Rank.TWO));
        List<Card> pureSeq = List.of(new Card(Suit.DIAMONDS, Rank.ACE), new Card(Suit.DIAMONDS, Rank.KING), new Card(Suit.DIAMONDS, Rank.QUEEN));
        List<Card> seq = List.of(new Card(Suit.HEARTS, Rank.ACE), new Card(Suit.CLUBS, Rank.KING), new Card(Suit.SPADES, Rank.QUEEN));
        List<Card> color = List.of(new Card(Suit.CLUBS, Rank.ACE), new Card(Suit.CLUBS, Rank.KING), new Card(Suit.CLUBS, Rank.JACK));
        List<Card> pair = List.of(new Card(Suit.HEARTS, Rank.ACE), new Card(Suit.SPADES, Rank.ACE), new Card(Suit.CLUBS, Rank.KING));
        List<Card> highCard = List.of(new Card(Suit.HEARTS, Rank.ACE), new Card(Suit.CLUBS, Rank.KING), new Card(Suit.SPADES, Rank.JACK));

        HandResult resTrail = HandEvaluator.evaluateHand(trail);
        HandResult resPureSeq = HandEvaluator.evaluateHand(pureSeq);
        HandResult resSeq = HandEvaluator.evaluateHand(seq);
        HandResult resColor = HandEvaluator.evaluateHand(color);
        HandResult resPair = HandEvaluator.evaluateHand(pair);
        HandResult resHighCard = HandEvaluator.evaluateHand(highCard);

        assertTrue(HandEvaluator.compareHands(resTrail, resPureSeq) > 0, "Lowest Trail (222) beats Highest Pure Sequence (QKA)");
        assertTrue(HandEvaluator.compareHands(resPureSeq, resSeq) > 0, "Pure Sequence beats Sequence");
        assertTrue(HandEvaluator.compareHands(resSeq, resColor) > 0, "Sequence beats Color");
        assertTrue(HandEvaluator.compareHands(resColor, resPair) > 0, "Color beats Pair");
        assertTrue(HandEvaluator.compareHands(resPair, resHighCard) > 0, "Pair beats High Card");
    }
}
