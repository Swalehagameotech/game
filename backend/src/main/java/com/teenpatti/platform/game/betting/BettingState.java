package com.teenpatti.platform.game.betting;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Server-authoritative betting snapshot for a single player view.
 * Frontend must use these values — never compute stakes locally.
 */
@Value
@Builder
public class BettingState {

    String tableId;
    String userId;
    String playerState; // BLIND | SEEN | PACKED
    long potPaise;
    long currentBaseStakePaise;
    long blindAmountPaise;
    long chaalAmountPaise;
    long showCostPaise;
    long sideShowCostPaise;
    long requiredBetPaise;
    long minRaiseBetPaise;
    long maxBetPaise;
    List<Long> raiseOptionsPaise;
    long playerContributedPaise;
    long walletBalancePaise;
    int blindSeenRatio;
    int turnTimerSeconds;
    boolean myTurn;
    List<String> allowedActions;
    /** Pre-betting phase label: DISCARD | AUCTION | null */
    String variantPhase;
    long auctionHighBidPaise;
    String auctionHighBidderId;
    long auctionMinBidPaise;
}
