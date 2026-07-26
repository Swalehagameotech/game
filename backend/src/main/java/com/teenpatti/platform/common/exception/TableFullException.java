package com.teenpatti.platform.common.exception;

/**
 * Exception thrown when a player attempts to join a table that is already full.
 */
public class TableFullException extends RuntimeException {
    public TableFullException(String message) {
        super(message);
    }
}
