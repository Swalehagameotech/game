package com.teenpatti.platform.common.exception;

/**
 * Exception thrown when a user attempts an operation (such as withdrawal) requiring VERIFIED KYC status.
 */
public class KycNotVerifiedException extends RuntimeException {
    public KycNotVerifiedException(String message) {
        super(message);
    }
}
