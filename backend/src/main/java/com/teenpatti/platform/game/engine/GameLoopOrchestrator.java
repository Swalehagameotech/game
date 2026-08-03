package com.teenpatti.platform.game.engine;

import com.teenpatti.platform.bot.event.BotActionNeededEvent;
import com.teenpatti.platform.game.GameSessionService;
import com.teenpatti.platform.game.betting.BettingLogicService;
import com.teenpatti.platform.game.round.RoundManagementService;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final RoundManagementService roundManagementService;
    private final WebSocketEventPublisher eventPublisher;
    private final GameBroadcastService gameBroadcastService;
    private final com.teenpatti.platform.game.variant.VariantPhaseService variantPhaseService;
    private final ApplicationEventPublisher applicationEventPublisher;

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
                if (table.getStatus() == TableStatus.WAITING
                        || table.getStatus() == TableStatus.ROUND_END
                        || table.getStatus() == TableStatus.NEXT_ROUND) {
                    roundManagementService.onPlayerLeftBetweenHands(tableId);
                    return;
                }
            }
        } else {
            roundManagementService.onPlayerLeftBetweenHands(tableId);
        }
    }

    /**
     * Mid-hand leave: pack the player in the engine before seat removal completes.
     */
    public void handlePlayerLeftMidHand(String tableId, String userId) {
        processAutoPack(tableId, userId);
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

        String normalizedAction = actionType != null ? actionType.trim().toUpperCase() : "";
        boolean isSeeCards = "SEE_CARDS".equalsIgnoreCase(actionType);
        if (variantPhaseService.isPreBettingPhase(tableId) && !isSeeCards) {
            return "Complete " + variantPhaseService.getPhase(tableId).name() + " phase first";
        }

        // SEE_CARDS must not cancel the active turn timer — it does not consume the turn.
        if (!isSeeCards) {
            cancelTurnTimer(tableId);
        }

        try {
            Optional<Table> optTable = tableRepository.findById(tableId);
            Table table = optTable.orElse(null);

            if (isSeeCards) {
                return processSeeCards(tableId, userId, table, engine);
            }

            // While a side-show or show is pending, only the target may respond.
            var pendingShowOpt = handContextManager.getPendingShow(tableId);
            if (pendingShowOpt.isPresent()
                    && !"SHOW_ACCEPT".equalsIgnoreCase(actionType)
                    && !"SHOW_REJECT".equalsIgnoreCase(actionType)
                    && !"SHOW_DECLINE".equalsIgnoreCase(actionType)) {
                return "Waiting for Show response.";
            }

            var pendingOpt = handContextManager.getPendingSideShow(tableId);
            if (pendingOpt.isPresent()
                    && !"SIDE_SHOW_ACCEPT".equalsIgnoreCase(actionType)
                    && !"SIDE_SHOW_REJECT".equalsIgnoreCase(actionType)) {
                return "Waiting for Side Show response.";
            }

            String handId = table != null ? table.getCurrentHandId() : null;

            if ("CHAAL".equalsIgnoreCase(actionType) || "PLAY_BLIND".equalsIgnoreCase(actionType)
                    || "BLIND".equalsIgnoreCase(actionType)
                    || "CALL".equalsIgnoreCase(actionType)) {
                long bet = bettingLogicService.resolveBetAmount(actionType, engine, userId, betAmount);
                bettingLogicService.validateBalance(userId, bet);
                PlayerActionType type = ("PLAY_BLIND".equalsIgnoreCase(actionType) || "BLIND".equalsIgnoreCase(actionType))
                        ? PlayerActionType.PLAY_BLIND
                        : PlayerActionType.CHAAL;
                engine.applyAction(PlayerAction.of(userId, type, bet));
                bettingLogicService.debitBet(tableId, handId, userId, bet);
                updateTablePot(table, engine, userId + " played " + type.name());
                turnManagementService.syncTableFromEngine(table, engine);
                eventPublisher.publishBlindPlayed(tableId, userId, bet, engine.getPotPaise());
                eventPublisher.publishPlayerAction(tableId, userId, actionType, bet, engine.getPotPaise());
            } else if ("RAISE".equalsIgnoreCase(actionType)) {
                long bet = bettingLogicService.resolveBetAmount(actionType, engine, userId, betAmount);
                bettingLogicService.validateBalance(userId, bet);
                engine.applyAction(PlayerAction.of(userId, PlayerActionType.RAISE, bet));
                bettingLogicService.debitBet(tableId, handId, userId, bet);
                updateTablePot(table, engine, userId + " raised");
                turnManagementService.syncTableFromEngine(table, engine);
                eventPublisher.publishRaisePlayed(tableId, userId, bet, engine.getPotPaise());
                eventPublisher.publishPlayerAction(tableId, userId, actionType, bet, engine.getPotPaise());
            } else if ("PACK".equalsIgnoreCase(actionType)) {
                boolean wasMyTurn = userId.equals(engine.getCurrentTurnPlayerId());
                engine.applyAction(PlayerAction.of(userId, PlayerActionType.PACK));
                updateTableAfterPack(table, userId, engine);
                turnManagementService.syncTableFromEngine(table, engine);
                if (wasMyTurn) {
                    // Cancel the packer's timer only. Do NOT endTurn() here — sync already
                    // advanced table.currentTurnUserId to the next player, and endTurn()
                    // would null that id and wrongly emit TURN_ENDED for the next player.
                    cancelTurnTimer(tableId);
                    eventPublisher.publishTurnEnded(tableId, userId);
                }
                eventPublisher.publishPackPlayed(tableId, userId, engine.isHandFinished());
                eventPublisher.publishPlayerAction(tableId, userId, actionType, 0L, engine.getPotPaise());
            } else if ("SIDE_SHOW_REQUEST".equalsIgnoreCase(actionType)) {
                if (!bettingLogicService.canRequestSideShow(engine, userId)) {
                    return "Side Show is not valid right now.";
                }
                if (handContextManager.getPendingSideShow(tableId).isPresent()) {
                    return "A Side Show request is already pending.";
                }
                String targetUserId = bettingLogicService.resolveSideShowTarget(engine, userId);
                if (targetUserId == null) {
                    return "No eligible player for Side Show.";
                }
                long sideShowCost = bettingLogicService.resolveBetAmount(actionType, engine, userId, betAmount);
                bettingLogicService.validateBalance(userId, sideShowCost);
                bettingLogicService.debitBet(tableId, handId, userId, sideShowCost);
                engine.addMetaBetToPot(userId, sideShowCost);
                if (table != null) {
                    table.setPotPaise(engine.getPotPaise());
                    table.setLastAction(userId + " requested side show vs " + targetUserId);
                    tableRepository.save(table);
                }
                handContextManager.setPendingSideShow(tableId, userId, targetUserId);
                cancelTurnTimer(tableId);
                eventPublisher.publishSideShowRequested(tableId, userId, targetUserId);
                eventPublisher.publishPlayerAction(tableId, userId, actionType, sideShowCost, engine.getPotPaise());
                publishActionSideEffects(table, engine, userId, actionType);
                // Must notify the challenged player (esp. bots) — same as SHOW path.
                publishLiveBettingViews(table, engine);
                applicationEventPublisher.publishEvent(
                        new BotActionNeededEvent(tableId, targetUserId, true));
                gameBroadcastService.broadcastTableState(tableId);
                return null;
            } else if ("SIDE_SHOW_ACCEPT".equalsIgnoreCase(actionType)) {
                return processSideShowResponse(tableId, userId, table, engine, true);
            } else if ("SIDE_SHOW_REJECT".equalsIgnoreCase(actionType)) {
                return processSideShowResponse(tableId, userId, table, engine, false);
            } else if ("SHOW_ACCEPT".equalsIgnoreCase(actionType)) {
                return processShowAccept(tableId, userId, table, engine);
            } else if ("SHOW_REJECT".equalsIgnoreCase(actionType)
                    || "SHOW_DECLINE".equalsIgnoreCase(actionType)) {
                return processShowReject(tableId, userId, table, engine);
            } else if ("SHOW".equalsIgnoreCase(actionType)) {
                if (engine.getActivePlayerIds().size() != 2) {
                    return "Show requires exactly two active players.";
                }
                if (engine.isHandFinished()) {
                    return "The hand has already finished.";
                }
                if (handContextManager.getPendingShow(tableId).isPresent()) {
                    return "A Show request is already pending.";
                }
                if (!userId.equals(engine.getCurrentTurnPlayerId())) {
                    return "It is not your turn to request Show.";
                }
                long required = bettingLogicService.resolveBetAmount(actionType, engine, userId, betAmount);
                bettingLogicService.validateBalance(userId, required);
                engine.recordShowRequest(userId, required);
                bettingLogicService.debitBet(tableId, handId, userId, required);

                String targetUserId = bettingLogicService.resolveShowTarget(engine, userId);
                if (targetUserId == null) {
                    return "No eligible player for Show.";
                }

                cancelTurnTimer(tableId);
                turnManagementService.endTurn(tableId);
                handContextManager.setPendingShow(tableId, userId, targetUserId);

                if (table != null) {
                    table.setStatus(TableStatus.SHOW);
                    table.setPotPaise(engine.getPotPaise());
                    table.setLastAction(userId + " requested show vs " + targetUserId);
                    tableRepository.save(table);
                }

                String requesterName = userRepository.findById(userId)
                        .map(u -> u.getDisplayName() != null ? u.getDisplayName() : "Player")
                        .orElse("Player");

                java.util.Map<String, Object> showPayload = new java.util.HashMap<>();
                showPayload.put("tableId", tableId);
                showPayload.put("requesterId", userId);
                showPayload.put("requesterUserId", userId);
                showPayload.put("requesterDisplayName", requesterName);
                showPayload.put("targetUserId", targetUserId);
                showPayload.put("potPaise", engine.getPotPaise());
                showPayload.put("showCostPaise", required);

                eventPublisher.publishShowRequested(tableId, showPayload);
                eventPublisher.publishShowRequestToPlayer(targetUserId, showPayload);
                // Raw WS first so Accept/Decline appears instantly (don't wait on STOMP/refresh).
                gameBroadcastService.broadcastEvent(tableId, "SHOW_REQUEST", showPayload);
                gameBroadcastService.broadcastEvent(tableId, "SHOW_REQUESTED", showPayload);
                gameBroadcastService.deliverEventToPlayer(targetUserId, "SHOW_REQUEST", showPayload);
                gameBroadcastService.deliverEventToPlayer(targetUserId, "SHOW_REQUESTED", showPayload);

                // Blind target may peek own cards to decide Accept/Decline — stay BLIND (no Seen requirement).
                if (engine.getPlayerStatus(targetUserId) == PlayerStatus.BLIND) {
                    var cards = engine.getPlayerCards(targetUserId);
                    java.util.Map<String, Object> peekPayload = new java.util.HashMap<>();
                    peekPayload.put("tableId", tableId);
                    peekPayload.put("userId", targetUserId);
                    peekPayload.put("reason", "SHOW_REQUEST");
                    peekPayload.put("cards", cards);
                    eventPublisher.publishPlayerCardsRevealedToSelf(targetUserId, peekPayload);
                    gameBroadcastService.deliverEventToPlayer(targetUserId, "PLAYER_CARDS_REVEALED_TO_SELF", peekPayload);
                }

                eventPublisher.publishPlayerAction(tableId, userId, actionType, required, engine.getPotPaise());
                eventPublisher.publishPotUpdated(tableId, engine.getPotPaise());
                publishLiveBettingViews(table, engine);
                // Personalized projection for the challenged player (pendingShow + Accept/Decline).
                gameBroadcastService.deliverPrivateHand(tableId, targetUserId);
                gameBroadcastService.broadcastTableState(tableId);
                return null;
            } else {
                return "Unsupported action: " + actionType;
            }

            eventPublisher.publishPotUpdated(tableId, engine.getPotPaise());
            publishActionSideEffects(table, engine, userId, actionType);
            if (engine.isHandFinished()) {
                handleHandFinished(tableId, engine);
            } else {
                // Notify clients whenever exactly two players remain (Show becomes legal).
                if (engine.getActivePlayerIds().size() == 2) {
                    Map<String, Object> showEnabledPayload = new java.util.HashMap<>();
                    showEnabledPayload.put("tableId", tableId);
                    showEnabledPayload.put("activePlayerCount", 2);
                    showEnabledPayload.put("showEnabled", true);
                    showEnabledPayload.put("activePlayerIds", engine.getActivePlayerIds());
                    eventPublisher.publishEvent(
                            com.teenpatti.platform.websocket.StompDestinations.topicTable(tableId),
                            "SHOW_ENABLED",
                            showEnabledPayload);
                    gameBroadcastService.broadcastEvent(tableId, "SHOW_ENABLED", showEnabledPayload);
                }
                String nextUser = engine.getCurrentTurnPlayerId();
                if (table != null && nextUser != null) {
                    int nextSeat = table.getSeatedPlayerIds().indexOf(nextUser);
                    startTurn(tableId, nextUser, nextSeat);
                }
                // Republish after turn timer is live so Show + myTurn land correctly.
                publishLiveBettingViews(table, engine);
                gameBroadcastService.broadcastTableState(tableId);
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
        } catch (com.teenpatti.platform.common.exception.InsufficientBalanceException ex) {
            log.warn("Insufficient balance for action [{}] user [{}]: {}", actionType, userId, ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("Error applying action [{}] for user [{}] on table [{}]", actionType, userId, tableId, e);
            return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
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
            eventPublisher.publishPlayerAction(tableId, userId, "PACK", 0L, engine.getPotPaise());
            eventPublisher.publishPlayerStateUpdated(tableId, Map.of(
                    "tableId", tableId,
                    "userId", userId,
                    "status", "PACKED"));
            publishActionSideEffects(optTable.orElse(null), engine, userId, "PACK");

            if (engine.isHandFinished()) {
                handleHandFinished(tableId, engine);
            } else {
                if (engine.getActivePlayerIds().size() == 2) {
                    Map<String, Object> showEnabledPayload = new java.util.HashMap<>();
                    showEnabledPayload.put("tableId", tableId);
                    showEnabledPayload.put("activePlayerCount", 2);
                    showEnabledPayload.put("showEnabled", true);
                    showEnabledPayload.put("activePlayerIds", engine.getActivePlayerIds());
                    eventPublisher.publishEvent(
                            com.teenpatti.platform.websocket.StompDestinations.topicTable(tableId),
                            "SHOW_ENABLED",
                            showEnabledPayload);
                    gameBroadcastService.broadcastEvent(tableId, "SHOW_ENABLED", showEnabledPayload);
                }
                String nextUser = engine.getCurrentTurnPlayerId();
                if (optTable.isPresent() && nextUser != null) {
                    int nextSeat = optTable.get().getSeatedPlayerIds().indexOf(nextUser);
                    startTurn(tableId, nextUser, nextSeat);
                }
                publishLiveBettingViews(optTable.orElse(null), engine);
                gameBroadcastService.broadcastTableState(tableId);
            }
        } catch (Exception e) {
            log.error("Error auto-packing user [{}] on table [{}]: {}", userId, tableId, e.getMessage());
        }
    }

    private void startTurn(String tableId, String userId, int seatIndex) {
        turnManagementService.beginTurn(tableId, userId, seatIndex,
                () -> processAutoPack(tableId, userId));
        applicationEventPublisher.publishEvent(new BotActionNeededEvent(tableId, userId, false));
    }

    /**
     * STOMP private queue + raw WebSocket so clients always get myTurn / Show buttons live.
     */
    private void publishLiveBettingViews(Table table, BettingRoundEngine engine) {
        if (table == null || engine == null || engine.isHandFinished()) {
            return;
        }
        bettingLogicService.publishBettingStateForTable(table, engine);
        List<String> seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
        for (String playerId : seated) {
            var state = bettingLogicService.buildBettingState(table, engine, playerId);
            gameBroadcastService.deliverEventToPlayer(
                    playerId,
                    com.teenpatti.platform.websocket.RealTimeEventType.BETTING_STATE.name(),
                    state);
            // Notify bots that need to answer show / side-show / variant prompts
            if (state.isMyTurn() || (state.getAllowedActions() != null && state.getAllowedActions().stream()
                    .anyMatch(a -> a.contains("ACCEPT") || a.contains("REJECT")
                            || a.equals("DISCARD_CARD") || a.startsWith("AUCTION")))) {
                applicationEventPublisher.publishEvent(new BotActionNeededEvent(table.getId(), playerId, true));
            }
        }
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
        // Pot-only signal — avoid full table entity overwrite on clients (wipes SEEN cards).
        eventPublisher.publishPotUpdated(table.getId(), engine.getPotPaise());
    }

    private String processShowAccept(String tableId, String userId, Table table, BettingRoundEngine engine) {
        var pendingOpt = handContextManager.getPendingShow(tableId);
        if (pendingOpt.isEmpty()) {
            return "No Show request is pending.";
        }
        HandContextManager.PendingShow pending = pendingOpt.get();
        if (!userId.equals(pending.targetId())) {
            return "Only the challenged player can accept Show.";
        }

        String requesterId = pending.requesterId();
        handContextManager.clearPendingShow(tableId);

        engine.resolveShowdownAfterShowAccept();
        HandOutcome outcome = engine.getOutcome();
        if (outcome == null) {
            return "Show resolution failed.";
        }

        if (table != null) {
            table.setPotPaise(engine.getPotPaise());
            table.setLastAction(userId + " accepted show");
            table.setStatus(TableStatus.ROUND_END);
            tableRepository.save(table);
        }

        java.util.Map<String, Object> revealedPayload = new java.util.LinkedHashMap<>();
        revealedPayload.put("tableId", tableId);
        revealedPayload.put("requesterId", requesterId);
        revealedPayload.put("targetUserId", userId);
        revealedPayload.put("winnerId", outcome.getWinnerId());
        if (outcome.getRevealedHands() != null) {
            java.util.Map<String, java.util.List<String>> hands = new java.util.LinkedHashMap<>();
            outcome.getRevealedHands().forEach((pid, cards) ->
                    hands.put(pid, cards.stream()
                            .map(com.teenpatti.platform.game.engine.Card::toShortString)
                            .toList()));
            revealedPayload.put("hands", hands);
        }

        eventPublisher.publishShowAccepted(tableId, Map.of(
                "tableId", tableId,
                "requesterId", requesterId,
                "targetUserId", userId,
                "winnerId", outcome.getWinnerId()));
        eventPublisher.publishFinalHandsRevealed(tableId, revealedPayload);
        eventPublisher.publishPlayerAction(tableId, userId, "SHOW_ACCEPT", 0L, engine.getPotPaise());
        gameBroadcastService.broadcastEvent(tableId, "SHOW_ACCEPTED", Map.of(
                "tableId", tableId,
                "requesterId", requesterId,
                "targetUserId", userId,
                "winnerId", outcome.getWinnerId()));
        gameBroadcastService.broadcastEvent(tableId, "FINAL_HANDS_REVEALED", revealedPayload);

        handleHandFinished(tableId, engine);
        return null;
    }

    /**
     * Target declines Show — pot already includes requester's Show cost; resume requester's turn.
     * Blind status is preserved (Show never forced Seen).
     */
    private String processShowReject(String tableId, String userId, Table table, BettingRoundEngine engine) {
        var pendingOpt = handContextManager.getPendingShow(tableId);
        if (pendingOpt.isEmpty()) {
            return "No Show request is pending.";
        }
        HandContextManager.PendingShow pending = pendingOpt.get();
        if (!userId.equals(pending.targetId())) {
            return "Only the challenged player can decline Show.";
        }

        String requesterId = pending.requesterId();
        handContextManager.clearPendingShow(tableId);

        if (table != null) {
            table.setStatus(TableStatus.RUNNING);
            table.setLastAction(userId + " declined show");
            tableRepository.save(table);
        }

        eventPublisher.publishShowRejected(tableId, Map.of(
                "tableId", tableId,
                "requesterId", requesterId,
                "targetUserId", userId));
        eventPublisher.publishPlayerAction(tableId, userId, "SHOW_REJECT", 0L, engine.getPotPaise());
        gameBroadcastService.broadcastEvent(tableId, "SHOW_REJECTED", Map.of(
                "tableId", tableId,
                "requesterId", requesterId,
                "targetUserId", userId));

        bettingLogicService.publishBettingStateForTable(table, engine);
        gameBroadcastService.broadcastTableState(tableId);

        if (table != null && requesterId != null && !engine.isHandFinished()) {
            int seat = table.getSeatedPlayerIds().indexOf(requesterId);
            startTurn(tableId, requesterId, seat);
        }
        return null;
    }

    private String processSideShowResponse(
            String tableId, String userId, Table table, BettingRoundEngine engine, boolean accepted) {
        var pendingOpt = handContextManager.getPendingSideShow(tableId);
        if (pendingOpt.isEmpty()) {
            return "No Side Show request is pending.";
        }
        HandContextManager.PendingSideShow pending = pendingOpt.get();
        if (!userId.equals(pending.targetId())) {
            return "Only the challenged player can respond to Side Show.";
        }

        handContextManager.clearPendingSideShow(tableId);
        String requesterId = pending.requesterId();

        if (!accepted) {
            eventPublisher.publishSideShowRejected(tableId, requesterId, userId);
            eventPublisher.publishPlayerAction(tableId, userId, "SIDE_SHOW_REJECT", 0L, engine.getPotPaise());
            if (table != null) {
                table.setLastAction(userId + " rejected side show");
                tableRepository.save(table);
            }
            publishActionSideEffects(table, engine, userId, "SIDE_SHOW_REJECT");
            gameBroadcastService.broadcastTableState(tableId);
            // Resume requester's turn
            if (table != null && requesterId != null) {
                int seat = table.getSeatedPlayerIds().indexOf(requesterId);
                startTurn(tableId, requesterId, seat);
            }
            return null;
        }

        String loserId = engine.resolveSideShowLoser(requesterId, userId);
        if (loserId != null) {
            boolean finished = engine.forcePack(loserId);
            if (table != null) {
                updateTableAfterPack(table, loserId, engine);
                turnManagementService.syncTableFromEngine(table, engine);
            }
            eventPublisher.publishSideShowAccepted(tableId, requesterId, userId, loserId);
            eventPublisher.publishPackPlayed(tableId, loserId, false);
            eventPublisher.publishPlayerStateUpdated(tableId, Map.of(
                    "tableId", tableId,
                    "userId", loserId,
                    "status", "PACKED",
                    "reason", "SIDE_SHOW_LOST"));
            publishActionSideEffects(table, engine, userId, "SIDE_SHOW_ACCEPT");
            if (finished || engine.isHandFinished()) {
                handleHandFinished(tableId, engine);
            } else {
                gameBroadcastService.broadcastTableState(tableId);
                String nextUser = engine.getCurrentTurnPlayerId();
                if (table != null && nextUser != null) {
                    int nextSeat = table.getSeatedPlayerIds().indexOf(nextUser);
                    startTurn(tableId, nextUser, nextSeat);
                }
            }
        } else {
            // Tie — neither packs
            eventPublisher.publishSideShowAccepted(tableId, requesterId, userId, "");
            publishActionSideEffects(table, engine, userId, "SIDE_SHOW_ACCEPT");
            gameBroadcastService.broadcastTableState(tableId);
            if (table != null && requesterId != null) {
                int seat = table.getSeatedPlayerIds().indexOf(requesterId);
                startTurn(tableId, requesterId, seat);
            }
        }
        return null;
    }

    /**
     * Blind → Seen: validates RUNNING hand, BLIND status, seat membership;
     * reveals cards only via per-player STATE_UPDATE; broadcasts status without card values.
     */
    private String processSeeCards(String tableId, String userId, Table table, BettingRoundEngine engine) {
        if (table == null) {
            return "Table not found";
        }
        if (table.getSeatedPlayerIds() == null || !table.getSeatedPlayerIds().contains(userId)) {
            return "Player is not seated at this table";
        }
        if (table.getStatus() != TableStatus.RUNNING
                && table.getStatus() != TableStatus.IN_PROGRESS
                && table.getStatus() != TableStatus.PLAYING
                && table.getStatus() != TableStatus.SHOW) {
            return "Game is not running";
        }
        if (engine.isHandFinished()) {
            return ActionRejectionReason.HAND_ALREADY_FINISHED.getDescription();
        }

        engine.applyAction(PlayerAction.of(userId, PlayerActionType.SEE_CARDS));
        updateTableAfterSee(tableId, table, userId, engine);
        turnManagementService.syncTableFromEngine(table, engine);

        String displayName = userRepository.findById(userId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : "Player")
                .orElse("Player");

        // Public: status only — NEVER include card values
        eventPublisher.publishPlayerSeenCards(tableId, userId, displayName);
        eventPublisher.publishSeenPlayed(tableId, userId);
        eventPublisher.publishPlayerAction(tableId, userId, "SEE_CARDS", 0L, engine.getPotPaise());
        eventPublisher.publishPlayerStateUpdated(tableId, Map.of(
                "tableId", tableId,
                "userId", userId,
                "status", "SEEN"));
        eventPublisher.publishGameStateUpdated(tableId, Map.of(
                "tableId", tableId,
                "turnUserId", engine.getCurrentTurnPlayerId(),
                "potPaise", engine.getPotPaise()));

        syncSession(table, engine);
        bettingLogicService.publishBettingStateForTable(table, engine);
        // Personalized projections: only the seer receives their card values
        gameBroadcastService.broadcastTableState(tableId);
        return null;
    }

    private void publishActionSideEffects(Table table, BettingRoundEngine engine, String userId, String actionType) {
        String tableId = table != null ? table.getId() : null;
        if (tableId == null) {
            return;
        }
        // Map.of rejects nulls — turnUserId is null after hand finishes.
        java.util.HashMap<String, Object> betPayload = new java.util.HashMap<>();
        betPayload.put("tableId", tableId);
        betPayload.put("currentBaseStakePaise", engine.getCurrentBaseStakePaise());
        betPayload.put("potPaise", engine.getPotPaise());
        eventPublisher.publishBetUpdated(tableId, betPayload);

        java.util.HashMap<String, Object> statePayload = new java.util.HashMap<>();
        statePayload.put("tableId", tableId);
        statePayload.put("userId", userId);
        statePayload.put("actionType", actionType);
        eventPublisher.publishPlayerStateUpdated(tableId, statePayload);

        java.util.HashMap<String, Object> gamePayload = new java.util.HashMap<>();
        gamePayload.put("tableId", tableId);
        gamePayload.put("turnUserId", engine.getCurrentTurnPlayerId());
        gamePayload.put("potPaise", engine.getPotPaise());
        gamePayload.put("activePlayerCount", engine.getActivePlayerIds().size());
        gamePayload.put("handFinished", engine.isHandFinished());
        eventPublisher.publishGameStateUpdated(tableId, gamePayload);

        if (!engine.isHandFinished()) {
            syncSession(table, engine);
            bettingLogicService.publishBettingStateForTable(table, engine);
        }
    }

    private void updateTableAfterSee(String tableId, Table table, String userId, BettingRoundEngine engine) {
        if (table == null) {
            return;
        }
        if (table.getBlindPlayerIds() == null) {
            table.setBlindPlayerIds(new ArrayList<>());
        }
        if (table.getSeenPlayerIds() == null) {
            table.setSeenPlayerIds(new ArrayList<>());
        }
        table.getBlindPlayerIds().remove(userId);
        if (!table.getSeenPlayerIds().contains(userId)) {
            table.getSeenPlayerIds().add(userId);
        }
        table.setLastAction(userId + " saw cards");
        table.setUpdatedAt(Instant.now());
        tableRepository.save(table);
        // Status lists are reflected via STATE_UPDATE + PLAYER_SEEN_CARDS — avoid shell TABLE_UPDATED.
    }

    private void updateTableAfterPack(Table table, String userId, BettingRoundEngine engine) {
        if (table == null) {
            return;
        }
        if (table.getActivePlayerIds() == null) {
            table.setActivePlayerIds(new ArrayList<>());
        }
        table.getActivePlayerIds().remove(userId);
        if (table.getPackedPlayerIds() == null) {
            table.setPackedPlayerIds(new ArrayList<>());
        }
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

        WinnerSnapshot winnerSnapshot = winnerCalculationService.buildWinnerSnapshot(
                table, handId, outcome, engine, table.getGameVariant());

        if (table.getRoundResults() == null) {
            table.setRoundResults(new java.util.ArrayList<>());
        }
        table.getRoundResults().add(com.teenpatti.platform.table.TableRoundResult.builder()
                .roundNumber(table.getRoundNumber() > 0 ? table.getRoundNumber() : 1)
                .handId(handId)
                .winnerUserId(winnerId)
                .winnerDisplayName(winnerSnapshot.getWinnerDisplayName())
                .winningCategory(winnerSnapshot.getWinningCategory())
                .winningHandDescription(winnerSnapshot.getWinningHandDescription())
                .potPaise(winnerSnapshot.getPotPaise())
                .payoutPaise(winnerSnapshot.getPayoutPaise())
                .foldWin(winnerSnapshot.isFoldWin())
                .endedAt(java.time.Instant.now())
                .build());
        tableRepository.save(table);

        handSettlementService.settleCompletedHand(table, handId, outcome, startedAt);
        gameSessionService.completeSession(handId, outcome);

        for (String pid : table.getSeatedPlayerIds()) {
            userRepository.findById(pid).ifPresent(u -> {
                u.setMatchesPlayedCount(u.getMatchesPlayedCount() + 1);
                userRepository.save(u);
            });
        }

        // Aliased payload so frontend never reads ₹0 from missing field names.
        java.util.Map<String, Object> winnerPayload = new java.util.LinkedHashMap<>();
        winnerPayload.put("tableId", tableId);
        winnerPayload.put("handId", handId);
        winnerPayload.put("winnerUserId", winnerSnapshot.getWinnerUserId());
        winnerPayload.put("winnerId", winnerSnapshot.getWinnerUserId());
        winnerPayload.put("winnerDisplayName", winnerSnapshot.getWinnerDisplayName());
        winnerPayload.put("winningCategory", winnerSnapshot.getWinningCategory());
        winnerPayload.put("winningHandDescription", winnerSnapshot.getWinningHandDescription());
        winnerPayload.put("foldWin", winnerSnapshot.isFoldWin());
        winnerPayload.put("potPaise", winnerSnapshot.getPotPaise());
        winnerPayload.put("rakePaise", winnerSnapshot.getRakePaise());
        winnerPayload.put("payoutPaise", winnerSnapshot.getPayoutPaise());
        winnerPayload.put("winnerPayoutPaise", winnerSnapshot.getPayoutPaise());
        winnerPayload.put("notes", winnerSnapshot.getNotes());
        winnerPayload.put("participants", winnerSnapshot.getParticipants());

        winnerCalculationService.publishWinnerDeclared(tableId, winnerPayload);
        gameBroadcastService.broadcastEvent(tableId, "WINNER_DECLARED", winnerPayload);

        // Broadcast while engine still holds outcome/cards for showdown projection
        gameBroadcastService.broadcastTableState(tableId);
        handContextManager.clearHand(tableId);
        variantPhaseService.clear(tableId);

        table.setCurrentTurnUserId(null);
        tableRepository.save(table);
        // Do not broadcast again here — empty engine would strip winnerSnapshot from clients.

        // Table stays alive — schedule next round or WAITING / CLOSED
        roundManagementService.afterRoundFinished(tableId);
    }
}
