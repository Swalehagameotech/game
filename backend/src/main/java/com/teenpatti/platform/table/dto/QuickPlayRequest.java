package com.teenpatti.platform.table.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for Quick Play auto-matchmaking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickPlayRequest {

    @NotNull(message = "Boot amount in paise is required")
    @Min(value = 100, message = "Boot amount must be at least ₹1 (100 paise)")
    private Long bootAmountPaise;
}
