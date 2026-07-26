package com.teenpatti.platform.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repository interface for User documents.
 */
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByEmailOrPhoneNumber(String email, String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByDisplayName(String displayName);

    boolean existsByDisplayNameAndIdNot(String displayName, String id);

    org.springframework.data.domain.Page<User> findByKycStatus(KycStatus kycStatus, org.springframework.data.domain.Pageable pageable);
}
