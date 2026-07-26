package com.teenpatti.platform.transaction.dto;

import com.teenpatti.platform.transaction.WithdrawalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalResponse {
    private String id;
    private String userId;
    private long amountPaise;
    private String accountNumber;
    private String ifscCode;
    private String accountHolderName;
    private WithdrawalStatus status;
    private Instant createdAt;
    private Instant reviewedAt;
    private String reviewedByAdminId;
}
