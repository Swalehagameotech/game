package com.teenpatti.platform.game.distribution;

import com.teenpatti.platform.game.engine.Card;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of a private deal — each seated player gets exactly three cards.
 */
@Value
public class PrivateHandDeal {

    Map<String, List<Card>> handsByPlayerId;
    int totalCardsDealt;
    int remainingDeckCards;

    public Map<String, List<Card>> getHandsByPlayerId() {
        return Collections.unmodifiableMap(handsByPlayerId);
    }
}
