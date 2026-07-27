package com.teenpatti.platform.game.engine;

import com.teenpatti.platform.game.GameSessionService;
import com.teenpatti.platform.game.betting.BettingLogicService;
import com.teenpatti.platform.game.winner.WinnerCalculationService;
import com.teenpatti.platform.game.winner.WinnerSnapshot;
import com.teenpatti.platform.game.turn.TurnManagementService;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.GameBroadcastService;
import com.teenpatti.platform.websocket.HandSettlementService;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Server-authoritative gameplay loop: turn timers, player actions, hand settlement.
 * Game start (shuffle/deal/boot) is handled by {@link com.teenpatti.platform.game.GameStartService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameLoopOrchestrator {

    private final TableRepository tableRepository;
    private final UserRepository userRepository;
    private final HandContextManager handContextManager;
    private final HandSettlementService handSettlementService;
    private final GameSessionService gameSessionService;
    private final TurnManagementService turnManagementService;
    private final BettingLogicService bettingLogicService;
    private final WinnerCalculationService winnerCalculationService;
    private final WebSocketEventPublisher eventPublisher;
    private final GameBroadcastService gameBroadcastService;

    /**
     * No auto-start when players join — host must explicitly start the game.
     */
    public void handlePlayerSeated(String tableId, int currentCount, int minRequired) {
        log.debug("Player seated on table [{}]: {}/{}", tableId, currentCount, minRequired);
    }

    public void handlePlayerLeft(String tableId, int remainingCount, int minRequired) {
        if (remainingCount < minRequired) {
            Optional<Table> opt = tableRepository.findById(tableId);
            if (opt.isPresent()) {
                Table table = opt.get();
                if (table.getStatus() == TableStatus.WAITING || table.getStatus() == TableStatus.ROUND_END) {
                    table.setStatus(TableStatus.WAITING);
                    tableRepository.save(table);
                    eventPublisher.publishTableUpdated(tableId, table);
                }
            }
        }
    }

    public void beginTurn(String tableId, String userId, int seatIndex) {
        startTurn(tableId, userId, seatIndex);
    }

    /**
     * Applies a player action. Returns rejection reason, or null on success.
     */
    public synchronized String processAction(String tableId, String userId, String actionType, long betAmount) {
        Optional<BettingRoundEngine> engineOpt = handContextManager.getEngine(tableId);
        if (engineOpt.isEmpty()) {
            return "No active hand currently in progress";
        }
        BettingRoundEngine engine = engineOpt.get();

        cancelTurnTimer(tableId);

        try {
            Optional<Table> optTable = tableRepository.findById(tableId);
            Table table = optTable.orElse(null);

            if ("SEE_CARDS".equalsIgnoreCase(actionType)) {
                engine.applyAction(PlayerAction.of(userId, PlayerActionType.SEE_CARDS));
                updateTableAfterSee(tableId, table, userId, engine);
                turnManagementService.syncTableFromEngine(table, engine);
                eventPublisher.publishSeenPlayed(tableId, userId);
                eventPublisher.publishPlayerAction(tableId, userId, actionType, 0L, engine.getPotPaise());
                syncSession(table, engine);
                if (table != null) {
                    bettingLogicService.publishBettingState(table, engine, userId);
                }
                gameBroadcastService.broadcastTableState(tableId);
                return null;
            }

            String handId = table != null ? table.getCurrentHandId() : null;

            if ("CHAAL".equalsIgnoreCase(actionType) || "PLAY_BLIND".equalsIgnoreCase(actionType)
                    || "CALL".equalsIgnoreCase(actionType)) {
                long bet = bettingLogicService.resolveBetAmount(actionType, engine, userId, betAmount);
                bettingLogicService.debitBet(tableId, handId, userId, bet);
                PlayerActionType type = "PLAY_BLIND".equalsIgnoreCase(actionType)
                        ? PlayerActionType.PLAY_BLIND
                        : PlayerActionType.CHAAL;
                engine.applyAction(PlayerAction.of(userId, type, bet));
                updateTablePot(table, engine, userId + " played Chaal");
                turnManagementService.syncTableFromEngine(table, engine);
                eventPublisher.publishBlindPlayed(tableId, userId, bet, engine.getPotPaise());
                eventPublisher.publishPlayerAction(tableId, userId, actionType, bet, engine.getPotPaise());
            } else if ("RAISE".equalsIgnoreCase(actionType)) {
                long bet = bettingLogicService.resolveBetAmount(actionType, engine, userId, betAmount);
                bettingLogicService.debitBet(tableId, handId, userId, bet);
                engine.applyAction(PlayerAction.of(userId, PlayerActionType.RAISE, bet));
                updateTablePot(table, engine, userId + " raised");
                turnManagementService.syncTableFromEngine(table, engine);
                eventPublisher.publishRaisePlayed(tableId, userId, bet, engine.getPotPaise());
                eventPublisher.publishPlayerAction(tableId, userId, actionType, bet, engine.getPotPaise());
            } else if ("PACK".equalsIgnoreCase(actionType)) {
                engine.applyAction(PlayerAction.of(userId, PlayerActionType.PACK));
                updateTableAfterPack(table, userId, engine);
                turnManagementService.syncTableFromEngine(table, engine);
                eventPublisher.publishPackPlayed(tableId, userId, false);
                eventPublisher.publishPlayerAction(tableId, userId, actionType, 0L, engine.getPotPaise());
            } else if ("SIDE_SHOW_REQUEST".equalsIgnoreCase(actionType)) {
                eventPublisher.publishSideShowRequested(tableId, userId, "target");
                cancelTurnTimer(tableId);
                String nextUser = engine.getCurrentTurnPlayerId();
                if (table != null && nextUser != null) {
                    int nextSeat = table.getSeatedPlayerIds().indexOf(nextUser);
                    startTurn(tableId, nextUser, nextSeat);
                }
                return null;
            } else if ("SHOW".equalsIgnoreCase(actionType)) {
                long required = bettingLogicService.resolveBetAmount(actionType, engine, userId, betAmount);
                bettingLogicService.debitBet(tableId, handId, userId, required);
                engine.applyAction(PlayerAction.of(userId, PlayerActionType.SHOW, required));
                if (table != null) {
                    table.setStatus(TableStatus.SHOW);
                    table.setLastAction(userId + " requested Show");
                    tableRepository.save(table);
                    eventPublisher.publishTableUpdated(tableId, table);
                }
                eventPublisher.publishShowRequested(tableId, Map.of("requesterId", userId));
                eventPublisher.publishPlayerAction(tableId, userId, actionType, required, engine.getPotPaise());
            } else {
                return "Unsupported action: " + actionType;
            }

            eventPublisher.publishPotUpdated(tableId, engine.getPotPaise());
            syncSession(table, engine);
            if (table != null) {
                bettingLogicService.publishBettingStateForTable(table, engine);
            }

            if (engine.isHandFinished()) {
                handleHandFinished(tableId, engine);
            } else {
                gameBroadcastService.broadcastTableState(tableId);
                String nextUser = engine.getCurrentTurnPlayerId();
                if (table != null && nextUser != null) {
                    int nextSeat = table.getSeatedPlayerIds().indexOf(nextUser);
                    startTurn(tableId, nextUser, nextSeat);
                }
            }
            return null;
        } catch (InvalidActionException ex) {
            log.warn("Action [{}] rejected for user [{}] on table [{}]: {}", actionType, userId, tableId, ex.getMessage());
            String nextUser = engine.getCurrentTurnPlayerId();
            Optional<Table> optTable = tableRepository.findById(tableId);
            if (optTable.isPresent() && nextUser != null) {
                int nextSeat = optTable.get().getSeatedPlayerIds().indexOf(nextUser);
                startTurn(tableId, nextUser, nextSeat);
            }
            return ex.getMessage() != null ? ex.getMessage() : "Invalid action";
        } catch (Exception e) {
            log.error("Error applying action [{}] for user [{}] on table [{}]: {}", actionType, userId, tableId, e.getMessage());
            return e.getMessage();
        }
    }

    public synchronized void processAutoPack(String tableId, String userId) {
        Optional<BettingRoundEngine> engineOpt = handContextManager.getEngine(tableId);
        if (engineOpt.isEmpty()) {
            return;
        }
        BettingRoundEngine engine = engineOpt.get();
        if (engine.isHandFinished()) {
            return;
        }

        try {
            engine.applyAction(PlayerAction.of(userId, PlayerActionType.PACK));
            Optional<Table> optTable = tableRepository.findById(tableId);
            if (optTable.isPresent()) {
                updateTableAfterPack(optTable.get(), userId, engine);
                turnManagementService.syncTableFromEngine(optTable.get(), engine);
            }
            eventPublisher.publishPackPlayed(tableId, userId, true);

            if (engine.isHandFinished()) {
                handleHandFinished(tableId, engine);
            } else {
                gameBroadcastService.broadcastTableState(tableId);
                String nextUser = engine.getCurrentTurnPlayerId();
                if (optTable.isPresent() && nextUser != null) {
                    int nextSeat = optTable.get().getSeatedPlayerIds().indexOf(nextUser);
                    startTurn(tableId, nextUser, nextSeat);
                }
            }
        } catch (Exception e) {
            log.error("Error auto-packing user [{}] on table [{}]: {}", userId, tableId, e.getMessage());
        }
    }

    private void startTurn(String tableId, String userId, int seatIndex) {
        turnManagementService.beginTurn(tableId, userId, seatIndex,
                () -> processAutoPack(tableId, userId));
    }

    private void cancelTurnTimer(String tableId) {
        turnManagementService.cancelTurn(tableId);
    }

    private void syncSession(Table table, BettingRoundEngine engine) {
        if (table == null || table.getCurrentHandId() == null) {
            return;
        }
        Instant deadline = turnManagementService.getTurnDeadline(table.getId()).orElse(null);
        gameSessionService.syncActiveSession(table.getCurrentHandId(), engine, table, deadline);
    }

    private void updateTablePot(Table table, BettingRoundEngine engine, String lastAction) {
        if (table == null) {
            return;
        }
        table.setPotPaise(engine.getPotPaise());
        table.setLastAction(lastAction);
        tableRepository.save(table);
        eventPublisher.publishTableUpdated(table.getId(), table);
    }

    private void updateTableAfterSee(String tableId, Table table, String userId, BettingRoundEngine engine) {
        if (table == null) {
            return;
        }
        table.getBlindPlayerIds().remove(userId);
        if (!table.getSeenPlayerIds().contains(userId)) {
            table.getSeenPlayerIds().add(userId);
        }
        table.setLastAction(userId + " saw cards");
        tableRepository.save(table);
        eventPublisher.publishTableUpdated(tableId, table);
    }

    private void updateTableAfterPack(Table table, String userId, BettingRoundEngine engine) {
        if (table == null) {
            return;
        }
        table.getActivePlayerIds().remove(userId);
        if (!table.getPackedPlayerIds().contains(userId)) {
            table.getPackedPlayerIds().add(userId);
        }
        table.setPotPaise(engine.getPotPaise());
        table.setLastAction(userId + " packed");
        tableRepository.save(table);
        eventPublisher.publishTableUpdated(table.getId(), table);
    }

    private void handleHandFinished(String tableId, BettingRoundEngine engine) {
        cancelTurnTimer(tableId);
        turnManagementService.endTurn(tableId);
        HandOutcome outcome = engine.getOutcome();
        if (outcome == null) {
            return;
        }

        String winnerId = outcome.getWinnerId();
        log.info("Hand finished on table [{}]. Winner [{}]", tableId, winnerId);

        Optional<Table> optTable = tableRepository.findById(tableId);
        if (optTable.isEmpty()) {
            handContextManager.clearHand(tableId);
            return;
        }

        Table table = optTable.get();
        String handId = table.getCurrentHandId() != null ? table.getCurrentHandId() : UUID.randomUUID().toString();
        Instant startedAt = handContextManager.getHandStartTime(tableId);

        table.setStatus(TableStatus.ROUND_END);
        table.setWinnerUserId(winnerId);
        table.setPotPaise(0);
        table.setLastAction("Winner: " + winnerId);
        tableRepository.save(table);

        handSettlementService.settleCompletedHand(table, handId, outcome, startedAt);
        gameSessionService.completeSession(handId, outcome);

        for (String pid : table.getSeatedPlayerIds()) {
            userRepository.findById(pid).ifPresent(u -> {
                u.setMatchesPlayedCount(u.getMatchesPlayedCount() + 1);
                userRepository.save(u);
            });
        }

        WinnerSnapshot winnerSnapshot = winnerCalculationService.buildWinnerSnapshot(
                table, handId, outcome, engine, table.getGameVariant());
        winnerCalculationService.publishWinnerDeclared(tableId, winnerSnapshot);

        eventPublisher.publishRoundFinished(tableId, 0);
        eventPublisher.publishTableUpdated(tableId, table);

        // Broadcast while engine still holds outcome/cards, then clear in-memory hand
        gameBroadcastService.broadcastTableState(tableId);
        handContextManager.clearHand(tableId);

        table.setCurrentTurnUserId(null);
        tableRepository.save(table);
        gameBroadcastService.broadcastTableState(tableId);
    }
}
