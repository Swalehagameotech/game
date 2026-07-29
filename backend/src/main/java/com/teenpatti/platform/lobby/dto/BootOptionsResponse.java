package com.teenpatti.platform.lobby.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class BootOptionsResponse {
    List<Long> bootAmountOptionsPaise;
    int minimumPlayers;
    int maximumPlayers;
}
