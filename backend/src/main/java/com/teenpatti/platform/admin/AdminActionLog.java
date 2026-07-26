package com.teenpatti.platform.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * AdminActionLog document storing audit logs of administrative actions.
 *
 * CRITICAL ARCHITECTURAL REQUIREMENT:
 * Append-only by design — every admin action affecting a user or money must be logged here
 * and never modified or deleted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "admin_action_logs")
public class AdminActionLog {

    @Id
    private String id;

    @Indexed
    private String adminUserId;

    private AdminActionType actionType;

    @Indexed
    private String targetUserId;

    private Map<String, Object> details;

    @CreatedDate
    private Instant createdAt;
}
