package com.teenpatti.platform.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wallet balance response DTO for GET /api/wallet/balance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceResponse {
    private String userId;
    private long balancePaise;
    private String currency;
}
