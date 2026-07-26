package com.teenpatti.platform.common.exception;

/**
 * Exception thrown when a payment gateway webhook signature fails verification.
 */
public class InvalidWebhookSignatureException extends RuntimeException {
    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
