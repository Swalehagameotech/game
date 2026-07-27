package com.teenpatti.platform.game;

import com.teenpatti.platform.game.dto.GameSessionSummaryDto;
import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.Deck;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.game.engine.PlayerStatus;
import com.teenpatti.platform.table.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Persists in-progress and completed hands to {@code game_sessions} for audit and admin visibility.
 * Deck and card payloads are stored server-side only — never exposed via REST or STOMP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameSessionService {

    private final GameSessionRepository gameSessionRepository;

    public GameSession openSession(
            Table table,
            String handId,
            BettingRoundEngine engine,
            Deck deck,
            int dealerSeatIndex,
            String shuffleId) {

        cancelActiveSessions(table.getId());

        List<String> seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
        Map<String, List<String>> encodedHands = new HashMap<>();
        Map<String, String> statuses = new HashMap<>();

        for (String playerId : seated) {
            List<Card> cards = engine.getPlayerCards(playerId);
            encodedHands.put(playerId, cards.stream().map(Card::toShortString).toList());
            PlayerStatus status = engine.getPlayerStatus(playerId);
            statuses.put(playerId, status != null ? status.name() : PlayerStatus.BLIND.name());
        }

        List<String> remainingDeck = deck.getCards().stream()
                .map(Card::toShortString)
                .collect(Collectors.toList());

        GameSession session = GameSession.builder()
                .tableId(table.getId())
                .handId(handId)
                .shuffleId(shuffleId)
                .variant(table.getGameVariant() != null ? table.getGameVariant() : com.teenpatti.platform.table.GameVariant.CLASSIC)
                .roundNumber(table.getRoundNumber())
                .deck(remainingDeck)
                .playerHands(encodedHands)
                .playerStatus(statuses)
                .potPaise(engine.getPotPaise())
                .currentBaseStakePaise(engine.getCurrentBaseStakePaise())
                .currentTurnUserId(engine.getCurrentTurnPlayerId())
                .currentTurnIndex(seated.indexOf(engine.getCurrentTurnPlayerId()))
                .dealerSeatIndex(dealerSeatIndex)
                .status(GameSessionStatus.ACTIVE)
                .startedAt(Instant.now())
                .build();

        GameSession saved = gameSessionRepository.save(session);
        log.info("Opened game session [{}] for table [{}] hand [{}]", saved.getId(), table.getId(), handId);
        return saved;
    }

    public void syncActiveSession(String handId, BettingRoundEngine engine, Table table) {
        syncActiveSession(handId, engine, table, null);
    }

    public void syncActiveSession(String handId, BettingRoundEngine engine, Table table, Instant turnDeadlineAt) {
        if (handId == null || engine == null) {
            return;
        }
        gameSessionRepository.findByHandId(handId).ifPresent(session -> {
            if (session.getStatus() != GameSessionStatus.ACTIVE) {
                return;
            }
            List<String> seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds() : List.of();
            Map<String, String> statuses = new LinkedHashMap<>();
            for (String playerId : seated) {
                PlayerStatus status = engine.getPlayerStatus(playerId);
                statuses.put(playerId, status != null ? status.name() : PlayerStatus.BLIND.name());
            }
            session.setPlayerStatus(statuses);
            session.setPotPaise(engine.getPotPaise());
            session.setCurrentBaseStakePaise(engine.getCurrentBaseStakePaise());
            session.setCurrentTurnUserId(engine.getCurrentTurnPlayerId());
            session.setCurrentTurnIndex(seated.indexOf(engine.getCurrentTurnPlayerId()));
            if (turnDeadlineAt != null) {
                session.setTurnDeadlineAt(turnDeadlineAt);
            }
            gameSessionRepository.save(session);
        });
    }

    public void completeSession(String handId, HandOutcome outcome) {
        if (handId == null) {
            return;
        }
        gameSessionRepository.findByHandId(handId).ifPresent(session -> {
            session.setStatus(GameSessionStatus.COMPLETED);
            session.setEndedAt(Instant.now());
            if (outcome != null) {
                session.setPotPaise(outcome.getPotAmountPaise());
            }
            gameSessionRepository.save(session);
            log.info("Completed game session for hand [{}]", handId);
        });
    }

    public void cancelActiveSessions(String tableId) {
        gameSessionRepository.findByTableIdAndStatus(tableId, GameSessionStatus.ACTIVE)
                .ifPresent(session -> {
                    session.setStatus(GameSessionStatus.CANCELLED);
                    session.setEndedAt(Instant.now());
                    gameSessionRepository.save(session);
                });
    }

    public Optional<GameSessionSummaryDto> getActiveSessionSummary(String tableId) {
        return gameSessionRepository.findByTableIdAndStatus(tableId, GameSessionStatus.ACTIVE)
                .map(this::toSummaryDto);
    }

    public Optional<GameSessionSummaryDto> getSessionSummaryByHandId(String handId) {
        return gameSessionRepository.findByHandId(handId).map(this::toSummaryDto);
    }

    private GameSessionSummaryDto toSummaryDto(GameSession session) {
        return GameSessionSummaryDto.builder()
                .sessionId(session.getId())
                .tableId(session.getTableId())
                .handId(session.getHandId())
                .variant(session.getVariant())
                .roundNumber(session.getRoundNumber())
                .status(session.getStatus())
                .potPaise(session.getPotPaise())
                .currentBaseStakePaise(session.getCurrentBaseStakePaise())
                .currentTurnUserId(session.getCurrentTurnUserId())
                .dealerSeatIndex(session.getDealerSeatIndex())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .playerStatus(session.getPlayerStatus() != null ? Map.copyOf(session.getPlayerStatus()) : Map.of())
                .build();
    }
}
