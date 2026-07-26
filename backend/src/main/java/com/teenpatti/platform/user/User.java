package com.teenpatti.platform.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * User document representing player accounts, credentials, and verification status.
 * Primary entity used across Phase 3 (Auth), Phase 5 (User Profile), and all platform modules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    @Indexed(unique = true)
    private String phoneNumber;

    @Indexed(unique = true)
    private String displayName;

    private String avatarUrl;

    @Builder.Default
    private KycStatus kycStatus = KycStatus.NOT_STARTED;

    private KycDetails kycDetails;

    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Builder.Default
    private UserRole role = UserRole.PLAYER;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
