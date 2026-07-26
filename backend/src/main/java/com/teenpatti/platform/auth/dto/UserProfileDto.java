package com.teenpatti.platform.auth.dto;

import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.KycStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Safe public user profile DTO (never contains passwordHash or internal security metadata).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {

    private String id;
    private String email;
    private String phoneNumber;
    private String displayName;
    private KycStatus kycStatus;
    private AccountStatus accountStatus;
    private UserRole role;
    private Instant createdAt;

    public static UserProfileDto fromUser(User user) {
        if (user == null) {
            return null;
        }
        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .displayName(user.getDisplayName())
                .kycStatus(user.getKycStatus())
                .accountStatus(user.getAccountStatus())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
