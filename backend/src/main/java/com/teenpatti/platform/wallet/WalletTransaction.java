package com.teenpatti.platform.wallet;

import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.transaction.LedgerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable wallet transaction record exposed to users and admin panels.
 * Financial source of truth for balance changes remains {@code ledger_entries};
 * this collection mirrors entries for history UI and reporting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wallet_transactions")
@CompoundIndexes({
        @CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "table_hand_idx", def = "{'tableId': 1, 'handId': 1}")
})
public class WalletTransaction {

    @Id
    private String id;

    @Indexed
    private String userId;

    /**
     * Signed amount in paise (positive credit, negative debit).
     */
    private long amountPaise;

    private LedgerEntryType type;

    /**
     * Idempotency key — must be unique across all wallet movements.
     */
    @Indexed(unique = true, sparse = true)
    private String referenceId;

    private String tableId;

    private String handId;

    @Builder.Default
    private LedgerStatus status = LedgerStatus.COMPLETED;

    private long balanceAfterPaise;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /** @deprecated Legacy field; use {@link #amountPaise}. */
    @Deprecated
    private Long amount;

    /** @deprecated Legacy field; use {@link #type}. */
    @Deprecated
    private String transactionType;

    /** @deprecated Legacy field; stored in {@link #metadata}. */
    @Deprecated
    private String reason;

    /** @deprecated Legacy field; use {@link #balanceAfterPaise}. */
    @Deprecated
    private Long balanceAfterTransaction;

    @CreatedDate
    private Instant createdAt;

    public long getEffectiveAmountPaise() {
        if (amountPaise != 0L) {
            return amountPaise;
        }
        if (amount != null) {
            long legacy = amount;
            return "Debit".equalsIgnoreCase(transactionType) ? -Math.abs(legacy) : Math.abs(legacy);
        }
        return 0L;
    }

    public long getEffectiveBalanceAfterPaise() {
        return balanceAfterPaise != 0L
                ? balanceAfterPaise
                : (balanceAfterTransaction != null ? balanceAfterTransaction : 0L);
    }
}
