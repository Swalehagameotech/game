package com.teenpatti.platform.table;

/**
 * Resolves client-provided variant strings to {@link GameVariant}, defaulting to {@link GameVariant#CLASSIC}.
 */
public final class GameVariantResolver {

    private GameVariantResolver() {
    }

    public static GameVariant resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return GameVariant.CLASSIC;
        }
        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "CLASSIC", "HIGHER" -> GameVariant.CLASSIC;
            case "MUFLIS" -> GameVariant.MUFLIS;
            case "AK47" -> GameVariant.AK47;
            case "JOKER" -> GameVariant.JOKER;
            case "BEST_OF_FOUR", "BESTOFFOUR" -> GameVariant.BEST_OF_FOUR;
            case "999", "NINE_NINE", "NINENINE" -> GameVariant.NINE_NINE;
            default -> {
                try {
                    yield GameVariant.valueOf(normalized);
                } catch (IllegalArgumentException ex) {
                    yield GameVariant.CLASSIC;
                }
            }
        };
    }
}
