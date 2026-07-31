package com.teenpatti.platform.lobby.dto;

import com.teenpatti.platform.table.StakeTier;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePrivateTableRequest {

    private String tableName;

    private String tableType; // PUBLIC or PRIVATE

    @NotNull(message = "stakeTier is required")
    @Builder.Default
    private StakeTier stakeTier = StakeTier.LOW;

    @Min(value = 2, message = "maxPlayers must be at least 2")
    @Max(value = 6, message = "maxPlayers cannot exceed 6")
    @Builder.Default
    private int maxPlayers = 6;

    @Min(value = 2, message = "minPlayers must be at least 2")
    @Max(value = 6, message = "minPlayers cannot exceed 6")
    @Builder.Default
    private int minPlayers = 3;

    private Long bootAmount;

    private String gameVariant; // CLASSIC, AK47, JOKER, MUFLIS, ...

    /** Optional user IDs to receive in-app GAME_INVITE notifications. */
    private List<String> inviteUserIds;
}
