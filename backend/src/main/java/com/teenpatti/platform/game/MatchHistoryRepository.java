package com.teenpatti.platform.game;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchHistoryRepository extends MongoRepository<MatchHistory, String> {

    Page<MatchHistory> findByTableIdOrderByEndedAtDesc(String tableId, Pageable pageable);

    Page<MatchHistory> findByWinnerIdOrderByEndedAtDesc(String winnerId, Pageable pageable);

    Page<MatchHistory> findByPlayerIdsContainingOrderByEndedAtDesc(String userId, Pageable pageable);
}
