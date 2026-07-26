package com.teenpatti.platform.game.engine;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of evaluating a 3-card Teen Patti hand, including rank category
 * and ordered tiebreaker values for resolving same-category comparisons.
 */
public final class HandResult implements Comparable<HandResult> {

    private final HandRankCategory category;
    private final List<Integer> tiebreakers;
    private final List<Card> cards;

    public HandResult(HandRankCategory category, List<Integer> tiebreakers, List<Card> cards) {
        if (category == null || tiebreakers == null || cards == null) {
            throw new IllegalArgumentException("Category, tiebreakers, and cards must not be null");
        }
        this.category = category;
        this.tiebreakers = Collections.unmodifiableList(tiebreakers);
        this.cards = Collections.unmodifiableList(cards);
    }

    public HandRankCategory getCategory() {
        return category;
    }

    public List<Integer> getTiebreakers() {
        return tiebreakers;
    }

    public List<Card> getCards() {
        return cards;
    }

    @Override
    public int compareTo(HandResult other) {
        if (other == null) return 1;
        // 1. Compare Category Priority
        int categoryCompare = Integer.compare(this.category.getPriority(), other.category.getPriority());
        if (categoryCompare != 0) {
            return categoryCompare;
        }

        // 2. Compare Tiebreakers Element-by-Element
        int minLength = Math.min(this.tiebreakers.size(), other.tiebreakers.size());
        for (int i = 0; i < minLength; i++) {
            int cmp = Integer.compare(this.tiebreakers.get(i), other.tiebreakers.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(this.tiebreakers.size(), other.tiebreakers.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HandResult that = (HandResult) o;
        return category == that.category && Objects.equals(tiebreakers, that.tiebreakers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, tiebreakers);
    }

    @Override
    public String toString() {
        return category.name() + " tiebreakers=" + tiebreakers + " cards=" + cards;
    }
}
