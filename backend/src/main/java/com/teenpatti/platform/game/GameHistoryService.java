package com.teenpatti.platform.game;

import com.teenpatti.platform.game.dto.GameHistoryDetailDto;
import com.teenpatti.platform.game.dto.GameHistorySummaryDto;
import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.game.engine.HandRankCategory;
import com.teenpatti.platform.home.dto.HomeDashboardResponse;
import com.teenpatti.platform.table.GameVariant;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Canonical game history: records completed hands to {@code game_history} and serves read APIs.
 * Legacy {@link MatchHistory} is dual-written for older admin/investigation flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameHistoryService {

    private final GameHistoryRepository gameHistoryRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final UserRepository userRepository;
    private final WebSocketEventPublisher eventPublisher;

    /**
     * Idempotently records a completed hand. Returns existing record when {@code handId} was already saved.
     */
    public GameHistory recordCompletedHand(Table table, String handId, HandOutcome outcome, Instant startedAt) {
        if (table == null || handId == null || handId.isBlank() || outcome == null) {
            throw new IllegalArgumentException("Table, handId, and outcome must not be null");
        }

        Optional<GameHistory> existing = gameHistoryRepository.findByHandId(handId);
        if (existing.isPresent()) {
            log.debug("GameHistory already recorded for hand [{}]", handId);
            return existing.get();
        }

        String tableId = table.getId();
        String winnerId = outcome.getWinnerId();
        Instant endedAt = Instant.now();
        HandSummary handSummary = buildHandSummary(outcome);
        WinningCategory winningCategory = mapWinningCategory(outcome.getWinningCategory());
        Instant effectiveStart = startedAt != null ? startedAt : endedAt;
        long durationSeconds = Math.max(0, endedAt.getEpochSecond() - effectiveStart.getEpochSecond());
        long bootPaise = table.getBootAmountPaise() > 0 ? table.getBootAmountPaise() : table.getCurrentStakePaise();

        GameHistory history = GameHistory.builder()
                .tableId(tableId)
                .handId(handId)
                .roundNumber(table.getRoundNumber() > 0 ? table.getRoundNumber() : 1)
                .variant(table.getGameVariant() != null ? table.getGameVariant() : GameVariant.CLASSIC)
                .playerIds(new ArrayList<>(table.getSeatedPlayerIds()))
                .winnerId(winnerId)
                .potAmountPaise(outcome.getPotAmountPaise())
                .rakeAmountPaise(outcome.getRakeAmountPaise())
                .winnerPayoutPaise(outcome.getWinnerPayoutPaise())
                .bootAmountPaise(bootPaise)
                .durationSeconds(durationSeconds)
                .winningCategory(winningCategory)
                .handSummary(handSummary)
                .startedAt(effectiveStart)
                .endedAt(endedAt)
                .build();

        GameHistory saved = gameHistoryRepository.save(history);
        persistLegacyMatchHistory(table, handId, outcome, startedAt, endedAt, handSummary, tableId, winnerId);

        log.info("Recorded GameHistory [{}] table [{}] hand [{}] winner [{}] payout {} paise",
                saved.getId(), tableId, handId, winnerId, outcome.getWinnerPayoutPaise());

        notifyParticipants(saved);
        return saved;
    }

    public Page<GameHistorySummaryDto> getHistoryForUser(String userId, Pageable pageable) {
        return gameHistoryRepository.findByPlayerIdsContainingOrderByEndedAtDesc(userId, pageable)
                .map(record -> toSummaryDto(record, userId));
    }

    public List<HomeDashboardResponse.GameHistoryDto> getRecentForHomeDashboard(String userId, int limit) {
        List<GameHistory> records = gameHistoryRepository
                .findByPlayerIdsContainingOrderByEndedAtDesc(userId, PageRequest.of(0, limit))
                .getContent();
        if (!records.isEmpty()) {
            return records.stream()
                    .map(record -> toHomeDashboardDto(record, userId))
                    .toList();
        }
        return getLegacyHomeDashboardHistory(userId, limit);
    }

    public Optional<GameHistoryDetailDto> getDetailForUser(String userId, String historyId) {
        return gameHistoryRepository.findById(historyId)
                .filter(record -> record.getPlayerIds() != null && record.getPlayerIds().contains(userId))
                .map(record -> toDetailDto(record, userId));
    }

    private List<HomeDashboardResponse.GameHistoryDto> getLegacyHomeDashboardHistory(String userId, int limit) {
        try {
            return matchHistoryRepository
                    .findByPlayerIdsContainingOrderByEndedAtDesc(userId, PageRequest.of(0, limit))
                    .getContent()
                    .stream()
                    .map(legacy -> toHomeDashboardDtoFromLegacy(legacy, userId))
                    .toList();
        } catch (Exception ex) {
            log.warn("Legacy match history lookup failed for user [{}]: {}", userId, ex.getMessage());
            return List.of();
        }
    }

    private void persistLegacyMatchHistory(
            Table table,
            String handId,
            HandOutcome outcome,
            Instant startedAt,
            Instant endedAt,
            HandSummary handSummary,
            String tableId,
            String winnerId) {
        MatchHistory legacy = MatchHistory.builder()
                .tableId(tableId)
                .handId(handId)
                .roundNumber(table.getRoundNumber() > 0 ? table.getRoundNumber() : 1)
                .playerIds(new ArrayList<>(table.getSeatedPlayerIds()))
                .winnerId(winnerId)
                .potAmountPaise(outcome.getPotAmountPaise())
                .rakeAmountPaise(outcome.getRakeAmountPaise())
                .handSummary(handSummary)
                .startedAt(startedAt != null ? startedAt : endedAt)
                .endedAt(endedAt)
                .build();
        matchHistoryRepository.save(legacy);
    }

    private HandSummary buildHandSummary(HandOutcome outcome) {
        Map<String, String> cardStringMap = new HashMap<>();
        if (outcome.getRevealedHands() != null) {
            for (Map.Entry<String, List<Card>> entry : outcome.getRevealedHands().entrySet()) {
                String cardsStr = entry.getValue().stream()
                        .map(Card::toShortString)
                        .collect(Collectors.joining(","));
                cardStringMap.put(entry.getKey(), cardsStr);
            }
        }

        HandRankCategory category = outcome.getWinningCategory();
        return HandSummary.builder()
                .winningHandName(category != null ? category.getDescription() : "Fold Win")
                .winningRank(category != null ? category.getPriority() : 0)
                .playerHandsMap(cardStringMap)
                .notes(outcome.getNotes())
                .build();
    }

    private WinningCategory mapWinningCategory(HandRankCategory category) {
        if (category == null) {
            return WinningCategory.FOLD_WIN;
        }
        return WinningCategory.valueOf(category.name());
    }

    private void notifyParticipants(GameHistory saved) {
        if (saved.getPlayerIds() == null) {
            return;
        }
        for (String playerId : saved.getPlayerIds()) {
            eventPublisher.publishGameHistoryRecorded(playerId, toSummaryDto(saved, playerId));
        }
    }

    private GameHistorySummaryDto toSummaryDto(GameHistory record, String viewerUserId) {
        boolean isWinner = viewerUserId != null && viewerUserId.equals(record.getWinnerId());
        HandSummary summary = record.getHandSummary();
        return GameHistorySummaryDto.builder()
                .id(record.getId())
                .handId(record.getHandId())
                .tableId(record.getTableId())
                .tableName(resolveTableLabel(record.getTableId()))
                .roundNumber(record.getRoundNumber())
                .variant(record.getVariant() != null ? record.getVariant().name() : GameVariant.CLASSIC.name())
                .winnerId(record.getWinnerId())
                .winnerDisplayName(resolveDisplayName(record.getWinnerId()))
                .result(isWinner ? "WON" : "LOST")
                .potAmountPaise(record.getPotAmountPaise())
                .winnerPayoutPaise(record.getWinnerPayoutPaise())
                .rakeAmountPaise(record.getRakeAmountPaise())
                .netAmountPaise(isWinner ? record.getWinnerPayoutPaise() : 0L)
                .winningCategory(record.getWinningCategory() != null ? record.getWinningCategory().name() : WinningCategory.FOLD_WIN.name())
                .winningHandDescription(summary != null && summary.getWinningHandName() != null
                        ? summary.getWinningHandName()
                        : "Fold Win")
                .foldWin(record.getWinningCategory() == null || record.getWinningCategory() == WinningCategory.FOLD_WIN)
                .playerCount(record.getPlayerIds() != null ? record.getPlayerIds().size() : 0)
                .playedAt(record.getEndedAt() != null ? record.getEndedAt().toString() : "")
                .build();
    }

    private GameHistoryDetailDto toDetailDto(GameHistory record, String viewerUserId) {
        GameHistorySummaryDto summary = toSummaryDto(record, viewerUserId);
        HandSummary handSummary = record.getHandSummary();
        Map<String, String> revealed = handSummary != null && handSummary.getPlayerHandsMap() != null
                ? Map.copyOf(handSummary.getPlayerHandsMap())
                : Map.of();

        return GameHistoryDetailDto.builder()
                .id(summary.getId())
                .handId(summary.getHandId())
                .tableId(summary.getTableId())
                .tableName(summary.getTableName())
                .roundNumber(summary.getRoundNumber())
                .variant(summary.getVariant())
                .winnerId(summary.getWinnerId())
                .winnerDisplayName(summary.getWinnerDisplayName())
                .result(summary.getResult())
                .potAmountPaise(summary.getPotAmountPaise())
                .winnerPayoutPaise(summary.getWinnerPayoutPaise())
                .rakeAmountPaise(summary.getRakeAmountPaise())
                .netAmountPaise(summary.getNetAmountPaise())
                .winningCategory(summary.getWinningCategory())
                .winningHandDescription(summary.getWinningHandDescription())
                .foldWin(summary.isFoldWin())
                .playerCount(summary.getPlayerCount())
                .startedAt(record.getStartedAt() != null ? record.getStartedAt().toString() : "")
                .endedAt(record.getEndedAt() != null ? record.getEndedAt().toString() : "")
                .notes(handSummary != null ? handSummary.getNotes() : null)
                .playerIds(record.getPlayerIds() != null ? List.copyOf(record.getPlayerIds()) : List.of())
                .revealedHands(revealed)
                .build();
    }

    private HomeDashboardResponse.GameHistoryDto toHomeDashboardDto(GameHistory record, String userId) {
        GameHistorySummaryDto summary = toSummaryDto(record, userId);
        return HomeDashboardResponse.GameHistoryDto.builder()
                .id(summary.getId())
                .gameId(formatPublicGameId(summary.getId()))
                .handId(summary.getHandId())
                .tableId(summary.getTableId())
                .tableName(summary.getTableName())
                .result(summary.getResult())
                .winningAmountPaise(summary.getNetAmountPaise())
                .potAmountPaise(summary.getPotAmountPaise())
                .winnerPayoutPaise(summary.getWinnerPayoutPaise())
                .variant(summary.getVariant())
                .winningCategory(summary.getWinningCategory())
                .winningHandDescription(summary.getWinningHandDescription())
                .foldWin(summary.isFoldWin())
                .playerCount(summary.getPlayerCount())
                .playedAt(summary.getPlayedAt())
                .build();
    }

    private HomeDashboardResponse.GameHistoryDto toHomeDashboardDtoFromLegacy(MatchHistory legacy, String userId) {
        boolean isWinner = userId.equals(legacy.getWinnerId());
        HandSummary summary = legacy.getHandSummary();
        return HomeDashboardResponse.GameHistoryDto.builder()
                .id(legacy.getId())
                .gameId(formatPublicGameId(legacy.getId()))
                .handId(legacy.getHandId())
                .tableId(legacy.getTableId())
                .tableName(resolveTableLabel(legacy.getTableId()))
                .result(isWinner ? "WON" : "LOST")
                .winningAmountPaise(isWinner ? legacy.getPotAmountPaise() : 0L)
                .potAmountPaise(legacy.getPotAmountPaise())
                .winnerPayoutPaise(legacy.getPotAmountPaise())
                .winningHandDescription(summary != null ? summary.getWinningHandName() : "Unknown")
                .foldWin(summary == null || summary.getWinningRank() == 0)
                .playerCount(legacy.getPlayerIds() != null ? legacy.getPlayerIds().size() : 0)
                .playedAt(legacy.getEndedAt() != null ? legacy.getEndedAt().toString() : "")
                .build();
    }

    private String resolveDisplayName(String userId) {
        if (userId == null) {
            return "Player";
        }
        return userRepository.findById(userId)
                .map(user -> user.getDisplayName() != null ? user.getDisplayName() : "Player")
                .orElse("Player");
    }

    private static String resolveTableLabel(String tableId) {
        if (tableId != null && tableId.length() >= 6) {
            return "Table #" + tableId.substring(0, 6).toUpperCase();
        }
        return "Teen Patti Table";
    }

    private static String formatPublicGameId(String id) {
        if (id == null || id.isBlank()) {
            return "gm_unknown";
        }
        return "gm_" + id.substring(0, Math.min(id.length(), 8));
    }
}
