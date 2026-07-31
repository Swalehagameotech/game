package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandEvaluator;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.game.engine.Suit;
import com.teenpatti.platform.table.GameVariant;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-Eyed Jack variant: only J♥ and J♠ are jokers.
 * Other jacks remain normal cards.
 */
@Component
public class OneEyedJackVariantStrategy implements GameVariantStrategy {

    @Override
    public GameVariant getVariant() {
        return GameVariant.ONE_EYED_JACK;
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
        return WildcardVariantSupport.evaluateWithWildcards(threeCards, this::isOneEyedJack);
    }

    @Override
    public int compareHands(HandResult handA, HandResult handB) {
        return HandEvaluator.compareHands(handA, handB);
    }

    private boolean isOneEyedJack(Card card) {
        if (card == null || card.getRank() != Rank.JACK) {
            return false;
        }
        return card.getSuit() == Suit.HEARTS || card.getSuit() == Suit.SPADES;
    }
}

