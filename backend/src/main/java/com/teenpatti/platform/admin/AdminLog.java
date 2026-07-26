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

/**
 * Admin Log document tracking all administrative actions (wallet additions, deductions, status changes).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "admin_logs")
public class AdminLog {

    @Id
    private String id;

    @Indexed
    private String adminId;

    @Indexed
    private String userId;

    private String action;

    private long amount;

    @CreatedDate
    private Instant timestamp;
}
