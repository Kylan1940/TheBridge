package org.kylan1940.thebridge.arena;

public enum ArenaState {

    DISABLED,
    WAITING,
    STARTING,
    IN_GAME,
    ENDING,
    RESETTING;

    public boolean canChangeTo(ArenaState next) {
        switch (this) {
            case DISABLED:
                return next == WAITING;
            case WAITING:
                return next == STARTING || next == DISABLED;
            case STARTING:
                return next == WAITING || next == IN_GAME || next == DISABLED;
            case IN_GAME:
                return next == ENDING;
            case ENDING:
                return next == RESETTING;
            case RESETTING:
                return next == WAITING || next == DISABLED;
            default:
                return false;
        }
    }

    public boolean isJoinable() {
        return this == WAITING || this == STARTING;
    }

    public boolean isMatchRunning() {
        return this == STARTING || this == IN_GAME || this == ENDING;
    }
}
