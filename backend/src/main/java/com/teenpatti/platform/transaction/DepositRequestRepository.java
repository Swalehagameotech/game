package com.teenpatti.platform.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DepositRequestRepository extends MongoRepository<DepositRequest, String> {

    Optional<DepositRequest> findByGatewayOrderId(String gatewayOrderId);

    Page<DepositRequest> findByUserId(String userId, Pageable pageable);
}
