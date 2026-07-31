package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandEvaluator;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.table.GameVariant;
import com.teenpatti.platform.table.Table;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Auction: players bid before betting; highest bidder wins the round joker (wild rank revealed after auction).
 */
@Component
public class AuctionVariantStrategy implements GameVariantStrategy {

    private final Rank jokerRank;

    public AuctionVariantStrategy() {
        this(null);
    }

    private AuctionVariantStrategy(Rank jokerRank) {
        this.jokerRank = jokerRank;
    }

    @Override
    public GameVariant getVariant() {
        return GameVariant.AUCTION;
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
    public void initializeRound(Table table) {
        table.setJokerRank(null);
        table.setAuctionWinner(null);
    }

    @Override
    public HandResult evaluateHand(List<Card> threeCards) {
        if (jokerRank == null) {
            return HandEvaluator.evaluateHand(threeCards);
        }
        return WildcardVariantSupport.evaluateWithWildcards(threeCards, c -> c != null && c.getRank() == jokerRank);
    }

    @Override
    public int compareHands(HandResult handA, HandResult handB) {
        return HandEvaluator.compareHands(handA, handB);
    }

    @Override
    public Map<String, Object> specialRules() {
        return jokerRank == null ? Map.of() : Map.of("jokerRank", jokerRank.name());
    }

    @Override
    public GameVariantStrategy withRoundContext(Map<String, Object> context) {
        if (context == null) {
            return this;
        }
        Object raw = context.get("jokerRank");
        if (raw == null) {
            return this;
        }
        try {
            Rank rank = Rank.valueOf(String.valueOf(raw).trim().toUpperCase());
            return new AuctionVariantStrategy(rank);
        } catch (IllegalArgumentException ignored) {
            return this;
        }
    }
}
