package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandEvaluator;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.table.GameVariant;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * AK47 variant: A, K, 4, 7 act as wild jokers and can represent any card.
 * Betting/turn/show/pot flows remain fully Classic.
 */
@Component
public class Ak47VariantStrategy implements GameVariantStrategy {

    private static final Set<Rank> WILD_RANKS = Set.of(Rank.ACE, Rank.KING, Rank.FOUR, Rank.SEVEN);

    @Override
    public GameVariant getVariant() {
        return GameVariant.AK47;
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
        return WildcardVariantSupport.evaluateWithWildcards(threeCards, c -> WILD_RANKS.contains(c.getRank()));
    }

    @Override
    public int compareHands(HandResult handA, HandResult handB) {
        return HandEvaluator.compareHands(handA, handB);
    }
}

