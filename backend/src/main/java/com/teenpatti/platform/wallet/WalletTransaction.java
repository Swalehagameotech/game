package com.teenpatti.platform.wallet;

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
 * Wallet Transaction document maintaining full immutable wallet history entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wallet_transactions")
public class WalletTransaction {

    @Id
    private String id;

    @Indexed
    private String userId;

    private long amount;

    private String transactionType; // Credit or Debit

    private String reason;

    private long balanceAfterTransaction;

    @CreatedDate
    private Instant createdAt;
}
