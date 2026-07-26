package com.teenpatti.platform.game.engine;

/**
 * Immutable configuration parameters for a Teen Patti hand instance.
 */
public final class GameEngineConfig {

    private final long bootAmountPaise;
    private final long maxBetPaise;
    private final double rakePercentage;
    private final int blindSeenRatio;

    public GameEngineConfig(long bootAmountPaise, long maxBetPaise, double rakePercentage, int blindSeenRatio) {
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
}
