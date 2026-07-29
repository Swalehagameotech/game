package com.teenpatti.platform.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithdrawalRequestRepository extends MongoRepository<WithdrawalRequest, String> {

    Page<WithdrawalRequest> findByUserId(String userId, Pageable pageable);

    java.util.List<WithdrawalRequest> findByUserIdAndStatus(String userId, WithdrawalStatus status);

    java.util.List<WithdrawalRequest> findByUserId(String userId);

    Page<WithdrawalRequest> findByStatus(WithdrawalStatus status, Pageable pageable);

    long countByStatus(WithdrawalStatus status);
}
