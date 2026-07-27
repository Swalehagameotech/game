package com.teenpatti.platform.wallet;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.common.response.PageResponse;
import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.transaction.dto.InitiateWithdrawalRequest;
import com.teenpatti.platform.transaction.dto.WithdrawalResponse;
import com.teenpatti.platform.wallet.dto.LedgerEntryResponse;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import com.teenpatti.platform.wallet.dto.WalletDepositRequest;
import com.teenpatti.platform.wallet.dto.WalletDepositResponse;
import com.teenpatti.platform.wallet.dto.WalletSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * User wallet REST API — balance, deposit, withdraw, transaction history.
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final WalletFacadeService walletFacadeService;

    @GetMapping({"/balance", "/me"})
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(@CurrentUser String userId) {
        WalletBalanceResponse balance = walletService.getBalance(userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet balance retrieved successfully", balance));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<WalletSummaryResponse>> getSummary(@CurrentUser String userId) {
        WalletSummaryResponse summary = walletFacadeService.getSummary(userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet summary retrieved successfully", summary));
    }

    @GetMapping({"/ledger", "/me/history"})
    public ResponseEntity<ApiResponse<PageResponse<LedgerEntryResponse>>> getLedgerHistory(
            @CurrentUser String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<LedgerEntryResponse> ledgerHistory = walletService.getLedgerHistory(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Ledger history retrieved successfully", ledgerHistory));
    }

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<WalletDepositResponse>> deposit(
            @CurrentUser String userId,
            @Valid @RequestBody WalletDepositRequest request) {
        WalletDepositResponse response = walletFacadeService.deposit(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Deposit processed successfully", response));
    }

    @PostMapping("/deposit/demo")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> depositDemoChips(
            @CurrentUser String userId,
            @RequestParam(defaultValue = "100000") long amountPaise) {
        WalletBalanceResponse balance = walletService.depositDemoChips(userId, amountPaise);
        return ResponseEntity.ok(ApiResponse.success("Demo chips added to wallet successfully", balance));
    }

    @PostMapping("/deposit/{depositRequestId}/complete")
    public ResponseEntity<ApiResponse<WalletDepositResponse>> completeDeposit(
            @CurrentUser String userId,
            @PathVariable String depositRequestId) {
        WalletDepositResponse response = walletFacadeService.completeGatewayDeposit(userId, depositRequestId);
        return ResponseEntity.ok(ApiResponse.success("Deposit completed successfully", response));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> withdraw(
            @CurrentUser String userId,
            @Valid @RequestBody InitiateWithdrawalRequest request) {
        WithdrawalResponse response = walletFacadeService.withdraw(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request submitted successfully", response));
    }
}
