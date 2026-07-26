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
public class KycSubmissionRequest {

    @NotBlank(message = "documentType is required")
    private String documentType; // e.g. PAN, AADHAAR

    @NotBlank(message = "documentNumber is required")
    private String documentNumber;
}
