package com.teenpatti.platform.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithdrawalRequestRepository extends MongoRepository<WithdrawalRequest, String> {

    Page<WithdrawalRequest> findByUserId(String userId, Pageable pageable);

    Page<WithdrawalRequest> findByStatus(WithdrawalStatus status, Pageable pageable);
}
