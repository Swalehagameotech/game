package com.teenpatti.platform.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminActionLogRepository extends MongoRepository<AdminActionLog, String> {

    Page<AdminActionLog> findByTargetUserIdOrderByCreatedAtDesc(String targetUserId, Pageable pageable);

    Page<AdminActionLog> findByAdminUserIdOrderByCreatedAtDesc(String adminUserId, Pageable pageable);
}
