package com.teenpatti.platform.common.exception;

/**
 * Exception thrown when a table or private table invite code is not found.
 */
public class TableNotFoundException extends RuntimeException {
    public TableNotFoundException(String message) {
        super(message);
    }
}
