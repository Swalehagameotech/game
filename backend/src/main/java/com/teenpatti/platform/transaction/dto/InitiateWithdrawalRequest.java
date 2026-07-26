package com.teenpatti.platform.transaction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateWithdrawalRequest {

    @NotNull(message = "amountPaise is required")
    @Min(value = 1, message = "amountPaise must be greater than 0")
    private Long amountPaise;

    @NotBlank(message = "accountNumber is required")
    @Pattern(regexp = "^[0-9]{9,18}$", message = "accountNumber must be numeric and between 9 and 18 digits")
    private String accountNumber;

    @NotBlank(message = "ifscCode is required")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC code format")
    private String ifscCode;

    @NotBlank(message = "accountHolderName is required")
    private String accountHolderName;
}
