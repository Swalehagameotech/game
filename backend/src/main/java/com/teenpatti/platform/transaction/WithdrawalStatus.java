package com.teenpatti.platform.transaction;

/**
 * Status of a withdrawal request.
 */
public enum WithdrawalStatus {
    PENDING_ADMIN_REVIEW,
    APPROVED,
    REJECTED,
    PAID_OUT
}
