package com.teenpatti.platform.websocket.dto;

import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.game.winner.WinnerSnapshot;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerViewGameState {
    private String tableId;
    private String hostId;
    private int minPlayers;
    private int maxPlayers;
    private TableStatus status;
    private String currentTurnPlayerId;
    private int dealerSeatIndex;
    private int currentTurnSeatIndex;
    private int turnTimeoutSeconds;
    private int turnSecondsRemaining;
    private Instant turnDeadlineAt;
    private List<String> activePlayerIds;
    private List<String> blindPlayerIds;
    private List<String> seenPlayerIds;
    private List<String> packedPlayerIds;
    private long potPaise;
    private long currentBaseStakePaise;
    private long blindAmountPaise;
    private long chaalAmountPaise;
    private long showCostPaise;
    private long sideShowCostPaise;
    private long requiredBetPaise;
    private long minRaiseBetPaise;
    private long maxBetPaise;
    private List<Long> raiseOptionsPaise;
    private long playerContributedPaise;
    private long walletBalancePaise;
    private int blindSeenRatio;
    private String playerState;
    private int turnTimerSeconds;
    private boolean myTurn;
    private List<String> allowedActions;
    private List<PlayerSummaryView> players;
    private HandOutcome handOutcome;
    private WinnerSnapshot winnerSnapshot;
    private String tableType;
    private String inviteCode;
    private int countdownSeconds;
    private long bootAmountPaise;
    /** Present while a final Show is awaiting accept (null otherwise). */
    private PendingShowView pendingShow;
    /** Seated players in disconnect grace period. */
    private List<String> disconnectedPlayerIds;
}
