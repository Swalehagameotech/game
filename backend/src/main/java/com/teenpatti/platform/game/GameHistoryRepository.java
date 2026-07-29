package com.teenpatti.platform.game;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameHistoryRepository extends MongoRepository<GameHistory, String> {

    Optional<GameHistory> findByHandId(String handId);

    Page<GameHistory> findByTableIdOrderByEndedAtDesc(String tableId, Pageable pageable);

    Page<GameHistory> findByWinnerIdOrderByEndedAtDesc(String winnerId, Pageable pageable);

    Page<GameHistory> findByPlayerIdsContainingOrderByEndedAtDesc(String userId, Pageable pageable);

    long countByWinnerId(String winnerId);

    long countByPlayerIdsContaining(String userId);
}
