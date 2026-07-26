package com.teenpatti.platform.transaction;

/**
 * Types of financial ledger transactions.
 */
public enum LedgerEntryType {
    DEPOSIT,
    WITHDRAWAL,
    BET,
    WIN,
    RAKE,
    REFUND,
    BONUS,
    ADMIN_ADJUSTMENT
}
