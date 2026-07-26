package com.teenpatti.platform.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Reduced public profile DTO for /api/users/{userId}/public endpoint.
 * Contains only safe non-PII fields suitable for public leaderboards and friend lists.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicProfileResponse {
    private String id;
    private String displayName;
    private String avatarUrl;
    private Instant createdAt;
}
