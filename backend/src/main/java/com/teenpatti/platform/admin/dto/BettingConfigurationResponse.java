package com.teenpatti.platform.admin.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class BettingConfigurationResponse {
    String id;
    boolean active;
    long bootAmount;
    List<Long> bootAmountOptions;
    int minimumPlayers;
    int maximumPlayers;
    int turnTimer;
    long blindBetAmount;
    List<Long> blindRaiseOptions;
    long seenChaalAmount;
    List<Long> seenRaiseOptions;
    long showCost;
    long sideShowCost;
    boolean sideShowEnabled;
    boolean showEnabled;
    String updatedBy;
    Instant updatedAt;
    Long version;
}
