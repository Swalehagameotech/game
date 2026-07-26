package com.teenpatti.platform.transaction;

import com.teenpatti.platform.common.exception.InsufficientBalanceException;
import com.teenpatti.platform.common.exception.InvalidTransactionAmountException;
import com.teenpatti.platform.common.exception.KycNotVerifiedException;
import com.teenpatti.platform.common.exception.UserNotFoundException;
import com.teenpatti.platform.transaction.dto.InitiateWithdrawalRequest;
import com.teenpatti.platform.transaction.dto.WithdrawalResponse;
import com.teenpatti.platform.user.KycStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final long minWithdrawalAmountPaise;
    private final long maxWithdrawalAmountPaise;

    public WithdrawalService(
            WithdrawalRequestRepository withdrawalRequestRepository,
            UserRepository userRepository,
            WalletService walletService,
            @Value("${app.withdrawal.min-amount-paise:50000}") long minWithdrawalAmountPaise,
            @Value("${app.withdrawal.max-amount-paise:10000000}") long maxWithdrawalAmountPaise) {
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.minWithdrawalAmountPaise = minWithdrawalAmountPaise;
        this.maxWithdrawalAmountPaise = maxWithdrawalAmountPaise;
    }

    public WithdrawalResponse requestWithdrawal(String userId, InitiateWithdrawalRequest request) {
        // 1. Verify User KYC Status
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        if (user.getKycStatus() != KycStatus.VERIFIED) {
            log.warn("Withdrawal request rejected for user [{}]: KYC status is [{}]", userId, user.getKycStatus());
            throw new KycNotVerifiedException("KYC verification is required before initiating withdrawals. Current status: " + user.getKycStatus());
        }

        // 2. Validate Amount Limits
        long amountPaise = request.getAmountPaise();
        if (amountPaise < minWithdrawalAmountPaise || amountPaise > maxWithdrawalAmountPaise) {
            log.warn("Withdrawal request amount [{}] paise outside configured limits [{} - {}] paise",
                    amountPaise, minWithdrawalAmountPaise, maxWithdrawalAmountPaise);
            throw new InvalidTransactionAmountException(
                    "Withdrawal amount must be between " + minWithdrawalAmountPaise + " paise (₹" + (minWithdrawalAmountPaise / 100) +
                            ") and " + maxWithdrawalAmountPaise + " paise (₹" + (maxWithdrawalAmountPaise / 100) + ")."
            );
        }

        // 3. Verify Wallet Balance
        WalletBalanceResponse walletBalance = walletService.getBalance(userId);
        if (walletBalance.getBalancePaise() < amountPaise) {
            log.warn("Withdrawal rejected for user [{}]: requested {} paise, available {} paise",
                    userId, amountPaise, walletBalance.getBalancePaise());
            throw new InsufficientBalanceException(
                    "Insufficient available balance. Available: " + walletBalance.getBalancePaise() + " paise, requested: " + amountPaise + " paise."
            );
        }

        // 4. Create WithdrawalRequest record with status PENDING_ADMIN_REVIEW
        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .userId(userId)
                .amountPaise(amountPaise)
                .accountNumber(request.getAccountNumber())
                .ifscCode(request.getIfscCode())
                .accountHolderName(request.getAccountHolderName())
                .status(WithdrawalStatus.PENDING_ADMIN_REVIEW)
                .createdAt(Instant.now())
                .build();

        WithdrawalRequest savedRequest = withdrawalRequestRepository.save(withdrawalRequest);

        // 5. HOLD funds immediately via WalletService.applyLedgerEntry (debit)
        String referenceId = "withdrawal:" + savedRequest.getId();
        walletService.applyLedgerEntry(userId, LedgerEntryType.WITHDRAWAL, amountPaise, referenceId);

        log.info("Created WithdrawalRequest [{}] and held {} paise for user [{}]",
                savedRequest.getId(), amountPaise, userId);

        return toWithdrawalResponse(savedRequest);
    }

    public WithdrawalResponse getWithdrawalRequest(String userId, String withdrawalRequestId) {
        WithdrawalRequest withdrawalRequest = withdrawalRequestRepository.findById(withdrawalRequestId)
                .orElseThrow(() -> new UserNotFoundException("Withdrawal request not found: " + withdrawalRequestId));

        if (!withdrawalRequest.getUserId().equals(userId)) {
            throw new UserNotFoundException("Withdrawal request not found: " + withdrawalRequestId);
        }

        return toWithdrawalResponse(withdrawalRequest);
    }

    private WithdrawalResponse toWithdrawalResponse(WithdrawalRequest request) {
        return WithdrawalResponse.builder()
                .id(request.getId())
                .userId(request.getUserId())
                .amountPaise(request.getAmountPaise())
                .accountNumber(request.getAccountNumber())
                .ifscCode(request.getIfscCode())
                .accountHolderName(request.getAccountHolderName())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
                .reviewedByAdminId(request.getReviewedByAdminId())
                .build();
    }
}
