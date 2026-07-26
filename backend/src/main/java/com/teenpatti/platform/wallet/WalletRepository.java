package com.teenpatti.platform.wallet;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repository interface for Wallet documents.
 */
public interface WalletRepository extends MongoRepository<Wallet, String> {

    Optional<Wallet> findByUserId(String userId);

    boolean existsByUserId(String userId);
}
