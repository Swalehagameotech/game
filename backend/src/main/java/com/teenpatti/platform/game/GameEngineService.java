package com.teenpatti.platform.game;

import com.teenpatti.platform.game.dto.GameSessionSummaryDto;
import com.teenpatti.platform.game.distribution.CardDistributionService;
import com.teenpatti.platform.game.distribution.PrivateHandDeal;
import com.teenpatti.platform.game.config.GameBettingConfigResolver;
import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.GameLoopOrchestrator;
import com.teenpatti.platform.game.engine.HandContextManager;
import com.teenpatti.platform.game.betting.BettingLogicService;
import com.teenpatti.platform.game.winner.WinnerCalculationService;
import com.teenpatti.platform.game.shuffle.CardShuffleService;
import com.teenpatti.platform.game.shuffle.ShuffledDeck;
import com.teenpatti.platform.game.turn.TurnManagementService;
import com.teenpatti.platform.game.variant.GameVariantRegistry;
import com.teenpatti.platform.game.variant.GameVariantStrategy;
import com.teenpatti.platform.lobby.config.StakeTierConfig;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import com.teenpatti.platform.websocket.GameBroadcastService;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Single server-authoritative entry point for Teen Patti gameplay.
 * All hand lifecycle and player actions flow through this service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameEngineService {

    private final TableRepository tableRepository;
    private final WalletService walletService;
    private final HandContextManager handContextManager;
    private final GameLoopOrchestrator gameLoopOrchestrator;
    private final GameSessionService gameSessionService;
    private final CardShuffleService cardShuffleService;
    private final CardDistributionService cardDistributionService;
    private final GameVariantRegistry variantRegistry;
    private final StakeTierConfig stakeTierConfig;
    private final WebSocketEventPublisher eventPublisher;
    private final GameBroadcastService gameBroadcastService;
    private final TurnManagementService turnManagementService;
    private final BettingLogicService bettingLogicService;
    private final WinnerCalculationService winnerCalculationService;
    private final GameBettingConfigResolver gameBettingConfigResolver;

    public boolean hasActiveHand(String tableId) {
        return handContextManager.hasActiveHand(tableId) || ensureActiveEngine(tableId).isPresent();
    }

    /**
     * Returns the live engine, restoring it from Mongo {@code game_sessions} when the JVM lost in-memory state.
     */
    public Optional<BettingRoundEngine> ensureActiveEngine(String tableId) {
        Optional<BettingRoundEngine> existing = handContextManager.getEngine(tableId);
        if (existing.isPresent()) {
            if (existing.get().isHandFinished()) {
                handContextManager.clearHand(tableId);
            } else {
                return existing;
            }
        }

        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null || !com.teenpatti.platform.table.TableStatusGroups.isRunning(table.getStatus())) {
            return Optional.empty();
        }

        GameVariantStrategy variantStrategy = variantRegistry.requireStrategy(table.getGameVariant());
        long bootPaise = table.getBootAmountPaise() > 0
                ? table.getBootAmountPaise()
                : gameBettingConfigResolver.resolveEngineConfig().getBootAmountPaise();
        com.teenpatti.platform.game.engine.GameEngineConfig liveConfig =
                gameBettingConfigResolver.resolveEngineConfigForBoot(bootPaise);
        return gameSessionService.restoreEngineFromSession(
                        table,
                        liveConfig,
                        winnerCalculationService.createResolver(variantStrategy))
                .map(engine -> {
                    if (engine.isHandFinished()) {
                        log.warn("Skipping rehydrate of finished hand for table [{}]", tableId);
                        return null;
                    }
                    Instant startedAt = Instant.now();
                    int dealerSeat = table.getDealerSeatIndex() >= 0
                            ? table.getDealerSeatIndex()
                            : handContextManager.getDealerSeat(tableId);
                    handContextManager.registerHand(tableId, engine, startedAt, dealerSeat);
                    turnManagementService.syncTableFromEngine(table, engine);
                    log.info("Rehydrated live hand engine for table [{}] status={}", tableId, table.getStatus());
                    return engine;
                });
    }

    public Optional<GameSessionSummaryDto> getActiveSession(String tableId) {
        return gameSessionService.getActiveSessionSummary(tableId);
    }

    public Table startGame(String hostUserId, String tableId) {
        return startGameInternal(tableId, hostUserId, true);
    }

    public Table startGameAutomatically(String tableId) {
        return startGameInternal(tableId, null, false);
    }

    public com.teenpatti.platform.game.dto.SeeCardsResponse seeCards(String userId, String tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new com.teenpatti.platform.common.exception.TableNotFoundException(
                        "Table not found: " + tableId));

        if (table.getSeatedPlayerIds() == null || !table.getSeatedPlayerIds().contains(userId)) {
            throw new com.teenpatti.platform.common.exception.PlayerNotSeatedException(
                    "Player is not seated at table: " + tableId);
        }

        ensureActiveEngine(tableId).orElseThrow(() ->
                new IllegalStateException("No active hand currently in progress"));

        Optional<BettingRoundEngine> live = handContextManager.getEngine(tableId);
        if (live.isPresent() && live.get().isHandFinished()) {
            handContextManager.clearHand(tableId);
            throw new IllegalStateException("This hand has already ended. Wait for the next round.");
        }

        String rejection = gameLoopOrchestrator.processAction(tableId, userId, "SEE_CARDS", 0L);
        if (rejection != null) {
            throw new IllegalStateException(rejection);
        }

        BettingRoundEngine engine = handContextManager.getEngine(tableId)
                .orElseThrow(() -> new IllegalStateException("No active hand currently in progress"));

        List<com.teenpatti.platform.game.engine.Card> cards = engine.getPlayerCards(userId);
        return com.teenpatti.platform.game.dto.SeeCardsResponse.builder()
                .tableId(tableId)
                .userId(userId)
                .status(com.teenpatti.platform.game.engine.PlayerStatus.SEEN)
                .cards(cards != null ? List.copyOf(cards) : List.of())
                .build();
    }

    public String processAction(String tableId, String userId, String actionType, long betAmount) {
        ensureActiveEngine(tableId);
        return gameLoopOrchestrator.processAction(tableId, userId, actionType, betAmount);
    }

    public void processAutoPack(String tableId, String userId) {
        gameLoopOrchestrator.processAutoPack(tableId, userId);
    }

    public void handlePlayerSeated(String tableId, int currentCount, int minRequired) {
        gameLoopOrchestrator.handlePlayerSeated(tableId, currentCount, minRequired);
    }

    public void handlePlayerLeft(String tableId, int remainingCount, int minRequired) {
        gameLoopOrchestrator.handlePlayerLeft(tableId, remainingCount, minRequired);
    }

    public void handlePlayerLeftMidHand(String tableId, String userId) {
        gameLoopOrchestrator.handlePlayerLeftMidHand(tableId, userId);
    }

    private Table startGameInternal(String tableId, String hostUserId, boolean requireHost) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new com.teenpatti.platform.common.exception.TableNotFoundException("Table not found: " + tableId));

        if (requireHost) {
            if (hostUserId == null || table.getHostId() == null || !table.getHostId().equals(hostUserId)) {
                throw new IllegalStateException("Only the table host can start the game.");
            }
        }

        TableStatus status = table.getStatus();
        if (status != TableStatus.WAITING
                && status != TableStatus.ROUND_END
                && status != TableStatus.NEXT_ROUND
                && status != TableStatus.COUNTDOWN) {
            throw new IllegalStateException("Game cannot be started in status: " + status);
        }

        if (handContextManager.hasActiveHand(tableId)) {
            throw new IllegalStateException("A hand is already in progress on this table.");
        }

        List<String> seated = table.getSeatedPlayerIds() != null
                ? new ArrayList<>(table.getSeatedPlayerIds())
                : new ArrayList<>();
        com.teenpatti.platform.admin.betting.BettingConfiguration activeConfig =
                gameBettingConfigResolver.resolveActiveConfiguration();
        // Table boot is authoritative (player selected ₹10 / ₹50 / etc). Never overwrite with admin primary boot.
        long bootPaise = table.getBootAmountPaise() > 0
                ? table.getBootAmountPaise()
                : Math.max(100L, activeConfig.getBootAmount());
        com.teenpatti.platform.game.engine.GameEngineConfig liveConfig =
                gameBettingConfigResolver.resolveEngineConfigForBoot(bootPaise);
        table.setMinPlayers(activeConfig.getMinimumPlayers());
        table.setMaxPlayers(activeConfig.getMaximumPlayers());
        table.setTurnTimeoutSeconds(activeConfig.getTurnTimer());
        int minRequired = table.getMinPlayers() > 0 ? table.getMinPlayers() : 3;

        if (seated.size() < minRequired) {
            throw new IllegalStateException(
                    "Minimum " + minRequired + " players required. Currently seated: " + seated.size());
        }

        GameVariantStrategy variantStrategy = variantRegistry.requireStrategy(table.getGameVariant());
        validateAllBalances(seated, bootPaise);

        String handId = UUID.randomUUID().toString();
        Instant handStart = Instant.now();

        // Increment round BEFORE opening session so session + history share the same number
        int nextRound = Math.max(0, table.getRoundNumber()) + 1;
        table.setRoundNumber(nextRound);
        table.setStatus(TableStatus.STARTING);
        table.setCurrentHandId(handId);
        table.setUpdatedAt(Instant.now());
        tableRepository.save(table);

        eventPublisher.publishHostStartedGame(tableId, Map.of(
                "message", requireHost ? "Host started the game." : "Game starting automatically.",
                "hostId", table.getHostId(),
                "autoStart", !requireHost
        ));
        eventPublisher.publishTableUpdated(tableId, table);

        int dealerSeat = handContextManager.rotateDealerSeat(tableId, seated.size());
        table.setDealerSeatIndex(dealerSeat);

        BettingRoundEngine engine = new BettingRoundEngine(
                liveConfig,
                winnerCalculationService.createResolver(variantStrategy));

        ShuffledDeck shuffled = cardShuffleService.createShuffledDeck(table.getGameVariant());
        PrivateHandDeal deal = cardDistributionService.dealPrivateHands(shuffled.getDeck(), seated);
        engine.startHand(seated, deal.getHandsByPlayerId());
        int firstTurnSeat = turnManagementService.resolveFirstTurnSeatIndex(dealerSeat, seated.size());
        String firstTurnUserId = seated.get(firstTurnSeat);
        engine.setStartingTurnPlayer(firstTurnUserId);
        handContextManager.registerHand(tableId, engine, handStart, dealerSeat);
        gameSessionService.openSession(table, handId, engine, shuffled.getDeck(), dealerSeat, shuffled.getShuffleId());

        long walletPotCollected = bettingLogicService.collectBoot(tableId, handId, seated, bootPaise);
        // Engine already included boot chips in pot during startHand — keep table/engine in lockstep.
        long enginePot = engine.getPotPaise();
        if (enginePot != walletPotCollected) {
            log.warn("Boot pot mismatch on table [{}]: engine={} walletCollected={} — using engine pot",
                    tableId, enginePot, walletPotCollected);
        }
        table.setPotPaise(enginePot);
        table.setActivePlayerIds(new ArrayList<>(seated));
        table.setBlindPlayerIds(new ArrayList<>(seated));
        table.setSeenPlayerIds(new ArrayList<>());
        table.setPackedPlayerIds(new ArrayList<>());
        table.setWinnerUserId(null);
        table.setCurrentStakePaise(bootPaise);
        table.setLeftMidHandPlayerIds(new ArrayList<>());
        table.setStatus(TableStatus.RUNNING);
        table.setCountdownSeconds(0);
        table.setUpdatedAt(Instant.now());

        turnManagementService.syncTableFromEngine(table, engine);
        tableRepository.save(table);

        eventPublisher.publishCardsDistributed(tableId, Map.of(
                "message", "Cards dealt privately to all players.",
                "handId", handId,
                "cardsPerPlayer", com.teenpatti.platform.game.engine.DeckConstants.CARDS_PER_HAND,
                "playersDealt", seated.size()
        ));
        eventPublisher.publishDealerSelected(tableId, dealerSeat);
        eventPublisher.publishPotUpdated(tableId, engine.getPotPaise());
        eventPublisher.publishGameRunning(tableId, Map.of(
                "tableId", tableId,
                "handId", handId,
                "potPaise", engine.getPotPaise(),
                "bootPaise", bootPaise,
                "seatedPlayers", seated
        ));
        eventPublisher.publishGameStarted(tableId, Map.of(
                "tableId", tableId,
                "handId", handId,
                "bootPaise", bootPaise,
                "potPaise", engine.getPotPaise(),
                "dealerSeatIndex", dealerSeat,
                "variant", variantStrategy.getVariant().name()
        ));
        eventPublisher.publishTableUpdated(tableId, table);

        for (String playerId : seated) {
            gameBroadcastService.deliverPrivateHand(tableId, playerId);
        }

        gameLoopOrchestrator.beginTurn(tableId, firstTurnUserId, firstTurnSeat);

        bettingLogicService.publishBettingStateForTable(table, engine);

        log.info("Game engine started hand [{}] on table [{}], shuffle [{}], variant {}, {} players, pot {} paise",
                handId, tableId, shuffled.getShuffleId(), variantStrategy.getVariant(), seated.size(), engine.getPotPaise());

        return table;
    }

    private long resolveBootPaise(Table table) {
        if (table.getBootAmountPaise() > 0) {
            return table.getBootAmountPaise();
        }
        return stakeTierConfig.getMinBuyInPaise(table.getStakeTier());
    }

    private void validateAllBalances(List<String> seated, long bootPaise) {
        for (String playerId : seated) {
            WalletBalanceResponse balance = walletService.getBalance(playerId);
            if (balance.getBalancePaise() < bootPaise) {
                throw new com.teenpatti.platform.common.exception.InsufficientBalanceException(
                        "Player " + playerId + " has insufficient balance for boot amount " + bootPaise);
            }
        }
    }
}
