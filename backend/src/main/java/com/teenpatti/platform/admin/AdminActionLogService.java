package com.teenpatti.platform.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Service encapsulating creation and persistence of append-only AdminActionLog audit records.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminActionLogService {

    private final AdminActionLogRepository adminActionLogRepository;

    public AdminActionLog logAction(
            String adminUserId,
            AdminActionType actionType,
            String targetUserId,
            Map<String, Object> details) {

        AdminActionLog actionLog = AdminActionLog.builder()
                .adminUserId(adminUserId)
                .actionType(actionType)
                .targetUserId(targetUserId)
                .details(details != null ? details : Map.of())
                .createdAt(Instant.now())
                .build();

        AdminActionLog saved = adminActionLogRepository.save(actionLog);
        log.info("Logged AdminAction [{}] by admin [{}] targeting user [{}]", actionType, adminUserId, targetUserId);
        return saved;
    }
}
