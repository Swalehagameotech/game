package com.teenpatti.platform.matchmaking;

import com.teenpatti.platform.bot.BotService;
import com.teenpatti.platform.lobby.LobbyService;
import com.teenpatti.platform.lobby.dto.CreatePrivateTableRequest;
import com.teenpatti.platform.lobby.dto.TableSummaryResponse;
import com.teenpatti.platform.table.*;
import com.teenpatti.platform.table.dto.JoinTableResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;

/**
 * Smart matchmaking: seat the human quickly, wait briefly for other humans,
 * then fill remaining seats with AI bots so the game can start.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private final PublicTableService publicTableService;
    private final LobbyService lobbyService;
    private final TableService tableService;
    private final TableRepository tableRepository;
    private final BotService botService;
    private final MatchmakingProperties properties;
    private final PublicTableCountdownService countdownService;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingFills = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "matchmaking");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    void shutdown() {
        pendingFills.values().forEach(f -> f.cancel(false));
        scheduler.shutdownNow();
    }

    /**
     * Quick Play entry: join or create a public table, then schedule bot fill if needed.
     */
    public JoinTableResponse quickPlay(String userId, long bootAmountPaise, GameVariant variant) {
        GameVariant selected = variant != null ? variant : GameVariant.CLASSIC;

        List<Table> candidates = publicTableService.findQuickPlayCandidates(bootAmountPaise, userId, selected);
        JoinTableResponse response;
        String tableId;

        if (!candidates.isEmpty()) {
            Table target = pickBestCandidate(candidates);
            log.info("Matchmaking joining table [{}] for user [{}]", target.getId(), userId);
            response = tableService.joinTable(userId, target.getId());
            tableId = target.getId();
        } else {
            log.info("Matchmaking creating table for user [{}] boot {} variant {}", userId, bootAmountPaise, selected);
            StakeTier tier = bootAmountPaise <= 1000L ? StakeTier.LOW
                    : bootAmountPaise <= 5000L ? StakeTier.MEDIUM
                    : StakeTier.HIGH;

            CreatePrivateTableRequest createReq = CreatePrivateTableRequest.builder()
                    .tableName("Teen Patti Quick Play ₹" + (bootAmountPaise / 100))
                    .stakeTier(tier)
                    .bootAmount(bootAmountPaise)
                    .gameVariant(selected.name())
                    .minPlayers(properties.getMinPlayers())
                    .maxPlayers(6)
                    .build();

            TableSummaryResponse summary = lobbyService.createPublicTable(userId, createReq);
            tableId = summary.getTableId();
            response = JoinTableResponse.builder()
                    .tableId(tableId)
                    .seatIndex(0)
                    .heldBuyInPaise(summary.getBootAmount())
                    .tableDetail(tableService.getTableDetails(userId, tableId))
                    .build();
        }

        scheduleBotFillIfNeeded(tableId);
        return response;
    }

    /**
     * Prefer tables that already have humans (faster starts) and most empty seats.
     */
    private Table pickBestCandidate(List<Table> candidates) {
        return candidates.stream()
                .sorted((a, b) -> {
                    int ha = countHumans(a);
                    int hb = countHumans(b);
                    if (ha != hb) return Integer.compare(hb, ha); // more humans first
                    int sa = a.getSeatedPlayerIds() != null ? a.getSeatedPlayerIds().size() : 0;
                    int sb = b.getSeatedPlayerIds() != null ? b.getSeatedPlayerIds().size() : 0;
                    return Integer.compare(sa, sb); // then fewer seated (more room)
                })
                .findFirst()
                .orElse(candidates.get(0));
    }

    private void scheduleBotFillIfNeeded(String tableId) {
        // Cancel previous fill for this table (another human may have just joined)
        ScheduledFuture<?> prev = pendingFills.remove(tableId);
        if (prev != null) prev.cancel(false);

        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null || table.getTableType() != TableType.PUBLIC) return;

        int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        int min = Math.max(properties.getMinPlayers(), table.getMinPlayers() > 0 ? table.getMinPlayers() : 3);
        int max = table.getMaxPlayers() > 0 ? table.getMaxPlayers() : 6;

        if (seated >= min) {
            // Enough players — public countdown handles start; optional fill-to-max still allowed
            if (properties.isFillToMax() && seated < max) {
                scheduleFill(tableId, max - seated, 1_500L);
            }
            return;
        }

        long wait = Math.max(3_000L, properties.getWaitForHumansMs());
        scheduleFill(tableId, -1, wait); // -1 = compute at fire time
        log.info("Matchmaking: waiting {}ms for humans on table [{}] (seated={}/{})", wait, tableId, seated, min);
    }

    private void scheduleFill(String tableId, int requestedCount, long delayMs) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                fillBotsNow(tableId, requestedCount);
            } catch (Exception ex) {
                log.error("Bot fill failed for table [{}]: {}", tableId, ex.getMessage(), ex);
            } finally {
                pendingFills.remove(tableId);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        pendingFills.put(tableId, future);
    }

    private void fillBotsNow(String tableId, int requestedCount) {
        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null) return;
        if (table.getStatus() != TableStatus.WAITING && table.getStatus() != TableStatus.ROUND_END) {
            log.debug("Skip bot fill — table [{}] status {}", tableId, table.getStatus());
            return;
        }
        if (countHumans(table) == 0) {
            log.debug("Skip bot fill — no humans left on table [{}]", tableId);
            return;
        }

        int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        int min = Math.max(properties.getMinPlayers(), table.getMinPlayers() > 0 ? table.getMinPlayers() : 3);
        int max = table.getMaxPlayers() > 0 ? table.getMaxPlayers() : 6;

        int need;
        if (requestedCount >= 0) {
            need = Math.min(requestedCount, max - seated);
        } else if (properties.isFillToMax()) {
            need = Math.max(0, max - seated);
        } else {
            need = Math.max(0, min - seated);
        }

        if (need <= 0) {
            countdownService.evaluate(tableId);
            return;
        }

        log.info("Matchmaking filling {} bot(s) on table [{}] (seated={}, min={}, max={})",
                need, tableId, seated, min, max);
        List<String> bots = botService.fillSeats(tableId, need);
        log.info("Seated {} bots on table [{}]: {}", bots.size(), tableId, bots);
        // joinTable already triggers afterPublicTableMutation → countdown when >= min
        countdownService.evaluate(tableId);
    }

    private int countHumans(Table table) {
        if (table.getSeatedPlayerIds() == null) return 0;
        int n = 0;
        for (String id : table.getSeatedPlayerIds()) {
            if (!botService.isBot(id)) n++;
        }
        return n;
    }

    /**
     * After a hand / when table empties of humans, remove bots so they don't linger.
     */
    public void cleanupBotsIfNoHumans(String tableId) {
        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null || table.getSeatedPlayerIds() == null) return;
        boolean anyHuman = table.getSeatedPlayerIds().stream().anyMatch(id -> !botService.isBot(id));
        if (!anyHuman) {
            botService.leaveTableBots(tableId);
        }
    }
}
