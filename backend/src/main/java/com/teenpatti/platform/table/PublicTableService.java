package com.teenpatti.platform.table;

import com.teenpatti.platform.lobby.config.StakeTierConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Public-table business rules: join eligibility, quick-play matchmaking, and countdown triggers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicTableService {

    public static final Set<TableStatus> JOINABLE_STATUSES = EnumSet.of(
            TableStatus.WAITING,
            TableStatus.ROUND_END
    );

    private final TableRepository tableRepository;
    private final PublicTableCountdownService publicTableCountdownService;
    private final StakeTierConfig stakeTierConfig;

    public boolean isPublicTable(Table table) {
        return table != null && table.getTableType() == TableType.PUBLIC;
    }

    public boolean isJoinable(Table table) {
        if (table == null || table.getTableType() != TableType.PUBLIC || table.getStatus() == TableStatus.CLOSED) {
            return false;
        }
        if (!JOINABLE_STATUSES.contains(table.getStatus())) {
            return false;
        }
        int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        return seated < table.getMaxPlayers();
    }

    public void assertJoinable(Table table) {
        if (table == null) {
            throw new com.teenpatti.platform.common.exception.TableNotFoundException("Table not found");
        }
        if (table.getTableType() != TableType.PUBLIC) {
            return;
        }
        if (table.getStatus() == TableStatus.CLOSED) {
            throw new com.teenpatti.platform.common.exception.TableNotFoundException("Table is closed");
        }
        if (table.getStatus() == TableStatus.COUNTDOWN) {
            throw new com.teenpatti.platform.common.exception.TableFullException(
                    "Game countdown in progress — cannot join this public table.");
        }
        if (TableStatusGroups.isRunning(table.getStatus()) || table.getStatus() == TableStatus.STARTING) {
            throw new com.teenpatti.platform.common.exception.TableFullException(
                    "Game already in progress on this public table.");
        }
        if (!JOINABLE_STATUSES.contains(table.getStatus())) {
            throw new com.teenpatti.platform.common.exception.TableFullException(
                    "Public table is not open for joining. Status: " + table.getStatus());
        }
    }

    public void assertHostStartAllowed(Table table) {
        if (isPublicTable(table)) {
            throw new IllegalStateException(
                    "Public tables start automatically when minimum players are seated.");
        }
    }

    /**
     * Finds open public tables matching boot amount for quick-play matchmaking.
     */
    public List<Table> findQuickPlayCandidates(long bootAmountPaise, String userId, GameVariant variant) {
        GameVariant selectedVariant = variant != null ? variant : GameVariant.CLASSIC;
        return tableRepository.findAll().stream()
                .filter(this::isPublicTable)
                .filter(t -> t.getStatus() != TableStatus.CLOSED)
                .filter(t -> JOINABLE_STATUSES.contains(t.getStatus()))
                .filter(t -> resolveBootPaise(t) == bootAmountPaise)
                .filter(t -> (t.getGameVariant() != null ? t.getGameVariant() : GameVariant.CLASSIC) == selectedVariant)
                .filter(t -> {
                    int seated = t.getSeatedPlayerIds() != null ? t.getSeatedPlayerIds().size() : 0;
                    return seated < t.getMaxPlayers();
                })
                .filter(t -> t.getSeatedPlayerIds() == null || !t.getSeatedPlayerIds().contains(userId))
                .toList();
    }

    public void afterPublicTableMutation(String tableId) {
        publicTableCountdownService.evaluate(tableId);
    }

    public int minPlayersRequired(Table table) {
        return table.getMinPlayers() > 0 ? table.getMinPlayers() : 3;
    }

    public long resolveBootPaise(Table table) {
        if (table.getBootAmountPaise() > 0) {
            return table.getBootAmountPaise();
        }
        return stakeTierConfig.getMinBuyInPaise(table.getStakeTier());
    }
}
