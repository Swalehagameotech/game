package com.teenpatti.platform.common.exception;

/**
 * Thrown when a user with non-ACTIVE account status (SUSPENDED / BANNED) attempts login.
 */
public class AccountStatusException extends RuntimeException {
    public AccountStatusException(String message) {
        super(message);
    }
}
