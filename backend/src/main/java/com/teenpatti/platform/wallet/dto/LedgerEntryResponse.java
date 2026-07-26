package com.teenpatti.platform.wallet.dto;

import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.transaction.LedgerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User-facing ledger transaction record response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryResponse {
    private String id;
    private LedgerEntryType type;
    private long amountPaise;
    private long balanceAfterPaise;
    private String referenceId;
    private LedgerStatus status;
    private Instant createdAt;
}
