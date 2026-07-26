package com.teenpatti.platform.user;

import com.teenpatti.platform.user.dto.PublicProfileResponse;
import com.teenpatti.platform.user.dto.UserProfileResponse;

/**
 * Mapper utility for transforming User entity documents into external DTO responses.
 */
public class UserMapper {

    /**
     * Converts User document entity into UserProfileResponse DTO with phone masking.
     * Masking Rule: Retains only the last 4 digits, replacing all preceding digits with 'X'.
     * Example: "9876543210" -> "XXXXXX3210"
     */
    public static UserProfileResponse toUserProfileResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(maskPhoneNumber(user.getPhoneNumber()))
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .kycStatus(user.getKycStatus())
                .accountStatus(user.getAccountStatus())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Converts User document entity into reduced PublicProfileResponse DTO.
     */
    public static PublicProfileResponse toPublicProfileResponse(User user) {
        if (user == null) {
            return null;
        }
        return PublicProfileResponse.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private static String maskPhoneNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.length() <= 4) {
            return rawPhone;
        }
        int len = rawPhone.length();
        String unmaskedSuffix = rawPhone.substring(len - 4);
        return "X".repeat(len - 4) + unmaskedSuffix;
    }
}
