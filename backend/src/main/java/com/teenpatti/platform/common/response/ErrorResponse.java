package com.teenpatti.platform.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response structure for API exception handling.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    @Builder.Default
    private boolean success = false;
    private String errorCode;
    private String message;
    private List<String> details;
    @Builder.Default
    private long timestamp = Instant.now().toEpochMilli();

    public static ErrorResponse of(String errorCode, String message) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now().toEpochMilli())
                .build();
    }

    public static ErrorResponse of(String errorCode, String message, List<String> details) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .timestamp(Instant.now().toEpochMilli())
                .build();
    }
}
