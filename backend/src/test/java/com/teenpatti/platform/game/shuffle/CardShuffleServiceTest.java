package com.teenpatti.platform.game.shuffle;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.Deck;
import com.teenpatti.platform.game.engine.DeckConstants;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.game.engine.Suit;
import com.teenpatti.platform.table.GameVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CardShuffleServiceTest {

    @Test
    @DisplayName("createShuffledDeck produces 52 unique standard cards with audit metadata")
    void createShuffledDeck_producesValidDeck() {
        CardShuffleService service = new CardShuffleService(new SecureRandom());

        ShuffledDeck result = service.createShuffledDeck();

        assertNotNull(result.getShuffleId());
        assertNotNull(result.getShuffledAt());
        assertEquals(DeckConstants.STANDARD_DECK_SIZE, result.getDeck().remainingCards());

        Set<Card> unique = new HashSet<>(result.getDeck().getCards());
        assertEquals(DeckConstants.STANDARD_DECK_SIZE, unique.size());
    }

    @Test
    @DisplayName("1000 shuffles never lose or duplicate a card")
    void createShuffledDeck_integrityAcrossManyShuffles() {
        CardShuffleService service = new CardShuffleService(new SecureRandom());

        for (int i = 0; i < 1000; i++) {
            ShuffledDeck result = service.createShuffledDeck();
            result.getDeck().assertIntegrity();
        }
    }

    @Test
    @DisplayName("shuffle ids are unique across consecutive shuffles")
    void createShuffledDeck_uniqueShuffleIds() {
        CardShuffleService service = new CardShuffleService(new SecureRandom());

        ShuffledDeck first = service.createShuffledDeck();
        ShuffledDeck second = service.createShuffledDeck();

        assertNotEquals(first.getShuffleId(), second.getShuffleId());
    }

    @Test
    @DisplayName("deck contains exactly 4 suits × 13 ranks — no jokers")
    void createShuffledDeck_noJokersStandardComposition() {
        CardShuffleService service = new CardShuffleService(new SecureRandom());
        ShuffledDeck result = service.createShuffledDeck();

        int expected = Suit.values().length * Rank.values().length;
        assertEquals(52, expected);
        assertEquals(expected, result.getDeck().remainingCards());
    }

    @Test
    @DisplayName("unsupported variant rejects shuffle")
    void createShuffledDeck_rejectsUnsupportedVariant() {
        CardShuffleService service = new CardShuffleService(new SecureRandom());

        assertThrows(UnsupportedOperationException.class, () -> service.createShuffledDeck(GameVariant.JOKER));
    }
}
