package com.teenpatti.platform.wallet.dto;

import com.teenpatti.platform.transaction.dto.DepositInitiationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a deposit attempt — immediate credit or gateway order details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDepositResponse {
    private boolean demoCredited;
    private long amountPaise;
    private long balancePaise;
    private String formattedBalance;
    private DepositInitiationResponse gatewayOrder;
}
