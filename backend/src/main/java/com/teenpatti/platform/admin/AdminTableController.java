package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminTableSummaryDto;
import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.game.GameHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tables")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTableController {

    private final AdminTableService adminTableService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminTableSummaryDto>>> listTables(
            @RequestParam(defaultValue = "active") String group,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminTableService.listTables(group, page, size)));
    }

    @GetMapping("/{tableId}/history")
    public ResponseEntity<ApiResponse<Page<GameHistory>>> getTableHistory(
            @PathVariable String tableId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminTableService.getTableHistory(tableId, page, size)));
    }

    @PostMapping("/{tableId}/force-close")
    public ResponseEntity<ApiResponse<AdminTableSummaryDto>> forceCloseTable(
            @AuthenticationPrincipal String adminUserId,
            @PathVariable String tableId,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success(adminTableService.forceCloseTable(adminUserId, tableId, reason)));
    }
}
