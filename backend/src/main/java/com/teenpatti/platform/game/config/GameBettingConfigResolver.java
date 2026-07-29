package com.teenpatti.platform.game.config;

import com.teenpatti.platform.admin.betting.BettingConfiguration;
import com.teenpatti.platform.admin.betting.BettingConfigurationService;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GameBettingConfigResolver {

    private final BettingConfigurationService bettingConfigurationService;

    public BettingConfiguration resolveActiveConfiguration() {
        return bettingConfigurationService.getActiveOrCreateDefault();
    }

    public GameEngineConfig resolveEngineConfig() {
        BettingConfiguration c = resolveActiveConfiguration();
        return resolveEngineConfigForBoot(c.getBootAmount());
    }

    /**
     * Builds engine config for a specific table boot.
     * Scales blind/chaal/raise/show amounts so a ₹10 table pots at ₹10 × players,
     * not the admin "primary" boot amount.
     */
    public GameEngineConfig resolveEngineConfigForBoot(long tableBootPaise) {
        BettingConfiguration c = resolveActiveConfiguration();
        long configuredBoot = Math.max(1L, c.getBootAmount());
        long boot = tableBootPaise > 0 ? tableBootPaise : configuredBoot;
        double scale = (double) boot / (double) configuredBoot;

        long blindBet = scaleAmount(c.getBlindBetAmount(), scale, boot);
        long seenChaal = scaleAmount(c.getSeenChaalAmount(), scale, boot * 2);
        long showCost = scaleAmount(c.getShowCost(), scale, seenChaal);
        long sideShowCost = scaleAmount(c.getSideShowCost(), scale, seenChaal);
        List<Long> blindRaises = scaleList(c.getBlindRaiseOptions(), scale, List.of(boot * 2, boot * 4));
        List<Long> seenRaises = scaleList(c.getSeenRaiseOptions(), scale, List.of(seenChaal * 2, seenChaal * 4));

        int blindSeenRatio = (int) Math.max(1L, seenChaal / Math.max(1L, blindBet));
        long maxFromOptions = maxOf(blindRaises, seenRaises, seenChaal, blindBet);
        long maxBet = Math.max(maxFromOptions, blindBet);

        return new GameEngineConfig(
                boot,
                maxBet,
                5.0,
                blindSeenRatio,
                blindBet,
                seenChaal,
                blindRaises,
                seenRaises,
                showCost,
                sideShowCost,
                c.isSideShowEnabled(),
                c.isShowEnabled(),
                c.getTurnTimer()
        );
    }

    private long scaleAmount(long configured, double scale, long fallback) {
        if (configured <= 0) {
            return Math.max(1L, fallback);
        }
        long scaled = Math.round(configured * scale);
        return Math.max(1L, scaled);
    }

    private List<Long> scaleList(List<Long> source, double scale, List<Long> fallback) {
        List<Long> sanitized = sanitize(source);
        if (sanitized.isEmpty()) {
            return fallback;
        }
        return sanitized.stream()
                .map(v -> Math.max(1L, Math.round(v * scale)))
                .distinct()
                .sorted()
                .toList();
    }

    private long maxOf(List<Long> blind, List<Long> seen, long fallbackSeen, long fallbackBlind) {
        long maxBlind = sanitize(blind).stream().max(Comparator.naturalOrder()).orElse(fallbackBlind);
        long maxSeen = sanitize(seen).stream().max(Comparator.naturalOrder()).orElse(fallbackSeen);
        return Math.max(maxBlind, maxSeen);
    }

    private List<Long> sanitize(List<Long> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream().filter(v -> v != null && v > 0).distinct().sorted().toList();
    }
}
