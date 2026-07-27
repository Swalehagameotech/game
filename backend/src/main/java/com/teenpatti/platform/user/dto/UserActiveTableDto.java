package com.teenpatti.platform.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of the table the user is currently seated at (if any).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActiveTableDto {
    private String tableId;
    private String tableName;
    private String status;
    private String tableType;
    private int seatedCount;
    private int maxPlayers;
    private int userSeatIndex;
    private boolean host;
}
