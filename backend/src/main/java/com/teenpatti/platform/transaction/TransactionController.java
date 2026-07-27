package com.teenpatti.platform.transaction;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.transaction.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller exposing deposit initiation, Razorpay webhook acknowledgment,
 * withdrawal requests, and transaction status lookup endpoints.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final DepositService depositService;
    private final RazorpayWebhookService razorpayWebhookService;
    private final WithdrawalService withdrawalService;

    @PostMapping("/deposit/initiate")
    public ResponseEntity<ApiResponse<DepositInitiationResponse>> initiateDeposit(
            @CurrentUser String userId,
            @Valid @RequestBody InitiateDepositRequest request) {
        DepositInitiationResponse response = depositService.initiateDeposit(userId, request.getAmountPaise());
        return ResponseEntity.ok(ApiResponse.success("Deposit order initiated successfully", response));
    }

    @PostMapping("/webhook/razorpay")
    public ResponseEntity<ApiResponse<Void>> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        razorpayWebhookService.processWebhook(payload, signature);
        return ResponseEntity.ok(ApiResponse.success("Webhook processed successfully", null));
    }

    @PostMapping({"/withdraw/request", "/withdrawal/request"})
    public ResponseEntity<ApiResponse<WithdrawalResponse>> requestWithdrawal(
            @CurrentUser String userId,
            @Valid @RequestBody InitiateWithdrawalRequest request) {
        WithdrawalResponse response = withdrawalService.requestWithdrawal(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request submitted successfully", response));
    }

    @GetMapping("/deposit/{depositRequestId}")
    public ResponseEntity<ApiResponse<DepositResponse>> getDepositStatus(
            @CurrentUser String userId,
            @PathVariable String depositRequestId) {
        DepositResponse response = depositService.getDepositRequest(userId, depositRequestId);
        return ResponseEntity.ok(ApiResponse.success("Deposit request retrieved successfully", response));
    }

    @GetMapping("/withdraw/{withdrawalRequestId}")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> getWithdrawalStatus(
            @CurrentUser String userId,
            @PathVariable String withdrawalRequestId) {
        WithdrawalResponse response = withdrawalService.getWithdrawalRequest(userId, withdrawalRequestId);
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request retrieved successfully", response));
    }
}
