package com.teenpatti.platform.leaderboard;

import com.teenpatti.platform.common.event.HandCompletedEvent;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.leaderboard.dto.LeaderboardItemResponse;
import com.teenpatti.platform.leaderboard.dto.UserRankResponse;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Service managing precomputed, time-windowed leaderboard entries.
 * Updates are performed via atomic $inc operations to prevent race conditions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Resolves current window instance string identifier.
     */
    public String resolveWindowKey(LeaderboardWindow window, Instant instant) {
        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
        return switch (window) {
            case DAILY -> zdt.toLocalDate().toString();
            case WEEKLY -> {
                int year = zdt.get(WeekFields.ISO.weekBasedYear());
                int week = zdt.get(WeekFields.ISO.weekOfWeekBasedYear());
                yield String.format("%d-W%02d", year, week);
            }
            case ALL_TIME -> "ALL_TIME";
        };
    }

    public void recordHandResult(HandCompletedEvent event) {
        if (event == null || event.getParticipantUserIds() == null || event.getParticipantUserIds().isEmpty()) return;

        Instant now = event.getTimestamp() != null ? event.getTimestamp() : Instant.now();
        String winnerId = event.getWinnerId();
        long winnerPayout = event.getWinnerPayoutPaise();

        for (String userId : event.getParticipantUserIds()) {
            boolean isWinner = userId.equals(winnerId);
            long winningsToAdd = isWinner ? winnerPayout : 0L;
            int winsToAdd = isWinner ? 1 : 0;
            int handsPlayedToAdd = 1;

            updateUserWindowsAtomic(userId, winsToAdd, handsPlayedToAdd, winningsToAdd, now);
        }
    }

    /**
     * Records hand completion metrics for all table participants across DAILY, WEEKLY, and ALL_TIME windows.
     * Uses atomic MongoTemplate $inc upserts to guarantee thread-safe concurrency.
     */
    public void recordHandResult(Table table, HandOutcome outcome) {
        if (table == null || outcome == null) return;

        Instant now = Instant.now();
        List<String> participants = table.getSeatedPlayerIds();
        if (participants == null || participants.isEmpty()) return;

        String winnerId = outcome.getWinnerId();
        long winnerPayout = outcome.getWinnerPayoutPaise();

        for (String userId : participants) {
            boolean isWinner = userId.equals(winnerId);
            long winningsToAdd = isWinner ? winnerPayout : 0L;
            int winsToAdd = isWinner ? 1 : 0;
            int handsPlayedToAdd = 1;

            updateUserWindowsAtomic(userId, winsToAdd, handsPlayedToAdd, winningsToAdd, now);
        }
    }

    private void updateUserWindowsAtomic(String userId, int wins, int handsPlayed, long winningsPaise, Instant now) {
        for (LeaderboardWindow window : LeaderboardWindow.values()) {
            String windowKey = resolveWindowKey(window, now);

            Query query = Query.query(Criteria.where("userId").is(userId)
                    .and("window").is(window)
                    .and("windowKey").is(windowKey));

            Update update = new Update()
                    .inc("handsWon", wins)
                    .inc("handsPlayed", handsPlayed)
                    .inc("totalWinningsPaise", winningsPaise)
                    .set("updatedAt", now);

            mongoTemplate.upsert(query, update, LeaderboardEntry.class);
        }
    }

    public Page<LeaderboardItemResponse> getLeaderboard(LeaderboardWindow window, LeaderboardMetric metric, Pageable pageable) {
        String windowKey = resolveWindowKey(window, Instant.now());
        Page<LeaderboardEntry> page = metric == LeaderboardMetric.WINS ?
                leaderboardEntryRepository.findByWindowAndWindowKeyOrderByHandsWonDesc(window, windowKey, pageable) :
                leaderboardEntryRepository.findByWindowAndWindowKeyOrderByTotalWinningsPaiseDesc(window, windowKey, pageable);

        int startRank = (int) pageable.getOffset() + 1;
        List<LeaderboardItemResponse> items = new ArrayList<>();

        int index = 0;
        for (LeaderboardEntry entry : page.getContent()) {
            int rank = startRank + index++;
            User user = userRepository.findById(entry.getUserId()).orElse(null);

            long statValue = metric == LeaderboardMetric.WINS ? entry.getHandsWon() : entry.getTotalWinningsPaise();

            items.add(LeaderboardItemResponse.builder()
                    .rank(rank)
                    .userId(entry.getUserId())
                    .displayName(user != null ? user.getDisplayName() : "Unknown Player")
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .handsWon(entry.getHandsWon())
                    .handsPlayed(entry.getHandsPlayed())
                    .totalWinningsPaise(entry.getTotalWinningsPaise())
                    .statValue(statValue)
                    .build());
        }

        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    public UserRankResponse getUserRank(String userId, LeaderboardWindow window, LeaderboardMetric metric) {
        String windowKey = resolveWindowKey(window, Instant.now());
        User user = userRepository.findById(userId).orElse(null);

        Optional<LeaderboardEntry> userEntryOpt = leaderboardEntryRepository.findByUserIdAndWindowAndWindowKey(userId, window, windowKey);

        if (userEntryOpt.isEmpty()) {
            return UserRankResponse.builder()
                    .userId(userId)
                    .displayName(user != null ? user.getDisplayName() : "Unknown Player")
                    .window(window)
                    .windowKey(windowKey)
                    .metric(metric)
                    .ranked(false)
                    .rank(null)
                    .handsWon(0)
                    .handsPlayed(0)
                    .totalWinningsPaise(0L)
                    .statValue(0L)
                    .build();
        }

        LeaderboardEntry entry = userEntryOpt.get();

        // Calculate exact rank position across all window entries
        List<LeaderboardEntry> allEntries = metric == LeaderboardMetric.WINS ?
                leaderboardEntryRepository.findByWindowAndWindowKeyOrderByHandsWonDesc(window, windowKey) :
                leaderboardEntryRepository.findByWindowAndWindowKeyOrderByTotalWinningsPaiseDesc(window, windowKey);

        int rankPosition = 1;
        for (LeaderboardEntry e : allEntries) {
            if (e.getUserId().equals(userId)) {
                break;
            }
            rankPosition++;
        }

        long statValue = metric == LeaderboardMetric.WINS ? entry.getHandsWon() : entry.getTotalWinningsPaise();

        return UserRankResponse.builder()
                .userId(userId)
                .displayName(user != null ? user.getDisplayName() : "Unknown Player")
                .window(window)
                .windowKey(windowKey)
                .metric(metric)
                .ranked(true)
                .rank(rankPosition)
                .handsWon(entry.getHandsWon())
                .handsPlayed(entry.getHandsPlayed())
                .totalWinningsPaise(entry.getTotalWinningsPaise())
                .statValue(statValue)
                .build();
    }
}
