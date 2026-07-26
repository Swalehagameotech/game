package com.teenpatti.platform.table;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Ensures default public Teen Patti tables exist at startup so players can discover
 * and join shared public tables immediately in the lobby.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTableInitializer {

    private final TableRepository tableRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultPublicTables() {
        ensureDefaultPublicTables();
    }

    public synchronized void ensureDefaultPublicTables() {
        // Clear ghost seats on WAITING tables where no game is in progress
        for (Table table : tableRepository.findAll()) {
            if (table.getStatus() == TableStatus.WAITING && table.getSeatedPlayerIds() != null && !table.getSeatedPlayerIds().isEmpty()) {
                table.getSeatedPlayerIds().clear();
                tableRepository.save(table);
                log.info("Cleared stale ghost seats for WAITING table [{}]", table.getId());
            }
        }

        for (StakeTier tier : StakeTier.values()) {
            List<Table> available = tableRepository.findAll().stream()
                    .filter(t -> t.getTableType() == TableType.PUBLIC)
                    .filter(t -> t.getStakeTier() == tier)
                    .filter(t -> t.getStatus() != TableStatus.CLOSED)
                    .filter(t -> t.getSeatedPlayerIds() == null || t.getSeatedPlayerIds().size() < t.getMaxPlayers())
                    .toList();

            if (available.isEmpty()) {
                log.info("Creating default public Teen Patti table for tier [{}]", tier);
                Table table = Table.builder()
                        .tableType(TableType.PUBLIC)
                        .stakeTier(tier)
                        .maxPlayers(6)
                        .seatedPlayerIds(new ArrayList<>())
                        .status(TableStatus.WAITING)
                        .createdAt(Instant.now())
                        .build();
                tableRepository.save(table);
            }
        }
    }
}
