package com.teenpatti.platform.game.engine;

import java.util.Objects;

/**
 * Action submitted by a player during their turn.
 */
public final class PlayerAction {

    private final String playerId;
    private final PlayerActionType actionType;
    private final long amountPaise;

    public PlayerAction(String playerId, PlayerActionType actionType, long amountPaise) {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId must not be null or blank");
        }
        if (actionType == null) {
            throw new IllegalArgumentException("actionType must not be null");
        }
        this.playerId = playerId;
        this.actionType = actionType;
        this.amountPaise = amountPaise;
    }

    public static PlayerAction of(String playerId, PlayerActionType actionType) {
        return new PlayerAction(playerId, actionType, 0L);
    }

    public static PlayerAction of(String playerId, PlayerActionType actionType, long amountPaise) {
        return new PlayerAction(playerId, actionType, amountPaise);
    }

    public String getPlayerId() {
        return playerId;
    }

    public PlayerActionType getActionType() {
        return actionType;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerAction that = (PlayerAction) o;
        return amountPaise == that.amountPaise && Objects.equals(playerId, that.playerId) && actionType == that.actionType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, actionType, amountPaise);
    }

    @Override
    public String toString() {
        return "PlayerAction{" +
                "playerId='" + playerId + '\'' +
                ", actionType=" + actionType +
                ", amountPaise=" + amountPaise +
                '}';
    }
}
