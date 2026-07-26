package com.teenpatti.platform.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableBroadcastPayload implements Serializable {
    private String tableId;
    private String recipientUserId;
    private String messageJson;
}
