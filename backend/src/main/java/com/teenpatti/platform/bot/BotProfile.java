package com.teenpatti.platform.bot;

import lombok.Builder;
import lombok.Value;

/**
 * In-memory runtime profile for a seated bot (not persisted separately from User).
 */
@Value
@Builder
public class BotProfile {
    String userId;
    String displayName;
    String avatarUrl;
    BotPersonality personality;
    /** Blind rounds before the bot prefers to see cards. */
    int preferSeeAfterRounds;
}
