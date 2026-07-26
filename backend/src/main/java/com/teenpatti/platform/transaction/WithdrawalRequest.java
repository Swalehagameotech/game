package com.teenpatti.platform.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Audit record of user withdrawal requests.
 * NOTE: Bank details (accountNumber, ifscCode, accountHolderName) are stored directly on this document
 * and should be flagged for future field-level encryption-at-rest before production deployment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "withdrawal_requests")
public class WithdrawalRequest {

    @Id
    private String id;

    @Indexed
    private String userId;

    private long amountPaise;

    // TODO: Field-level encryption at rest required prior to production
    private String accountNumber;

    // TODO: Field-level encryption at rest required prior to production
    private String ifscCode;

    // TODO: Field-level encryption at rest required prior to production
    private String accountHolderName;

    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.PENDING_ADMIN_REVIEW;

    @CreatedDate
    private Instant createdAt;

    private Instant reviewedAt;

    private String reviewedByAdminId;

    private String rejectionReason;
}
