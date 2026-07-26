package com.teenpatti.platform.transaction.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayOrder {
    private String orderId;
    private long amountPaise;
    private String currency;
    private String keyId;
}
