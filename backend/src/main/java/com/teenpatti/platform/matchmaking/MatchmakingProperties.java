package com.teenpatti.platform.matchmaking;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "teenpatti.matchmaking")
public class MatchmakingProperties {

    /** Wait for real players before injecting bots (ms). */
    private long waitForHumansMs = 7_000L;

    /** Minimum players required to start (bots fill up to this). */
    private int minPlayers = 3;

    /** If true, after timeout also fill empty seats up to maxPlayers. */
    private boolean fillToMax = false;

    private long minThinkMs = 2_000L;
    private long maxThinkMs = 8_000L;
}
