package com.teenpatti.platform.bot;

import com.teenpatti.platform.game.betting.BettingState;
import com.teenpatti.platform.game.engine.Card;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Strong Teen Patti bots — stay in pots, raise pressure, rarely snap-pack.
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
            // Blind: assume playable — do not invent "weak" and snap-fold
            strength = ThreadLocalRandom.current().nextDouble() < 0.20
                    ? HandStrength.STRONG : HandStrength.MEDIUM;
        }

        boolean seeFirst = false;
        if (blind && allowed.contains("SEE_CARDS")
                && bettingRoundHint >= profile.getPreferSeeAfterRounds()
                && ThreadLocalRandom.current().nextDouble() < seeProbability(profile, strength)) {
            seeFirst = true;
        }

        String action = pickBettingAction(profile, state, allowed, strength, blind, bettingRoundHint);
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
            case VERY_STRONG -> 0.96;
            case STRONG -> 0.88;
            case MEDIUM -> 0.62;
            case WEAK -> 0.35;
            case VERY_WEAK -> 0.18;
        };
        if (profile.getPersonality() == BotPersonality.AGGRESSIVE
                || profile.getPersonality() == BotPersonality.RISKY
                || profile.getPersonality() == BotPersonality.PROFESSIONAL) {
            acceptP += 0.08;
        }
        boolean accept = ThreadLocalRandom.current().nextDouble() < acceptP && allowed.contains("SHOW_ACCEPT");
        String action = accept ? "SHOW_ACCEPT" : (allowed.contains("SHOW_REJECT") ? "SHOW_REJECT" : "SHOW_ACCEPT");
        return BotDecision.builder().actionType(action).reason("show-response").build();
    }

    private BotDecision decideSideShowResponse(BotProfile profile, List<String> allowed, List<Card> cards) {
        HandStrength s = (cards != null && cards.size() == 3)
                ? handStrengthEvaluator.evaluate(cards) : HandStrength.MEDIUM;
        double acceptP = switch (s) {
            case VERY_STRONG, STRONG -> 0.82;
            case MEDIUM -> 0.55;
            case WEAK, VERY_WEAK -> 0.22;
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
                && (s == HandStrength.VERY_STRONG || s == HandStrength.STRONG || s == HandStrength.MEDIUM
                || ThreadLocalRandom.current().nextDouble() < 0.45);
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
            case PROFESSIONAL, DEFENSIVE -> 0.55;
            case BEGINNER -> 0.45;
            case AGGRESSIVE, RISKY, BLUFFER -> 0.25;
            case BALANCED -> 0.40;
        };
        return Math.min(0.70, base);
    }

    private String pickBettingAction(
            BotProfile profile,
            BettingState state,
            List<String> allowed,
            HandStrength strength,
            boolean blind,
            int bettingRoundHint) {

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        BotPersonality p = profile.getPersonality();

        // Strong bots bluff more often instead of packing weak hands
        boolean bluffing = (strength == HandStrength.WEAK || strength == HandStrength.VERY_WEAK)
                && rng.nextDouble() < Math.min(0.45, p.bluffChance() * 1.8);

        double raiseW;
        double callW;
        double foldW;
        double showW = 0.0;
        double sideW = 0.0;

        if (bluffing) {
            raiseW = 0.70 * p.raiseBias();
            callW = 0.28;
            foldW = 0.02;
        } else {
            switch (strength) {
                case VERY_STRONG -> {
                    raiseW = 0.78 * p.raiseBias();
                    callW = 0.18;
                    foldW = 0.005;
                    showW = 0.18;
                }
                case STRONG -> {
                    raiseW = 0.62 * p.raiseBias();
                    callW = 0.32;
                    foldW = 0.015;
                    showW = 0.16;
                }
                case MEDIUM -> {
                    raiseW = 0.42 * p.raiseBias();
                    callW = 0.55;
                    foldW = 0.04 * p.foldBias() * p.caution();
                }
                case WEAK -> {
                    raiseW = 0.28 * p.raiseBias();
                    callW = 0.62;
                    foldW = 0.12 * p.foldBias() * p.caution();
                }
                case VERY_WEAK -> {
                    raiseW = 0.18 * p.raiseBias();
                    callW = 0.58;
                    foldW = 0.22 * p.foldBias() * p.caution();
                }
                default -> {
                    raiseW = 0.40;
                    callW = 0.55;
                    foldW = 0.05;
                }
            }
        }

        // Early rounds: almost never pack — stay glued to the pot
        if (bettingRoundHint <= 2) {
            foldW *= 0.15;
            callW += 0.20;
            raiseW += 0.08;
        } else if (bettingRoundHint <= 4) {
            foldW *= 0.45;
        }

        // Blind play: packing without seeing looks weak — suppress hard
        if (blind) {
            foldW *= 0.08;
            callW += 0.25;
        }

        if (allowed.contains("SIDE_SHOW_REQUEST") && !blind
                && (strength == HandStrength.STRONG || strength == HandStrength.VERY_STRONG
                || strength == HandStrength.MEDIUM
                || rng.nextDouble() < 0.18)) {
            sideW = 0.22;
        }
        if (allowed.contains("SHOW") && (strength == HandStrength.VERY_STRONG
                || strength == HandStrength.STRONG
                || strength == HandStrength.MEDIUM)) {
            showW = Math.max(showW, 0.20);
        }

        List<Weighted> options = new ArrayList<>();
        if (allowed.contains("RAISE") && raiseW > 0) {
            options.add(new Weighted("RAISE", raiseW));
        }
        if (blind && (allowed.contains("BLIND") || allowed.contains("PLAY_BLIND"))) {
            String blindAct = allowed.contains("BLIND") ? "BLIND" : "PLAY_BLIND";
            options.add(new Weighted(blindAct, callW));
        } else {
            if (allowed.contains("CHAAL")) options.add(new Weighted("CHAAL", callW));
            if (allowed.contains("CALL")) options.add(new Weighted("CALL", callW));
        }
        // Cap pack so it never dominates (~ max ~12% of weight pool in practice)
        if (allowed.contains("PACK") && foldW > 0.001) {
            options.add(new Weighted("PACK", Math.min(foldW, 0.12)));
        }
        if (allowed.contains("SHOW") && showW > 0) {
            options.add(new Weighted("SHOW", showW));
        }
        if (allowed.contains("SIDE_SHOW_REQUEST") && sideW > 0) {
            options.add(new Weighted("SIDE_SHOW_REQUEST", sideW));
        }

        if (options.isEmpty()) {
            return preferredStayAction(allowed);
        }

        return pickWeighted(options, rng);
    }

    /** Prefer staying in over packing when weights collapse. */
    private String preferredStayAction(List<String> allowed) {
        for (String prefer : List.of("CHAAL", "CALL", "BLIND", "PLAY_BLIND", "RAISE", "SHOW")) {
            if (allowed.contains(prefer)) return prefer;
        }
        for (String a : allowed) {
            if (!"SEE_CARDS".equals(a) && !"PACK".equals(a)) return a;
        }
        return allowed.contains("PACK") ? "PACK" : allowed.get(0);
    }

    private long pickRaiseAmount(BotProfile profile, HandStrength strength, List<Long> options) {
        if (options.size() == 1) return options.get(0);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double highBias = profile.getPersonality().raiseBias()
                * (strength == HandStrength.VERY_STRONG || strength == HandStrength.STRONG ? 1.35 : 0.95);
        if (rng.nextDouble() < Math.min(0.85, highBias)) {
            return options.get(options.size() - 1);
        }
        if (rng.nextDouble() < 0.55) {
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
