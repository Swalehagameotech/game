package com.teenpatti.platform.wallet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends MongoRepository<WalletTransaction, String> {

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

    Page<WalletTransaction> findByUserId(String userId, Pageable pageable);

    long countByUserId(String userId);
}
