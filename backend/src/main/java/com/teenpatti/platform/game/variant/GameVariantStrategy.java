package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.GameVariant;

import java.util.List;
import java.util.Map;

/**
 * Pluggable rules for a Teen Patti variant. Only {@link GameVariant#CLASSIC} is fully implemented;
 * other variants register here for future modules without changing the core engine facade.
 */
public interface GameVariantStrategy {

    GameVariant getVariant();

    boolean isFullyImplemented();

    GameEngineConfig buildEngineConfig(long bootAmountPaise);

    /**
     * Cards to deal per player for this variant.
     * Classic remains 3.
     */
    default int cardsPerHand() {
        return 3;
    }

    /**
     * Hook called before round start when variant needs to seed round metadata.
     */
    default void initializeRound(Table table) {
        // no-op by default
    }

    /**
     * Hook called after dealing to apply custom variant setup/broadcast behavior.
     */
    default void dealCards(Table table, Map<String, List<Card>> handsByPlayerId) {
        // no-op by default
    }

    /**
     * Hook called before betting starts.
     */
    default void beforeBetting(Table table) {
        // no-op by default
    }

    HandResult evaluateHand(List<Card> threeCards);

    /**
     * @return positive if handA wins, negative if handB wins, zero if tie
     */
    int compareHands(HandResult handA, HandResult handB);

    /**
     * Hook called after round settles.
     */
    default void afterRound(Table table) {
        // no-op by default
    }

    /**
     * Hook exposing ad-hoc metadata from the variant (joker rank, banker etc).
     */
    default Map<String, Object> specialRules() {
        return Map.of();
    }

    /**
     * Returns strategy instance with table/round context injected.
     * Stateless strategies return {@code this}.
     */
    default GameVariantStrategy withRoundContext(Map<String, Object> context) {
        return this;
    }
}
