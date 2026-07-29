package com.teenpatti.platform.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingShowView {
    private String requesterId;
    private String targetId;
    private String requesterDisplayName;
}
