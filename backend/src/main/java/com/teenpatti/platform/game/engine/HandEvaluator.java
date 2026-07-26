package com.teenpatti.platform.game.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure Java Teen Patti 3-card hand evaluator and tie-breaker comparison utility.
 *
 * TEEN PATTI SEQUENCE RULES & CONVENTIONS:
 * 1. TRAIL (Three of a Kind): AAA is highest, 222 is lowest.
 * 2. PURE_SEQUENCE (Straight Flush):
 *    - A-K-Q is the HIGHEST possible pure sequence (tiebreaker value 14).
 *    - A-2-3 is a VALID LOWEST pure sequence (tiebreaker value 3, ranking below 4-3-2 which is 4).
 * 3. SEQUENCE (Straight, mixed suits):
 *    - A-K-Q is the HIGHEST sequence (tiebreaker value 14).
 *    - A-2-3 is the LOWEST sequence (tiebreaker value 3).
 * 4. COLOR (Flush, non-sequential): Compare highest card, then middle card, then lowest card.
 * 5. PAIR: Compare pair rank first, then kicker rank.
 * 6. HIGH_CARD: Compare highest card, then middle card, then lowest card.
 */
public final class HandEvaluator {

    private HandEvaluator() {
        // Utility class
    }

    public static HandResult evaluateHand(List<Card> threeCards) {
        if (threeCards == null || threeCards.size() != 3) {
            throw new IllegalArgumentException("Hand evaluation requires exactly 3 cards");
        }

        List<Card> sorted = new ArrayList<>(threeCards);
        sorted.sort(Comparator.comparingInt((Card c) -> c.getRank().getValue()).reversed());

        Rank r0 = sorted.get(0).getRank(); // highest rank
        Rank r1 = sorted.get(1).getRank(); // middle rank
        Rank r2 = sorted.get(2).getRank(); // lowest rank

        boolean isFlush = (sorted.get(0).getSuit() == sorted.get(1).getSuit()) &&
                          (sorted.get(1).getSuit() == sorted.get(2).getSuit());

        // 1. Check TRAIL (Three of a Kind)
        if (r0 == r1 && r1 == r2) {
            return new HandResult(HandRankCategory.TRAIL, List.of(r0.getValue()), sorted);
        }

        // 2. Check SEQUENCE / PURE_SEQUENCE
        // Standard Sequence: e.g. A-K-Q (14,13,12), K-Q-J (13,12,11), 4-3-2 (4,3,2)
        boolean isStandardSeq = (r0.getValue() - r1.getValue() == 1) && (r1.getValue() - r2.getValue() == 1);
        // Low-Ace Sequence: A-2-3 (values 14, 3, 2)
        boolean isLowAceSeq = (r0 == Rank.ACE && r1 == Rank.THREE && r2 == Rank.TWO);

        if (isStandardSeq || isLowAceSeq) {
            // A-K-Q high sequence is 14. A-2-3 low sequence tiebreaker is 3 (ranks below 4-3-2 which is 4).
            int sequenceTiebreaker = isLowAceSeq ? 3 : r0.getValue();
            HandRankCategory category = isFlush ? HandRankCategory.PURE_SEQUENCE : HandRankCategory.SEQUENCE;
            return new HandResult(category, List.of(sequenceTiebreaker), sorted);
        }

        // 3. Check COLOR (Flush, non-sequential)
        if (isFlush) {
            return new HandResult(
                    HandRankCategory.COLOR,
                    List.of(r0.getValue(), r1.getValue(), r2.getValue()),
                    sorted
            );
        }

        // 4. Check PAIR
        if (r0 == r1) {
            return new HandResult(HandRankCategory.PAIR, List.of(r0.getValue(), r2.getValue()), sorted);
        } else if (r1 == r2) {
            return new HandResult(HandRankCategory.PAIR, List.of(r1.getValue(), r0.getValue()), sorted);
        } else if (r0 == r2) {
            return new HandResult(HandRankCategory.PAIR, List.of(r0.getValue(), r1.getValue()), sorted);
        }

        // 5. HIGH_CARD
        return new HandResult(
                HandRankCategory.HIGH_CARD,
                List.of(r0.getValue(), r1.getValue(), r2.getValue()),
                sorted
        );
    }

    /**
     * Compares two evaluated hand results.
     * Returns positive if a > b, negative if a < b, 0 if tie.
     */
    public static int compareHands(HandResult a, HandResult b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("HandResult arguments must not be null");
        }
        return a.compareTo(b);
    }
}
