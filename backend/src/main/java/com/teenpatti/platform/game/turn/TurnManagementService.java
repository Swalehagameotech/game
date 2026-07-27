package com.teenpatti.platform.game.turn;

import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.HandContextManager;
import com.teenpatti.platform.game.engine.PlayerStatus;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Server-authoritative turn management: dealer position, active turn, player groups,
 * turn timer, and broadcast on every change.
 */
@Slf4j
@Service
public class TurnManagementService {

    private final TableRepository tableRepository;
    private final HandContextManager handContextManager;
    private final WebSocketEventPublisher eventPublisher;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeTurnTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> turnDeadlines = new ConcurrentHashMap<>();

    @Value("${app.game.turn-timeout-seconds:20}")
    private int turnTimeoutSeconds;

    public TurnManagementService(
            TableRepository tableRepository,
            HandContextManager handContextManager,
            WebSocketEventPublisher eventPublisher) {
        this.tableRepository = tableRepository;
        this.handContextManager = handContextManager;
        this.eventPublisher = eventPublisher;
    }

    public int getTurnTimeoutSeconds() {
        return turnTimeoutSeconds;
    }

    /**
     * First action seat is immediately clockwise from the dealer.
     */
    public int resolveFirstTurnSeatIndex(int dealerSeatIndex, int seatedCount) {
        if (seatedCount <= 0) {
            return 0;
        }
        return (dealerSeatIndex + 1) % seatedCount;
    }

    public void syncTableFromEngine(Table table, BettingRoundEngine engine) {
        if (table == null || engine == null) {
            return;
        }
        table.setActivePlayerIds(new ArrayList<>(engine.getActivePlayerIds()));
        table.setBlindPlayerIds(new ArrayList<>(engine.getPlayerIdsByStatus(PlayerStatus.BLIND)));
        table.setSeenPlayerIds(new ArrayList<>(engine.getPlayerIdsByStatus(PlayerStatus.SEEN)));
        table.setPackedPlayerIds(new ArrayList<>(engine.getPlayerIdsByStatus(PlayerStatus.PACKED)));
        table.setPotPaise(engine.getPotPaise());
        table.setCurrentStakePaise(engine.getCurrentBaseStakePaise());
        String turnUserId = engine.getCurrentTurnPlayerId();
        table.setCurrentTurnUserId(turnUserId);
        table.setUpdatedAt(Instant.now());
        tableRepository.save(table);
    }

    public TurnState buildTurnState(Table table, BettingRoundEngine engine) {
        if (table == null) {
            throw new IllegalArgumentException("Table must not be null");
        }

        List<String> seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
        String turnUserId = engine != null ? engine.getCurrentTurnPlayerId() : table.getCurrentTurnUserId();
        int turnSeat = turnUserId != null ? seated.indexOf(turnUserId) : -1;
        int dealerSeat = table.getDealerSeatIndex();
        if (dealerSeat < 0 && table.getId() != null) {
            dealerSeat = handContextManager.getDealerSeat(table.getId());
        }

        Instant deadline = table.getId() != null ? turnDeadlines.get(table.getId()) : null;
        int secondsRemaining = computeSecondsRemaining(deadline);

        return TurnState.builder()
                .tableId(table.getId())
                .currentTurnUserId(turnUserId)
                .currentTurnSeatIndex(turnSeat)
                .dealerSeatIndex(dealerSeat)
                .potPaise(engine != null ? engine.getPotPaise() : table.getPotPaise())
                .currentBaseStakePaise(engine != null ? engine.getCurrentBaseStakePaise() : table.getCurrentStakePaise())
                .turnTimeoutSeconds(turnTimeoutSeconds)
                .turnSecondsRemaining(secondsRemaining)
                .turnDeadlineAt(deadline)
                .activePlayerIds(engine != null
                        ? engine.getActivePlayerIds()
                        : copyList(table.getActivePlayerIds()))
                .blindPlayerIds(engine != null
                        ? engine.getPlayerIdsByStatus(PlayerStatus.BLIND)
                        : copyList(table.getBlindPlayerIds()))
                .seenPlayerIds(engine != null
                        ? engine.getPlayerIdsByStatus(PlayerStatus.SEEN)
                        : copyList(table.getSeenPlayerIds()))
                .packedPlayerIds(engine != null
                        ? engine.getPlayerIdsByStatus(PlayerStatus.PACKED)
                        : copyList(table.getPackedPlayerIds()))
                .build();
    }

    public void beginTurn(String tableId, String userId, int seatIndex, Runnable onTimeout) {
        cancelTurn(tableId);

        Instant deadline = Instant.now().plusSeconds(turnTimeoutSeconds);
        turnDeadlines.put(tableId, deadline);

        Optional<Table> tableOpt = tableRepository.findById(tableId);
        if (tableOpt.isEmpty()) {
            return;
        }
        Table table = tableOpt.get();
        table.setCurrentTurnUserId(userId);
        table.setUpdatedAt(Instant.now());
        tableRepository.save(table);

        BettingRoundEngine engine = handContextManager.getEngine(tableId).orElse(null);
        TurnState turnState = buildTurnState(table, engine);

        log.info("Turn started for user [{}] at seat [{}] on table [{}], deadline in {}s",
                userId, seatIndex, tableId, turnTimeoutSeconds);

        eventPublisher.publishTurnStarted(tableId, userId, seatIndex, turnTimeoutSeconds);
        eventPublisher.publishTurnState(tableId, turnState);
        eventPublisher.publishTableUpdated(tableId, table);

        ScheduledFuture<?> timerTask = scheduler.schedule(() -> {
            log.warn("Turn timeout for user [{}] on table [{}]", userId, tableId);
            if (onTimeout != null) {
                onTimeout.run();
            }
        }, turnTimeoutSeconds, TimeUnit.SECONDS);

        activeTurnTimers.put(tableId, timerTask);
    }

    public void endTurn(String tableId) {
        cancelTurn(tableId);
        turnDeadlines.remove(tableId);

        tableRepository.findById(tableId).ifPresent(table -> {
            String previousUserId = table.getCurrentTurnUserId();
            table.setCurrentTurnUserId(null);
            table.setUpdatedAt(Instant.now());
            tableRepository.save(table);
            eventPublisher.publishTurnEnded(tableId, previousUserId);
        });
    }

    public void cancelTurn(String tableId) {
        ScheduledFuture<?> timer = activeTurnTimers.remove(tableId);
        if (timer != null) {
            timer.cancel(true);
        }
    }

    public Optional<Instant> getTurnDeadline(String tableId) {
        return Optional.ofNullable(turnDeadlines.get(tableId));
    }

    public int getTurnSecondsRemaining(String tableId) {
        return computeSecondsRemaining(turnDeadlines.get(tableId));
    }

    private int computeSecondsRemaining(Instant deadline) {
        if (deadline == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), deadline).getSeconds();
        return (int) Math.max(0, seconds);
    }

    private List<String> copyList(List<String> source) {
        return source != null ? new ArrayList<>(source) : List.of();
    }
}
