package com.teenpatti.platform.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

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

    long countByIsOnlineTrue();

    Page<User> findByKycStatus(KycStatus kycStatus, Pageable pageable);

    @Query("{ '$or': [ { 'email': { '$regex': ?0, '$options': 'i' } }, { 'displayName': { '$regex': ?0, '$options': 'i' } }, { 'phoneNumber': { '$regex': ?0, '$options': 'i' } } ] }")
    Page<User> searchByEmailOrDisplayNameOrPhoneNumber(String query, Pageable pageable);
}
