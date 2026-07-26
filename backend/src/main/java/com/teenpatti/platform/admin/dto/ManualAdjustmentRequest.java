package com.teenpatti.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualAdjustmentRequest {

    @NotNull(message = "amountPaise is required")
    private Long amountPaise;

    @NotBlank(message = "reason is required")
    private String reason;
}
