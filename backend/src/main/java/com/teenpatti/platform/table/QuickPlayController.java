package com.teenpatti.platform.table;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.lobby.LobbyService;
import com.teenpatti.platform.lobby.dto.CreatePrivateTableRequest;
import com.teenpatti.platform.lobby.dto.TableSummaryResponse;
import com.teenpatti.platform.table.dto.JoinTableResponse;
import com.teenpatti.platform.table.dto.QuickPlayRequest;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller providing Quick Play single-click matchmaking.
 */
@Slf4j
@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class QuickPlayController {

    private final TableRepository tableRepository;
    private final TableService tableService;
    private final LobbyService lobbyService;
    private final WebSocketEventPublisher webSocketEventPublisher;

    @PostMapping("/quick-play")
    public ResponseEntity<ApiResponse<JoinTableResponse>> quickPlay(
            Authentication authentication,
            @Valid @RequestBody QuickPlayRequest request) {

        String userId = authentication.getPrincipal().toString();
        long targetBootPaise = request.getBootAmountPaise() != null ? request.getBootAmountPaise() : 1000L;

        log.info("Quick Play request received for user [{}] for boot amount {} paise", userId, targetBootPaise);

        // 1. Search for existing active public table with matching boot amount and open seats
        List<Table> availableTables = tableRepository.findAll().stream()
                .filter(t -> t.getTableType() == TableType.PUBLIC)
                .filter(t -> t.getStatus() == TableStatus.WAITING)
                .filter(t -> t.getBootAmountPaise() == targetBootPaise)
                .filter(t -> t.getSeatedPlayerIds() == null || t.getSeatedPlayerIds().size() < t.getMaxPlayers())
                .filter(t -> t.getSeatedPlayerIds() == null || !t.getSeatedPlayerIds().contains(userId))
                .toList();

        if (!availableTables.isEmpty()) {
            Table targetTable = availableTables.get(0);
            log.info("Found matching active table [{}] for Quick Play. Joining user [{}]...", targetTable.getId(), userId);
            JoinTableResponse joinResponse = tableService.joinTable(userId, targetTable.getId());
            return ResponseEntity.ok(ApiResponse.success("Quick Play successfully joined table", joinResponse));
        }

        // 2. No matching table found: Automatically create a new public table and join creator
        log.info("No matching active table found for boot amount {} paise. Auto-creating public table for user [{}]...", targetBootPaise, userId);

        StakeTier tier = targetBootPaise <= 1000L ? StakeTier.LOW : targetBootPaise <= 5000L ? StakeTier.MEDIUM : StakeTier.HIGH;

        CreatePrivateTableRequest createReq = CreatePrivateTableRequest.builder()
                .tableName("Teen Patti Quick Play ₹" + (targetBootPaise / 100))
                .stakeTier(tier)
                .bootAmount(targetBootPaise)
                .gameVariant("HIGHER")
                .maxPlayers(6)
                .build();

        TableSummaryResponse summary = lobbyService.createPublicTable(userId, createReq);

        JoinTableResponse joinResponse = tableService.joinTable(userId, summary.getTableId());
        return ResponseEntity.ok(ApiResponse.success("Quick Play table auto-created and joined", joinResponse));
    }
}
