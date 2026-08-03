package com.teenpatti.platform.bot;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable action chosen by the decision engine.
 */
@Value
@Builder
public class BotDecision {
    String actionType;
    long amountPaise;
    boolean seeCardsFirst;
    String reason;
}
