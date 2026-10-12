package org.kylan1940.thebridge.stats;

import java.util.UUID;

/** The lifetime statistics of one player, as stored in the database. */
public record PlayerStats(
        UUID uuid,
        String name,
        int wins,
        int losses,
        int goals,
        int kills,
        int deaths,
        int matchesPlayed,
        int winStreak,
        int bestWinStreak
) {
}
