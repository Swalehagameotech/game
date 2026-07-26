package com.teenpatti.platform.common.exception;

/**
 * Thrown when authentication fails due to invalid login credentials.
 */
public class BadCredentialsException extends RuntimeException {
    public BadCredentialsException(String message) {
        super(message);
    }
}
