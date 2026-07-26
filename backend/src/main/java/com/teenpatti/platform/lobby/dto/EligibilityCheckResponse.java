package com.teenpatti.platform.lobby.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityCheckResponse {
    private boolean eligible;
    private String reason;
    private Long minRequiredPaise;
    private Long currentBalancePaise;

    public static EligibilityCheckResponse eligible(long minRequiredPaise, long currentBalancePaise) {
        return EligibilityCheckResponse.builder()
                .eligible(true)
                .reason(null)
                .minRequiredPaise(minRequiredPaise)
                .currentBalancePaise(currentBalancePaise)
                .build();
    }

    public static EligibilityCheckResponse ineligible(String reason, Long minRequiredPaise, Long currentBalancePaise) {
        return EligibilityCheckResponse.builder()
                .eligible(false)
                .reason(reason)
                .minRequiredPaise(minRequiredPaise)
                .currentBalancePaise(currentBalancePaise)
                .build();
    }
}
