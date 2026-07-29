package com.teenpatti.platform.common.exception;

import com.teenpatti.platform.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;

/**
 * Global exception handler providing standardized error responses across all REST controllers.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.of("RESOURCE_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUserException(DuplicateUserException ex) {
        log.warn("Duplicate user registration attempt: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.of("DUPLICATE_USER", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler({org.springframework.dao.DuplicateKeyException.class, com.mongodb.MongoWriteException.class})
    public ResponseEntity<ErrorResponse> handleDuplicateKeyException(Exception ex) {
        log.warn("Database duplicate key constraint violation: {}", ex.getMessage());
        String msg = "An account or display name already exists with the provided details.";
        if (ex.getMessage() != null && ex.getMessage().contains("displayName")) {
            msg = "The specified display name is already taken. Please choose a different display name.";
        } else if (ex.getMessage() != null && ex.getMessage().contains("email")) {
            msg = "An account with the provided email already exists.";
        } else if (ex.getMessage() != null && ex.getMessage().contains("phoneNumber")) {
            msg = "An account with the provided phone number already exists.";
        }
        ErrorResponse errorResponse = ErrorResponse.of("DUPLICATE_USER", msg);
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Bad credentials authentication failure: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.of("INVALID_CREDENTIALS", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<ErrorResponse> handleAccountStatusException(AccountStatusException ex) {
        log.warn("Account status access restriction: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.of("ACCOUNT_RESTRICTED", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenException(InvalidTokenException ex) {
        log.warn("Invalid/expired token presented: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.of("INVALID_TOKEN", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(KycNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleKycNotVerifiedException(KycNotVerifiedException ex) {
        log.warn("KYC restriction: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.of("KYC_REQUIRED", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<ErrorResponse> handleInvalidWebhookSignature(InvalidWebhookSignatureException ex) {
        log.warn("Potential security event: Invalid webhook signature: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.of("INVALID_WEBHOOK_SIGNATURE", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidTransactionAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransactionAmount(InvalidTransactionAmountException ex) {
        log.warn("Invalid transaction amount: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.of("INVALID_TRANSACTION_AMOUNT", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                "INSUFFICIENT_FUNDS",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                "CONCURRENCY_CONFLICT",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                "USER_NOT_FOUND",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(TableNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTableNotFound(TableNotFoundException ex) {
        log.warn("Table not found: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                "TABLE_NOT_FOUND",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(TableFullException.class)
    public ResponseEntity<ErrorResponse> handleTableFull(TableFullException ex) {
        log.warn("Table full: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                "TABLE_FULL",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(PlayerNotSeatedException.class)
    public ResponseEntity<ErrorResponse> handlePlayerNotSeated(PlayerNotSeatedException ex) {
        log.warn("Player not seated: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                "PLAYER_NOT_SEATED",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class)
    public ResponseEntity<ErrorResponse> handleUnrecognizedProperty(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException ex) {
        String fieldName = ex.getPropertyName();
        log.warn("Disallowed field present in request body: [{}]", fieldName);
        ErrorResponse response = ErrorResponse.of(
                "DISALLOWED_FIELD",
                "Field '" + fieldName + "' is not updatable via this endpoint."
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException upe) {
            return handleUnrecognizedProperty(upe);
        }
        log.warn("Malformed JSON request body: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                "MALFORMED_JSON",
                "Malformed JSON request body or unparseable field."
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        log.warn("Request validation failed: {}", details);
        ErrorResponse errorResponse = ErrorResponse.of(
                "VALIDATION_FAILED",
                "Validation failed for one or more fields",
                details
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.of("ILLEGAL_STATE", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Fallback handler for unhandled exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception caught in GlobalExceptionHandler: ", ex);
        ErrorResponse errorResponse = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred"
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
