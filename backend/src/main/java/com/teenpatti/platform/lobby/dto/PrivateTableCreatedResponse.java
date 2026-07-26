package com.teenpatti.platform.lobby.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivateTableCreatedResponse {
    private String tableId;
    private String inviteCode;
}
