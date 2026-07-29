package com.teenpatti.platform.table;

import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Single-host ownership rules: transfer when the host leaves between hands,
 * or when the seated host is no longer at the table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostManagementService {

    private static final int MAX_RETRIES = 8;

    private final TableRepository tableRepository;
    private final UserRepository userRepository;
    private final WebSocketEventPublisher eventPublisher;

    /**
     * If {@code hostId} is missing or not seated, assigns the lowest-seat / first-joined player.
     *
     * @return new host id when changed, empty if unchanged or table empty
     */
    public Optional<String> ensureValidHost(String tableId) {
        int attempts = 0;
        while (attempts++ < MAX_RETRIES) {
            try {
                Optional<Table> opt = tableRepository.findById(tableId);
                if (opt.isEmpty()) {
                    return Optional.empty();
                }
                Table table = opt.get();
                Optional<String> change = applyHostTransferIfNeeded(table);
                if (change.isPresent()) {
                    tableRepository.save(table);
                    publishHostChanged(table, change.get());
                    log.info("Host transferred on table [{}] -> [{}]", tableId, change.get());
                }
                return change;
            } catch (OptimisticLockingFailureException ex) {
                backoff();
            }
        }
        return Optional.empty();
    }

    /**
     * Called when a specific player has left between hands and was the host.
     */
    public Optional<String> transferHostAfterDeparture(String tableId, String departedUserId) {
        int attempts = 0;
        while (attempts++ < MAX_RETRIES) {
            try {
                Optional<Table> opt = tableRepository.findById(tableId);
                if (opt.isEmpty()) {
                    return Optional.empty();
                }
                Table table = opt.get();
                if (!departedUserId.equals(table.getHostId())) {
                    return ensureValidHostInternal(table);
                }
                Optional<String> next = selectNextHostId(table);
                if (next.isEmpty()) {
                    return Optional.empty();
                }
                String previousHost = table.getHostId();
                table.setHostId(next.get());
                tableRepository.save(table);
                publishHostChanged(table, previousHost);
                log.info("Host [{}] left table [{}]; new host [{}]", departedUserId, tableId, next.get());
                return next;
            } catch (OptimisticLockingFailureException ex) {
                backoff();
            }
        }
        return Optional.empty();
    }

    public Optional<String> selectNextHostId(Table table) {
        if (table.getSeatedPlayerIds() == null || table.getSeatedPlayerIds().isEmpty()) {
            return Optional.empty();
        }
        if (table.getSeatMap() != null && !table.getSeatMap().isEmpty()) {
            return table.getSeatMap().stream()
                    .min(Comparator.comparingInt(TableSeat::getSeatIndex))
                    .map(TableSeat::getUserId)
                    .filter(id -> table.getSeatedPlayerIds().contains(id));
        }
        return Optional.of(table.getSeatedPlayerIds().get(0));
    }

    public boolean isHostSeated(Table table) {
        return table.getHostId() != null
                && table.getSeatedPlayerIds() != null
                && table.getSeatedPlayerIds().contains(table.getHostId());
    }

    public static boolean isBetweenHands(TableStatus status) {
        return status == TableStatus.WAITING
                || status == TableStatus.ROUND_END
                || status == TableStatus.NEXT_ROUND
                || status == TableStatus.COUNTDOWN;
    }

    private Optional<String> ensureValidHostInternal(Table table) {
        Optional<String> change = applyHostTransferIfNeeded(table);
        if (change.isPresent()) {
            tableRepository.save(table);
            publishHostChanged(table, null);
        }
        return change;
    }

    private Optional<String> applyHostTransferIfNeeded(Table table) {
        if (isHostSeated(table)) {
            return Optional.empty();
        }
        Optional<String> next = selectNextHostId(table);
        if (next.isEmpty()) {
            return Optional.empty();
        }
        table.setHostId(next.get());
        return next;
    }

    private void publishHostChanged(Table table, String previousHostId) {
        String newHostId = table.getHostId();
        String displayName = userRepository.findById(newHostId)
                .map(User::getDisplayName)
                .orElse("Player");
        eventPublisher.publishHostChanged(
                table.getId(),
                Map.of(
                        "tableId", table.getId(),
                        "previousHostId", previousHostId != null ? previousHostId : "",
                        "hostId", newHostId,
                        "hostUserId", newHostId,
                        "hostDisplayName", displayName
                ));
        eventPublisher.publishTableUpdated(table.getId(), table);
    }

    private void backoff() {
        try {
            Thread.sleep((long) (Math.random() * 30 + 10));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
