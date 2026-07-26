package com.teenpatti.platform.admin;

import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Service managing administrative manual wallet balance adjustments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminWalletService {

    private final WalletService walletService;
    private final AdminActionLogService adminActionLogService;

    public LedgerEntry adjustBalance(String adminUserId, String targetUserId, long amountPaise, String reason) {
        if (amountPaise == 0) {
            throw new IllegalArgumentException("Adjustment amountPaise must be non-zero");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Adjustment reason is required");
        }

        String refId = "admin-adjustment:" + UUID.randomUUID();
        LedgerEntry entry = walletService.applyLedgerEntry(targetUserId, LedgerEntryType.ADMIN_ADJUSTMENT, amountPaise, refId);

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.BALANCE_ADJUSTMENT,
                targetUserId,
                Map.of("amountPaise", amountPaise, "reason", reason, "referenceId", refId)
        );

        log.info("Admin [{}] ADJUSTED wallet for user [{}] by {} paise. Reason: {}", adminUserId, targetUserId, amountPaise, reason);
        return entry;
    }
}
