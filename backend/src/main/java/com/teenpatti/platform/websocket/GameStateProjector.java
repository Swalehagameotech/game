package com.teenpatti.platform.websocket;

import com.teenpatti.platform.game.engine.*;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.dto.PlayerSummaryView;
import com.teenpatti.platform.websocket.dto.PlayerViewGameState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Safety-critical projection component responsible for generating personalized,
 * recipient-filtered views of game state.
 *
 * CRITICAL SECURITY GUARANTEE:
 * 1. A recipient NEVER receives another player's actual card values while active/blind/seen.
 * 2. A recipient receives their OWN card values ONLY IF their status is SEEN.
 * 3. Cards are revealed ONLY in HandOutcome for showdown participants. Folded players' cards
 *    are NEVER leaked to anyone.
 */
@Component
@RequiredArgsConstructor
public class GameStateProjector {

    private final UserRepository userRepository;
    private final SessionRegistry sessionRegistry;

    public PlayerViewGameState createProjection(
            Table table,
            BettingRoundEngine engine,
            String recipientUserId) {

        if (table == null) {
            throw new IllegalArgumentException("Table must not be null");
        }

        List<String> seatedIds = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
        Map<String, String> displayNameMap = userRepository.findAllById(seatedIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getDisplayName() != null ? u.getDisplayName() : "Player"));

        HandOutcome outcome = engine != null ? engine.getOutcome() : null;
        Map<String, List<Card>> showdownRevealed = outcome != null ? outcome.getRevealedHands() : Map.of();

        List<PlayerSummaryView> playerViews = new ArrayList<>();
        for (String pid : seatedIds) {
            PlayerStatus status = engine != null ? engine.getPlayerStatus(pid) : PlayerStatus.BLIND;
            if (status == null) status = PlayerStatus.BLIND;

            List<Card> actualCards = engine != null ? engine.getPlayerCards(pid) : List.of();
            int cardCount = actualCards != null ? actualCards.size() : 0;

            List<Card> visibleCards = null;

            // Rule 1: Showdown revealed cards (only for showdown participants in outcome)
            if (showdownRevealed.containsKey(pid)) {
                visibleCards = showdownRevealed.get(pid);
            }
            // Rule 2: Recipient's own cards (only if recipient has chosen to SEE cards)
            else if (pid.equals(recipientUserId) && status == PlayerStatus.SEEN) {
                visibleCards = actualCards;
            }
            // Rule 3: Otherwise, cards remain NULL (hidden from recipient)

            boolean connected = sessionRegistry.isUserConnected(pid);

            playerViews.add(PlayerSummaryView.builder()
                    .userId(pid)
                    .displayName(displayNameMap.getOrDefault(pid, "Player"))
                    .status(status)
                    .totalContributedPaise(0L) // Can be populated if tracked
                    .cards(visibleCards)
                    .cardCount(cardCount)
                    .connected(connected)
                    .build());
        }

        TableStatus status = table.getStatus();
        String currentTurnPlayerId = engine != null ? engine.getCurrentTurnPlayerId() : null;
        long potPaise = engine != null ? engine.getPotPaise() : table.getPotPaise();
        long currentBaseStakePaise = engine != null ? engine.getCurrentBaseStakePaise() : 0L;
        long requiredBetPaise = engine != null ? engine.getRequiredBetPaise(recipientUserId) : 0L;

        return PlayerViewGameState.builder()
                .tableId(table.getId())
                .status(status)
                .currentTurnPlayerId(currentTurnPlayerId)
                .potPaise(potPaise)
                .currentBaseStakePaise(currentBaseStakePaise)
                .requiredBetPaise(requiredBetPaise)
                .players(playerViews)
                .handOutcome(outcome)
                .build();
    }
}
