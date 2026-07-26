package com.teenpatti.platform.common.exception;

/**
 * Exception thrown when a deposit or withdrawal amount is outside configured limits.
 */
public class InvalidTransactionAmountException extends RuntimeException {
    public InvalidTransactionAmountException(String message) {
        super(message);
    }
}
