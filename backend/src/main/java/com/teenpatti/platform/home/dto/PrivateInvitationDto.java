package com.teenpatti.platform.home.dto;

import com.teenpatti.platform.lobby.dto.TableSummaryResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Private table invitation surfaced on login / home dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivateInvitationDto {

    private String notificationId;
    private String tableId;
    private String tableName;
    private String inviteCode;
    private String hostId;
    private String hostDisplayName;
    private long bootAmountPaise;
    private String gameVariant;
    private int currentPlayerCount;
    private int maxPlayers;
    private String status;
    private String invitedAt;
}
