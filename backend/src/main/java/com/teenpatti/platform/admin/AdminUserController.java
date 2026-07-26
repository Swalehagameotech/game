package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminReasonRequest;
import com.teenpatti.platform.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/{userId}/suspend")
    public ResponseEntity<User> suspendUser(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String userId,
            @Valid @RequestBody AdminReasonRequest request) {
        return ResponseEntity.ok(adminUserService.suspendUser(adminUserId, userId, request.getReason()));
    }

    @PostMapping("/{userId}/ban")
    public ResponseEntity<User> banUser(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String userId,
            @Valid @RequestBody AdminReasonRequest request) {
        return ResponseEntity.ok(adminUserService.banUser(adminUserId, userId, request.getReason()));
    }

    @PostMapping("/{userId}/reinstate")
    public ResponseEntity<User> reinstateUser(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String userId) {
        return ResponseEntity.ok(adminUserService.reinstateUser(adminUserId, userId));
    }
}
