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
 * Audit record of user deposit requests via payment gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "deposit_requests")
public class DepositRequest {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String gatewayOrderId;

    private String gatewayPaymentId;

    private long amountPaise;

    @Builder.Default
    private DepositStatus status = DepositStatus.PENDING;

    @CreatedDate
    private Instant createdAt;

    private Instant completedAt;
}
