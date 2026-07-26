package com.teenpatti.platform.lobby.dto;

import com.teenpatti.platform.table.StakeTier;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePrivateTableRequest {

    @NotNull(message = "stakeTier is required")
    private StakeTier stakeTier;

    @Min(value = 2, message = "maxPlayers must be at least 2")
    @Max(value = 6, message = "maxPlayers cannot exceed 6")
    private int maxPlayers;
}
