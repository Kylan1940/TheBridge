package org.kylan1940.thebridge.stats;

import java.util.Objects;
import java.util.UUID;

/** The lifetime statistics of one player, as stored in the database. */
public final class PlayerStats {

    private final UUID uuid;
    private final String name;
    private final int wins;
    private final int losses;
    private final int goals;
    private final int kills;
    private final int deaths;
    private final int matchesPlayed;
    private final int winStreak;
    private final int bestWinStreak;

    public PlayerStats(UUID uuid, String name, int wins, int losses, int goals, int kills, int deaths, int matchesPlayed, int winStreak, int bestWinStreak) {
        this.uuid = uuid;
        this.name = name;
        this.wins = wins;
        this.losses = losses;
        this.goals = goals;
        this.kills = kills;
        this.deaths = deaths;
        this.matchesPlayed = matchesPlayed;
        this.winStreak = winStreak;
        this.bestWinStreak = bestWinStreak;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public int wins() {
        return wins;
    }

    public int losses() {
        return losses;
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

    public int matchesPlayed() {
        return matchesPlayed;
    }

    public int winStreak() {
        return winStreak;
    }

    public int bestWinStreak() {
        return bestWinStreak;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PlayerStats that = (PlayerStats) other;
        return Objects.equals(uuid, that.uuid)
                && Objects.equals(name, that.name)
                && wins == that.wins
                && losses == that.losses
                && goals == that.goals
                && kills == that.kills
                && deaths == that.deaths
                && matchesPlayed == that.matchesPlayed
                && winStreak == that.winStreak
                && bestWinStreak == that.bestWinStreak;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, name, wins, losses, goals, kills, deaths, matchesPlayed, winStreak, bestWinStreak);
    }

    @Override
    public String toString() {
        return "PlayerStats[" + "uuid=" + uuid
                + ", name=" + name
                + ", wins=" + wins
                + ", losses=" + losses
                + ", goals=" + goals
                + ", kills=" + kills
                + ", deaths=" + deaths
                + ", matchesPlayed=" + matchesPlayed
                + ", winStreak=" + winStreak
                + ", bestWinStreak=" + bestWinStreak + "]";
    }
}
