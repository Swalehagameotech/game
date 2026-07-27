package com.teenpatti.platform.user.dto;

import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Full user profile for {@code GET /api/users/me}.
 * KYC is deferred — no submission fields are exposed here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String id;
    private String email;
    private String phoneNumber;
    private String displayName;
    private String avatarUrl;
    private AccountStatus accountStatus;
    private UserRole role;
    private long walletBalancePaise;
    private String formattedWalletBalance;
    private boolean isOnline;
    private Instant lastSeenAt;
    private int matchesPlayedCount;
    private boolean firstLoginTutorialCompleted;
    private UserActiveTableDto activeTable;
    private Instant createdAt;
    private Instant updatedAt;
}
