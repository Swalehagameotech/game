package com.teenpatti.platform.transaction.dto;

import com.teenpatti.platform.transaction.DepositStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositResponse {
    private String id;
    private String userId;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private long amountPaise;
    private DepositStatus status;
    private Instant createdAt;
    private Instant completedAt;
}
