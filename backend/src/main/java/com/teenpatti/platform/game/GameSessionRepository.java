package com.teenpatti.platform.game;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameSessionRepository extends MongoRepository<GameSession, String> {

    Optional<GameSession> findByTableIdAndStatus(String tableId, GameSessionStatus status);

    List<GameSession> findByTableIdOrderByStartedAtDesc(String tableId);

    Optional<GameSession> findByHandId(String handId);
}
