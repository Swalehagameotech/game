package com.teenpatti.platform.game.engine;

import java.util.Objects;

/**
 * Immutable Playing Card representation.
 */
public final class Card implements Comparable<Card> {

    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank) {
        if (suit == null || rank == null) {
            throw new IllegalArgumentException("Suit and Rank must not be null");
        }
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() {
        return suit;
    }

    public Rank getRank() {
        return rank;
    }

    public String toShortString() {
        return rank.getSymbol() + suit.getSymbol();
    }

    /**
     * Parses codes produced by {@link #toShortString()} (e.g. {@code As}, {@code 10h}, {@code Qd}).
     */
    public static Card parse(String code) {
        if (code == null || code.length() < 2) {
            throw new IllegalArgumentException("Invalid card code: " + code);
        }
        String normalized = code.trim();
        String suitSymbol = normalized.substring(normalized.length() - 1);
        String rankSymbol = normalized.substring(0, normalized.length() - 1);

        Suit suit = null;
        for (Suit s : Suit.values()) {
            if (s.getSymbol().equalsIgnoreCase(suitSymbol)) {
                suit = s;
                break;
            }
        }
        Rank rank = null;
        for (Rank r : Rank.values()) {
            if (r.getSymbol().equalsIgnoreCase(rankSymbol)) {
                rank = r;
                break;
            }
        }
        if (suit == null || rank == null) {
            throw new IllegalArgumentException("Invalid card code: " + code);
        }
        return new Card(suit, rank);
    }

    @Override
    public int compareTo(Card other) {
        return Integer.compare(this.rank.getValue(), other.rank.getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return suit == card.suit && rank == card.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, rank);
    }

    @Override
    public String toString() {
        return toShortString();
    }
}
