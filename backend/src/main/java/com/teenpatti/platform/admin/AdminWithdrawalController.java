package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminReasonRequest;
import com.teenpatti.platform.admin.dto.AdminWithdrawalResponse;
import com.teenpatti.platform.transaction.WithdrawalRequest;
import com.teenpatti.platform.transaction.WithdrawalStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/withdrawals")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminWithdrawalController {

    private final AdminWithdrawalService adminWithdrawalService;

    @GetMapping
    public ResponseEntity<Page<AdminWithdrawalResponse>> listWithdrawals(
            @RequestParam(required = false) WithdrawalStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(adminWithdrawalService.listWithdrawals(status, pageable));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<WithdrawalRequest> approveWithdrawal(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String requestId) {
        return ResponseEntity.ok(adminWithdrawalService.approveWithdrawal(adminUserId, requestId));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<WithdrawalRequest> rejectWithdrawal(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String requestId,
            @Valid @RequestBody AdminReasonRequest request) {
        return ResponseEntity.ok(adminWithdrawalService.rejectWithdrawal(adminUserId, requestId, request.getReason()));
    }

    @PostMapping("/{requestId}/mark-paid-out")
    public ResponseEntity<WithdrawalRequest> markPaidOut(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String requestId) {
        return ResponseEntity.ok(adminWithdrawalService.markPaidOut(adminUserId, requestId));
    }
}
