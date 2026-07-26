package com.teenpatti.platform.game.engine;

/**
 * Standard card suits for Teen Patti.
 */
public enum Suit {
    SPADES("s"),
    HEARTS("h"),
    DIAMONDS("d"),
    CLUBS("c");

    private final String symbol;

    Suit(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
