package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminAnnouncementRequest;
import com.teenpatti.platform.admin.dto.AdminDashboardResponse;
import com.teenpatti.platform.admin.dto.AdminUserDetailDto;
import com.teenpatti.platform.admin.dto.AdminUserSummaryDto;
import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.wallet.WalletTransaction;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPanelController {

    private final AdminPanelService adminPanelService;
    private final AdminWalletService adminWalletService;
    private final AdminAnnouncementService adminAnnouncementService;

    @Data
    public static class WalletAdjustmentRequest {
        private long amount;
        private String reason;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboardStats() {
        return ResponseEntity.ok(ApiResponse.success(adminPanelService.getDashboardStats()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserSummaryDto>>> searchUsers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminPanelService.searchUsers(query, page, size)));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminUserSummaryDto>> getUserProfile(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(adminPanelService.getUserProfile(userId)));
    }

    @GetMapping("/users/{userId}/details")
    public ResponseEntity<ApiResponse<AdminUserDetailDto>> getUserDetails(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(adminPanelService.getUserDetails(userId)));
    }

    @GetMapping("/users/{userId}/wallet/history")
    public ResponseEntity<ApiResponse<List<WalletTransaction>>> getUserWalletHistory(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(adminPanelService.getUserWalletHistory(userId)));
    }

    @PostMapping("/users/{userId}/wallet/add")
    public ResponseEntity<ApiResponse<LedgerEntry>> addMoney(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String userId,
            @RequestBody WalletAdjustmentRequest request) {
        LedgerEntry entry = adminWalletService.adjustBalance(
                adminUserId, userId, request.getAmount(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(entry));
    }

    @PostMapping("/users/{userId}/wallet/deduct")
    public ResponseEntity<ApiResponse<LedgerEntry>> deductMoney(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String userId,
            @RequestBody WalletAdjustmentRequest request) {
        LedgerEntry entry = adminWalletService.adjustBalance(
                adminUserId, userId, -Math.abs(request.getAmount()), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(entry));
    }

    @PostMapping("/announcements")
    public ResponseEntity<ApiResponse<Map<String, Object>>> broadcastAnnouncement(
            @AuthenticationPrincipal String adminUserId,
            @Valid @RequestBody AdminAnnouncementRequest request) {
        int count = adminAnnouncementService.broadcastAnnouncement(adminUserId, request);
        return ResponseEntity.ok(ApiResponse.success(Map.of("recipientCount", count)));
    }
}
