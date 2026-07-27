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
    long potPaise;
    long currentBaseStakePaise;
    long requiredBetPaise;
    long minRaiseBetPaise;
    long maxBetPaise;
    long playerContributedPaise;
    int blindSeenRatio;
    boolean myTurn;
    List<String> allowedActions;
}
