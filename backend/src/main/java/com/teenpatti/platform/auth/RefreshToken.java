package com.teenpatti.platform.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * RefreshToken document storing hashed refresh tokens for session rotation and revocation.
 * Raw refresh tokens are NEVER stored in plaintext.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "refresh_tokens")
public class RefreshToken {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String tokenHash;

    private Instant expiresAt;

    @Builder.Default
    private boolean revoked = false;

    @CreatedDate
    private Instant createdAt;
}
