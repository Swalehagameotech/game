package com.teenpatti.platform.game.engine;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Standard 52-card deck (4 suits × 13 ranks, no jokers) with Fisher-Yates shuffling via {@link SecureRandom}.
 * Deck order is server-only and must never be broadcast to clients.
 */
public class Deck {

    private final List<Card> cards;
    private final SecureRandom random;

    public Deck() {
        this(new SecureRandom());
    }

    public Deck(SecureRandom random) {
        this.random = random != null ? random : new SecureRandom();
        this.cards = new ArrayList<>(DeckConstants.STANDARD_DECK_SIZE);
        reset();
    }

    /**
     * Resets the deck to a full standard 52-card set (no jokers).
     */
    public final void reset() {
        cards.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
        if (cards.size() != DeckConstants.STANDARD_DECK_SIZE) {
            throw new IllegalStateException(
                    "Invalid deck composition: expected " + DeckConstants.STANDARD_DECK_SIZE + " cards, got " + cards.size());
        }
    }

    /**
     * Shuffles in-place using unbiased Fisher-Yates (Knuth) with {@link SecureRandom}.
     */
    public void shuffle() {
        if (cards.size() != DeckConstants.STANDARD_DECK_SIZE) {
            throw new IllegalStateException("Cannot shuffle: deck must contain exactly 52 cards before shuffle");
        }
        for (int i = cards.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Card temp = cards.get(i);
            cards.set(i, cards.get(j));
            cards.set(j, temp);
        }
    }

    /**
     * Verifies exactly 52 unique standard cards — call after shuffle before dealing.
     */
    public void assertIntegrity() {
        if (cards.size() != DeckConstants.STANDARD_DECK_SIZE) {
            throw new IllegalStateException(
                    "Deck integrity failed: expected " + DeckConstants.STANDARD_DECK_SIZE + " cards, found " + cards.size());
        }
        Set<Card> unique = new HashSet<>(cards);
        if (unique.size() != DeckConstants.STANDARD_DECK_SIZE) {
            throw new IllegalStateException(
                    "Deck integrity failed: duplicate cards detected (" + unique.size() + " unique of " + cards.size() + ")");
        }
    }

    /**
     * Deals a specified number of cards from the top of the deck.
     */
    public List<Card> deal(int count) {
        if (count < 1 || count > cards.size()) {
            throw new IllegalArgumentException("Cannot deal " + count + " cards from deck of size " + cards.size());
        }
        List<Card> dealt = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            dealt.add(cards.remove(cards.size() - 1));
        }
        return dealt;
    }

    public int remainingCards() {
        return cards.size();
    }

    public List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }
}
