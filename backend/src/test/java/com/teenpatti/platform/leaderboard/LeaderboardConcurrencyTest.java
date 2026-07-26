package com.teenpatti.platform.leaderboard;

import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.game.engine.HandRankCategory;
import com.teenpatti.platform.table.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class LeaderboardConcurrencyTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private LeaderboardEntryRepository leaderboardEntryRepository;

    @BeforeEach
    void setUp() {
        leaderboardEntryRepository.deleteAll();
    }

    @Test
    @DisplayName("CONCURRENCY TEST: 50 concurrent hand completions for the same user result in exact atomic accumulation with zero lost updates")
    void recordHandResult_ConcurrentUpdates_AccumulatesCorrectly() throws Exception {
        String testUserId = "user_concurrent_lb";
        int totalThreads = 10;
        int handsPerThread = 5; // Total 50 hands
        int totalHands = totalThreads * handsPerThread;
        long payoutPerHand = 1_000L; // 1,000 paise per win

        Table mockTable = Table.builder()
                .id("table_conc_lb")
                .seatedPlayerIds(List.of(testUserId, "opponent_user_1"))
                .build();

        HandOutcome winOutcome = new HandOutcome(
                testUserId,
                1_000L,
                0L,
                payoutPerHand,
                HandRankCategory.PAIR,
                Collections.emptyMap(),
                "Winner"
        );

        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalHands);

        for (int i = 0; i < totalHands; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    leaderboardService.recordHandResult(mockTable, winOutcome);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await();
        executorService.shutdown();

        // Verify across all 3 windows (DAILY, WEEKLY, ALL_TIME)
        for (LeaderboardWindow window : LeaderboardWindow.values()) {
            String windowKey = leaderboardService.resolveWindowKey(window, Instant.now());
            LeaderboardEntry entry = leaderboardEntryRepository
                    .findByUserIdAndWindowAndWindowKey(testUserId, window, windowKey)
                    .orElseThrow(() -> new AssertionError("Missing leaderboard entry for window " + window));

            assertEquals(totalHands, entry.getHandsWon(), "handsWon must match total concurrent hands exactly");
            assertEquals(totalHands, entry.getHandsPlayed(), "handsPlayed must match total concurrent hands exactly");
            assertEquals(totalHands * payoutPerHand, entry.getTotalWinningsPaise(), "totalWinningsPaise must match accumulated total exactly");
        }
    }
}
