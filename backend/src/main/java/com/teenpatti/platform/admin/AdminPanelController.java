package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminDashboardResponse;
import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.wallet.WalletTransaction;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPanelController {

    private final AdminPanelService adminPanelService;

    @Data
    public static class WalletAdjustmentRequest {
        private long amount;
        private String reason;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboardStats() {
        AdminDashboardResponse stats = adminPanelService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<User>>> searchUsers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<User> users = adminPanelService.searchUsers(query, page, size);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<User>> getUserProfile(@PathVariable String userId) {
        User user = adminPanelService.getUserProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping("/users/{userId}/wallet/history")
    public ResponseEntity<ApiResponse<List<WalletTransaction>>> getUserWalletHistory(@PathVariable String userId) {
        List<WalletTransaction> history = adminPanelService.getUserWalletHistory(userId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @PostMapping("/users/{userId}/wallet/add")
    public ResponseEntity<ApiResponse<User>> addMoney(
            @AuthenticationPrincipal UserDetails admin,
            @PathVariable String userId,
            @RequestBody WalletAdjustmentRequest request) {
        String adminId = admin != null ? admin.getUsername() : "admin";
        User updated = adminPanelService.addMoneyToUserWallet(adminId, userId, request.getAmount(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/users/{userId}/wallet/deduct")
    public ResponseEntity<ApiResponse<User>> deductMoney(
            @AuthenticationPrincipal UserDetails admin,
            @PathVariable String userId,
            @RequestBody WalletAdjustmentRequest request) {
        String adminId = admin != null ? admin.getUsername() : "admin";
        User updated = adminPanelService.deductMoneyFromUserWallet(adminId, userId, request.getAmount(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/users/{userId}/block")
    public ResponseEntity<ApiResponse<User>> blockUser(
            @AuthenticationPrincipal UserDetails admin,
            @PathVariable String userId) {
        String adminId = admin != null ? admin.getUsername() : "admin";
        User updated = adminPanelService.blockUser(adminId, userId);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/users/{userId}/unblock")
    public ResponseEntity<ApiResponse<User>> unblockUser(
            @AuthenticationPrincipal UserDetails admin,
            @PathVariable String userId) {
        String adminId = admin != null ? admin.getUsername() : "admin";
        User updated = adminPanelService.unblockUser(adminId, userId);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
