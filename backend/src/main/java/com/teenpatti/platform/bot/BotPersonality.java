package com.teenpatti.platform.bot;

/**
 * Bot play style — weights fold / call / raise / bluff and thinking pace.
 * Tuned for strong table presence (low fold bias).
 */
public enum BotPersonality {
    AGGRESSIVE(1.15, 0.08, 0.22, 0.85, 0.55),
    DEFENSIVE(0.45, 0.22, 0.06, 1.05, 0.85),
    BLUFFER(0.95, 0.10, 0.28, 0.90, 0.60),
    BALANCED(0.85, 0.12, 0.14, 1.00, 0.65),
    RISKY(1.20, 0.07, 0.24, 0.75, 0.50),
    BEGINNER(0.55, 0.18, 0.08, 1.10, 0.80),
    PROFESSIONAL(1.05, 0.09, 0.16, 0.90, 0.58);

    private final double raiseBias;
    private final double foldBias;
    private final double bluffChance;
    /** Multiplier on thinking delay (lower = snappier). */
    private final double thinkSpeed;
    /** Multiplier on fold probability for weak hands. */
    private final double caution;

    BotPersonality(double raiseBias, double foldBias, double bluffChance, double thinkSpeed, double caution) {
        this.raiseBias = raiseBias;
        this.foldBias = foldBias;
        this.bluffChance = bluffChance;
        this.thinkSpeed = thinkSpeed;
        this.caution = caution;
    }

    public double raiseBias() { return raiseBias; }
    public double foldBias() { return foldBias; }
    public double bluffChance() { return bluffChance; }
    public double thinkSpeed() { return thinkSpeed; }
    public double caution() { return caution; }

    /** Prefer strong styles when spawning bots (~90% aggressive pool). */
    public static BotPersonality randomStrong() {
        BotPersonality[] strong = {
                AGGRESSIVE, AGGRESSIVE, AGGRESSIVE,
                RISKY, RISKY,
                PROFESSIONAL, PROFESSIONAL, PROFESSIONAL,
                BLUFFER, BLUFFER,
                BALANCED,
                DEFENSIVE
        };
        return strong[java.util.concurrent.ThreadLocalRandom.current().nextInt(strong.length)];
    }
}
