package com.teenpatti.platform.admin.dto;

import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTableSummaryDto {

    private String id;
    private String tableName;
    private TableType tableType;
    private TableStatus status;
    private String hostId;
    private int seatedCount;
    private int maxPlayers;
    private long bootAmountPaise;
    private int countdownSeconds;
    private Instant createdAt;
    private Instant updatedAt;
}
