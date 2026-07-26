package com.teenpatti.platform.game.engine;

/**
 * Exception thrown when a player action violates Teen Patti game engine rules.
 */
public class InvalidActionException extends RuntimeException {

    private final ActionRejectionReason reason;

    public InvalidActionException(ActionRejectionReason reason) {
        super(reason.getDescription());
        this.reason = reason;
    }

    public InvalidActionException(ActionRejectionReason reason, String customMessage) {
        super(customMessage);
        this.reason = reason;
    }

    public ActionRejectionReason getReason() {
        return reason;
    }
}
