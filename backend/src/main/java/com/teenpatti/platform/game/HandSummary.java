package com.teenpatti.platform.game;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Nested summary details for a completed Teen Patti hand (used for auditing and dispute resolution).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandSummary {

    private String winningHandName;
    private int winningRank;

    /**
     * Map of userId -> cards shown at showdown or hand fold state.
     */
    private Map<String, String> playerHandsMap;

    private String notes;
}
