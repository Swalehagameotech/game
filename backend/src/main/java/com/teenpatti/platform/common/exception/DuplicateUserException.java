package com.teenpatti.platform.common.exception;

/**
 * Thrown when registration fails due to duplicate email or phone number.
 */
public class DuplicateUserException extends RuntimeException {
    public DuplicateUserException(String message) {
        super(message);
    }
}
