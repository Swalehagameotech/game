package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminReasonRequest;
import com.teenpatti.platform.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/kyc")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminKycController {

    private final AdminKycService adminKycService;

    @GetMapping("/pending")
    public ResponseEntity<Page<User>> listPendingKyc(Pageable pageable) {
        return ResponseEntity.ok(adminKycService.listPendingKyc(pageable));
    }

    @PostMapping("/{userId}/approve")
    public ResponseEntity<User> approveKyc(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String userId) {
        return ResponseEntity.ok(adminKycService.approveKyc(adminUserId, userId));
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<User> rejectKyc(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String userId,
            @Valid @RequestBody AdminReasonRequest request) {
        return ResponseEntity.ok(adminKycService.rejectKyc(adminUserId, userId, request.getReason()));
    }
}
