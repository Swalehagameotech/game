package com.teenpatti.platform.game.betting;

import com.teenpatti.platform.common.exception.InsufficientBalanceException;
import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.PlayerStatus;
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

    public BettingState buildBettingState(Table table, BettingRoundEngine engine, String userId) {
        if (table == null || engine == null || userId == null) {
            throw new IllegalArgumentException("Table, engine, and userId must not be null");
        }

        GameEngineConfig config = engine.getConfig();
        PlayerStatus status = engine.getPlayerStatus(userId);
        boolean inHand = status != null && status != PlayerStatus.PACKED;
        boolean myTurn = inHand && userId.equals(engine.getCurrentTurnPlayerId());
        long requiredBet = inHand ? engine.getRequiredBetPaise(userId) : 0L;
        long minRaise = inHand ? computeMinRaiseBetPaise(engine, userId) : 0L;

        return BettingState.builder()
                .tableId(table.getId())
                .userId(userId)
                .potPaise(engine.getPotPaise())
                .currentBaseStakePaise(engine.getCurrentBaseStakePaise())
                .requiredBetPaise(requiredBet)
                .minRaiseBetPaise(minRaise)
                .maxBetPaise(config.getMaxBetPaise())
                .playerContributedPaise(engine.getPlayerContributedPaise(userId))
                .blindSeenRatio(config.getBlindSeenRatio())
                .myTurn(myTurn)
                .allowedActions(resolveAllowedActions(engine, userId))
                .build();
    }

    /**
     * Resolves the authoritative bet amount for an action before wallet debit.
     */
    public long resolveBetAmount(String actionType, BettingRoundEngine engine, String userId, long requestedAmount) {
        String normalized = normalizeActionType(actionType);
        return switch (normalized) {
            case "PLAY_BLIND", "CHAAL", "CALL" -> {
                long required = engine.getRequiredBetPaise(userId);
                yield requestedAmount > 0 ? Math.max(requestedAmount, required) : required;
            }
            case "RAISE" -> {
                long minRaise = computeMinRaiseBetPaise(engine, userId);
                if (requestedAmount > 0) {
                    yield Math.max(requestedAmount, minRaise);
                }
                yield minRaise;
            }
            case "SHOW" -> engine.getRequiredBetPaise(userId);
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
        eventPublisher.publishBettingState(table.getId(), state);
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
        List<String> actions = new ArrayList<>();
        if (engine.isHandFinished()) {
            return actions;
        }

        PlayerStatus status = engine.getPlayerStatus(userId);
        if (status == null || status == PlayerStatus.PACKED) {
            return actions;
        }

        if (status == PlayerStatus.BLIND) {
            actions.add("SEE_CARDS");
        }

        boolean myTurn = userId.equals(engine.getCurrentTurnPlayerId());
        if (!myTurn) {
            return actions;
        }

        if (status == PlayerStatus.BLIND) {
            actions.add("PLAY_BLIND");
        }
        actions.add("CHAAL");
        actions.add("CALL");
        actions.add("PACK");

        if (canRaise(engine, userId)) {
            actions.add("RAISE");
        }
        if (engine.getActivePlayerIds().size() == 2) {
            actions.add("SHOW");
        }
        if (status == PlayerStatus.SEEN && engine.getActivePlayerIds().size() > 2) {
            actions.add("SIDE_SHOW_REQUEST");
        }

        return actions;
    }

    private boolean canRaise(BettingRoundEngine engine, String userId) {
        long minRaise = computeMinRaiseBetPaise(engine, userId);
        return minRaise > 0 && minRaise <= engine.getConfig().getMaxBetPaise() * engine.getConfig().getBlindSeenRatio();
    }

    private long computeMinRaiseBetPaise(BettingRoundEngine engine, String userId) {
        PlayerStatus status = engine.getPlayerStatus(userId);
        if (status == null || status == PlayerStatus.PACKED) {
            return 0L;
        }
        long nextBaseStake = engine.getCurrentBaseStakePaise() * 2;
        if (nextBaseStake > engine.getConfig().getMaxBetPaise()) {
            return 0L;
        }
        return status == PlayerStatus.SEEN
                ? nextBaseStake * engine.getConfig().getBlindSeenRatio()
                : nextBaseStake;
    }

    private String normalizeActionType(String actionType) {
        if (actionType == null) {
            return "";
        }
        String upper = actionType.toUpperCase(Locale.ROOT);
        if ("CALL".equals(upper)) {
            return "CHAAL";
        }
        return upper;
    }
}
