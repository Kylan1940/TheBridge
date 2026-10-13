package org.kylan1940.thebridge.stats;

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
