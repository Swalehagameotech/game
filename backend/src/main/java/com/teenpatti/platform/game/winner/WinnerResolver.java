package com.teenpatti.platform.game.winner;

import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.HandOutcome;

import java.util.List;
import java.util.Map;

import com.teenpatti.platform.game.engine.Card;

/**
 * Pluggable hand completion resolver used by {@link com.teenpatti.platform.game.engine.BettingRoundEngine}.
 */
public interface WinnerResolver {

    HandOutcome resolveFoldWin(String winnerId, long potPaise, GameEngineConfig config);

    HandOutcome resolveShowdown(
            String playerOneId,
            List<Card> playerOneHand,
            String playerTwoId,
            List<Card> playerTwoHand,
            long potPaise,
            GameEngineConfig config);
}
