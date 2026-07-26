package com.teenpatti.platform.table;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.table.dto.JoinTableResponse;
import com.teenpatti.platform.table.dto.LeaveTableResponse;
import com.teenpatti.platform.table.dto.TableDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller exposing table seat joining, leaving, and state inspection endpoints.
 */
@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @PostMapping("/{tableId}/join")
    public ResponseEntity<ApiResponse<JoinTableResponse>> joinTable(
            @CurrentUser String userId,
            @PathVariable String tableId) {
        JoinTableResponse response = tableService.joinTable(userId, tableId);
        return ResponseEntity.ok(ApiResponse.success("Successfully joined table", response));
    }

    @PostMapping("/{tableId}/leave")
    public ResponseEntity<ApiResponse<LeaveTableResponse>> leaveTable(
            @CurrentUser String userId,
            @PathVariable String tableId) {
        LeaveTableResponse response = tableService.leaveTable(userId, tableId);
        return ResponseEntity.ok(ApiResponse.success("Successfully processed leave table request", response));
    }

    @GetMapping("/{tableId}")
    public ResponseEntity<ApiResponse<TableDetailResponse>> getTableDetails(
            @CurrentUser String userId,
            @PathVariable String tableId) {
        TableDetailResponse response = tableService.getTableDetails(userId, tableId);
        return ResponseEntity.ok(ApiResponse.success("Table details retrieved successfully", response));
    }
}
