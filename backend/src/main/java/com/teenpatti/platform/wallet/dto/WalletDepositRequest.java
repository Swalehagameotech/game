package com.teenpatti.platform.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User-initiated deposit request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDepositRequest {

    @NotNull
    @Min(1)
    private Long amountPaise;

    /**
     * When true, credits wallet immediately (demo/dev). When false, initiates payment gateway order.
     */
    @Builder.Default
    private boolean demo = true;
}
