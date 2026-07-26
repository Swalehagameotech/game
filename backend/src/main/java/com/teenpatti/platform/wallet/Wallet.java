package com.teenpatti.platform.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Wallet document managing user chip/money balance in paise with optimistic locking.
 * Enforces exactly one wallet document per user via unique index on userId.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wallets")
public class Wallet {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    /**
     * Account balance stored as long (in paise, e.g., ₹100.00 = 10000 paise).
     * NEVER use double, float, or BigDecimal for monetary values.
     */
    private long balancePaise;

    /**
     * Version field for Spring Data optimistic locking during concurrent balance updates.
     */
    @Version
    private Long version;

    @Builder.Default
    private String currency = "INR";

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
