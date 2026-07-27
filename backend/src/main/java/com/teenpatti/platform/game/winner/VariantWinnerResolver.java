package com.teenpatti.platform.game.winner;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.game.variant.GameVariantStrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Variant-aware winner resolution using {@link GameVariantStrategy} hand evaluation.
 */
public class VariantWinnerResolver implements WinnerResolver {

    private final GameVariantStrategy variantStrategy;

    public VariantWinnerResolver(GameVariantStrategy variantStrategy) {
        if (variantStrategy == null) {
            throw new IllegalArgumentException("variantStrategy must not be null");
        }
        this.variantStrategy = variantStrategy;
    }

    @Override
    public HandOutcome resolveFoldWin(String winnerId, long potPaise, GameEngineConfig config) {
        long rake = calculateRake(potPaise, config);
        long payout = potPaise - rake;
        return new HandOutcome(
                winnerId,
                potPaise,
                rake,
                payout,
                null,
                Map.of(),
                "Winner by fold (all other players packed)"
        );
    }

    @Override
    public HandOutcome resolveShowdown(
            String playerOneId,
            List<Card> playerOneHand,
            String playerTwoId,
            List<Card> playerTwoHand,
            long potPaise,
            GameEngineConfig config) {

        HandResult result1 = variantStrategy.evaluateHand(playerOneHand);
        HandResult result2 = variantStrategy.evaluateHand(playerTwoHand);

        int cmp = variantStrategy.compareHands(result1, result2);
        String winnerId = cmp >= 0 ? playerOneId : playerTwoId;
        HandResult winningResult = cmp >= 0 ? result1 : result2;

        long rake = calculateRake(potPaise, config);
        long payout = potPaise - rake;

        Map<String, List<Card>> revealedMap = new HashMap<>();
        revealedMap.put(playerOneId, List.copyOf(playerOneHand));
        revealedMap.put(playerTwoId, List.copyOf(playerTwoHand));

        return new HandOutcome(
                winnerId,
                potPaise,
                rake,
                payout,
                winningResult.getCategory(),
                revealedMap,
                "Winner by showdown (" + winningResult.getCategory().getDescription() + ")"
        );
    }

    private long calculateRake(long potAmount, GameEngineConfig config) {
        double rakeDouble = potAmount * (config.getRakePercentage() / 100.0);
        return Math.round(rakeDouble);
    }
}
