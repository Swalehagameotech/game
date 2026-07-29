package com.teenpatti.platform.game.dto;

import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.PlayerStatus;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Private response to the authenticated player after successfully seeing cards.
 * Contains ONLY that player's three cards — never opponents' hands or the deck.
 */
@Value
@Builder
public class SeeCardsResponse {
    String tableId;
    String userId;
    PlayerStatus status;
    List<Card> cards;
}
