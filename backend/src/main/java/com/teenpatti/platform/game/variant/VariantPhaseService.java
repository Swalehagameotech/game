package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.common.exception.InsufficientBalanceException;
import com.teenpatti.platform.game.betting.BettingLogicService;
import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.GameLoopOrchestrator;
import com.teenpatti.platform.game.engine.HandContextManager;
import com.teenpatti.platform.game.engine.Rank;
import com.teenpatti.platform.game.winner.WinnerCalculationService;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import com.teenpatti.platform.websocket.GameBroadcastService;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestrates pre-betting variant phases (discard-one, auction) before the first turn begins.
 */
@Slf4j
@Service
public class VariantPhaseService {

    private final VariantPhaseTracker phaseTracker;
    private final HandContextManager handContextManager;
    private final TableRepository tableRepository;
    private final WalletService walletService;
    private final BettingLogicService bettingLogicService;
    private final WinnerCalculationService winnerCalculationService;
    private final GameVariantRegistry variantRegistry;
    private final WebSocketEventPublisher eventPublisher;
    private final GameBroadcastService gameBroadcastService;
    private final GameLoopOrchestrator gameLoopOrchestrator;

    public VariantPhaseService(
            VariantPhaseTracker phaseTracker,
            HandContextManager handContextManager,
            TableRepository tableRepository,
            WalletService walletService,
            @Lazy BettingLogicService bettingLogicService,
            WinnerCalculationService winnerCalculationService,
            GameVariantRegistry variantRegistry,
            WebSocketEventPublisher eventPublisher,
            @Lazy GameBroadcastService gameBroadcastService,
            @Lazy GameLoopOrchestrator gameLoopOrchestrator) {
        this.phaseTracker = phaseTracker;
        this.handContextManager = handContextManager;
        this.tableRepository = tableRepository;
        this.walletService = walletService;
        this.bettingLogicService = bettingLogicService;
        this.winnerCalculationService = winnerCalculationService;
        this.variantRegistry = variantRegistry;
        this.eventPublisher = eventPublisher;
        this.gameBroadcastService = gameBroadcastService;
        this.gameLoopOrchestrator = gameLoopOrchestrator;
    }

    public PreBettingPhase getPhase(String tableId) {
        return phaseTracker.getPhase(tableId);
    }

    public boolean isPreBettingPhase(String tableId) {
        return phaseTracker.isPreBettingPhase(tableId);
    }

    public boolean isDiscardPhase(String tableId) {
        return phaseTracker.isDiscardPhase(tableId);
    }

    public boolean isAuctionPhase(String tableId) {
        return phaseTracker.isAuctionPhase(tableId);
    }

    public boolean hasPendingDiscard(String tableId, String userId) {
        return phaseTracker.hasPendingDiscard(tableId, userId);
    }

    public boolean canActInAuction(String tableId, String userId) {
        return phaseTracker.canActInAuction(tableId, userId);
    }

    public void startDiscardPhase(
            String tableId,
            List<String> seated,
            String firstTurnUserId,
            int firstTurnSeat) {
        phaseTracker.beginDiscardPhase(tableId, seated, firstTurnUserId, firstTurnSeat);
        eventPublisher.publishDiscardPhaseStarted(tableId, Map.of(
                "message", "Discard one card before betting begins.",
                "playersPending", seated.size()));
        log.info("Discard phase started on table [{}] for {} players", tableId, seated.size());
    }

    public void startAuctionPhase(
            String tableId,
            List<String> seated,
            String firstTurnUserId,
            int firstTurnSeat,
            long minBidPaise) {
        phaseTracker.beginAuctionPhase(tableId, seated, firstTurnUserId, firstTurnSeat, minBidPaise);
        eventPublisher.publishAuctionStarted(tableId, Map.of(
                "message", "Auction for joker — highest bidder wins the wild rank.",
                "minBidPaise", minBidPaise,
                "playersPending", seated.size()));
        log.info("Auction phase started on table [{}], min bid {} paise", tableId, minBidPaise);
    }

    public String processDiscard(String tableId, String userId, int cardIndex) {
        if (!isDiscardPhase(tableId)) {
            return "Discard phase is not active";
        }
        if (!hasPendingDiscard(tableId, userId)) {
            return "You have already discarded";
        }

        BettingRoundEngine engine = handContextManager.requireEngine(tableId);
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalStateException("Table not found"));

        List<com.teenpatti.platform.game.engine.Card> hand = engine.getPlayerCards(userId);
        if (hand == null || hand.size() <= 3) {
            return "No card to discard";
        }
        if (cardIndex < 0 || cardIndex >= hand.size()) {
            return "Invalid card index";
        }

        if (!engine.discardCard(userId, cardIndex)) {
            return "Discard failed";
        }

        Set<String> pending = phaseTracker.getPendingDiscards(tableId);
        if (pending != null) {
            pending.remove(userId);
        }

        eventPublisher.publishCardDiscarded(tableId, Map.of(
                "userId", userId,
                "cardIndex", cardIndex,
                "remainingPending", pending != null ? pending.size() : 0));

        gameBroadcastService.deliverPrivateHand(tableId, userId);
        syncPotToTable(table, engine);
        bettingLogicService.publishBettingStateForTable(table, engine);

        if (pending == null || pending.isEmpty()) {
            completePreBettingPhase(tableId);
        }
        return null;
    }

    public String processAuctionBid(String tableId, String userId, long amountPaise) {
        if (!isAuctionPhase(tableId)) {
            return "Auction is not active";
        }
        if (!canActInAuction(tableId, userId)) {
            return "You have already acted in the auction";
        }

        long minBid = phaseTracker.getAuctionMinBidPaise(tableId);
        long currentHigh = phaseTracker.getAuctionHighBidPaise(tableId);
        long required = currentHigh > 0 ? currentHigh + 1 : minBid;
        if (amountPaise < required) {
            return "Bid must be at least " + required + " paise";
        }

        WalletBalanceResponse balance = walletService.getBalance(userId);
        if (balance.getBalancePaise() < amountPaise) {
            throw new InsufficientBalanceException("Insufficient balance for auction bid");
        }

        BettingRoundEngine engine = handContextManager.requireEngine(tableId);
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalStateException("Table not found"));
        String handId = table.getCurrentHandId() != null ? table.getCurrentHandId() : tableId;

        bettingLogicService.debitBet(tableId, handId, userId, amountPaise);
        engine.addMetaBetToPot(userId, amountPaise);

        phaseTracker.recordAuctionBid(tableId, userId, amountPaise);

        Set<String> pending = phaseTracker.getPendingAuctionActors(tableId);
        if (pending != null) {
            pending.remove(userId);
        }

        eventPublisher.publishAuctionBid(tableId, Map.of(
                "userId", userId,
                "amountPaise", amountPaise,
                "potPaise", engine.getPotPaise(),
                "highBidPaise", amountPaise,
                "highBidderId", userId));

        eventPublisher.publishPotUpdated(tableId, engine.getPotPaise());
        syncPotToTable(table, engine);
        bettingLogicService.publishBettingStateForTable(table, engine);

        if (pending == null || pending.isEmpty()) {
            finishAuction(tableId);
        }
        return null;
    }

    public String processAuctionPass(String tableId, String userId) {
        if (!isAuctionPhase(tableId)) {
            return "Auction is not active";
        }
        if (!canActInAuction(tableId, userId)) {
            return "You have already acted in the auction";
        }

        BettingRoundEngine engine = handContextManager.requireEngine(tableId);
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalStateException("Table not found"));

        Set<String> pending = phaseTracker.getPendingAuctionActors(tableId);
        if (pending != null) {
            pending.remove(userId);
        }

        eventPublisher.publishAuctionBid(tableId, Map.of(
                "userId", userId,
                "passed", true,
                "highBidPaise", phaseTracker.getAuctionHighBidPaise(tableId),
                "highBidderId", phaseTracker.getAuctionHighBidderId(tableId)));

        bettingLogicService.publishBettingStateForTable(table, engine);

        if (pending == null || pending.isEmpty()) {
            finishAuction(tableId);
        }
        return null;
    }

    private void finishAuction(String tableId) {
        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null) {
            clear(tableId);
            return;
        }

        BettingRoundEngine engine = handContextManager.requireEngine(tableId);
        String winnerId = phaseTracker.getAuctionHighBidderId(tableId);
        Rank jokerRank = pickRandomRank();
        table.setJokerRank(jokerRank.name());
        table.setAuctionWinner(winnerId);
        table.setUpdatedAt(Instant.now());
        tableRepository.save(table);

        GameVariantStrategy strategy = resolveStrategyWithJoker(table);
        engine.setWinnerResolver(winnerCalculationService.createResolver(strategy));

        eventPublisher.publishJokerRevealed(tableId, jokerRank.name());
        eventPublisher.publishAuctionEnded(tableId, Map.of(
                "winnerUserId", winnerId != null ? winnerId : "",
                "highBidPaise", phaseTracker.getAuctionHighBidPaise(tableId),
                "jokerRank", jokerRank.name(),
                "potPaise", engine.getPotPaise()));

        log.info("Auction ended on table [{}]: winner={}, joker={}", tableId, winnerId, jokerRank.name());
        completePreBettingPhase(tableId);
    }

    private void completePreBettingPhase(String tableId) {
        var pending = phaseTracker.removePendingBettingStart(tableId);
        phaseTracker.clearPhaseState(tableId);

        Table table = tableRepository.findById(tableId).orElse(null);
        BettingRoundEngine engine = handContextManager.getEngine(tableId).orElse(null);
        if (table == null || engine == null || pending.isEmpty()) {
            return;
        }

        GameVariantStrategy variantStrategy = variantRegistry.requireStrategy(table.getGameVariant());
        if (table.getGameVariant() == com.teenpatti.platform.table.GameVariant.JOKER
                || table.getGameVariant() == com.teenpatti.platform.table.GameVariant.AUCTION) {
            variantStrategy = resolveStrategyWithJoker(table);
        }
        variantStrategy.beforeBetting(table);
        gameLoopOrchestrator.beginTurn(tableId, pending.get().firstTurnUserId(), pending.get().firstTurnSeat());
        bettingLogicService.publishBettingStateForTable(table, engine);
        gameBroadcastService.broadcastTableState(tableId);
    }

    private GameVariantStrategy resolveStrategyWithJoker(Table table) {
        GameVariantStrategy base = variantRegistry.requireStrategy(table.getGameVariant());
        String rank = table.getJokerRank();
        if (rank != null && !rank.isBlank()) {
            return base.withRoundContext(Map.of("jokerRank", rank));
        }
        return base;
    }

    private void syncPotToTable(Table table, BettingRoundEngine engine) {
        table.setPotPaise(engine.getPotPaise());
        table.setUpdatedAt(Instant.now());
        tableRepository.save(table);
    }

    private Rank pickRandomRank() {
        Rank[] values = Rank.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    public void clear(String tableId) {
        phaseTracker.clear(tableId);
    }
}
