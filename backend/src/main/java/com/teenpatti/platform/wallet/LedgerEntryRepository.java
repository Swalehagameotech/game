package com.teenpatti.platform.wallet;

import com.teenpatti.platform.transaction.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data Mongo repository for immutable LedgerEntry audit logs.
 */
@Repository
public interface LedgerEntryRepository extends MongoRepository<LedgerEntry, String> {
    Optional<LedgerEntry> findByReferenceId(String referenceId);

    Page<LedgerEntry> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
