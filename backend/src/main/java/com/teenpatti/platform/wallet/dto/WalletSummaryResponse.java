package com.teenpatti.platform.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wallet overview for the wallet UI — balance and lifetime totals from ledger.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletSummaryResponse {
    private String userId;
    private long balancePaise;
    private String formattedBalance;
    private String currency;
    private long totalDepositedPaise;
    private long totalWithdrawnPaise;
    private long pendingWithdrawalPaise;
    private long minDepositPaise;
    private long maxDepositPaise;
    private long minWithdrawalPaise;
    private long maxWithdrawalPaise;
}
