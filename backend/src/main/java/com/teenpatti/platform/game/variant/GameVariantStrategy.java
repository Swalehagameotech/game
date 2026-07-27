package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.table.GameVariant;

import java.util.List;

/**
 * Pluggable rules for a Teen Patti variant. Only {@link GameVariant#CLASSIC} is fully implemented;
 * other variants register here for future modules without changing the core engine facade.
 */
public interface GameVariantStrategy {

    GameVariant getVariant();

    boolean isFullyImplemented();

    GameEngineConfig buildEngineConfig(long bootAmountPaise);

    HandResult evaluateHand(List<Card> threeCards);

    /**
     * @return positive if handA wins, negative if handB wins, zero if tie
     */
    int compareHands(HandResult handA, HandResult handB);
}
