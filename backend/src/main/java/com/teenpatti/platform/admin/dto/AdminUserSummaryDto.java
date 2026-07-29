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
    private String profileImage;
    private String username;
    private String fullName;
    private String email;
    private String mobileNumber;
    private String displayName;
    private UserRole role;
    private AccountStatus accountStatus;
    private boolean online;
    private long walletBalancePaise;
    private String currentTableId;
    private String currentGameId;
    private int matchesPlayedCount;
    private long gamesWon;
    private long gamesLost;
    private long totalWinningsPaise;
    private long totalDepositsPaise;
    private long totalWithdrawalsPaise;
    private Instant createdAt;
    private Instant lastLogin;
}
