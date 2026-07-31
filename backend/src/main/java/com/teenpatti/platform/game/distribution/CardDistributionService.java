package com.teenpatti.platform.game.distribution;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.Deck;
import com.teenpatti.platform.game.engine.DeckConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-authoritative card distribution: exactly {@link DeckConstants#CARDS_PER_HAND} private
 * cards per seated player. Card values are never broadcast — only delivered via per-player projections.
 */
@Slf4j
@Service
public class CardDistributionService {

    public PrivateHandDeal dealPrivateHands(Deck deck, List<String> playerIdsInSeatOrder) {
        return dealPrivateHands(deck, playerIdsInSeatOrder, DeckConstants.CARDS_PER_HAND);
    }

    public PrivateHandDeal dealPrivateHands(Deck deck, List<String> playerIdsInSeatOrder, int cardsPerHand) {
        validateInputs(deck, playerIdsInSeatOrder, cardsPerHand);

        Map<String, List<Card>> hands = new LinkedHashMap<>();
        Set<Card> dealtCards = new HashSet<>();

        for (String playerId : playerIdsInSeatOrder) {
            List<Card> hand = List.copyOf(deck.deal(cardsPerHand));
            validateHand(playerId, hand, cardsPerHand);
            for (Card card : hand) {
                if (!dealtCards.add(card)) {
                    throw new IllegalStateException(
                            "Card distribution integrity failed: duplicate card dealt to table");
                }
            }
            hands.put(playerId, hand);
        }

        int totalDealt = playerIdsInSeatOrder.size() * cardsPerHand;
        log.debug("Dealt {} cards ({} per player) to {} players, {} cards remain in deck",
                totalDealt, cardsPerHand, playerIdsInSeatOrder.size(), deck.remainingCards());

        return new PrivateHandDeal(hands, totalDealt, deck.remainingCards());
    }

    private void validateInputs(Deck deck, List<String> playerIdsInSeatOrder, int cardsPerHand) {
        if (deck == null) {
            throw new IllegalArgumentException("Deck must not be null");
        }
        if (playerIdsInSeatOrder == null || playerIdsInSeatOrder.size() < 2) {
            throw new IllegalArgumentException("At least 2 seated players are required to deal cards");
        }

        long uniquePlayers = playerIdsInSeatOrder.stream().distinct().count();
        if (uniquePlayers != playerIdsInSeatOrder.size()) {
            throw new IllegalArgumentException("Duplicate player ids in seat order");
        }

        if (cardsPerHand < 3) {
            throw new IllegalArgumentException("cardsPerHand must be at least 3");
        }
        int requiredCards = playerIdsInSeatOrder.size() * cardsPerHand;
        if (deck.remainingCards() < requiredCards) {
            throw new IllegalStateException(
                    "Insufficient cards in deck: need " + requiredCards + ", have " + deck.remainingCards());
        }
    }

    private void validateHand(String playerId, List<Card> hand, int cardsPerHand) {
        if (hand == null || hand.size() != cardsPerHand) {
            throw new IllegalStateException(
                    "Player " + playerId + " must receive exactly " + cardsPerHand + " cards");
        }
    }
}
