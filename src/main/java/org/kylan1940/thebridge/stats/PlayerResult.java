package org.kylan1940.thebridge.stats;

import java.util.Objects;
import java.util.UUID;

/**
 * What one player achieved in ONE match. StatsManager builds these when a match ends
 * and the database adds them to the lifetime numbers.
 *
 * In a draw both won and lost are false: the match counts as played, nothing else changes.
 */
public final class PlayerResult {

    private final UUID uuid;
    private final String name;
    private final boolean won;
    private final boolean lost;
    private final int goals;
    private final int kills;
    private final int deaths;

    public PlayerResult(UUID uuid, String name, boolean won, boolean lost, int goals, int kills, int deaths) {
        this.uuid = uuid;
        this.name = name;
        this.won = won;
        this.lost = lost;
        this.goals = goals;
        this.kills = kills;
        this.deaths = deaths;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public boolean won() {
        return won;
    }

    public boolean lost() {
        return lost;
    }

    public int goals() {
        return goals;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PlayerResult that = (PlayerResult) other;
        return Objects.equals(uuid, that.uuid)
                && Objects.equals(name, that.name)
                && won == that.won
                && lost == that.lost
                && goals == that.goals
                && kills == that.kills
                && deaths == that.deaths;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, name, won, lost, goals, kills, deaths);
    }

    @Override
    public String toString() {
        return "PlayerResult[" + "uuid=" + uuid
                + ", name=" + name
                + ", won=" + won
                + ", lost=" + lost
                + ", goals=" + goals
                + ", kills=" + kills
                + ", deaths=" + deaths + "]";
    }
}
