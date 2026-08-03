package com.teenpatti.platform.bot;

/**
 * Bot play style — weights fold / call / raise / bluff and thinking pace.
 */
public enum BotPersonality {
    AGGRESSIVE(0.55, 0.20, 0.12, 0.75, 0.85),
    DEFENSIVE(0.15, 0.55, 0.04, 1.15, 1.10),
    BLUFFER(0.35, 0.25, 0.18, 0.90, 0.95),
    BALANCED(0.30, 0.40, 0.08, 1.00, 1.00),
    RISKY(0.50, 0.20, 0.14, 0.70, 0.80),
    BEGINNER(0.22, 0.45, 0.06, 1.20, 1.25),
    PROFESSIONAL(0.38, 0.35, 0.07, 0.85, 0.90);

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
}
