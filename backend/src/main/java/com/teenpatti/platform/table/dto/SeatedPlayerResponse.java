package com.teenpatti.platform.table.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatedPlayerResponse {
    private String userId;
    private String displayName;
}
