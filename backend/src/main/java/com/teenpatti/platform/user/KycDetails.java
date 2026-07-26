package com.teenpatti.platform.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Nested KYC details for a user.
 *
 * IMPORTANT SECURITY NOTE:
 * Fields such as panNumber MUST be encrypted at rest using AES-256 before storing
 * real customer PII data in Phase 5. Do not store plain text PAN or bank details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDetails {

    /**
     * PAN card number (Encryption-at-rest required before real data storage in Phase 5).
     */
    private String panNumber;

    /**
     * Timestamp when KYC verification was approved.
     */
    private Instant verifiedAt;
}
