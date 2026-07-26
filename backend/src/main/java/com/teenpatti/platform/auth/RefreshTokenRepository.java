package com.teenpatti.platform.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repository interface for RefreshToken documents.
 */
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserId(String userId);
}
