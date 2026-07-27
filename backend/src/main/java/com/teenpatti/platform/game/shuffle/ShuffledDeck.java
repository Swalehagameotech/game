package com.teenpatti.platform.game.shuffle;

import com.teenpatti.platform.game.engine.Deck;
import lombok.Value;

import java.time.Instant;

/**
 * Server-only result of a secure shuffle. Never sent to clients — deck order stays private.
 */
@Value
public class ShuffledDeck {

    Deck deck;
    String shuffleId;
    Instant shuffledAt;
}
