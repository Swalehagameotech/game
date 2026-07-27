package com.teenpatti.platform.table;

/**
 * Teen Patti table game variants.
 * {@link #CLASSIC} is the only fully implemented variant; others are reserved for future rules engines.
 */
public enum GameVariant {
    CLASSIC,
    MUFLIS,
    AK47,
    JOKER,
    BEST_OF_FOUR,
    NINE_NINE,
    /** @deprecated Use {@link #CLASSIC}. Kept for backward-compatible Mongo documents. */
    @Deprecated
    HIGHER,
    /** @deprecated Legacy stake-tier alias. */
    @Deprecated
    MEDIUM,
    /** @deprecated Legacy stake-tier alias. */
    @Deprecated
    LOWER
}
