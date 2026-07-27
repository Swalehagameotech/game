package com.teenpatti.platform.game.shuffle;

import com.teenpatti.platform.game.engine.Deck;
import com.teenpatti.platform.game.engine.DeckConstants;
import com.teenpatti.platform.table.GameVariant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Production card shuffle service: fresh 52-card standard deck (no jokers),
 * Fisher-Yates shuffle via {@link SecureRandom}, integrity validation before use.
 */
@Slf4j
@Service
public class CardShuffleService {

    private final SecureRandom secureRandom;

    public CardShuffleService() {
        this(new SecureRandom());
    }

    CardShuffleService(SecureRandom secureRandom) {
        this.secureRandom = secureRandom != null ? secureRandom : new SecureRandom();
    }

    /**
     * Creates a new shuffled deck for Classic Teen Patti (52 cards, no jokers).
     */
    public ShuffledDeck createShuffledDeck() {
        return createShuffledDeck(GameVariant.CLASSIC);
    }

    public ShuffledDeck createShuffledDeck(GameVariant variant) {
        GameVariant resolved = variant != null ? variant : GameVariant.CLASSIC;
        if (resolved != GameVariant.CLASSIC
                && resolved != GameVariant.HIGHER
                && resolved != GameVariant.MEDIUM
                && resolved != GameVariant.LOWER) {
            throw new UnsupportedOperationException(
                    "Shuffle for variant " + resolved + " is not implemented yet. Only CLASSIC is supported.");
        }

        Deck deck = new Deck(secureRandom);
        deck.shuffle();
        deck.assertIntegrity();

        String shuffleId = UUID.randomUUID().toString();
        Instant shuffledAt = Instant.now();

        log.debug("Created shuffled deck [{}] with {} cards (variant={})", shuffleId, deck.remainingCards(), resolved);

        return new ShuffledDeck(deck, shuffleId, shuffledAt);
    }
}
