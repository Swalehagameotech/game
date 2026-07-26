package com.teenpatti.platform.game.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeckTest {

    @Test
    @DisplayName("Deck initialization creates all 52 unique standard cards")
    void deck_Initialization_Has52UniqueCards() {
        Deck deck = new Deck();
        assertEquals(52, deck.remainingCards());

        Set<Card> uniqueCards = new HashSet<>(deck.getCards());
        assertEquals(52, uniqueCards.size(), "Deck must contain exactly 52 unique cards");
    }

    @Test
    @DisplayName("Deck.shuffle() produces all 52 unique cards with no duplicates or omissions across 1000 shuffles")
    void shuffle_ProducesAll52UniqueCardsNoLossOrDuplicates() {
        Deck deck = new Deck();
        for (int i = 0; i < 1000; i++) {
            deck.reset();
            deck.shuffle();
            assertEquals(52, deck.remainingCards());
            Set<Card> cardSet = new HashSet<>(deck.getCards());
            assertEquals(52, cardSet.size(), "Shuffle iteration " + i + " must contain 52 unique cards");
        }
    }

    @Test
    @DisplayName("Deck.deal() correctly reduces remaining cards and returns dealt subset")
    void deal_DealsRequestedCount() {
        Deck deck = new Deck();
        List<Card> dealt = deck.deal(3);
        assertEquals(3, dealt.size());
        assertEquals(49, deck.remainingCards());
    }
}
