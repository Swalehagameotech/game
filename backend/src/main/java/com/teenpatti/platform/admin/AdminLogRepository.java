package com.teenpatti.platform.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminLogRepository extends MongoRepository<AdminLog, String> {

    List<AdminLog> findByUserIdOrderByTimestampDesc(String userId);

    Page<AdminLog> findByAdminId(String adminId, Pageable pageable);
}
