package com.teenpatti.platform.websocket;

import com.teenpatti.platform.game.engine.*;
import com.teenpatti.platform.game.betting.BettingLogicService;
import com.teenpatti.platform.game.betting.BettingState;
import com.teenpatti.platform.game.winner.WinnerCalculationService;
import com.teenpatti.platform.game.winner.WinnerSnapshot;
import com.teenpatti.platform.game.turn.TurnManagementService;
import com.teenpatti.platform.game.turn.TurnState;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.dto.PlayerSummaryView;
import com.teenpatti.platform.websocket.dto.PlayerViewGameState;
import com.teenpatti.platform.websocket.dto.PendingShowView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Safety-critical projection component responsible for generating personalized,
 * recipient-filtered views of game state.
 *
 * CRITICAL SECURITY GUARANTEE:
 * 1. A recipient NEVER receives another player's actual card values while active/blind/seen.
 * 2. A recipient receives their OWN card values ONLY IF their status is SEEN.
 * 3. Cards are revealed ONLY in HandOutcome for showdown participants. Folded players' cards
 *    are NEVER leaked to anyone.
 */
@Component
@RequiredArgsConstructor
public class GameStateProjector {

    private final UserRepository userRepository;
    private final SessionRegistry sessionRegistry;
    private final TurnManagementService turnManagementService;
    private final BettingLogicService bettingLogicService;
    private final WinnerCalculationService winnerCalculationService;
    private final HandContextManager handContextManager;

    public PlayerViewGameState createProjection(
            Table table,
            BettingRoundEngine engine,
            String recipientUserId) {

        if (table == null) {
            throw new IllegalArgumentException("Table must not be null");
        }

        List<String> seatedIds = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
        Map<String, String> displayNameMap = userRepository.findAllById(seatedIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getDisplayName() != null ? u.getDisplayName() : "Player"));

        HandOutcome outcome = engine != null ? engine.getOutcome() : null;
        Map<String, List<Card>> showdownRevealed = outcome != null ? outcome.getRevealedHands() : Map.of();

        List<PlayerSummaryView> playerViews = new ArrayList<>();
        for (String pid : seatedIds) {
            PlayerStatus status = resolvePlayerStatus(table, engine, pid);

            List<Card> actualCards = engine != null ? engine.getPlayerCards(pid) : List.of();
            int cardCount = actualCards != null ? actualCards.size() : 0;

            List<Card> visibleCards = null;

            // Rule 1: Showdown revealed cards (only for showdown participants in outcome)
            if (showdownRevealed.containsKey(pid)) {
                visibleCards = showdownRevealed.get(pid);
            }
            // Rule 2: Recipient sees own card values ONLY after choosing SEE (BLIND = card backs via cardCount)
            // Exception: Blind Show target may peek own cards while deciding Accept/Decline.
            else if (pid.equals(recipientUserId) && engine != null && actualCards != null && !actualCards.isEmpty()) {
                PlayerStatus ownerStatus = resolvePlayerStatus(table, engine, pid);
                boolean pendingShowTarget = false;
                if (table.getId() != null) {
                    var pendingShow = handContextManager.getPendingShow(table.getId());
                    pendingShowTarget = pendingShow.isPresent()
                            && recipientUserId.equals(pendingShow.get().targetId());
                }
                if (ownerStatus == PlayerStatus.SEEN || pendingShowTarget) {
                    visibleCards = actualCards;
                }
            }
            // Rule 3: Otherwise, cards remain NULL (hidden from recipient)

            boolean connected = sessionRegistry.isUserConnected(pid);

            playerViews.add(PlayerSummaryView.builder()
                    .userId(pid)
                    .displayName(displayNameMap.getOrDefault(pid, "Player"))
                    .status(status)
                    .totalContributedPaise(0L) // Can be populated if tracked
                    .cards(visibleCards)
                    .cardCount(cardCount)
                    .connected(connected)
                    .build());
        }

        TableStatus status = table.getStatus();
        boolean handEnded = status == TableStatus.ROUND_END
                || status == TableStatus.WAITING
                || status == TableStatus.NEXT_ROUND
                || status == TableStatus.COUNTDOWN
                || status == TableStatus.CLOSED
                || (engine != null && engine.isHandFinished());
        String currentTurnPlayerId = handEnded
                ? null
                : (engine != null ? engine.getCurrentTurnPlayerId() : table.getCurrentTurnUserId());
        long potPaise = engine != null ? engine.getPotPaise() : table.getPotPaise();
        long currentBaseStakePaise = engine != null ? engine.getCurrentBaseStakePaise() : table.getCurrentStakePaise();

        TurnState turnState = turnManagementService.buildTurnState(table, engine);
        BettingState bettingState = engine != null
                ? bettingLogicService.buildBettingState(table, engine, recipientUserId)
                : BettingState.builder()
                        .tableId(table.getId())
                        .userId(recipientUserId)
                        .playerState(PlayerStatus.PACKED.name())
                        .potPaise(potPaise)
                        .currentBaseStakePaise(currentBaseStakePaise)
                        .blindAmountPaise(0L)
                        .chaalAmountPaise(0L)
                        .showCostPaise(0L)
                        .sideShowCostPaise(0L)
                        .requiredBetPaise(0L)
                        .minRaiseBetPaise(0L)
                        .maxBetPaise(0L)
                        .raiseOptionsPaise(List.of())
                        .playerContributedPaise(0L)
                        .walletBalancePaise(0L)
                        .blindSeenRatio(2)
                        .turnTimerSeconds(0)
                        .myTurn(false)
                        .allowedActions(List.of())
                        .variantPhase(null)
                        .auctionHighBidPaise(0L)
                        .auctionHighBidderId(null)
                        .auctionMinBidPaise(0L)
                        .build();

        long requiredBetPaise = handEnded ? 0L : bettingState.getRequiredBetPaise();
        long minRaiseBetPaise = handEnded ? 0L : bettingState.getMinRaiseBetPaise();
        long maxBetPaise = bettingState.getMaxBetPaise();
        long playerContributedPaise = bettingState.getPlayerContributedPaise();
        int blindSeenRatio = bettingState.getBlindSeenRatio();
        List<String> allowedActions = handEnded ? List.of() : bettingState.getAllowedActions();
        boolean myTurn = handEnded ? false : bettingState.isMyTurn();

        // While Show/Side-Show is pending, the challenged player may respond immediately.
        if (!handEnded && allowedActions != null
                && (allowedActions.contains("SHOW_ACCEPT")
                || allowedActions.contains("SHOW_REJECT")
                || allowedActions.contains("SIDE_SHOW_ACCEPT"))) {
            myTurn = true;
        }

        PendingShowView pendingShowView = null;
        if (!handEnded && table.getId() != null) {
            var pendingOpt = handContextManager.getPendingShow(table.getId());
            if (pendingOpt.isPresent()) {
                var pending = pendingOpt.get();
                pendingShowView = PendingShowView.builder()
                        .requesterId(pending.requesterId())
                        .targetId(pending.targetId())
                        .requesterDisplayName(displayNameMap.getOrDefault(pending.requesterId(), "Player"))
                        .build();
            }
        }

        WinnerSnapshot winnerSnapshot = null;
        if (handEnded && outcome != null) {
            winnerSnapshot = winnerCalculationService.buildWinnerSnapshot(
                    table,
                    table.getCurrentHandId(),
                    outcome,
                    engine,
                    table.getGameVariant());
        } else if (handEnded && outcome == null) {
            winnerSnapshot = resolveWinnerSnapshotFromTable(table);
        }

        int dealerSeatIndex = turnState.getDealerSeatIndex();
        int currentTurnSeatIndex = turnState.getCurrentTurnSeatIndex();
        int turnTimeoutSeconds = turnState.getTurnTimeoutSeconds();
        int turnSecondsRemaining = handEnded ? 0 : turnState.getTurnSecondsRemaining();
        Instant turnDeadlineAt = handEnded ? null : turnState.getTurnDeadlineAt();

        return PlayerViewGameState.builder()
                .tableId(table.getId())
                .hostId(table.getHostId())
                .tableType(table.getTableType() != null ? table.getTableType().name() : TableType.PUBLIC.name())
                .gameVariant(table.getGameVariant() != null ? table.getGameVariant().name() : com.teenpatti.platform.table.GameVariant.CLASSIC.name())
                .jokerRank(table.getJokerRank())
                .inviteCode(table.getInviteCode())
                .countdownSeconds(table.getCountdownSeconds())
                .bootAmountPaise(table.getBootAmountPaise())
                .minPlayers(table.getMinPlayers() > 0 ? table.getMinPlayers() : 3)
                .maxPlayers(table.getMaxPlayers() > 0 ? table.getMaxPlayers() : 6)
                .status(status)
                .currentTurnPlayerId(currentTurnPlayerId)
                .dealerSeatIndex(dealerSeatIndex)
                .currentTurnSeatIndex(currentTurnSeatIndex)
                .turnTimeoutSeconds(turnTimeoutSeconds)
                .turnSecondsRemaining(turnSecondsRemaining)
                .turnDeadlineAt(turnDeadlineAt)
                .activePlayerIds(turnState.getActivePlayerIds())
                .blindPlayerIds(turnState.getBlindPlayerIds())
                .seenPlayerIds(turnState.getSeenPlayerIds())
                .packedPlayerIds(turnState.getPackedPlayerIds())
                .potPaise(potPaise)
                .currentBaseStakePaise(currentBaseStakePaise)
                .blindAmountPaise(handEnded ? 0L : bettingState.getBlindAmountPaise())
                .chaalAmountPaise(handEnded ? 0L : bettingState.getChaalAmountPaise())
                .showCostPaise(handEnded ? 0L : bettingState.getShowCostPaise())
                .sideShowCostPaise(handEnded ? 0L : bettingState.getSideShowCostPaise())
                .requiredBetPaise(requiredBetPaise)
                .minRaiseBetPaise(minRaiseBetPaise)
                .maxBetPaise(maxBetPaise)
                .raiseOptionsPaise(handEnded ? List.of() : bettingState.getRaiseOptionsPaise())
                .playerContributedPaise(playerContributedPaise)
                .walletBalancePaise(bettingState.getWalletBalancePaise())
                .playerState(bettingState.getPlayerState())
                .blindSeenRatio(blindSeenRatio)
                .turnTimerSeconds(turnTimeoutSeconds)
                .allowedActions(allowedActions)
                .myTurn(myTurn)
                .players(playerViews)
                .handOutcome(outcome)
                .winnerSnapshot(winnerSnapshot)
                .pendingShow(pendingShowView)
                .disconnectedPlayerIds(table.getDisconnectedPlayerIds() != null
                        ? table.getDisconnectedPlayerIds() : List.of())
                .variantPhase(bettingState.getVariantPhase())
                .auctionHighBidPaise(bettingState.getAuctionHighBidPaise())
                .auctionHighBidderId(bettingState.getAuctionHighBidderId())
                .auctionMinBidPaise(bettingState.getAuctionMinBidPaise())
                .build();
    }

    private PlayerStatus resolvePlayerStatus(Table table, BettingRoundEngine engine, String playerId) {
        if (engine != null) {
            PlayerStatus status = engine.getPlayerStatus(playerId);
            if (status != null) {
                return status;
            }
        }
        if (table.getPackedPlayerIds() != null && table.getPackedPlayerIds().contains(playerId)) {
            return PlayerStatus.PACKED;
        }
        if (table.getSeenPlayerIds() != null && table.getSeenPlayerIds().contains(playerId)) {
            return PlayerStatus.SEEN;
        }
        return PlayerStatus.BLIND;
    }

    private WinnerSnapshot resolveWinnerSnapshotFromTable(Table table) {
        if (table.getRoundResults() == null || table.getRoundResults().isEmpty()) {
            return null;
        }
        com.teenpatti.platform.table.TableRoundResult last = table.getRoundResults()
                .get(table.getRoundResults().size() - 1);
        return WinnerSnapshot.builder()
                .tableId(table.getId())
                .handId(last.getHandId())
                .winnerUserId(last.getWinnerUserId())
                .winnerDisplayName(last.getWinnerDisplayName())
                .winningCategory(last.getWinningCategory())
                .winningHandDescription(last.getWinningHandDescription())
                .foldWin(last.isFoldWin())
                .potPaise(last.getPotPaise())
                .payoutPaise(last.getPayoutPaise())
                .build();
    }
}
