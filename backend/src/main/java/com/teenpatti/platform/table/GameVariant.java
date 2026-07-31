package com.teenpatti.platform.table;

/**
 * Teen Patti table game variants.
 * {@link #CLASSIC} is the only fully implemented variant; others are reserved for future rules engines.
 */
public enum GameVariant {
    CLASSIC,
    AK47,
    JOKER,
    MUFLIS,
    BEST_OF_FOUR,
    DISCARD_ONE,
    LOWEST_JOKER,
    HIGH_WILD,
    LOW_WILD,
    HIDDEN_JOKER,
    ONE_EYED_JACK,
    BUST_CARD,
    REVOLVING_JOKER,
    NINE_NINE_NINE,
    TWENTY_TWENTY,
    AUCTION,
    BANKO,
    DEALERS_CHOICE,
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
