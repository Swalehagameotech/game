package com.teenpatti.platform.game.engine;

/**
 * Immutable configuration parameters for a Teen Patti hand instance.
 */
public final class GameEngineConfig {

    private final long bootAmountPaise;
    private final long maxBetPaise;
    private final double rakePercentage;
    private final int blindSeenRatio;
    private final long blindBetAmountPaise;
    private final long seenChaalAmountPaise;
    private final java.util.List<Long> blindRaiseOptionsPaise;
    private final java.util.List<Long> seenRaiseOptionsPaise;
    private final long showCostPaise;
    private final long sideShowCostPaise;
    private final boolean sideShowEnabled;
    private final boolean showEnabled;
    private final int turnTimeoutSeconds;

    public GameEngineConfig(long bootAmountPaise, long maxBetPaise, double rakePercentage, int blindSeenRatio) {
        this(
                bootAmountPaise,
                maxBetPaise,
                rakePercentage,
                blindSeenRatio,
                bootAmountPaise,
                bootAmountPaise * Math.max(1, blindSeenRatio),
                java.util.List.of(bootAmountPaise * 2),
                java.util.List.of((bootAmountPaise * Math.max(1, blindSeenRatio)) * 2),
                bootAmountPaise * Math.max(1, blindSeenRatio),
                bootAmountPaise * Math.max(1, blindSeenRatio),
                true,
                true,
                20
        );
    }

    public GameEngineConfig(
            long bootAmountPaise,
            long maxBetPaise,
            double rakePercentage,
            int blindSeenRatio,
            long blindBetAmountPaise,
            long seenChaalAmountPaise,
            java.util.List<Long> blindRaiseOptionsPaise,
            java.util.List<Long> seenRaiseOptionsPaise,
            long showCostPaise,
            long sideShowCostPaise,
            boolean sideShowEnabled,
            boolean showEnabled,
            int turnTimeoutSeconds) {
        if (bootAmountPaise <= 0) {
            throw new IllegalArgumentException("bootAmountPaise must be greater than 0");
        }
        if (maxBetPaise < bootAmountPaise) {
            throw new IllegalArgumentException("maxBetPaise must be >= bootAmountPaise");
        }
        if (rakePercentage < 0.0 || rakePercentage > 100.0) {
            throw new IllegalArgumentException("rakePercentage must be between 0.0 and 100.0");
        }
        if (blindSeenRatio < 1) {
            throw new IllegalArgumentException("blindSeenRatio must be at least 1");
        }
        this.bootAmountPaise = bootAmountPaise;
        this.maxBetPaise = maxBetPaise;
        this.rakePercentage = rakePercentage;
        this.blindSeenRatio = blindSeenRatio;
        this.blindBetAmountPaise = blindBetAmountPaise;
        this.seenChaalAmountPaise = seenChaalAmountPaise;
        this.blindRaiseOptionsPaise = blindRaiseOptionsPaise != null ? java.util.List.copyOf(blindRaiseOptionsPaise) : java.util.List.of();
        this.seenRaiseOptionsPaise = seenRaiseOptionsPaise != null ? java.util.List.copyOf(seenRaiseOptionsPaise) : java.util.List.of();
        this.showCostPaise = showCostPaise;
        this.sideShowCostPaise = sideShowCostPaise;
        this.sideShowEnabled = sideShowEnabled;
        this.showEnabled = showEnabled;
        this.turnTimeoutSeconds = turnTimeoutSeconds;
    }

    public static GameEngineConfig defaultConfig(long bootAmountPaise, long maxBetPaise) {
        return new GameEngineConfig(bootAmountPaise, maxBetPaise, 5.0, 2);
    }

    public long getBootAmountPaise() {
        return bootAmountPaise;
    }

    public long getMaxBetPaise() {
        return maxBetPaise;
    }

    public double getRakePercentage() {
        return rakePercentage;
    }

    public int getBlindSeenRatio() {
        return blindSeenRatio;
    }

    public long getBlindBetAmountPaise() {
        return blindBetAmountPaise;
    }

    public long getSeenChaalAmountPaise() {
        return seenChaalAmountPaise;
    }

    public java.util.List<Long> getBlindRaiseOptionsPaise() {
        return blindRaiseOptionsPaise;
    }

    public java.util.List<Long> getSeenRaiseOptionsPaise() {
        return seenRaiseOptionsPaise;
    }

    public long getShowCostPaise() {
        return showCostPaise;
    }

    public long getSideShowCostPaise() {
        return sideShowCostPaise;
    }

    public boolean isSideShowEnabled() {
        return sideShowEnabled;
    }

    public boolean isShowEnabled() {
        return showEnabled;
    }

    public int getTurnTimeoutSeconds() {
        return turnTimeoutSeconds;
    }
}
