package com.teenpatti.platform.bot.event;

/**
 * Fired when a player's turn (or show prompt) needs a response — bots listen and act.
 */
public record BotActionNeededEvent(
        String tableId,
        String userId,
        boolean promptOnly
) {}
