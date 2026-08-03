package com.teenpatti.platform.websocket.dto;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.PlayerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSummaryView {
    private String userId;
    private String displayName;
    private String avatarUrl;
    private PlayerStatus status;
    private long totalContributedPaise;
    /**
     * Non-null ONLY if:
     * 1. This player is the recipient AND has chosen to SEE cards, OR
     * 2. This player's cards were revealed at showdown in HandOutcome.
     */
    private List<Card> cards;
    private int cardCount;
    private boolean connected;
}
