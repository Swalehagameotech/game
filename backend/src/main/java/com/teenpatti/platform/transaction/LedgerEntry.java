package com.teenpatti.platform.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * LedgerEntry document storing financial transaction audit logs.
 *
 * CRITICAL ARCHITECTURAL REQUIREMENT:
 * This collection is APPEND-ONLY by design. No update or delete operations
 * should ever be executed against ledger_entries in any service layer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ledger_entries")
@CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}")
public class LedgerEntry {

    @Id
    private String id;

    @Indexed
    private String userId;

    private LedgerEntryType type;

    /**
     * Transaction amount stored in paise (long, signed: positive for credit, negative for debit).
     */
    private long amountPaise;

    /**
     * Resulting wallet balance in paise after transaction application.
     */
    private long balanceAfterPaise;

    /**
     * Reference ID linking to matchId, payment gateway txn ID, or admin action ID.
     */
    @Indexed(unique = true, name = "uniq_reference_id")
    private String referenceId;

    @Builder.Default
    private LedgerStatus status = LedgerStatus.COMPLETED;

    @CreatedDate
    private Instant createdAt;
}
