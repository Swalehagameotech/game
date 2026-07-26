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
        // Clean up empty WAITING tables that have no seated players
        for (Table table : tableRepository.findAll()) {
            if (table.getStatus() == TableStatus.WAITING && (table.getSeatedPlayerIds() == null || table.getSeatedPlayerIds().isEmpty())) {
                table.setStatus(TableStatus.CLOSED);
                tableRepository.save(table);
                log.info("Cleaned up empty unseated table [{}]", table.getId());
            }
        }
    }
}
