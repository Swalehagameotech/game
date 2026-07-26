package com.teenpatti.platform.common.exception;

/**
 * Thrown when a JWT or refresh token is invalid, expired, or revoked.
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
