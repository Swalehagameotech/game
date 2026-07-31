package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandEvaluator;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.table.GameVariant;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Best of Four: deal 4 cards, evaluate strongest 3-card combination automatically.
 */
@Component
public class BestOfFourVariantStrategy implements GameVariantStrategy {

    @Override
    public GameVariant getVariant() {
        return GameVariant.BEST_OF_FOUR;
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
    public HandResult evaluateHand(List<Card> cards) {
        if (cards == null || cards.size() < 3) {
            throw new IllegalArgumentException("BestOfFour needs at least 3 cards");
        }
        if (cards.size() == 3) {
            return HandEvaluator.evaluateHand(cards);
        }
        HandResult best = null;
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                for (int k = j + 1; k < cards.size(); k++) {
                    HandResult cur = HandEvaluator.evaluateHand(List.of(cards.get(i), cards.get(j), cards.get(k)));
                    if (best == null || HandEvaluator.compareHands(cur, best) > 0) {
                        best = cur;
                    }
                }
            }
        }
        return new HandResult(best.getCategory(), best.getTiebreakers(), cards);
    }

    @Override
    public int compareHands(HandResult handA, HandResult handB) {
        return HandEvaluator.compareHands(handA, handB);
    }
}

