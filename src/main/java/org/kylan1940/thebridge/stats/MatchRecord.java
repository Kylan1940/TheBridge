package org.kylan1940.thebridge.stats;

import java.util.Objects;

/** A short summary of a finished match, stored in the match history table. */
public final class MatchRecord {

    private final String arena;
    /** "RED", "BLUE" or "DRAW". */
    private final String winner;
    private final int redScore;
    private final int blueScore;
    private final int durationSeconds;
    private final long endedAt;

    public MatchRecord(String arena, String winner, int redScore, int blueScore, int durationSeconds, long endedAt) {
        this.arena = arena;
        this.winner = winner;
        this.redScore = redScore;
        this.blueScore = blueScore;
        this.durationSeconds = durationSeconds;
        this.endedAt = endedAt;
    }

    public String arena() {
        return arena;
    }

    public String winner() {
        return winner;
    }

    public int redScore() {
        return redScore;
    }

    public int blueScore() {
        return blueScore;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public long endedAt() {
        return endedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        MatchRecord that = (MatchRecord) other;
        return Objects.equals(arena, that.arena)
                && Objects.equals(winner, that.winner)
                && redScore == that.redScore
                && blueScore == that.blueScore
                && durationSeconds == that.durationSeconds
                && endedAt == that.endedAt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(arena, winner, redScore, blueScore, durationSeconds, endedAt);
    }

    @Override
    public String toString() {
        return "MatchRecord[" + "arena=" + arena
                + ", winner=" + winner
                + ", redScore=" + redScore
                + ", blueScore=" + blueScore
                + ", durationSeconds=" + durationSeconds
                + ", endedAt=" + endedAt + "]";
    }
}
