package com.teenpatti.platform.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameWebSocketMessage {
    private String type; // e.g. JOIN_TABLE, PLAY_BLIND, SEE_CARDS, CHAAL, RAISE, PACK, SHOW
    private String tableId;
    private long amountPaise;
}
