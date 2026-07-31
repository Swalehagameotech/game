package com.teenpatti.platform.game.betting;

import com.teenpatti.platform.common.exception.InsufficientBalanceException;
import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.PlayerStatus;
import com.teenpatti.platform.game.variant.VariantPhaseTracker;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Centralizes Teen Patti betting rules: stake resolution, balance validation,
 * wallet debits, and betting-state broadcasts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BettingLogicService {

    private final WalletService walletService;
    private final WebSocketEventPublisher eventPublisher;
    private final com.teenpatti.platform.game.engine.HandContextManager handContextManager;
    private final VariantPhaseTracker variantPhaseTracker;

    public BettingState buildBettingState(Table table, BettingRoundEngine engine, String userId) {
        if (table == null || engine == null || userId == null) {
            throw new IllegalArgumentException("Table, engine, and userId must not be null");
        }

        GameEngineConfig config = engine.getConfig();
        PlayerStatus status = engine.getPlayerStatus(userId);
        boolean inHand = status != null && status != PlayerStatus.PACKED;
        boolean myTurn = inHand && userId.equals(engine.getCurrentTurnPlayerId());
        long stakeUnit = engine.getCurrentBaseStakePaise() > 0
                ? engine.getCurrentBaseStakePaise()
                : config.getBootAmountPaise();
        int ratio = Math.max(1, config.getBlindSeenRatio());
        long blindAmount = inHand ? stakeUnit : 0L;
        long chaalAmount = inHand ? stakeUnit * ratio : 0L;
        long requiredBet = inHand ? engine.getRequiredBetPaise(userId) : 0L;
        long minRaise = inHand ? computeMinRaiseBetPaise(engine, userId) : 0L;
        long maxBet = config.getMaxBetPaise();
        List<Long> raiseOptions = inHand ? computeRaiseOptions(engine, userId) : List.of();
        long walletBalance = walletService.getBalance(userId).getBalancePaise();
        int turnTimerSeconds = config.getTurnTimeoutSeconds();
        String playerState = status != null ? status.name() : PlayerStatus.PACKED.name();
        long showCost = inHand ? Math.max(config.getShowCostPaise(), chaalAmount) : 0L;
        long sideShowCost = inHand ? Math.max(config.getSideShowCostPaise(), chaalAmount) : 0L;
        List<String> allowed = resolveAllowedActions(table.getId(), engine, userId);
        allowed = filterAffordableActions(allowed, engine, userId, walletBalance);
        if (allowed.contains("SHOW_ACCEPT") || allowed.contains("SIDE_SHOW_ACCEPT")
                || allowed.contains("SIDE_SHOW_REJECT") || allowed.contains("SHOW_REJECT")) {
            myTurn = true;
        }
        if (allowed.contains("DISCARD_CARD") || allowed.contains("AUCTION_BID")
                || allowed.contains("AUCTION_PASS")) {
            myTurn = true;
        }

        return BettingState.builder()
                .tableId(table.getId())
                .userId(userId)
                .playerState(playerState)
                .potPaise(engine.getPotPaise())
                .currentBaseStakePaise(engine.getCurrentBaseStakePaise())
                .blindAmountPaise(blindAmount)
                .chaalAmountPaise(chaalAmount)
                .showCostPaise(showCost)
                .sideShowCostPaise(sideShowCost)
                .requiredBetPaise(requiredBet)
                .minRaiseBetPaise(minRaise)
                .maxBetPaise(maxBet)
                .raiseOptionsPaise(raiseOptions.stream().filter(v -> v <= walletBalance).toList())
                .playerContributedPaise(engine.getPlayerContributedPaise(userId))
                .walletBalancePaise(walletBalance)
                .blindSeenRatio(config.getBlindSeenRatio())
                .turnTimerSeconds(turnTimerSeconds)
                .myTurn(myTurn)
                .allowedActions(allowed)
                .variantPhase(resolveVariantPhaseLabel(table.getId()))
                .auctionHighBidPaise(resolveAuctionHighBid(table.getId()))
                .auctionHighBidderId(resolveAuctionHighBidder(table.getId()))
                .auctionMinBidPaise(resolveAuctionMinBid(table.getId()))
                .build();
    }

    /**
     * Resolves the authoritative bet amount for an action before wallet debit.
     * Client-requested amounts are ignored for Blind/Chaal/Show — server is source of truth.
     */
    public long resolveBetAmount(String actionType, BettingRoundEngine engine, String userId, long requestedAmount) {
        String normalized = normalizeActionType(actionType);
        return switch (normalized) {
            case "PLAY_BLIND", "BLIND", "CHAAL", "CALL" -> engine.getRequiredBetPaise(userId);
            case "RAISE" -> {
                List<Long> options = computeRaiseOptions(engine, userId);
                if (options.isEmpty()) {
                    yield 0L;
                }
                if (requestedAmount > 0 && options.contains(requestedAmount)) {
                    yield requestedAmount;
                }
                yield options.get(0);
            }
            case "SHOW" -> Math.max(engine.getConfig().getShowCostPaise(), engine.getRequiredBetPaise(userId));
            case "SIDE_SHOW_REQUEST" -> Math.max(engine.getConfig().getSideShowCostPaise(), engine.getRequiredBetPaise(userId));
            default -> 0L;
        };
    }

    public void validateBalance(String userId, long betAmount) {
        if (betAmount <= 0) {
            return;
        }
        WalletBalanceResponse balance = walletService.getBalance(userId);
        if (balance.getBalancePaise() < betAmount) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Required " + betAmount + " paise, available "
                            + balance.getBalancePaise() + " paise.");
        }
    }

    public void debitBet(String tableId, String handId, String userId, long betAmount) {
        if (betAmount <= 0) {
            return;
        }
        validateBalance(userId, betAmount);
        String refId = "bet:" + tableId + ":" + (handId != null ? handId : "live") + ":" + userId + ":"
                + System.currentTimeMillis();
        walletService.applyLedgerEntry(userId, LedgerEntryType.BET, betAmount, refId);
        WalletBalanceResponse balance = walletService.getBalance(userId);
        eventPublisher.publishWalletUpdated(userId, balance.getBalancePaise());
        log.debug("Debited {} paise from user [{}] on table [{}]", betAmount, userId, tableId);
    }

    public long collectBoot(String tableId, String handId, List<String> seated, long bootPaise) {
        long total = 0L;
        for (String playerId : seated) {
            validateBalance(playerId, bootPaise);
            String refId = "boot:" + tableId + ":" + handId + ":" + playerId;
            walletService.applyLedgerEntry(playerId, LedgerEntryType.BET, bootPaise, refId);
            WalletBalanceResponse balance = walletService.getBalance(playerId);
            eventPublisher.publishWalletUpdated(playerId, balance.getBalancePaise());
            total += bootPaise;
        }
        return total;
    }

    public void publishBettingState(Table table, BettingRoundEngine engine, String userId) {
        BettingState state = buildBettingState(table, engine, userId);
        // Per-player only — shared table topic would overwrite everyone else's myTurn/actions.
        eventPublisher.publishPrivateBettingState(userId, state);
    }

    public void publishBettingStateForTable(Table table, BettingRoundEngine engine) {
        if (table == null || engine == null) {
            return;
        }
        List<String> seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
        for (String playerId : seated) {
            publishBettingState(table, engine, playerId);
        }
    }

    public List<String> resolveAllowedActions(BettingRoundEngine engine, String userId) {
        return resolveAllowedActions(null, engine, userId);
    }

    public List<String> resolveAllowedActions(String tableId, BettingRoundEngine engine, String userId) {
        List<String> actions = new ArrayList<>();
        if (engine.isHandFinished()) {
            return actions;
        }

        PlayerStatus status = engine.getPlayerStatus(userId);
        if (status == null || status == PlayerStatus.PACKED) {
            return actions;
        }

        // Pre-betting variant phases gate normal betting actions.
        if (tableId != null && variantPhaseTracker.isDiscardPhase(tableId)) {
            if (variantPhaseTracker.hasPendingDiscard(tableId, userId)) {
                actions.add("DISCARD_CARD");
            }
            if (status == PlayerStatus.BLIND) {
                actions.add("SEE_CARDS");
            }
            return actions;
        }
        if (tableId != null && variantPhaseTracker.isAuctionPhase(tableId)) {
            if (variantPhaseTracker.canActInAuction(tableId, userId)) {
                actions.add("AUCTION_BID");
                actions.add("AUCTION_PASS");
            }
            if (status == PlayerStatus.BLIND) {
                actions.add("SEE_CARDS");
            }
            return actions;
        }

        // Pending side-show: only the target may Accept/Reject.
        if (tableId != null) {
            var pendingShow = handContextManager.getPendingShow(tableId);
            if (pendingShow.isPresent()) {
                if (userId.equals(pendingShow.get().targetId())) {
                    actions.add("SHOW_ACCEPT");
                    actions.add("SHOW_REJECT");
                }
                return actions;
            }

            var pending = handContextManager.getPendingSideShow(tableId);
            if (pending.isPresent()) {
                if (userId.equals(pending.get().targetId())) {
                    actions.add("SIDE_SHOW_ACCEPT");
                    actions.add("SIDE_SHOW_REJECT");
                }
                return actions;
            }
        }

        boolean myTurn = userId.equals(engine.getCurrentTurnPlayerId());

        if (status == PlayerStatus.BLIND) {
            actions.add("SEE_CARDS");
            if (!myTurn) {
                return actions;
            }
            actions.add("BLIND");
            if (canRaise(engine, userId)) {
                actions.add("RAISE");
            }
            actions.add("PACK");
            // Blind players may Show when only 2 remain — cards stay hidden on their UI.
            if (engine.getConfig().isShowEnabled() && engine.getActivePlayerIds().size() == 2) {
                actions.add("SHOW");
            }
            return actions;
        }

        if (status == PlayerStatus.SEEN) {
            if (!myTurn) {
                return actions;
            }
            actions.add("CHAAL");
            if (canRaise(engine, userId)) {
                actions.add("RAISE");
            }
            actions.add("PACK");
            if (engine.getConfig().isShowEnabled() && engine.getActivePlayerIds().size() == 2) {
                actions.add("SHOW");
            }
            if (canRequestSideShow(engine, userId)) {
                actions.add("SIDE_SHOW_REQUEST");
            }
        }

        return actions;
    }

    private List<String> filterAffordableActions(
            List<String> actions, BettingRoundEngine engine, String userId, long walletBalance) {
        List<String> out = new ArrayList<>();
        for (String action : actions) {
            if ("PACK".equals(action) || "SEE_CARDS".equals(action)
                    || "SIDE_SHOW_ACCEPT".equals(action) || "SIDE_SHOW_REJECT".equals(action)
                    || "SHOW_ACCEPT".equals(action) || "SHOW_REJECT".equals(action)
                    || "DISCARD_CARD".equals(action)
                    || "AUCTION_PASS".equals(action)) {
                out.add(action);
                continue;
            }
            long cost = resolveBetAmount(action, engine, userId, 0L);
            if (cost <= 0 || cost <= walletBalance) {
                out.add(action);
            }
        }
        // Raise only if at least one affordable option remains
        if (out.contains("RAISE")) {
            List<Long> affordable = computeRaiseOptions(engine, userId).stream()
                    .filter(v -> v <= walletBalance)
                    .toList();
            if (affordable.isEmpty()) {
                out.remove("RAISE");
            }
        }
        return out;
    }

    public String resolveSideShowTarget(BettingRoundEngine engine, String requesterId) {
        List<String> order = engine.getOrderedPlayerIds();
        int currentIdx = order.indexOf(requesterId);
        if (currentIdx < 0) {
            return null;
        }
        int n = order.size();
        for (int i = 1; i < n; i++) {
            int idx = (currentIdx - i + n) % n;
            String candidate = order.get(idx);
            if (engine.getPlayerStatus(candidate) == PlayerStatus.SEEN) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The other active player when exactly two remain (Show challenge target).
     */
    public String resolveShowTarget(BettingRoundEngine engine, String requesterId) {
        if (engine == null || requesterId == null) {
            return null;
        }
        return engine.getActivePlayerIds().stream()
                .filter(id -> !id.equals(requesterId))
                .findFirst()
                .orElse(null);
    }

    public boolean canRequestSideShow(BettingRoundEngine engine, String userId) {
        if (engine == null || userId == null || engine.isHandFinished() || !engine.getConfig().isSideShowEnabled()) {
            return false;
        }
        if (!userId.equals(engine.getCurrentTurnPlayerId())) {
            return false;
        }
        if (engine.getPlayerStatus(userId) != PlayerStatus.SEEN) {
            return false;
        }
        List<String> active = engine.getActivePlayerIds();
        if (active.size() <= 2) {
            return false;
        }
        return active.stream()
                .filter(pid -> !pid.equals(userId))
                .anyMatch(pid -> engine.getPlayerStatus(pid) == PlayerStatus.SEEN);
    }

    private boolean canRaise(BettingRoundEngine engine, String userId) {
        return !computeRaiseOptions(engine, userId).isEmpty();
    }

    private List<Long> computeRaiseOptions(BettingRoundEngine engine, String userId) {
        PlayerStatus status = engine.getPlayerStatus(userId);
        if (status == null || status == PlayerStatus.PACKED) {
            return List.of();
        }
        long currentStake = engine.getCurrentBaseStakePaise();
        int ratio = Math.max(1, engine.getConfig().getBlindSeenRatio());
        long maxBet = engine.getConfig().getMaxBetPaise();

        List<Long> configured = status == PlayerStatus.SEEN
                ? engine.getConfig().getSeenRaiseOptionsPaise()
                : engine.getConfig().getBlindRaiseOptionsPaise();

        List<Long> options = new ArrayList<>();
        if (configured != null) {
            configured.stream()
                    .filter(v -> v != null && v > 0)
                    .filter(v -> {
                        long newUnit = status == PlayerStatus.SEEN ? v / ratio : v;
                        return newUnit > currentStake;
                    })
                    .sorted()
                    .forEach(options::add);
        }

        if (options.isEmpty()) {
            // Always offer at least 2× / 4× current payment so Raise never disappears.
            long currentPay = Math.max(1L, status == PlayerStatus.SEEN ? currentStake * ratio : currentStake);
            long twoX = currentPay * 2;
            long fourX = currentPay * 4;
            long payCap = Math.max(currentPay * 8, status == PlayerStatus.SEEN ? maxBet * (long) ratio : maxBet);
            // Hard safety cap: never more than 50× table boot payment.
            long bootCap = Math.max(engine.getConfig().getBootAmountPaise(), 100L) * 50L
                    * (status == PlayerStatus.SEEN ? ratio : 1L);
            payCap = Math.min(payCap, bootCap);
            if (twoX <= payCap) {
                options.add(twoX);
            }
            if (fourX <= payCap && fourX != twoX) {
                options.add(fourX);
            }
        }
        // Drop absurd options above 50× boot.
        long hardCap = Math.max(engine.getConfig().getBootAmountPaise(), 100L) * 50L
                * (status == PlayerStatus.SEEN ? ratio : 1L);
        return options.stream().filter(v -> v != null && v > 0 && v <= hardCap).distinct().sorted().toList();
    }

    private long computeMinRaiseBetPaise(BettingRoundEngine engine, String userId) {
        List<Long> options = computeRaiseOptions(engine, userId);
        return options.isEmpty() ? 0L : options.get(0);
    }

    private String normalizeActionType(String actionType) {
        if (actionType == null) {
            return "";
        }
        String upper = actionType.toUpperCase(Locale.ROOT);
        if ("CALL".equals(upper)) {
            return "CHAAL";
        }
        if ("BLIND".equals(upper)) {
            return "PLAY_BLIND";
        }
        return upper;
    }

    private String resolveVariantPhaseLabel(String tableId) {
        if (tableId == null) {
            return null;
        }
        com.teenpatti.platform.game.variant.PreBettingPhase phase = variantPhaseTracker.getPhase(tableId);
        return phase == com.teenpatti.platform.game.variant.PreBettingPhase.NONE ? null : phase.name();
    }

    private long resolveAuctionHighBid(String tableId) {
        return variantPhaseTracker.getAuctionSnapshot(tableId)
                .map(VariantPhaseTracker.AuctionSnapshot::highBidPaise)
                .orElse(0L);
    }

    private String resolveAuctionHighBidder(String tableId) {
        return variantPhaseTracker.getAuctionSnapshot(tableId)
                .map(VariantPhaseTracker.AuctionSnapshot::highBidderId)
                .orElse(null);
    }

    private long resolveAuctionMinBid(String tableId) {
        return variantPhaseTracker.getAuctionSnapshot(tableId)
                .map(VariantPhaseTracker.AuctionSnapshot::minBidPaise)
                .orElse(0L);
    }
}
