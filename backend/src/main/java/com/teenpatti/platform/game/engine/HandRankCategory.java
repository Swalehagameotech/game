package com.teenpatti.platform.game.engine;

/**
 * Teen Patti 3-card hand rank categories in strict priority order (highest to lowest).
 */
public enum HandRankCategory {
    TRAIL(6, "Trail (Three of a Kind)"),
    PURE_SEQUENCE(5, "Pure Sequence (Straight Flush)"),
    SEQUENCE(4, "Sequence (Straight)"),
    COLOR(3, "Color (Flush)"),
    PAIR(2, "Pair"),
    HIGH_CARD(1, "High Card");

    private final int priority;
    private final String description;

    HandRankCategory(int priority, String description) {
        this.priority = priority;
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }
}
