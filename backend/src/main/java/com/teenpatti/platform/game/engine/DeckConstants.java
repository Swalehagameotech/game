package com.teenpatti.platform.game.engine;

/**
 * Constants for standard Teen Patti deck composition.
 */
public final class DeckConstants {

    public static final int STANDARD_DECK_SIZE = 52;
    public static final int CARDS_PER_HAND = 3;
    public static final int SUITS_COUNT = Suit.values().length;
    public static final int RANKS_COUNT = Rank.values().length;

    private DeckConstants() {
    }
}
