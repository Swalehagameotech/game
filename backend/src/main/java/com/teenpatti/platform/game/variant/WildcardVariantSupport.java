package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.HandEvaluator;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.game.engine.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Utility to evaluate Teen Patti hands with wildcard/joker cards while reusing Classic evaluator.
 * The implementation is deliberately isolated so Classic flow and APIs remain unchanged.
 */
final class WildcardVariantSupport {

    private WildcardVariantSupport() {
    }

    static HandResult evaluateWithWildcards(List<Card> threeCards, Predicate<Card> isWildcard) {
        if (threeCards == null || threeCards.size() != 3) {
            throw new IllegalArgumentException("Hand evaluation requires exactly 3 cards");
        }
        List<Integer> wildcardIndexes = new ArrayList<>();
        for (int i = 0; i < threeCards.size(); i++) {
            if (isWildcard.test(threeCards.get(i))) {
                wildcardIndexes.add(i);
            }
        }
        if (wildcardIndexes.isEmpty()) {
            return HandEvaluator.evaluateHand(threeCards);
        }

        List<Card> deck = deckCards();
        List<Card> mutable = new ArrayList<>(threeCards);
        HandResult best = evaluateReplacements(deck, wildcardIndexes, 0, mutable, null);

        if (best == null) {
            return HandEvaluator.evaluateHand(threeCards);
        }
        // Keep original cards in the result snapshot; only ranking semantics are wildcard-adjusted.
        return new HandResult(best.getCategory(), best.getTiebreakers(), threeCards);
    }

    private static HandResult evaluateReplacements(
            List<Card> deck,
            List<Integer> wildcardIndexes,
            int pos,
            List<Card> mutable,
            HandResult bestSoFar) {
        if (pos >= wildcardIndexes.size()) {
            HandResult current = HandEvaluator.evaluateHand(mutable);
            if (bestSoFar == null || HandEvaluator.compareHands(current, bestSoFar) > 0) {
                return current;
            }
            return bestSoFar;
        }
        int wildcardAt = wildcardIndexes.get(pos);
        HandResult best = bestSoFar;
        for (Card replacement : deck) {
            mutable.set(wildcardAt, replacement);
            best = evaluateReplacements(deck, wildcardIndexes, pos + 1, mutable, best);
        }
        return best;
    }

    private static List<Card> deckCards() {
        List<Card> deck = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit, rank));
            }
        }
        return deck;
    }
}

