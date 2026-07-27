package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandEvaluator;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.table.GameVariant;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClassicVariantStrategy implements GameVariantStrategy {

    @Override
    public GameVariant getVariant() {
        return GameVariant.CLASSIC;
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
    public HandResult evaluateHand(List<Card> threeCards) {
        return HandEvaluator.evaluateHand(threeCards);
    }

    @Override
    public int compareHands(HandResult handA, HandResult handB) {
        return HandEvaluator.compareHands(handA, handB);
    }
}
