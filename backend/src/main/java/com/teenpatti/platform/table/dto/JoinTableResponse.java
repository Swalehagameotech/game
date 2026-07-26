package com.teenpatti.platform.table.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinTableResponse {
    private String tableId;
    private int seatIndex;
    private long heldBuyInPaise;
    private TableDetailResponse tableDetail;
}
