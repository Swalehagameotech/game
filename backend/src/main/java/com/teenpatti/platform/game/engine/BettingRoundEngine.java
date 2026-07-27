package com.teenpatti.platform.game.engine;

import com.teenpatti.platform.game.variant.ClassicVariantStrategy;
import com.teenpatti.platform.game.winner.VariantWinnerResolver;
import com.teenpatti.platform.game.winner.WinnerResolver;

import java.util.*;

/**
 * Pure Java State Machine for Teen Patti betting rounds.
 * Manages player turns, blind/seen status transitions, chaal/raise/pack/show validation,
 * and exact pot & rake calculation upon hand completion.
 */
public class BettingRoundEngine {

    private final GameEngineConfig config;
    private final WinnerResolver winnerResolver;
    private final List<String> playerIds;
    private final Map<String, PlayerStatus> playerStatusMap;
    private final Map<String, List<Card>> playerHandMap;
    private final Map<String, Long> playerContributedMap;

    private int currentTurnIndex;
    private long currentBaseStakePaise;
    private long potPaise;
    private boolean handFinished;
    private HandOutcome outcome;

    public BettingRoundEngine(GameEngineConfig config) {
        this(config, new VariantWinnerResolver(new ClassicVariantStrategy()));
    }

    public BettingRoundEngine(GameEngineConfig config, WinnerResolver winnerResolver) {
        if (config == null) {
            throw new IllegalArgumentException("GameEngineConfig must not be null");
        }
        if (winnerResolver == null) {
            throw new IllegalArgumentException("WinnerResolver must not be null");
        }
        this.config = config;
        this.winnerResolver = winnerResolver;
        this.playerIds = new ArrayList<>();
        this.playerStatusMap = new HashMap<>();
        this.playerHandMap = new HashMap<>();
        this.playerContributedMap = new HashMap<>();
        this.handFinished = false;
    }

    /**
     * Initializes a hand using a pre-dealt private hand map (from {@link com.teenpatti.platform.game.distribution.CardDistributionService}).
     */
    public void startHand(List<String> players, Map<String, List<Card>> privateHands) {
        if (players == null || players.size() < 2) {
            throw new IllegalArgumentException("At least 2 players are required to start a Teen Patti hand");
        }
        if (privateHands == null || privateHands.isEmpty()) {
            throw new IllegalArgumentException("Private hands must not be null or empty");
        }

        this.playerIds.clear();
        this.playerIds.addAll(players);
        this.playerStatusMap.clear();
        this.playerHandMap.clear();
        this.playerContributedMap.clear();

        long bootPaise = config.getBootAmountPaise();
        this.potPaise = 0L;

        for (String playerId : playerIds) {
            List<Card> hand = privateHands.get(playerId);
            if (hand == null || hand.size() != com.teenpatti.platform.game.engine.DeckConstants.CARDS_PER_HAND) {
                throw new IllegalArgumentException(
                        "Player " + playerId + " must have exactly "
                                + com.teenpatti.platform.game.engine.DeckConstants.CARDS_PER_HAND + " cards dealt");
            }
            this.playerStatusMap.put(playerId, PlayerStatus.BLIND);
            this.playerHandMap.put(playerId, new ArrayList<>(hand));
            this.playerContributedMap.put(playerId, bootPaise);
            this.potPaise += bootPaise;
        }

        this.currentBaseStakePaise = bootPaise;
        this.currentTurnIndex = 0;
        this.handFinished = false;
        this.outcome = null;
    }

    public void startHand(List<String> players, Deck deck) {
        if (deck == null) {
            throw new IllegalArgumentException("Deck must not be null");
        }
        com.teenpatti.platform.game.distribution.CardDistributionService distributionService =
                new com.teenpatti.platform.game.distribution.CardDistributionService();
        startHand(players, distributionService.dealPrivateHands(deck, players).getHandsByPlayerId());
    }

    /**
     * Returns the required minimum bet amount in paise for the specified player given their current BLIND/SEEN status.
     */
    public long getRequiredBetPaise(String playerId) {
        PlayerStatus status = playerStatusMap.get(playerId);
        if (status == null || status == PlayerStatus.PACKED) {
            return 0L;
        }
        return status == PlayerStatus.SEEN ? (currentBaseStakePaise * config.getBlindSeenRatio()) : currentBaseStakePaise;
    }

    /**
     * Submits a player action to the game state machine.
     */
    public synchronized void applyAction(PlayerAction action) {
        if (handFinished) {
            throw new InvalidActionException(ActionRejectionReason.HAND_ALREADY_FINISHED);
        }
        if (action == null || action.getPlayerId() == null) {
            throw new IllegalArgumentException("PlayerAction and playerId must not be null");
        }

        String playerId = action.getPlayerId();
        if (!playerIds.contains(playerId) || playerStatusMap.get(playerId) == PlayerStatus.PACKED) {
            throw new InvalidActionException(ActionRejectionReason.PLAYER_NOT_IN_HAND);
        }

        // Action: SEE_CARDS (can be done anytime by a BLIND player)
        if (action.getActionType() == PlayerActionType.SEE_CARDS) {
            if (playerStatusMap.get(playerId) == PlayerStatus.SEEN) {
                throw new InvalidActionException(ActionRejectionReason.ALREADY_SEEN);
            }
            playerStatusMap.put(playerId, PlayerStatus.SEEN);
            return; // Does not advance turn
        }

        // Turn Validation for turn-based actions
        String activeTurnPlayer = getCurrentTurnPlayerId();
        if (!playerId.equals(activeTurnPlayer)) {
            throw new InvalidActionException(ActionRejectionReason.NOT_YOUR_TURN);
        }

        switch (action.getActionType()) {
            case PLAY_BLIND -> handlePlayBlind(playerId, action.getAmountPaise());
            case CHAAL -> handleChaal(playerId, action.getAmountPaise());
            case RAISE -> handleRaise(playerId, action.getAmountPaise());
            case PACK -> handlePack(playerId);
            case SHOW -> handleShow(playerId, action.getAmountPaise());
            default -> throw new IllegalArgumentException("Unsupported action type: " + action.getActionType());
        }
    }

    private void handlePlayBlind(String playerId, long betAmount) {
        if (playerStatusMap.get(playerId) != PlayerStatus.BLIND) {
            throw new InvalidActionException(ActionRejectionReason.MUST_BE_BLIND);
        }
        long required = currentBaseStakePaise;
        if (betAmount < required) {
            throw new InvalidActionException(ActionRejectionReason.INSUFFICIENT_BET_AMOUNT,
                    "Play blind requires minimum bet of " + required + " paise, but got " + betAmount);
        }
        recordBet(playerId, betAmount);
        advanceTurn();
    }

    private void handleChaal(String playerId, long betAmount) {
        long required = getRequiredBetPaise(playerId);
        if (betAmount < required) {
            throw new InvalidActionException(ActionRejectionReason.INSUFFICIENT_BET_AMOUNT,
                    "Chaal requires minimum bet of " + required + " paise for status " + playerStatusMap.get(playerId) + ", but got " + betAmount);
        }
        recordBet(playerId, betAmount);
        advanceTurn();
    }

    private void handleRaise(String playerId, long betAmount) {
        PlayerStatus status = playerStatusMap.get(playerId);
        long newBaseStake = status == PlayerStatus.SEEN ? betAmount / config.getBlindSeenRatio() : betAmount;

        if (newBaseStake <= currentBaseStakePaise) {
            throw new InvalidActionException(ActionRejectionReason.INSUFFICIENT_BET_AMOUNT,
                    "Raise must increase the base stake unit above " + currentBaseStakePaise + " paise");
        }
        if (newBaseStake > config.getMaxBetPaise()) {
            throw new InvalidActionException(ActionRejectionReason.EXCEEDS_MAX_BET,
                    "Base stake unit " + newBaseStake + " exceeds configured max limit " + config.getMaxBetPaise());
        }

        long required = status == PlayerStatus.SEEN ? newBaseStake * config.getBlindSeenRatio() : newBaseStake;
        if (betAmount < required) {
            throw new InvalidActionException(ActionRejectionReason.INSUFFICIENT_BET_AMOUNT);
        }

        this.currentBaseStakePaise = newBaseStake;
        recordBet(playerId, betAmount);
        advanceTurn();
    }

    private void handlePack(String playerId) {
        playerStatusMap.put(playerId, PlayerStatus.PACKED);

        List<String> activePlayers = getActivePlayerIds();
        if (activePlayers.size() == 1) {
            String winnerId = activePlayers.get(0);
            finishHandByFold(winnerId);
        } else {
            advanceTurn();
        }
    }

    private void handleShow(String playerId, long betAmount) {
        List<String> activePlayers = getActivePlayerIds();
        if (activePlayers.size() != 2) {
            throw new InvalidActionException(ActionRejectionReason.SHOW_REQUIRES_EXACTLY_TWO_PLAYERS);
        }

        long required = getRequiredBetPaise(playerId);
        if (betAmount < required) {
            throw new InvalidActionException(ActionRejectionReason.INSUFFICIENT_BET_AMOUNT,
                    "Show requires minimum bet of " + required + " paise, but got " + betAmount);
        }

        recordBet(playerId, betAmount);
        finishHandByShow(activePlayers);
    }

    private void recordBet(String playerId, long betAmount) {
        potPaise += betAmount;
        playerContributedMap.put(playerId, playerContributedMap.getOrDefault(playerId, 0L) + betAmount);
    }

    private void finishHandByFold(String winnerId) {
        handFinished = true;
        outcome = winnerResolver.resolveFoldWin(winnerId, potPaise, config);
    }

    private void finishHandByShow(List<String> activePlayers) {
        String p1 = activePlayers.get(0);
        String p2 = activePlayers.get(1);
        handFinished = true;
        outcome = winnerResolver.resolveShowdown(
                p1,
                playerHandMap.get(p1),
                p2,
                playerHandMap.get(p2),
                potPaise,
                config);
    }

    private void advanceTurn() {
        int original = currentTurnIndex;
        int count = playerIds.size();
        for (int i = 1; i <= count; i++) {
            int nextIdx = (original + i) % count;
            String nextPlayerId = playerIds.get(nextIdx);
            if (playerStatusMap.get(nextPlayerId) != PlayerStatus.PACKED) {
                currentTurnIndex = nextIdx;
                return;
            }
        }
    }

    public List<String> getActivePlayerIds() {
        return playerIds.stream()
                .filter(id -> playerStatusMap.get(id) != PlayerStatus.PACKED)
                .toList();
    }

    public List<String> getPlayerIdsByStatus(PlayerStatus status) {
        if (status == null) {
            return List.of();
        }
        return playerIds.stream()
                .filter(id -> playerStatusMap.get(id) == status)
                .toList();
    }

    /**
     * Sets the opening turn to the player left of the dealer.
     */
    public void setStartingTurnPlayer(String playerId) {
        int idx = playerIds.indexOf(playerId);
        if (idx < 0) {
            throw new IllegalArgumentException("Player not in hand: " + playerId);
        }
        if (handFinished) {
            throw new IllegalStateException("Cannot set turn on finished hand");
        }
        currentTurnIndex = idx;
    }

    public String getCurrentTurnPlayerId() {
        if (handFinished || playerIds.isEmpty()) return null;
        return playerIds.get(currentTurnIndex);
    }

    public List<Card> getPlayerCards(String playerId) {
        return Collections.unmodifiableList(playerHandMap.getOrDefault(playerId, List.of()));
    }

    public PlayerStatus getPlayerStatus(String playerId) {
        return playerStatusMap.get(playerId);
    }

    public long getCurrentBaseStakePaise() {
        return currentBaseStakePaise;
    }

    public long getPotPaise() {
        return potPaise;
    }

    public long getPlayerContributedPaise(String playerId) {
        return playerContributedMap.getOrDefault(playerId, 0L);
    }

    public boolean isHandFinished() {
        return handFinished;
    }

    public HandOutcome getOutcome() {
        return outcome;
    }

    public GameEngineConfig getConfig() {
        return config;
    }
}
