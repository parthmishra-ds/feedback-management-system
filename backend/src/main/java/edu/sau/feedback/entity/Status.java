package edu.sau.feedback.entity;

/**
 * Lifecycle of a feedback: SUBMITTED -> UNDER_REVIEW -> ACTION_TAKEN -> CLOSED.
 * Declaration order matters: a status may only move forward (higher ordinal).
 */
public enum Status {
    SUBMITTED, UNDER_REVIEW, ACTION_TAKEN, CLOSED;

    public boolean canMoveTo(Status next) {
        return next.ordinal() > this.ordinal();
    }
}
