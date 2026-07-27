package com.teenpatti.platform.wallet;

import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.transaction.LedgerEntryType;
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
        long amount = entry.getAmountPaise();
        String sign = amount >= 0 ? "+" : "-";
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .type(entry.getType())
                .typeLabel(formatTypeLabel(entry.getType()))
                .amountPaise(amount)
                .formattedAmount(sign + "₹" + String.format("%.2f", Math.abs(amount) / 100.0))
                .balanceAfterPaise(entry.getBalanceAfterPaise())
                .referenceId(entry.getReferenceId())
                .status(entry.getStatus())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    private static String formatTypeLabel(LedgerEntryType type) {
        if (type == null) {
            return "Transaction";
        }
        return switch (type) {
            case DEPOSIT -> "Deposit";
            case WITHDRAWAL -> "Withdrawal";
            case BET -> "Game Bet";
            case WIN -> "Game Win";
            case RAKE -> "Platform Rake";
            case REFUND -> "Refund";
            case BONUS -> "Bonus";
            case ADMIN_ADJUSTMENT -> "Admin Adjustment";
        };
    }
}
