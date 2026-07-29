package com.teenpatti.platform.admin.dto;

import com.teenpatti.platform.game.dto.GameHistorySummaryDto;
import com.teenpatti.platform.wallet.WalletTransaction;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AdminUserDetailDto {
    AdminUserSummaryDto profile;
    List<WalletTransaction> walletTransactions;
    List<GameHistorySummaryDto> gameHistory;
}
