package org.kylan1940.thebridge.stats;

/** A short summary of a finished match, stored in the match history table. */
public record MatchRecord(
        String arena,
        /** "RED", "BLUE" or "DRAW". */
        String winner,
        int redScore,
        int blueScore,
        int durationSeconds,
        long endedAt
) {
}
