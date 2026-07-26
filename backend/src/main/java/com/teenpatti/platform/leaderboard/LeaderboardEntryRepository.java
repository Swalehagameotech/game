package com.teenpatti.platform.leaderboard;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaderboardEntryRepository extends MongoRepository<LeaderboardEntry, String> {

    Optional<LeaderboardEntry> findByUserIdAndWindowAndWindowKey(String userId, LeaderboardWindow window, String windowKey);

    Page<LeaderboardEntry> findByWindowAndWindowKeyOrderByTotalWinningsPaiseDesc(LeaderboardWindow window, String windowKey, Pageable pageable);

    Page<LeaderboardEntry> findByWindowAndWindowKeyOrderByHandsWonDesc(LeaderboardWindow window, String windowKey, Pageable pageable);

    List<LeaderboardEntry> findByWindowAndWindowKeyOrderByTotalWinningsPaiseDesc(LeaderboardWindow window, String windowKey);

    List<LeaderboardEntry> findByWindowAndWindowKeyOrderByHandsWonDesc(LeaderboardWindow window, String windowKey);
}
