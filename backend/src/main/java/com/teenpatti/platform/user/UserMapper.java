package com.teenpatti.platform.user;

import com.teenpatti.platform.user.dto.PublicProfileResponse;
import com.teenpatti.platform.user.dto.UserActiveTableDto;
import com.teenpatti.platform.user.dto.UserProfileResponse;

/**
 * Maps {@link User} entities to external profile DTOs.
 */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserProfileResponse toUserProfileResponse(
            User user,
            long walletBalancePaise,
            UserActiveTableDto activeTable) {
        if (user == null) {
            return null;
        }
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(maskPhoneNumber(user.getPhoneNumber()))
                .displayName(user.getDisplayName())
                .avatarUrl(resolveAvatarUrl(user))
                .accountStatus(user.getAccountStatus())
                .role(user.getRole())
                .walletBalancePaise(walletBalancePaise)
                .formattedWalletBalance(String.format("₹%.2f", walletBalancePaise / 100.0))
                .isOnline(user.isOnline())
                .lastSeenAt(user.getLastSeenAt())
                .matchesPlayedCount(user.getMatchesPlayedCount())
                .firstLoginTutorialCompleted(user.isFirstLoginTutorialCompleted())
                .activeTable(activeTable)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public static PublicProfileResponse toPublicProfileResponse(User user) {
        if (user == null) {
            return null;
        }
        return PublicProfileResponse.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .avatarUrl(resolveAvatarUrl(user))
                .isOnline(user.isOnline())
                .matchesPlayedCount(user.getMatchesPlayedCount())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private static String resolveAvatarUrl(User user) {
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
            return user.getAvatarUrl();
        }
        return "https://api.dicebear.com/7.x/avataaars/svg?seed=" + user.getId();
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
