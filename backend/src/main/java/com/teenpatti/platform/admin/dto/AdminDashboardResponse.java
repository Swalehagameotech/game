package com.teenpatti.platform.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing system-wide gaming statistics for the Admin Dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponse {

    private long totalUsers;
    private long onlineUsers;
    private long totalTables;
    private long runningGames;
    private long waitingGames;
    private long totalWalletBalance;
    private long totalWalletTransactions;
}
