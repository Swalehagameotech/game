package com.teenpatti.platform.lobby.config;

import com.teenpatti.platform.table.StakeTier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration bean providing minimum buy-in requirements per StakeTier.
 * Shared across Lobby eligibility checks and future game engine bet validations.
 */
@Component
public class StakeTierConfig {

    private final Map<StakeTier, Long> minBuyInMap = new EnumMap<>(StakeTier.class);

    public StakeTierConfig(
            @Value("${app.stake.low.min-buy-in-paise:1000}") long lowMinBuyIn,
            @Value("${app.stake.medium.min-buy-in-paise:5000}") long mediumMinBuyIn,
            @Value("${app.stake.high.min-buy-in-paise:25000}") long highMinBuyIn) {
        minBuyInMap.put(StakeTier.LOW, lowMinBuyIn);
        minBuyInMap.put(StakeTier.MEDIUM, mediumMinBuyIn);
        minBuyInMap.put(StakeTier.HIGH, highMinBuyIn);
    }

    public long getMinBuyInPaise(StakeTier stakeTier) {
        if (stakeTier == null) {
            throw new IllegalArgumentException("StakeTier must not be null");
        }
        return minBuyInMap.getOrDefault(stakeTier, 1000L);
    }
}
