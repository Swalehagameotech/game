package com.teenpatti.platform.auth.dto;

import com.teenpatti.platform.home.dto.HomeDashboardResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication response payload: issued tokens, user profile, and full session aggregate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;
    private UserProfileDto user;
    private HomeDashboardResponse dashboard;
}
