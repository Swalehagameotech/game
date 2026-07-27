package com.teenpatti.platform.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Public-safe profile for leaderboards and table host display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicProfileResponse {
    private String id;
    private String displayName;
    private String avatarUrl;
    private boolean isOnline;
    private int matchesPlayedCount;
    private Instant createdAt;
}
