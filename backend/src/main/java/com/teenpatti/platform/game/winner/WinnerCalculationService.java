package com.teenpatti.platform.game.winner;

import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.game.engine.HandResult;
import com.teenpatti.platform.game.variant.GameVariantRegistry;
import com.teenpatti.platform.game.variant.GameVariantStrategy;
import com.teenpatti.platform.table.GameVariant;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Server-authoritative winner calculation: variant-aware showdown comparison,
 * fold-win handling, winner snapshot construction, and broadcast.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WinnerCalculationService {

    private final GameVariantRegistry variantRegistry;
    private final UserRepository userRepository;
    private final WebSocketEventPublisher eventPublisher;

    public WinnerResolver createResolver(GameVariant variant) {
        GameVariantStrategy strategy = variantRegistry.requireStrategy(variant);
        return new VariantWinnerResolver(strategy);
    }

    public WinnerResolver createResolver(GameVariantStrategy strategy) {
        return new VariantWinnerResolver(strategy);
    }

    public WinnerSnapshot buildWinnerSnapshot(
            Table table,
            String handId,
            HandOutcome outcome,
            BettingRoundEngine engine,
            GameVariant variant) {

        if (table == null || outcome == null) {
            throw new IllegalArgumentException("Table and outcome must not be null");
        }

        GameVariantStrategy strategy = variantRegistry.requireStrategy(
                variant != null ? variant : GameVariant.CLASSIC);

        String winnerId = outcome.getWinnerId();
        Map<String, String> displayNames = resolveDisplayNames(table);

        boolean foldWin = outcome.getWinningCategory() == null;
        String winningCategory = foldWin
                ? "FOLD_WIN"
                : outcome.getWinningCategory().name();
        String winningHandDescription = foldWin
                ? "Last player standing"
                : outcome.getWinningCategory().getDescription();

        List<ShowdownParticipantView> participants = buildParticipants(
                outcome, engine, strategy, displayNames, winnerId, foldWin);

        return WinnerSnapshot.builder()
                .tableId(table.getId())
                .handId(handId)
                .winnerUserId(winnerId)
                .winnerDisplayName(displayNames.getOrDefault(winnerId, "Winner"))
                .winningCategory(winningCategory)
                .winningHandDescription(winningHandDescription)
                .foldWin(foldWin)
                .potPaise(outcome.getPotAmountPaise())
                .rakePaise(outcome.getRakeAmountPaise())
                .payoutPaise(outcome.getWinnerPayoutPaise())
                .notes(outcome.getNotes())
                .participants(participants)
                .build();
    }

    public void publishWinnerDeclared(String tableId, WinnerSnapshot snapshot) {
        eventPublisher.publishWinnerDeclared(tableId, snapshot);
        log.info("Winner declared on table [{}]: {} ({}) payout {} paise",
                tableId,
                snapshot.getWinnerDisplayName(),
                snapshot.getWinningCategory(),
                snapshot.getPayoutPaise());
    }

    /** Overload for aliased Map payloads (winnerId / winnerPayoutPaise for clients). */
    public void publishWinnerDeclared(String tableId, Object payload) {
        eventPublisher.publishWinnerDeclared(tableId, payload);
        if (payload instanceof WinnerSnapshot snapshot) {
            log.info("Winner declared on table [{}]: {} ({}) payout {} paise",
                    tableId,
                    snapshot.getWinnerDisplayName(),
                    snapshot.getWinningCategory(),
                    snapshot.getPayoutPaise());
        } else {
            log.info("Winner declared on table [{}] payload={}", tableId, payload);
        }
    }

    private List<ShowdownParticipantView> buildParticipants(
            HandOutcome outcome,
            BettingRoundEngine engine,
            GameVariantStrategy strategy,
            Map<String, String> displayNames,
            String winnerId,
            boolean foldWin) {

        if (foldWin || outcome.getRevealedHands().isEmpty()) {
            return List.of(ShowdownParticipantView.builder()
                    .userId(winnerId)
                    .displayName(displayNames.getOrDefault(winnerId, "Winner"))
                    .handRank("FOLD_WIN")
                    .handDescription("Won because all opponents packed")
                    .winner(true)
                    .cards(List.of())
                    .build());
        }

        List<ShowdownParticipantView> views = new ArrayList<>();
        for (Map.Entry<String, List<Card>> entry : outcome.getRevealedHands().entrySet()) {
            String userId = entry.getKey();
            List<Card> cards = entry.getValue();
            HandResult evaluated = strategy.evaluateHand(cards);
            views.add(ShowdownParticipantView.builder()
                    .userId(userId)
                    .displayName(displayNames.getOrDefault(userId, "Player"))
                    .handRank(evaluated.getCategory().name())
                    .handDescription(evaluated.getCategory().getDescription())
                    .winner(userId.equals(winnerId))
                    .cards(cards.stream().map(Card::toShortString).toList())
                    .build());
        }

        return views;
    }

    private Map<String, String> resolveDisplayNames(Table table) {
        List<String> seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
        if (seated.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(seated).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u.getDisplayName() != null ? u.getDisplayName() : "Player",
                        (a, b) -> a,
                        LinkedHashMap::new));
    }
}
