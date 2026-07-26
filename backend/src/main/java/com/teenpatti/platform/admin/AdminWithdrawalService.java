package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminWithdrawalResponse;
import com.teenpatti.platform.common.exception.ResourceNotFoundException;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.transaction.WithdrawalRequest;
import com.teenpatti.platform.transaction.WithdrawalRequestRepository;
import com.teenpatti.platform.transaction.WithdrawalStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import com.teenpatti.platform.notification.NotificationService;
import com.teenpatti.platform.notification.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminWithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final AdminActionLogService adminActionLogService;
    private final NotificationService notificationService;

    public Page<AdminWithdrawalResponse> listWithdrawals(WithdrawalStatus status, Pageable pageable) {
        Page<WithdrawalRequest> requests = status != null ?
                withdrawalRequestRepository.findByStatus(status, pageable) :
                withdrawalRequestRepository.findAll(pageable);

        return requests.map(req -> {
            User user = userRepository.findById(req.getUserId()).orElse(null);
            return AdminWithdrawalResponse.builder()
                    .id(req.getId())
                    .userId(req.getUserId())
                    .userDisplayName(user != null ? user.getDisplayName() : "Unknown")
                    .userEmail(user != null ? user.getEmail() : "Unknown")
                    .userKycStatus(user != null ? user.getKycStatus() : null)
                    .amountPaise(req.getAmountPaise())
                    .bankAccountDetails(Map.of(
                            "accountNumber", req.getAccountNumber() != null ? req.getAccountNumber() : "",
                            "ifscCode", req.getIfscCode() != null ? req.getIfscCode() : "",
                            "accountHolderName", req.getAccountHolderName() != null ? req.getAccountHolderName() : ""
                    ))
                    .status(req.getStatus())
                    .rejectionReason(req.getRejectionReason())
                    .reviewedByAdminId(req.getReviewedByAdminId())
                    .requestedAt(req.getCreatedAt())
                    .reviewedAt(req.getReviewedAt())
                    .build();
        });
    }

    public WithdrawalRequest approveWithdrawal(String adminUserId, String requestId) {
        WithdrawalRequest request = getWithdrawalOrThrow(requestId);

        if (request.getStatus() != WithdrawalStatus.PENDING_ADMIN_REVIEW) {
            throw new IllegalStateException("Withdrawal is not currently PENDING_ADMIN_REVIEW. Current status: " + request.getStatus());
        }

        request.setStatus(WithdrawalStatus.APPROVED);
        request.setReviewedByAdminId(adminUserId);
        request.setReviewedAt(Instant.now());
        WithdrawalRequest updated = withdrawalRequestRepository.save(request);

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.WITHDRAWAL_APPROVAL,
                request.getUserId(),
                Map.of("withdrawalRequestId", requestId, "amountPaise", request.getAmountPaise())
        );

        log.info("Admin [{}] APPROVED withdrawal [{}] for user [{}]", adminUserId, requestId, request.getUserId());
        // Phase 14: Retroactive notification call
        notificationService.notify(
                request.getUserId(),
                NotificationType.WITHDRAWAL_SUCCESS,
                "Your withdrawal request of ₹" + (request.getAmountPaise() / 100) + " was approved."
        );
        return updated;
    }

    public WithdrawalRequest rejectWithdrawal(String adminUserId, String requestId, String reason) {
        WithdrawalRequest request = getWithdrawalOrThrow(requestId);

        if (request.getStatus() != WithdrawalStatus.PENDING_ADMIN_REVIEW) {
            throw new IllegalStateException("Withdrawal is not currently PENDING_ADMIN_REVIEW. Current status: " + request.getStatus());
        }

        request.setStatus(WithdrawalStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setReviewedByAdminId(adminUserId);
        request.setReviewedAt(Instant.now());
        WithdrawalRequest updated = withdrawalRequestRepository.save(request);

        // Refund held funds back to user wallet
        String refId = "withdrawal:" + requestId + ":reject-refund";
        walletService.applyLedgerEntry(request.getUserId(), LedgerEntryType.REFUND, request.getAmountPaise(), refId);

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.WITHDRAWAL_REJECTION,
                request.getUserId(),
                Map.of("withdrawalRequestId", requestId, "amountPaise", request.getAmountPaise(), "reason", reason)
        );

        log.info("Admin [{}] REJECTED withdrawal [{}] for user [{}] with reason: {}", adminUserId, requestId, request.getUserId(), reason);
        // Phase 14: Retroactive notification call
        notificationService.notify(
                request.getUserId(),
                NotificationType.WITHDRAWAL_FAILED,
                "Your withdrawal request of ₹" + (request.getAmountPaise() / 100) + " was rejected. Reason: " + reason
        );
        return updated;
    }

    public WithdrawalRequest markPaidOut(String adminUserId, String requestId) {
        WithdrawalRequest request = getWithdrawalOrThrow(requestId);

        if (request.getStatus() != WithdrawalStatus.APPROVED) {
            throw new IllegalStateException("Withdrawal must be in APPROVED status to mark paid out. Current status: " + request.getStatus());
        }

        request.setStatus(WithdrawalStatus.PAID_OUT);
        WithdrawalRequest updated = withdrawalRequestRepository.save(request);

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.WITHDRAWAL_PAYOUT_MARKED,
                request.getUserId(),
                Map.of("withdrawalRequestId", requestId, "amountPaise", request.getAmountPaise())
        );

        log.info("Admin [{}] MARKED PAID OUT withdrawal [{}] for user [{}]", adminUserId, requestId, request.getUserId());
        // Phase 14: Retroactive notification call
        notificationService.notify(
                request.getUserId(),
                NotificationType.WITHDRAWAL_SUCCESS,
                "Your withdrawal payout of ₹" + (request.getAmountPaise() / 100) + " has been processed."
        );
        return updated;
    }

    private WithdrawalRequest getWithdrawalOrThrow(String requestId) {
        return withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("WithdrawalRequest not found with id: " + requestId));
    }
}
