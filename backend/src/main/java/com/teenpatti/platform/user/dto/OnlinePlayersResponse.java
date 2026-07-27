package com.teenpatti.platform.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Platform online player count for lobby display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlinePlayersResponse {
    private int onlinePlayers;
}
