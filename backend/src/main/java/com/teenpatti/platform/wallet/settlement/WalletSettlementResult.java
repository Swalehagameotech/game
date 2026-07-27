package com.teenpatti.platform.wallet.settlement;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Result of idempotent hand wallet settlement (winner credit + platform rake).
 */
@Value
@Builder
public class WalletSettlementResult {

    String tableId;
    String handId;
    String winnerUserId;
    long potPaise;
    long rakePaise;
    long payoutPaise;
    long winnerBalanceAfterPaise;
    Long houseBalanceAfterPaise;
    boolean winIdempotentReplay;
    boolean rakeIdempotentReplay;
    String winReferenceId;
    String rakeReferenceId;
    Instant settledAt;
}
