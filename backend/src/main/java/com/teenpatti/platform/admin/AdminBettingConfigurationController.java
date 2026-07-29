package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.betting.BettingConfigurationService;
import com.teenpatti.platform.admin.dto.BettingConfigurationRequest;
import com.teenpatti.platform.admin.dto.BettingConfigurationResponse;
import com.teenpatti.platform.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/betting-config")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminBettingConfigurationController {

    private final BettingConfigurationService bettingConfigurationService;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<BettingConfigurationResponse>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(bettingConfigurationService.getActiveResponse()));
    }

    @PutMapping("/active")
    public ResponseEntity<ApiResponse<BettingConfigurationResponse>> updateActive(
            @AuthenticationPrincipal String adminUserId,
            @Valid @RequestBody BettingConfigurationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                bettingConfigurationService.updateActive(adminUserId, request)));
    }
}
