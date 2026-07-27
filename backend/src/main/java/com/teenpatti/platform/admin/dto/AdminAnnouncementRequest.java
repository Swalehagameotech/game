package com.teenpatti.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAnnouncementRequest {

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "message is required")
    private String message;
}
