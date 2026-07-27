package com.teenpatti.platform.game.distribution;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.Deck;
import com.teenpatti.platform.game.engine.DeckConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CardDistributionServiceTest {

    private final CardDistributionService service = new CardDistributionService();

    @Test
    @DisplayName("Each seated player receives exactly 3 unique private cards")
    void dealPrivateHands_threeCardsPerPlayer() {
        Deck deck = new Deck();
        deck.shuffle();
        List<String> players = List.of("p1", "p2", "p3");

        PrivateHandDeal deal = service.dealPrivateHands(deck, players);

        assertEquals(9, deal.getTotalCardsDealt());
        assertEquals(DeckConstants.STANDARD_DECK_SIZE - 9, deal.getRemainingDeckCards());
        assertEquals(3, players.size());

        Set<Card> allDealt = new HashSet<>();
        for (String playerId : players) {
            List<Card> hand = deal.getHandsByPlayerId().get(playerId);
            assertNotNull(hand);
            assertEquals(DeckConstants.CARDS_PER_HAND, hand.size());
            for (Card card : hand) {
                assertTrue(allDealt.add(card), "Duplicate card across players: " + card);
            }
        }
    }

    @Test
    @DisplayName("Six players consume 18 cards leaving 34 in deck")
    void dealPrivateHands_maxTableSize() {
        Deck deck = new Deck();
        List<String> players = List.of("p1", "p2", "p3", "p4", "p5", "p6");

        PrivateHandDeal deal = service.dealPrivateHands(deck, players);

        assertEquals(18, deal.getTotalCardsDealt());
        assertEquals(34, deal.getRemainingDeckCards());
    }

    @Test
    @DisplayName("Insufficient deck cards throws before partial deal")
    void dealPrivateHands_rejectsInsufficientDeck() {
        Deck deck = new Deck();
        deck.deal(DeckConstants.STANDARD_DECK_SIZE - 5);
        List<String> players = List.of("p1", "p2", "p3");

        assertThrows(IllegalStateException.class, () -> service.dealPrivateHands(deck, players));
    }

    @Test
    @DisplayName("Duplicate player ids in seat order are rejected")
    void dealPrivateHands_rejectsDuplicatePlayers() {
        Deck deck = new Deck();
        assertThrows(IllegalArgumentException.class,
                () -> service.dealPrivateHands(deck, List.of("p1", "p1", "p2")));
    }

    @Test
    @DisplayName("Hands are keyed by player id in seat order")
    void dealPrivateHands_preservesSeatOrderKeys() {
        Deck deck = new Deck();
        List<String> players = List.of("host", "guest", "friend");

        Map<String, List<Card>> hands = service.dealPrivateHands(deck, players).getHandsByPlayerId();

        assertEquals(List.of("host", "guest", "friend"), List.copyOf(hands.keySet()));
        hands.values().forEach(hand -> assertEquals(3, hand.size()));
    }
}
