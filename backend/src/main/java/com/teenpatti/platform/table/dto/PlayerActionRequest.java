package com.teenpatti.platform.table.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlayerActionRequest {
    @NotBlank
    private String actionType;
    private long amountPaise;
}
