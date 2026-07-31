package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandEvaluator;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.table.GameVariant;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Joker variant: one round-scoped rank is wild.
 * Rank selection/reveal is controlled by GameEngineService and injected via round context.
 */
@Component
public class JokerVariantStrategy implements GameVariantStrategy {

    private final Rank jokerRank;

    public JokerVariantStrategy() {
        this(null);
    }

    private JokerVariantStrategy(Rank jokerRank) {
        this.jokerRank = jokerRank;
    }

    @Override
    public GameVariant getVariant() {
        return GameVariant.JOKER;
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
        if (jokerRank == null) {
            // Safe fallback: if context is absent, preserve gameplay with classic semantics.
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
        if (context == null) return this;
        Object raw = context.get("jokerRank");
        if (raw == null) return this;
        try {
            Rank rank = Rank.valueOf(String.valueOf(raw).trim().toUpperCase());
            return new JokerVariantStrategy(rank);
        } catch (IllegalArgumentException ignored) {
            return this;
        }
    }
}

