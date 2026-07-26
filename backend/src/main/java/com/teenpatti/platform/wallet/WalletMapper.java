package com.teenpatti.platform.wallet;

import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.wallet.dto.LedgerEntryResponse;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;

/**
 * Mapper utility for Wallet and LedgerEntry transformations.
 */
public class WalletMapper {

    public static WalletBalanceResponse toBalanceResponse(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        return WalletBalanceResponse.builder()
                .userId(wallet.getUserId())
                .balancePaise(wallet.getBalancePaise())
                .currency(wallet.getCurrency())
                .build();
    }

    public static LedgerEntryResponse toLedgerEntryResponse(LedgerEntry entry) {
        if (entry == null) {
            return null;
        }
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .type(entry.getType())
                .amountPaise(entry.getAmountPaise())
                .balanceAfterPaise(entry.getBalanceAfterPaise())
                .referenceId(entry.getReferenceId())
                .status(entry.getStatus())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
