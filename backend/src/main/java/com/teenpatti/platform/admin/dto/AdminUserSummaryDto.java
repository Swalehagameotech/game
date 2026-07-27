package com.teenpatti.platform.admin.dto;

import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserSummaryDto {

    private String id;
    private String email;
    private String displayName;
    private UserRole role;
    private AccountStatus accountStatus;
    private boolean online;
    private long walletBalancePaise;
    private int matchesPlayedCount;
    private Instant createdAt;
}
