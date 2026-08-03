package com.teenpatti.platform.bot;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.HandEvaluator;
import com.teenpatti.platform.game.engine.HandRankCategory;
import com.teenpatti.platform.game.engine.HandResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps a bot's private 3-card hand to a coarse strength bucket.
 * Uses only the bot's own cards — never opponents' hands.
 */
@Component
public class HandStrengthEvaluator {

    public HandStrength evaluate(List<Card> cards) {
        if (cards == null || cards.size() != 3) {
            return HandStrength.VERY_WEAK;
        }
        HandResult result = HandEvaluator.evaluateHand(cards);
        HandRankCategory cat = result.getCategory();
        return switch (cat) {
            case TRAIL, PURE_SEQUENCE -> HandStrength.VERY_STRONG;
            case SEQUENCE, COLOR -> HandStrength.STRONG;
            case PAIR -> {
                int pairRank = result.getTiebreakers().isEmpty() ? 0 : result.getTiebreakers().get(0);
                yield pairRank >= 11 ? HandStrength.STRONG : HandStrength.MEDIUM; // J+ pairs are strong
            }
            case HIGH_CARD -> {
                int high = result.getTiebreakers().isEmpty() ? 0 : result.getTiebreakers().get(0);
                if (high >= 13) yield HandStrength.MEDIUM;      // K/A high
                if (high >= 10) yield HandStrength.WEAK;
                yield HandStrength.VERY_WEAK;
            }
        };
    }

    public double confidence(HandStrength strength) {
        return switch (strength) {
            case VERY_STRONG -> 0.95;
            case STRONG -> 0.78;
            case MEDIUM -> 0.52;
            case WEAK -> 0.28;
            case VERY_WEAK -> 0.12;
        };
    }
}
