package com.teenpatti.platform.common.exception;

/**
 * Thrown when optimistic locking retries are exhausted during concurrent wallet updates.
 */
public class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String message) {
        super(message);
    }
}
