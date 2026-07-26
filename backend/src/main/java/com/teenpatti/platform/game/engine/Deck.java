package com.teenpatti.platform.game.engine;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Standard 52-card Deck with Fisher-Yates shuffling using SecureRandom.
 */
public class Deck {

    private final List<Card> cards;
    private final SecureRandom random;

    public Deck() {
        this(new SecureRandom());
    }

    public Deck(SecureRandom random) {
        this.random = random != null ? random : new SecureRandom();
        this.cards = new ArrayList<>(52);
        reset();
    }

    /**
     * Resets the deck to a full, ordered set of 52 cards.
     */
    public final void reset() {
        cards.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    /**
     * Shuffles the deck in-place using the unbiased Fisher-Yates algorithm.
     */
    public void shuffle() {
        for (int i = cards.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Card temp = cards.get(i);
            cards.set(i, cards.get(j));
            cards.set(j, temp);
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
