package com.teenpatti.platform.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositInitiationResponse {
    private String depositRequestId;
    private String gatewayOrderId;
    private long amountPaise;
    private String currency;
    private String keyId;
}
