package org.kylan1940.thebridge.stats;

import java.util.UUID;

/**
 * What one player achieved in ONE match. StatsManager builds these when a match ends
 * and the database adds them to the lifetime numbers.
 *
 * In a draw both won and lost are false: the match counts as played, nothing else changes.
 */
public record PlayerResult(
        UUID uuid,
        String name,
        boolean won,
        boolean lost,
        int goals,
        int kills,
        int deaths
) {
}
