package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandEvaluator;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.table.GameVariant;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Discard One: deal 4 cards; each player discards one before betting. Classic hand evaluation on final 3.
 */
@Component
public class DiscardOneVariantStrategy implements GameVariantStrategy {

    @Override
    public GameVariant getVariant() {
        return GameVariant.DISCARD_ONE;
    }

    @Override
    public boolean isFullyImplemented() {
        return true;
    }

    @Override
    public GameEngineConfig buildEngineConfig(long bootAmountPaise) {
        return GameEngineConfig.defaultConfig(bootAmountPaise, bootAmountPaise * 50L);
    }

    @Override
    public int cardsPerHand() {
        return 4;
    }

    @Override
    public HandResult evaluateHand(List<Card> threeCards) {
        return HandEvaluator.evaluateHand(threeCards);
    }

    @Override
    public int compareHands(HandResult handA, HandResult handB) {
        return HandEvaluator.compareHands(handA, handB);
    }
}
