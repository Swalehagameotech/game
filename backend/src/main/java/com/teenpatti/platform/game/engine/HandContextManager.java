package com.teenpatti.platform.game.engine;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single in-memory registry for active hand engines per table.
 * Replaces duplicate engine maps in WebSocket handler and game loop orchestrator.
 */
@Component
public class HandContextManager {

    private final ConcurrentHashMap<String, BettingRoundEngine> engines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> handStartTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> dealerSeats = new ConcurrentHashMap<>();

    public void registerHand(String tableId, BettingRoundEngine engine, Instant startedAt, int dealerSeatIndex) {
        engines.put(tableId, engine);
        handStartTimes.put(tableId, startedAt);
        dealerSeats.put(tableId, dealerSeatIndex);
    }

    public Optional<BettingRoundEngine> getEngine(String tableId) {
        return Optional.ofNullable(engines.get(tableId));
    }

    public BettingRoundEngine requireEngine(String tableId) {
        BettingRoundEngine engine = engines.get(tableId);
        if (engine == null) {
            throw new IllegalStateException("No active hand for table: " + tableId);
        }
        return engine;
    }

    public boolean hasActiveHand(String tableId) {
        return engines.containsKey(tableId);
    }

    public Instant getHandStartTime(String tableId) {
        return handStartTimes.getOrDefault(tableId, Instant.now());
    }

    public int getDealerSeat(String tableId) {
        return dealerSeats.getOrDefault(tableId, 0);
    }

    public int rotateDealerSeat(String tableId, int seatedCount) {
        int current = dealerSeats.getOrDefault(tableId, 0);
        int next = seatedCount > 0 ? (current + 1) % seatedCount : 0;
        dealerSeats.put(tableId, next);
        return next;
    }

    public void clearHand(String tableId) {
        engines.remove(tableId);
        handStartTimes.remove(tableId);
    }
}
