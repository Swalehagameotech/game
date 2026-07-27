package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.AdminTableSummaryDto;
import com.teenpatti.platform.common.exception.ResourceNotFoundException;
import com.teenpatti.platform.game.GameHistory;
import com.teenpatti.platform.game.GameHistoryRepository;
import com.teenpatti.platform.game.GameSessionService;
import com.teenpatti.platform.game.engine.HandContextManager;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableCountdownRegistry;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableStatusGroups;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTableService {

    private final TableRepository tableRepository;
    private final GameHistoryRepository gameHistoryRepository;
    private final TableCountdownRegistry countdownRegistry;
    private final HandContextManager handContextManager;
    private final GameSessionService gameSessionService;
    private final WebSocketEventPublisher webSocketEventPublisher;
    private final AdminActionLogService adminActionLogService;

    public Page<AdminTableSummaryDto> listTables(String group, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Table> tables = switch (group != null ? group.toLowerCase() : "active") {
            case "running" -> tableRepository.findByStatusIn(TableStatusGroups.runningList(), pageable);
            case "waiting" -> tableRepository.findByStatusIn(TableStatusGroups.waitingList(), pageable);
            case "closed" -> tableRepository.findByStatus(TableStatus.CLOSED, pageable);
            default -> tableRepository.findByStatusIn(TableStatusGroups.userActiveList(), pageable);
        };
        return tables.map(this::toSummary);
    }

    public Page<GameHistory> getTableHistory(String tableId, int page, int size) {
        if (!tableRepository.existsById(tableId)) {
            throw new ResourceNotFoundException("Table not found: " + tableId);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "endedAt"));
        return gameHistoryRepository.findByTableIdOrderByEndedAtDesc(tableId, pageable);
    }

    public AdminTableSummaryDto forceCloseTable(String adminUserId, String tableId, String reason) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));

        if (table.getStatus() == TableStatus.CLOSED) {
            return toSummary(table);
        }

        countdownRegistry.cancel(tableId);
        handContextManager.clearHand(tableId);
        gameSessionService.cancelActiveSessions(tableId);

        table.setStatus(TableStatus.CLOSED);
        table.setCountdownSeconds(0);
        Table saved = tableRepository.save(table);

        webSocketEventPublisher.publishTableClosed(tableId);

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.TABLE_FORCE_CLOSE,
                tableId,
                Map.of("reason", reason != null ? reason : "Force closed by admin")
        );

        log.info("Admin [{}] force-closed table [{}]", adminUserId, tableId);
        return toSummary(saved);
    }

    private AdminTableSummaryDto toSummary(Table table) {
        int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        return AdminTableSummaryDto.builder()
                .id(table.getId())
                .tableName(table.getTableName())
                .tableType(table.getTableType())
                .status(table.getStatus())
                .hostId(table.getHostId())
                .seatedCount(seated)
                .maxPlayers(table.getMaxPlayers())
                .bootAmountPaise(table.getBootAmountPaise())
                .countdownSeconds(table.getCountdownSeconds())
                .createdAt(table.getCreatedAt())
                .updatedAt(table.getUpdatedAt())
                .build();
    }
}
