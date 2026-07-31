package com.teenpatti.platform.game.variant;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory pre-betting phase state (discard / auction). No gameplay service dependencies
 * so betting projection can read phase without circular bean references.
 */
@Component
public class VariantPhaseTracker {

    public record PendingBettingStart(String firstTurnUserId, int firstTurnSeat) {}

    public record AuctionSnapshot(long highBidPaise, String highBidderId, long minBidPaise) {}

    private final ConcurrentHashMap<String, PreBettingPhase> phases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> pendingDiscards = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> pendingAuctionActors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> auctionHighBidPaise = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> auctionHighBidderId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> auctionMinBidPaise = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingBettingStart> pendingBettingStart = new ConcurrentHashMap<>();

    public PreBettingPhase getPhase(String tableId) {
        return phases.getOrDefault(tableId, PreBettingPhase.NONE);
    }

    public boolean isPreBettingPhase(String tableId) {
        PreBettingPhase phase = getPhase(tableId);
        return phase == PreBettingPhase.DISCARD || phase == PreBettingPhase.AUCTION;
    }

    public boolean isDiscardPhase(String tableId) {
        return getPhase(tableId) == PreBettingPhase.DISCARD;
    }

    public boolean isAuctionPhase(String tableId) {
        return getPhase(tableId) == PreBettingPhase.AUCTION;
    }

    public boolean hasPendingDiscard(String tableId, String userId) {
        Set<String> pending = pendingDiscards.get(tableId);
        return pending != null && pending.contains(userId);
    }

    public boolean canActInAuction(String tableId, String userId) {
        Set<String> pending = pendingAuctionActors.get(tableId);
        return pending != null && pending.contains(userId);
    }

    public Optional<AuctionSnapshot> getAuctionSnapshot(String tableId) {
        if (!isAuctionPhase(tableId)) {
            return Optional.empty();
        }
        return Optional.of(new AuctionSnapshot(
                auctionHighBidPaise.getOrDefault(tableId, 0L),
                auctionHighBidderId.get(tableId),
                auctionMinBidPaise.getOrDefault(tableId, 0L)));
    }

    public void beginDiscardPhase(String tableId, List<String> seated, String firstTurnUserId, int firstTurnSeat) {
        phases.put(tableId, PreBettingPhase.DISCARD);
        pendingDiscards.put(tableId, new HashSet<>(seated));
        pendingBettingStart.put(tableId, new PendingBettingStart(firstTurnUserId, firstTurnSeat));
    }

    public void beginAuctionPhase(
            String tableId,
            List<String> seated,
            String firstTurnUserId,
            int firstTurnSeat,
            long minBidPaise) {
        phases.put(tableId, PreBettingPhase.AUCTION);
        pendingAuctionActors.put(tableId, new HashSet<>(seated));
        auctionHighBidPaise.put(tableId, 0L);
        auctionHighBidderId.remove(tableId);
        auctionMinBidPaise.put(tableId, minBidPaise);
        pendingBettingStart.put(tableId, new PendingBettingStart(firstTurnUserId, firstTurnSeat));
    }

    public Set<String> getPendingDiscards(String tableId) {
        return pendingDiscards.get(tableId);
    }

    public Set<String> getPendingAuctionActors(String tableId) {
        return pendingAuctionActors.get(tableId);
    }

    public long getAuctionHighBidPaise(String tableId) {
        return auctionHighBidPaise.getOrDefault(tableId, 0L);
    }

    public String getAuctionHighBidderId(String tableId) {
        return auctionHighBidderId.get(tableId);
    }

    public long getAuctionMinBidPaise(String tableId) {
        return auctionMinBidPaise.getOrDefault(tableId, 0L);
    }

    public void recordAuctionBid(String tableId, String userId, long amountPaise) {
        auctionHighBidPaise.put(tableId, amountPaise);
        auctionHighBidderId.put(tableId, userId);
    }

    public Optional<PendingBettingStart> removePendingBettingStart(String tableId) {
        return Optional.ofNullable(pendingBettingStart.remove(tableId));
    }

    public void clear(String tableId) {
        clearPhaseState(tableId);
        pendingBettingStart.remove(tableId);
    }

    public void clearPhaseState(String tableId) {
        phases.remove(tableId);
        pendingDiscards.remove(tableId);
        pendingAuctionActors.remove(tableId);
        auctionHighBidPaise.remove(tableId);
        auctionHighBidderId.remove(tableId);
        auctionMinBidPaise.remove(tableId);
    }
}
