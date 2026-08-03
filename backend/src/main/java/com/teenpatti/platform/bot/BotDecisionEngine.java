package com.teenpatti.platform.bot;

import com.teenpatti.platform.game.betting.BettingState;
import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.PlayerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Probability-based Teen Patti bot decisions using only information a human would have.
 */
@Component
@RequiredArgsConstructor
public class BotDecisionEngine {

    private final HandStrengthEvaluator handStrengthEvaluator;

    public BotDecision decide(BotProfile profile, BettingState state, List<Card> ownCards, int bettingRoundHint) {
        if (state == null || profile == null) {
            return BotDecision.builder().actionType("PACK").reason("missing-state").build();
        }
        List<String> allowed = state.getAllowedActions() != null
                ? new ArrayList<>(state.getAllowedActions())
                : new ArrayList<>();
        if (allowed.isEmpty()) {
            return BotDecision.builder().actionType("PACK").reason("no-actions").build();
        }

        // Side-show / show response — never cheat; randomize accept/reject with personality.
        if (allowed.contains("SHOW_ACCEPT") || allowed.contains("SHOW_REJECT")) {
            return decideShowResponse(profile, allowed, ownCards);
        }
        if (allowed.contains("SIDE_SHOW_ACCEPT") || allowed.contains("SIDE_SHOW_REJECT")) {
            return decideSideShowResponse(profile, allowed, ownCards);
        }
        if (allowed.contains("DISCARD_CARD")) {
            return BotDecision.builder().actionType("DISCARD_CARD").amountPaise(0)
                    .reason("discard-random").build();
        }
        if (allowed.contains("AUCTION_BID") || allowed.contains("AUCTION_PASS")) {
            return decideAuction(profile, state, allowed, ownCards);
        }

        boolean blind = "BLIND".equalsIgnoreCase(state.getPlayerState());
        HandStrength strength = HandStrength.MEDIUM;
        if (!blind && ownCards != null && ownCards.size() == 3) {
            strength = handStrengthEvaluator.evaluate(ownCards);
        } else if (blind) {
            // Blind: unknown cards — treat as medium with personality noise
            strength = ThreadLocalRandom.current().nextDouble() < 0.35
                    ? HandStrength.WEAK : HandStrength.MEDIUM;
        }

        boolean seeFirst = false;
        if (blind && allowed.contains("SEE_CARDS")
                && bettingRoundHint >= profile.getPreferSeeAfterRounds()
                && ThreadLocalRandom.current().nextDouble() < seeProbability(profile, strength)) {
            seeFirst = true;
        }

        String action = pickBettingAction(profile, state, allowed, strength, blind);
        long amount = 0L;
        if ("RAISE".equals(action) && state.getRaiseOptionsPaise() != null && !state.getRaiseOptionsPaise().isEmpty()) {
            amount = pickRaiseAmount(profile, strength, state.getRaiseOptionsPaise());
        }

        return BotDecision.builder()
                .actionType(action)
                .amountPaise(amount)
                .seeCardsFirst(seeFirst)
                .reason(strength.name() + "/" + profile.getPersonality().name())
                .build();
    }

    private BotDecision decideShowResponse(BotProfile profile, List<String> allowed, List<Card> cards) {
        HandStrength s = (cards != null && cards.size() == 3)
                ? handStrengthEvaluator.evaluate(cards) : HandStrength.MEDIUM;
        double acceptP = switch (s) {
            case VERY_STRONG -> 0.92;
            case STRONG -> 0.75;
            case MEDIUM -> 0.45;
            case WEAK -> 0.22;
            case VERY_WEAK -> 0.10;
        };
        if (profile.getPersonality() == BotPersonality.AGGRESSIVE
                || profile.getPersonality() == BotPersonality.RISKY) {
            acceptP += 0.10;
        }
        boolean accept = ThreadLocalRandom.current().nextDouble() < acceptP && allowed.contains("SHOW_ACCEPT");
        String action = accept ? "SHOW_ACCEPT" : (allowed.contains("SHOW_REJECT") ? "SHOW_REJECT" : "SHOW_ACCEPT");
        return BotDecision.builder().actionType(action).reason("show-response").build();
    }

    private BotDecision decideSideShowResponse(BotProfile profile, List<String> allowed, List<Card> cards) {
        HandStrength s = (cards != null && cards.size() == 3)
                ? handStrengthEvaluator.evaluate(cards) : HandStrength.MEDIUM;
        double acceptP = switch (s) {
            case VERY_STRONG, STRONG -> 0.70;
            case MEDIUM -> 0.40;
            case WEAK, VERY_WEAK -> 0.18;
        };
        boolean accept = ThreadLocalRandom.current().nextDouble() < acceptP && allowed.contains("SIDE_SHOW_ACCEPT");
        String action = accept ? "SIDE_SHOW_ACCEPT"
                : (allowed.contains("SIDE_SHOW_REJECT") ? "SIDE_SHOW_REJECT" : "SIDE_SHOW_ACCEPT");
        return BotDecision.builder().actionType(action).reason("sideshow-response").build();
    }

    private BotDecision decideAuction(BotProfile profile, BettingState state, List<String> allowed, List<Card> cards) {
        HandStrength s = (cards != null && cards.size() == 3)
                ? handStrengthEvaluator.evaluate(cards) : HandStrength.MEDIUM;
        boolean bid = allowed.contains("AUCTION_BID")
                && (s == HandStrength.VERY_STRONG || s == HandStrength.STRONG
                || ThreadLocalRandom.current().nextDouble() < 0.35);
        if (bid) {
            long min = Math.max(state.getAuctionMinBidPaise(), 1L);
            return BotDecision.builder().actionType("AUCTION_BID").amountPaise(min).reason("auction-bid").build();
        }
        return BotDecision.builder()
                .actionType(allowed.contains("AUCTION_PASS") ? "AUCTION_PASS" : "AUCTION_BID")
                .reason("auction-pass").build();
    }

    private double seeProbability(BotProfile profile, HandStrength guessed) {
        double base = switch (profile.getPersonality()) {
            case PROFESSIONAL, DEFENSIVE -> 0.70;
            case BEGINNER -> 0.55;
            case AGGRESSIVE, RISKY, BLUFFER -> 0.35;
            case BALANCED -> 0.50;
        };
        if (guessed == HandStrength.WEAK || guessed == HandStrength.VERY_WEAK) {
            base += 0.15;
        }
        return Math.min(0.90, base);
    }

    private String pickBettingAction(
            BotProfile profile,
            BettingState state,
            List<String> allowed,
            HandStrength strength,
            boolean blind) {

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        BotPersonality p = profile.getPersonality();

        // Occasional bluff with weak hands
        boolean bluffing = (strength == HandStrength.WEAK || strength == HandStrength.VERY_WEAK)
                && rng.nextDouble() < Math.min(0.15, p.bluffChance());

        double raiseW;
        double callW;
        double foldW;
        double showW = 0.0;
        double sideW = 0.0;

        if (bluffing) {
            raiseW = 0.55 * p.raiseBias();
            callW = 0.30;
            foldW = 0.15 * p.foldBias() * p.caution();
        } else {
            switch (strength) {
                case VERY_STRONG -> {
                    raiseW = 0.70 * p.raiseBias();
                    callW = 0.20;
                    foldW = 0.02;
                    showW = 0.10;
                }
                case STRONG -> {
                    raiseW = 0.50 * p.raiseBias();
                    callW = 0.35;
                    foldW = 0.08 * p.foldBias();
                    showW = 0.12;
                }
                case MEDIUM -> {
                    raiseW = 0.30 * p.raiseBias();
                    callW = 0.50;
                    foldW = 0.20 * p.foldBias() * p.caution();
                }
                case WEAK -> {
                    raiseW = 0.10 * p.raiseBias();
                    callW = 0.30;
                    foldW = 0.60 * p.foldBias() * p.caution();
                }
                case VERY_WEAK -> {
                    raiseW = 0.05 * p.raiseBias();
                    callW = 0.25;
                    foldW = 0.70 * p.foldBias() * p.caution();
                }
                default -> {
                    raiseW = 0.25;
                    callW = 0.45;
                    foldW = 0.30;
                }
            }
        }

        if (allowed.contains("SIDE_SHOW_REQUEST") && !blind
                && (strength == HandStrength.STRONG || strength == HandStrength.VERY_STRONG
                || rng.nextDouble() < 0.12)) {
            sideW = 0.18;
        }
        if (allowed.contains("SHOW") && (strength == HandStrength.VERY_STRONG || strength == HandStrength.STRONG)) {
            showW = Math.max(showW, 0.15);
        }

        // Map weights onto available actions
        List<Weighted> options = new ArrayList<>();
        if (allowed.contains("RAISE") && raiseW > 0) {
            options.add(new Weighted("RAISE", raiseW));
        }
        // Blind play vs chaal/call
        if (blind && (allowed.contains("BLIND") || allowed.contains("PLAY_BLIND"))) {
            String blindAct = allowed.contains("BLIND") ? "BLIND" : "PLAY_BLIND";
            options.add(new Weighted(blindAct, callW));
        } else {
            if (allowed.contains("CHAAL")) options.add(new Weighted("CHAAL", callW));
            if (allowed.contains("CALL")) options.add(new Weighted("CALL", callW));
        }
        if (allowed.contains("PACK") && foldW > 0) {
            options.add(new Weighted("PACK", foldW));
        }
        if (allowed.contains("SHOW") && showW > 0) {
            options.add(new Weighted("SHOW", showW));
        }
        if (allowed.contains("SIDE_SHOW_REQUEST") && sideW > 0) {
            options.add(new Weighted("SIDE_SHOW_REQUEST", sideW));
        }

        if (options.isEmpty()) {
            // Fallback: first non-see action, else pack
            for (String a : allowed) {
                if (!"SEE_CARDS".equals(a)) return a;
            }
            return "PACK";
        }

        return pickWeighted(options, rng);
    }

    private long pickRaiseAmount(BotProfile profile, HandStrength strength, List<Long> options) {
        if (options.size() == 1) return options.get(0);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        // Aggressive / strong → prefer higher raises
        double highBias = profile.getPersonality().raiseBias()
                * (strength == HandStrength.VERY_STRONG || strength == HandStrength.STRONG ? 1.2 : 0.7);
        if (rng.nextDouble() < highBias) {
            return options.get(options.size() - 1);
        }
        if (rng.nextDouble() < 0.5) {
            return options.get(options.size() / 2);
        }
        return options.get(0);
    }

    private String pickWeighted(List<Weighted> options, ThreadLocalRandom rng) {
        double total = 0;
        for (Weighted w : options) total += w.weight;
        double roll = rng.nextDouble() * total;
        double acc = 0;
        for (Weighted w : options) {
            acc += w.weight;
            if (roll <= acc) return w.action;
        }
        return options.get(options.size() - 1).action;
    }

    private record Weighted(String action, double weight) {}
}
