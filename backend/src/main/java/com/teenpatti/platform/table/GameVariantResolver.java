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
            case "AK47" -> GameVariant.AK47;
            case "JOKER" -> GameVariant.JOKER;
            case "MUFLIS" -> GameVariant.MUFLIS;
            case "BEST_OF_FOUR", "BESTOFFOUR" -> GameVariant.BEST_OF_FOUR;
            case "DISCARD_ONE", "DISCARDONE" -> GameVariant.DISCARD_ONE;
            case "LOWEST_JOKER", "LOWESTJOKER" -> GameVariant.LOWEST_JOKER;
            case "HIGH_WILD", "HIGHWILD" -> GameVariant.HIGH_WILD;
            case "LOW_WILD", "LOWWILD" -> GameVariant.LOW_WILD;
            case "HIDDEN_JOKER", "HIDDENJOKER" -> GameVariant.HIDDEN_JOKER;
            case "ONE_EYED_JACK", "ONEEYEDJACK" -> GameVariant.ONE_EYED_JACK;
            case "BUST_CARD", "BUSTCARD" -> GameVariant.BUST_CARD;
            case "REVOLVING_JOKER", "REVOLVINGJOKER" -> GameVariant.REVOLVING_JOKER;
            case "999", "NINE_NINE_NINE", "NINENINENINE" -> GameVariant.NINE_NINE_NINE;
            case "2020", "TWENTY_TWENTY", "TWENTYTWENTY" -> GameVariant.TWENTY_TWENTY;
            case "AUCTION" -> GameVariant.AUCTION;
            case "BANKO" -> GameVariant.BANKO;
            case "DEALERS_CHOICE", "DEALERSCHOICE", "DEALER'S_CHOICE" -> GameVariant.DEALERS_CHOICE;
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
