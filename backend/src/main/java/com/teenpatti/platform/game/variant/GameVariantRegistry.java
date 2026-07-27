package com.teenpatti.platform.game.variant;

import com.teenpatti.platform.table.GameVariant;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@link GameVariantStrategy} for a table variant. Falls back to Classic for legacy aliases.
 */
@Component
public class GameVariantRegistry {

    private final Map<GameVariant, GameVariantStrategy> strategies = new EnumMap<>(GameVariant.class);

    public GameVariantRegistry(List<GameVariantStrategy> implementedStrategies) {
        for (GameVariantStrategy strategy : implementedStrategies) {
            strategies.put(strategy.getVariant(), strategy);
        }
        for (GameVariant variant : GameVariant.values()) {
            strategies.computeIfAbsent(variant, v -> new UnsupportedVariantStrategy(v));
        }
    }

    public GameVariantStrategy requireStrategy(GameVariant variant) {
        GameVariant resolved = variant != null ? variant : GameVariant.CLASSIC;
        if (resolved == GameVariant.HIGHER || resolved == GameVariant.MEDIUM || resolved == GameVariant.LOWER) {
            resolved = GameVariant.CLASSIC;
        }
        GameVariantStrategy strategy = strategies.get(resolved);
        if (strategy == null) {
            strategy = strategies.get(GameVariant.CLASSIC);
        }
        if (!strategy.isFullyImplemented()) {
            throw new IllegalStateException(
                    "Game variant " + resolved + " is not yet supported. Only CLASSIC is available.");
        }
        return strategy;
    }
}
