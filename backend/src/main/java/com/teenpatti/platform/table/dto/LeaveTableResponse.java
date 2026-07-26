package com.teenpatti.platform.table.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveTableResponse {
    private String tableId;
    private boolean refunded;
    private long refundAmountPaise;
    private String message;
}
