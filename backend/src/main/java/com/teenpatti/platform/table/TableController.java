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

    @GetMapping("/running")
    public ResponseEntity<ApiResponse<java.util.List<Table>>> getRunningTables() {
        java.util.List<Table> tables = tableService.getTablesByStatus(TableStatus.IN_PROGRESS);
        return ResponseEntity.ok(ApiResponse.success(tables));
    }

    @GetMapping("/waiting")
    public ResponseEntity<ApiResponse<java.util.List<Table>>> getWaitingTables() {
        java.util.List<Table> tables = tableService.getTablesByStatus(TableStatus.WAITING);
        return ResponseEntity.ok(ApiResponse.success(tables));
    }

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<java.util.List<Table>>> getPublicTables() {
        java.util.List<Table> tables = tableService.getTablesByTypeAndStatus(TableType.PUBLIC, TableStatus.WAITING);
        return ResponseEntity.ok(ApiResponse.success(tables));
    }

    @GetMapping("/private")
    public ResponseEntity<ApiResponse<java.util.List<Table>>> getPrivateTables() {
        java.util.List<Table> tables = tableService.getTablesByTypeAndStatus(TableType.PRIVATE, TableStatus.WAITING);
        return ResponseEntity.ok(ApiResponse.success(tables));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<java.util.List<Table>>> getActiveTables() {
        java.util.List<Table> activeTables = tableService.getActiveJoinableTables();
        return ResponseEntity.ok(ApiResponse.success(activeTables));
    }

    @DeleteMapping("/{tableId}")
    public ResponseEntity<ApiResponse<String>> deleteTable(
            @CurrentUser String userId,
            @PathVariable String tableId) {
        tableService.deleteTable(userId, tableId);
        return ResponseEntity.ok(ApiResponse.success("Table deleted successfully"));
    }
}
