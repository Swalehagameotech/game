package com.teenpatti.platform.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
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
@CompoundIndexes({
        @CompoundIndex(name = "role_status_idx", def = "{'role': 1, 'accountStatus': 1}"),
        @CompoundIndex(name = "online_lastseen_idx", def = "{'isOnline': 1, 'lastSeenAt': -1}")
})
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

    @Builder.Default
    private long walletBalance = 0L;

    @Builder.Default
    private boolean isOnline = false;

    private Instant lastSeenAt;

    @Builder.Default
    private boolean firstLoginTutorialCompleted = false;

    @Builder.Default
    private int matchesPlayedCount = 0;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public String getName() {
        return displayName;
    }

    public void setName(String name) {
        this.displayName = name;
    }

    public String getMobile() {
        return phoneNumber;
    }

    public void setMobile(String mobile) {
        this.phoneNumber = mobile;
    }

    public long getWalletBalancePaise() {
        return walletBalance;
    }

    public void setWalletBalancePaise(long walletBalancePaise) {
        this.walletBalance = walletBalancePaise;
    }

    public String getStatus() {
        return accountStatus != null ? accountStatus.name() : "ACTIVE";
    }
}
