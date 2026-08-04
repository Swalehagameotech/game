package com.teenpatti.platform.bot;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.HandEvaluator;
import com.teenpatti.platform.game.engine.HandRankCategory;
import com.teenpatti.platform.game.engine.HandResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps a bot's private 3-card hand to a coarse strength bucket.
 * Tuned so bots treat more hands as playable (stronger table presence).
 */
@Component
public class HandStrengthEvaluator {

    public HandStrength evaluate(List<Card> cards) {
        if (cards == null || cards.size() != 3) {
            return HandStrength.MEDIUM;
        }
        HandResult result = HandEvaluator.evaluateHand(cards);
        HandRankCategory cat = result.getCategory();
        return switch (cat) {
            case TRAIL, PURE_SEQUENCE -> HandStrength.VERY_STRONG;
            case SEQUENCE, COLOR -> HandStrength.VERY_STRONG;
            case PAIR -> {
                int pairRank = result.getTiebreakers().isEmpty() ? 0 : result.getTiebreakers().get(0);
                // Any pair is at least medium; mid/high pairs are strong
                if (pairRank >= 10) yield HandStrength.VERY_STRONG; // T+
                if (pairRank >= 7) yield HandStrength.STRONG;
                yield HandStrength.MEDIUM;
            }
            case HIGH_CARD -> {
                int high = result.getTiebreakers().isEmpty() ? 0 : result.getTiebreakers().get(0);
                if (high >= 13) yield HandStrength.STRONG;   // K/A high — play hard
                if (high >= 11) yield HandStrength.MEDIUM;  // J/Q high
                if (high >= 8) yield HandStrength.WEAK;     // 8–10 high — still often stay
                yield HandStrength.VERY_WEAK;               // only trash high cards
            }
        };
    }

    public double confidence(HandStrength strength) {
        return switch (strength) {
            case VERY_STRONG -> 0.95;
            case STRONG -> 0.82;
            case MEDIUM -> 0.62;
            case WEAK -> 0.40;
            case VERY_WEAK -> 0.22;
        };
    }
}
