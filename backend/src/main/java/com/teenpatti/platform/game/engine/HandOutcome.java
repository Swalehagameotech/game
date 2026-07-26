package com.teenpatti.platform.game.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result description of a completed Teen Patti hand.
 */
public final class HandOutcome {

    private final String winnerId;
    private final long potAmountPaise;
    private final long rakeAmountPaise;
    private final long winnerPayoutPaise;
    private final HandRankCategory winningCategory;
    private final Map<String, List<Card>> revealedHands;
    private final String notes;

    public HandOutcome(
            String winnerId,
            long potAmountPaise,
            long rakeAmountPaise,
            long winnerPayoutPaise,
            HandRankCategory winningCategory,
            Map<String, List<Card>> revealedHands,
            String notes) {
        this.winnerId = winnerId;
        this.potAmountPaise = potAmountPaise;
        this.rakeAmountPaise = rakeAmountPaise;
        this.winnerPayoutPaise = winnerPayoutPaise;
        this.winningCategory = winningCategory;
        this.revealedHands = revealedHands != null ? Collections.unmodifiableMap(revealedHands) : Collections.emptyMap();
        this.notes = notes;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public long getPotAmountPaise() {
        return potAmountPaise;
    }

    public long getRakeAmountPaise() {
        return rakeAmountPaise;
    }

    public long getWinnerPayoutPaise() {
        return winnerPayoutPaise;
    }

    public HandRankCategory getWinningCategory() {
        return winningCategory;
    }

    public Map<String, List<Card>> getRevealedHands() {
        return revealedHands;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        return "HandOutcome{" +
                "winnerId='" + winnerId + '\'' +
                ", potAmountPaise=" + potAmountPaise +
                ", rakeAmountPaise=" + rakeAmountPaise +
                ", winnerPayoutPaise=" + winnerPayoutPaise +
                ", winningCategory=" + winningCategory +
                ", notes='" + notes + '\'' +
                '}';
    }
}
