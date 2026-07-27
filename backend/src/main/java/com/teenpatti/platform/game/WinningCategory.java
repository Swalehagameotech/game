package com.teenpatti.platform.game;

/**
 * How a Teen Patti hand was decided (showdown rank or fold win).
 */
public enum WinningCategory {
    FOLD_WIN,
    TRAIL,
    PURE_SEQUENCE,
    SEQUENCE,
    COLOR,
    PAIR,
    HIGH_CARD
}
