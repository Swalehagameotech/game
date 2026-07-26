package com.teenpatti.platform.user.dto;

import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.KycStatus;
import com.teenpatti.platform.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Full user profile response DTO for /api/users/me endpoint.
 * Sensitive fields (passwordHash, raw KYC details, refresh tokens) are strictly omitted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String id;
    private String email;
    private String phoneNumber; // Masked (e.g. XXXXXX3210)
    private String displayName;
    private String avatarUrl;
    private KycStatus kycStatus;
    private AccountStatus accountStatus;
    private UserRole role;
    private Instant createdAt;
}
