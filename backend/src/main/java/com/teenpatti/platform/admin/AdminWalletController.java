package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.ManualAdjustmentRequest;
import com.teenpatti.platform.transaction.LedgerEntry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/wallet")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminWalletController {

    private final AdminWalletService adminWalletService;

    @PostMapping("/{userId}/adjust")
    public ResponseEntity<LedgerEntry> adjustWallet(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String userId,
            @Valid @RequestBody ManualAdjustmentRequest request) {
        return ResponseEntity.ok(adminWalletService.adjustBalance(adminUserId, userId, request.getAmountPaise(), request.getReason()));
    }
}
