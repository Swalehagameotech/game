package com.teenpatti.platform.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameServerMessage {
    private String type; // e.g. STATE_UPDATE, ACTION_REJECTED, ERROR, HAND_COMPLETED
    private Object payload;
    private String reason;

    public static GameServerMessage stateUpdate(Object payload) {
        return GameServerMessage.builder()
                .type("STATE_UPDATE")
                .payload(payload)
                .build();
    }

    public static GameServerMessage actionRejected(String reason) {
        return GameServerMessage.builder()
                .type("ACTION_REJECTED")
                .reason(reason)
                .build();
    }

    public static GameServerMessage error(String message) {
        return GameServerMessage.builder()
                .type("ERROR")
                .reason(message)
                .build();
    }
}
