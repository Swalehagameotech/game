package com.teenpatti.platform.admin.betting;

import com.teenpatti.platform.admin.AdminActionLogService;
import com.teenpatti.platform.admin.AdminActionType;
import com.teenpatti.platform.admin.dto.BettingConfigurationRequest;
import com.teenpatti.platform.admin.dto.BettingConfigurationResponse;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BettingConfigurationService {

    private final BettingConfigurationRepository bettingConfigurationRepository;
    private final AdminActionLogService adminActionLogService;
    private final WebSocketEventPublisher webSocketEventPublisher;

    @Value("${app.betting.default.boot-amount-paise:1000}")
    private long defaultBootAmount;
    @Value("${app.betting.default.minimum-players:3}")
    private int defaultMinPlayers;
    @Value("${app.betting.default.maximum-players:6}")
    private int defaultMaxPlayers;
    @Value("${app.betting.default.turn-timer-seconds:20}")
    private int defaultTurnTimer;
    @Value("${app.betting.default.blind-bet-paise:1000}")
    private long defaultBlindBet;
    @Value("${app.betting.default.seen-chaal-paise:2000}")
    private long defaultSeenChaal;
    @Value("${app.betting.default.show-cost-paise:2000}")
    private long defaultShowCost;
    @Value("${app.betting.default.side-show-cost-paise:2000}")
    private long defaultSideShowCost;

    public BettingConfiguration getActiveOrCreateDefault() {
        return bettingConfigurationRepository.findByActiveTrue()
                .orElseGet(this::createDefaultActive);
    }

    public BettingConfigurationResponse getActiveResponse() {
        return toResponse(getActiveOrCreateDefault());
    }

    @Transactional
    public BettingConfigurationResponse updateActive(String adminUserId, BettingConfigurationRequest request) {
        validateRequest(request);
        BettingConfiguration current = getActiveOrCreateDefault();

        BettingConfiguration updated = BettingConfiguration.builder()
                .id(current.getId())
                .active(true)
                .bootAmount(request.getBootAmount())
                .bootAmountOptions(resolveBootAmountOptions(request))
                .minimumPlayers(request.getMinimumPlayers())
                .maximumPlayers(request.getMaximumPlayers())
                .turnTimer(request.getTurnTimer())
                .blindBetAmount(request.getBlindBetAmount())
                .blindRaiseOptions(sortedDistinctPositive(request.getBlindRaiseOptions()))
                .seenChaalAmount(request.getSeenChaalAmount())
                .seenRaiseOptions(sortedDistinctPositive(request.getSeenRaiseOptions()))
                .showCost(request.getShowCost())
                .sideShowCost(request.getSideShowCost())
                .sideShowEnabled(request.isSideShowEnabled())
                .showEnabled(request.isShowEnabled())
                .updatedBy(adminUserId)
                .version(current.getVersion())
                .build();

        BettingConfiguration saved = bettingConfigurationRepository.save(updated);
        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.BETTING_CONFIGURATION_UPDATED,
                null,
                Map.of("oldValue", toResponse(current), "newValue", toResponse(saved)));

        webSocketEventPublisher.publishBettingConfigurationUpdated(toResponse(saved));
        return toResponse(saved);
    }

    private BettingConfiguration createDefaultActive() {
        BettingConfiguration config = BettingConfiguration.builder()
                .active(true)
                .bootAmount(defaultBootAmount)
                .bootAmountOptions(List.of(defaultBootAmount))
                .minimumPlayers(defaultMinPlayers)
                .maximumPlayers(defaultMaxPlayers)
                .turnTimer(defaultTurnTimer)
                .blindBetAmount(defaultBlindBet)
                .blindRaiseOptions(List.of(defaultBlindBet * 2, defaultBlindBet * 4))
                .seenChaalAmount(defaultSeenChaal)
                .seenRaiseOptions(List.of(defaultSeenChaal * 2, defaultSeenChaal * 4))
                .showCost(defaultShowCost)
                .sideShowCost(defaultSideShowCost)
                .sideShowEnabled(true)
                .showEnabled(true)
                .updatedBy("system")
                .build();
        return bettingConfigurationRepository.save(config);
    }

    private void validateRequest(BettingConfigurationRequest request) {
        if (request.getMinimumPlayers() > request.getMaximumPlayers()) {
            throw new IllegalArgumentException("minimumPlayers cannot be greater than maximumPlayers");
        }
        List<Long> bootOptions = resolveBootAmountOptions(request);
        if (bootOptions.isEmpty()) {
            throw new IllegalArgumentException("bootAmountOptions cannot be empty");
        }
        if (request.getBlindBetAmount() <= 0 || request.getSeenChaalAmount() <= 0) {
            throw new IllegalArgumentException("blind and seen betting amounts must be positive");
        }
        if (request.getSeenChaalAmount() < request.getBlindBetAmount()) {
            throw new IllegalArgumentException("seenChaalAmount must be >= blindBetAmount");
        }
        List<Long> blind = sortedDistinctPositive(request.getBlindRaiseOptions());
        List<Long> seen = sortedDistinctPositive(request.getSeenRaiseOptions());
        if (blind.get(0) <= request.getBlindBetAmount()) {
            throw new IllegalArgumentException("All blind raise options must be above blindBetAmount");
        }
        if (seen.get(0) <= request.getSeenChaalAmount()) {
            throw new IllegalArgumentException("All seen raise options must be above seenChaalAmount");
        }
    }

    private List<Long> sortedDistinctPositive(List<Long> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Raise options cannot be empty");
        }
        List<Long> out = new ArrayList<>();
        values.stream()
                .filter(v -> v != null && v > 0)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .forEach(out::add);
        if (out.isEmpty()) {
            throw new IllegalArgumentException("Raise options must contain positive amounts");
        }
        return out;
    }

    private List<Long> resolveBootAmountOptions(BettingConfigurationRequest request) {
        List<Long> options = sortedDistinctPositive(request.getBootAmountOptions());
        if (request.getBootAmount() > 0 && !options.contains(request.getBootAmount())) {
            options = new ArrayList<>(options);
            options.add(request.getBootAmount());
            options = options.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        }
        return options;
    }

    public BettingConfigurationResponse toResponse(BettingConfiguration c) {
        List<Long> options = c.getBootAmountOptions() != null && !c.getBootAmountOptions().isEmpty()
                ? c.getBootAmountOptions()
                : List.of(c.getBootAmount());
        return BettingConfigurationResponse.builder()
                .id(c.getId())
                .active(c.isActive())
                .bootAmount(c.getBootAmount())
                .bootAmountOptions(options)
                .minimumPlayers(c.getMinimumPlayers())
                .maximumPlayers(c.getMaximumPlayers())
                .turnTimer(c.getTurnTimer())
                .blindBetAmount(c.getBlindBetAmount())
                .blindRaiseOptions(c.getBlindRaiseOptions())
                .seenChaalAmount(c.getSeenChaalAmount())
                .seenRaiseOptions(c.getSeenRaiseOptions())
                .showCost(c.getShowCost())
                .sideShowCost(c.getSideShowCost())
                .sideShowEnabled(c.isSideShowEnabled())
                .showEnabled(c.isShowEnabled())
                .updatedBy(c.getUpdatedBy())
                .updatedAt(c.getUpdatedAt())
                .version(c.getVersion())
                .build();
    }
}
