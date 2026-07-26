package com.teenpatti.platform.common.exception;

/**
 * Thrown when a wallet debit transaction attempt would cause the balance to drop below 0 paise.
 */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
