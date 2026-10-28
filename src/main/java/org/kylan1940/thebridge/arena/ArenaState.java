package org.kylan1940.thebridge.arena;

/**
 * The life cycle of an arena. Every arena is always in exactly one of these states.
 *
 * <pre>
 * DISABLED -> WAITING -> STARTING -> IN_GAME -> ENDING -> RESETTING -> WAITING
 *                ^           |
 *                +-----------+   (a player left, the countdown is cancelled)
 * </pre>
 *
 * canChangeTo() is the only place that decides which jumps are legal, so a bug
 * elsewhere can never put an arena into a nonsense state without being noticed.
 */
public enum ArenaState {

    /** Not playable (not enabled, or the setup is incomplete). */
    DISABLED,
    /** Open for players. Waiting for enough of them. */
    WAITING,
    /** Enough players joined, the pre-match countdown is running. */
    STARTING,
    /** The match is running. */
    IN_GAME,
    /** A winner was decided. Players are still in the arena and see the result. */
    ENDING,
    /** Players are gone, changed blocks are being restored. Nobody may join. */
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

    /** Players are allowed to join in these states. */
    public boolean isJoinable() {
        return this == WAITING || this == STARTING;
    }

    /** A match exists (players are inside) in these states. */
    public boolean isMatchRunning() {
        return this == STARTING || this == IN_GAME || this == ENDING;
    }
}
