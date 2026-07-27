package com.teenpatti.platform.lobby;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.common.response.PageResponse;
import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.lobby.dto.CreatePrivateTableRequest;
import com.teenpatti.platform.lobby.dto.EligibilityCheckResponse;
import com.teenpatti.platform.lobby.dto.PrivateTableCreatedResponse;
import com.teenpatti.platform.lobby.dto.TableSummaryResponse;
import com.teenpatti.platform.table.dto.JoinTableResponse;
import com.teenpatti.platform.table.StakeTier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for the Lobby module: listing public tables, creating and looking up
 * private tables by invite code, and checking join eligibility.
 */
@RestController
@RequestMapping("/api/lobby")
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyService lobbyService;

    @GetMapping("/tables")
    public ResponseEntity<ApiResponse<PageResponse<TableSummaryResponse>>> getPublicTables(
            @CurrentUser String userId,
            @RequestParam(required = false) StakeTier stakeTier,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<TableSummaryResponse> response = lobbyService.getPublicTables(stakeTier, page, size);
        return ResponseEntity.ok(ApiResponse.success("Public tables retrieved successfully", response));
    }

    @PostMapping("/tables/private")
    public ResponseEntity<ApiResponse<PrivateTableCreatedResponse>> createPrivateTable(
            @CurrentUser String userId,
            @Valid @RequestBody CreatePrivateTableRequest request) {
        PrivateTableCreatedResponse response = lobbyService.createPrivateTable(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Private table created successfully", response));
    }

    @PostMapping("/tables/public")
    public ResponseEntity<ApiResponse<TableSummaryResponse>> createPublicTable(
            @CurrentUser String userId,
            @Valid @RequestBody CreatePrivateTableRequest request) {
        TableSummaryResponse response = lobbyService.createPublicTable(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Public table created successfully", response));
    }

    @GetMapping("/tables/private/{inviteCode}")
    public ResponseEntity<ApiResponse<TableSummaryResponse>> getPrivateTableByInviteCode(
            @CurrentUser String userId,
            @PathVariable String inviteCode) {
        TableSummaryResponse response = lobbyService.getPrivateTableByInviteCode(inviteCode);
        return ResponseEntity.ok(ApiResponse.success("Private table retrieved successfully", response));
    }

    @PostMapping("/tables/private/join")
    public ResponseEntity<ApiResponse<JoinTableResponse>> joinPrivateTableByInviteCode(
            @CurrentUser String userId,
            @RequestParam String inviteCode) {
        JoinTableResponse response = lobbyService.joinPrivateTableByInviteCode(userId, inviteCode);
        return ResponseEntity.ok(ApiResponse.success("Joined private table successfully", response));
    }

    @PostMapping("/tables/{tableId}/check-eligibility")
    public ResponseEntity<ApiResponse<EligibilityCheckResponse>> checkEligibility(
            @CurrentUser String userId,
            @PathVariable String tableId) {
        EligibilityCheckResponse response = lobbyService.checkJoinEligibility(userId, tableId);
        return ResponseEntity.ok(ApiResponse.success("Eligibility check completed", response));
    }
}
