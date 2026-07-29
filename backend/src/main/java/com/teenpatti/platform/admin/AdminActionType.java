package com.teenpatti.platform.admin;

/**
 * Types of administrative operations logged for audit trails.
 */
public enum AdminActionType {
    BALANCE_ADJUSTMENT,
    ACCOUNT_SUSPEND,
    ACCOUNT_BAN,
    ACCOUNT_REINSTATE,
    KYC_APPROVE,
    KYC_REJECT,
    WITHDRAWAL_APPROVAL,
    WITHDRAWAL_REJECTION,
    WITHDRAWAL_PAYOUT_MARKED,
    BETTING_CONFIGURATION_UPDATED,
    TABLE_FORCE_CLOSE,
    SYSTEM_ANNOUNCEMENT,
    OTHER
}
