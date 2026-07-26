package com.teenpatti.platform.admin.dto;

import com.teenpatti.platform.transaction.WithdrawalStatus;
import com.teenpatti.platform.user.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminWithdrawalResponse {
    private String id;
    private String userId;
    private String userDisplayName;
    private String userEmail;
    private KycStatus userKycStatus;
    private long amountPaise;
    private Map<String, String> bankAccountDetails;
    private WithdrawalStatus status;
    private String rejectionReason;
    private String reviewedByAdminId;
    private Instant requestedAt;
    private Instant reviewedAt;
}
