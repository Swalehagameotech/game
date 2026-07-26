package com.teenpatti.platform.wallet;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.common.response.PageResponse;
import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.wallet.dto.LedgerEntryResponse;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Thin REST Controller exposing user wallet balance and transaction ledger endpoints.
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(@CurrentUser String userId) {
        WalletBalanceResponse balance = walletService.getBalance(userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet balance retrieved successfully", balance));
    }

    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<PageResponse<LedgerEntryResponse>>> getLedgerHistory(
            @CurrentUser String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<LedgerEntryResponse> ledgerHistory = walletService.getLedgerHistory(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Ledger history retrieved successfully", ledgerHistory));
    }

    @PostMapping("/deposit/demo")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> depositDemoChips(
            @CurrentUser String userId,
            @RequestParam(defaultValue = "100000") long amountPaise) {
        WalletBalanceResponse balance = walletService.depositDemoChips(userId, amountPaise);
        return ResponseEntity.ok(ApiResponse.success("Demo chips added to wallet successfully", balance));
    }
}
