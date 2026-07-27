package com.teenpatti.platform.table;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${app.tables.seed-default-public:false}")
    private boolean seedDefaultPublicTablesEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultPublicTables() {
        if (!seedDefaultPublicTablesEnabled) {
            log.info("Default public table seeding is disabled.");
            return;
        }
        ensureDefaultPublicTables();
    }

    public synchronized void ensureDefaultPublicTables() {
        if (!seedDefaultPublicTablesEnabled) {
            return;
        }
        createDefaultPublicTableIfMissing(StakeTier.LOW, "Low Stakes Room", 1000L);
        createDefaultPublicTableIfMissing(StakeTier.MEDIUM, "Medium Stakes Room", 5000L);
        createDefaultPublicTableIfMissing(StakeTier.HIGH, "High Rollers Room", 25000L);
    }

    private void createDefaultPublicTableIfMissing(StakeTier tier, String name, long bootPaise) {
        boolean exists = tableRepository.findAll().stream()
                .anyMatch(t -> t.getTableType() == TableType.PUBLIC 
                        && t.getStakeTier() == tier 
                        && t.getStatus() != TableStatus.CLOSED);

        if (!exists) {
            Table table = Table.builder()
                    .tableName(name)
                    .tableType(TableType.PUBLIC)
                    .visibility("PUBLIC")
                    .stakeTier(tier)
                    .bootAmountPaise(bootPaise)
                    .maxPlayers(6)
                    .seatedPlayerIds(new ArrayList<>())
                    .status(TableStatus.WAITING)
                    .createdAt(Instant.now())
                    .build();
            tableRepository.save(table);
            log.info("Seeded default public table [{}] - Tier: {}", name, tier);
        }
    }
}
