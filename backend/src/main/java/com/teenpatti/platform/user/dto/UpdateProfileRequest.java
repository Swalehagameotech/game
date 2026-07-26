package com.teenpatti.platform.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for self-service profile updates via PATCH /api/users/me.
 * Configured with ignoreUnknown = false to strictly reject any unexpected or forbidden fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class UpdateProfileRequest {

    @Size(min = 3, max = 20, message = "Display name must be between 3 and 20 characters")
    private String displayName;

    @Pattern(regexp = "^(https?://.*)?$", message = "Avatar URL must be a valid URL")
    private String avatarUrl;
}
