package org.kylan1940.thebridge.stats;

import java.util.UUID;

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
