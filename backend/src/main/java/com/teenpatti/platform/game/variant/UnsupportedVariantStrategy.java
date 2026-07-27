package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.table.GameVariant;

import java.util.List;

/**
 * Placeholder for variants not yet implemented (Muflis, AK47, etc.).
 */
public class UnsupportedVariantStrategy implements GameVariantStrategy {

    private final GameVariant variant;

    public UnsupportedVariantStrategy(GameVariant variant) {
        this.variant = variant;
    }

    @Override
    public GameVariant getVariant() {
        return variant;
    }

    @Override
    public boolean isFullyImplemented() {
        return false;
    }

    @Override
    public GameEngineConfig buildEngineConfig(long bootAmountPaise) {
        throw new UnsupportedOperationException("Variant " + variant + " is not yet implemented");
    }

    @Override
    public HandResult evaluateHand(List<Card> threeCards) {
        throw new UnsupportedOperationException("Variant " + variant + " is not yet implemented");
    }

    @Override
    public int compareHands(HandResult handA, HandResult handB) {
        throw new UnsupportedOperationException("Variant " + variant + " is not yet implemented");
    }
}
