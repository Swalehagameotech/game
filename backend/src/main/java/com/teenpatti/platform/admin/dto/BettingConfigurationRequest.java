package com.teenpatti.platform.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BettingConfigurationRequest {
    @Min(1)
    private long bootAmount;
    @NotNull
    @NotEmpty
    private List<Long> bootAmountOptions;
    @Min(2)
    private int minimumPlayers;
    @Min(2)
    private int maximumPlayers;
    @Min(5)
    private int turnTimer;

    @Min(1)
    private long blindBetAmount;
    @NotNull
    @NotEmpty
    private List<Long> blindRaiseOptions;

    @Min(1)
    private long seenChaalAmount;
    @NotNull
    @NotEmpty
    private List<Long> seenRaiseOptions;

    @Min(1)
    private long showCost;
    @Min(1)
    private long sideShowCost;

    private boolean sideShowEnabled;
    private boolean showEnabled;
}
