package com.teenpatti.platform.common.exception;

/**
 * Exception thrown when a player attempts to leave a table at which they are not currently seated.
 */
public class PlayerNotSeatedException extends RuntimeException {
    public PlayerNotSeatedException(String message) {
        super(message);
    }
}
