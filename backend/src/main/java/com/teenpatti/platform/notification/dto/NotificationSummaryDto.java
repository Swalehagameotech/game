package com.teenpatti.platform.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.teenpatti.platform.notification.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Sanitized notification row for REST and STOMP delivery.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSummaryDto {

    private String id;
    private NotificationType type;
    private String title;
    private String message;
    private String displayLabel;

    @JsonProperty("isRead")
    private boolean isRead;

    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    private String createdAt;
}
