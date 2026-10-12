package org.kylan1940.thebridge.match;

import org.kylan1940.thebridge.arena.Arena;
import org.kylan1940.thebridge.arena.ArenaState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The RULES of one match: who is playing, which team they are on and what the score is.
 *
 * This class never touches Bukkit (no teleporting, no messages, no schedulers). That is on
 * purpose: it can be unit tested with plain JUnit, and MatchManager is the class that turns
 * these decisions into things players actually see.
 */
public class Match {

    /** What happened when a goal was reported. */
    public enum GoalResult {
        /** The goal does not count (wrong time, already scored this round, unknown player). */
        IGNORED,
        /** A point was scored and the match goes on. */
        SCORED,
        /** The point was the last one needed. The match is won. */
        MATCH_WON
    }

    private final Arena arena;
    private final int scoreLimit;

    /** Players that are currently in the match, in the order they joined. */
    private final List<UUID> players = new ArrayList<>();
    /** Team of every player who was ever assigned. Kept after a player leaves so stats stay correct. */
    private final Map<UUID, MatchTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();

    private final Map<MatchTeam, Integer> scores = new EnumMap<>(MatchTeam.class);
    private final Map<UUID, Integer> goals = new LinkedHashMap<>();
    private final Map<UUID, Integer> kills = new LinkedHashMap<>();
    private final Map<UUID, Integer> deaths = new LinkedHashMap<>();
    /** Players who died and are waiting for their respawn delay to pass. */
    private final Set<UUID> respawning = new HashSet<>();

    private final int durationSeconds;
    private int secondsLeft;
    /** Used for the pre-match countdown and for the short pause before every round. */
    private int countdown;
    /** True while players may fight and score. False during countdowns and right after a goal. */
    private boolean roundActive = false;

    private MatchTeam winner;
    private boolean finished = false;

    /** Developer test mode (config: match.solo-test). One player is enough, nothing is saved to the statistics. */
    private boolean soloTest = false;

    public Match(Arena arena, int scoreLimit, int durationSeconds) {
        this.arena = arena;
        this.scoreLimit = scoreLimit;
        this.durationSeconds = durationSeconds;
        this.secondsLeft = durationSeconds;
        scores.put(MatchTeam.RED, 0);
        scores.put(MatchTeam.BLUE, 0);
    }

    // ------------------------------------------------------------------
    // Players and teams
    // ------------------------------------------------------------------

    /** Adds a player to the waiting list. Returns false if the arena is full, not joinable or the player is already in. */
    public boolean addPlayer(UUID uuid, String name) {
        if (!arena.getState().isJoinable() || isFull() || players.contains(uuid)) {
            return false;
        }
        players.add(uuid);
        names.put(uuid, name);
        return true;
    }

    /**
     * Removes a player from the match (they left, disconnected or were kicked).
     * Their team and stats are kept so the final results stay correct.
     *
     * @return true if the player was in the match
     */
    public boolean removePlayer(UUID uuid) {
        respawning.remove(uuid);
        return players.remove(uuid);
    }

    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }

    public boolean isFull() {
        return players.size() >= arena.getMaxPlayers();
    }

    public void setSoloTest(boolean soloTest) {
        this.soloTest = soloTest;
    }

    public boolean isSoloTest() {
        return soloTest;
    }

    /** How many players must be waiting before the countdown starts. Normally a full arena, in solo test mode just one. */
    public int getPlayersNeeded() {
        return soloTest ? 1 : arena.getMaxPlayers();
    }

    public boolean isReadyToStart() {
        return players.size() >= getPlayersNeeded();
    }

    /**
     * Splits the waiting players into teams by alternating: RED, BLUE, RED, BLUE...
     * With 2 players (1v1) that gives one per team, with 4 players (2v2) two per team.
     */
    public void assignTeams() {
        teams.clear();
        MatchTeam next = MatchTeam.RED;
        for (UUID uuid : players) {
            teams.put(uuid, next);
            goals.put(uuid, 0);
            kills.put(uuid, 0);
            deaths.put(uuid, 0);
            next = next.opposite();
        }
    }

    public MatchTeam getTeam(UUID uuid) {
        return teams.get(uuid);
    }

    /** Players of this team that are still in the match. */
    public List<UUID> getActivePlayers(MatchTeam team) {
        List<UUID> result = new ArrayList<>();
        for (UUID uuid : players) {
            if (teams.get(uuid) == team) {
                result.add(uuid);
            }
        }
        return result;
    }

    /** Everyone who played in this match, including players who already left. */
    public List<UUID> getParticipants() {
        return new ArrayList<>(teams.keySet());
    }

    public String getName(UUID uuid) {
        return names.getOrDefault(uuid, "Unknown");
    }

    // ------------------------------------------------------------------
    // Rounds and scoring
    // ------------------------------------------------------------------

    public void startRound() {
        roundActive = true;
    }

    /** True while the round is paused: the match runs but nobody may move or fight yet. */
    public boolean isFrozen() {
        return arena.getState() == ArenaState.IN_GAME && !roundActive;
    }

    /**
     * Called when a player stepped into the goal of the OTHER team.
     *
     * Duplicate protection: the first call of a round sets roundActive to false.
     * Every later call (the same player moving again in the same tick, or the other
     * team scoring at the same moment) is IGNORED until the next round starts.
     * So "simultaneous goals" always resolve to: the first event that is processed wins.
     */
    public GoalResult registerGoal(UUID scorer) {
        if (arena.getState() != ArenaState.IN_GAME || !roundActive || finished) {
            return GoalResult.IGNORED;
        }
        MatchTeam team = teams.get(scorer);
        if (team == null || !players.contains(scorer) || respawning.contains(scorer)) {
            return GoalResult.IGNORED;
        }

        roundActive = false;
        scores.merge(team, 1, Integer::sum);
        goals.merge(scorer, 1, Integer::sum);

        if (scores.get(team) >= scoreLimit) {
            return GoalResult.MATCH_WON;
        }
        return GoalResult.SCORED;
    }

    public int getScore(MatchTeam team) {
        return scores.get(team);
    }

    public int getScoreLimit() {
        return scoreLimit;
    }

    public void addKill(UUID killer) {
        kills.merge(killer, 1, Integer::sum);
    }

    public void addDeath(UUID dead) {
        deaths.merge(dead, 1, Integer::sum);
    }

    public int getGoals(UUID uuid) {
        return goals.getOrDefault(uuid, 0);
    }

    public int getKills(UUID uuid) {
        return kills.getOrDefault(uuid, 0);
    }

    public int getDeaths(UUID uuid) {
        return deaths.getOrDefault(uuid, 0);
    }

    // ------------------------------------------------------------------
    // Respawning
    // ------------------------------------------------------------------

    public void setRespawning(UUID uuid, boolean value) {
        if (value) {
            respawning.add(uuid);
        } else {
            respawning.remove(uuid);
        }
    }

    public boolean isRespawning(UUID uuid) {
        return respawning.contains(uuid);
    }

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    public int getCountdown() {
        return countdown;
    }

    public void setCountdown(int seconds) {
        countdown = seconds;
    }

    /** Lowers the countdown by one second (never below 0) and returns the new value. */
    public int tickCountdown() {
        if (countdown > 0) {
            countdown--;
        }
        return countdown;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    /** How many seconds of active play have passed (paused rounds are not counted). */
    public int getElapsedSeconds() {
        return durationSeconds - secondsLeft;
    }

    /**
     * Lowers the match clock by one second, but only while a round is active
     * (the clock does not run during countdowns and respawn pauses of the whole round).
     *
     * @return true when the time just ran out
     */
    public boolean tickClock() {
        if (arena.getState() != ArenaState.IN_GAME || !roundActive || finished) {
            return false;
        }
        if (secondsLeft > 0) {
            secondsLeft--;
        }
        return secondsLeft == 0;
    }

    // ------------------------------------------------------------------
    // Ending
    // ------------------------------------------------------------------

    /**
     * The winner when time ran out: the team with more points.
     * Returns null for a tie (a draw).
     */
    public MatchTeam decideTimeoutWinner() {
        int red = scores.get(MatchTeam.RED);
        int blue = scores.get(MatchTeam.BLUE);
        if (red == blue) {
            return null;
        }
        return red > blue ? MatchTeam.RED : MatchTeam.BLUE;
    }

    /**
     * Checks if one team has no players left. The other team then wins by forfeit.
     * Returns null when both teams still have players, or when nobody is left at all.
     */
    public MatchTeam getForfeitWinner() {
        boolean redHasPlayers = !getActivePlayers(MatchTeam.RED).isEmpty();
        boolean blueHasPlayers = !getActivePlayers(MatchTeam.BLUE).isEmpty();

        if (redHasPlayers && !blueHasPlayers) {
            return MatchTeam.RED;
        }
        if (blueHasPlayers && !redHasPlayers) {
            return MatchTeam.BLUE;
        }
        return null;
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    /** Marks the match as over. winner is null for a draw. */
    public void finish(MatchTeam winner) {
        this.winner = winner;
        this.finished = true;
        this.roundActive = false;
    }

    public boolean isFinished() {
        return finished;
    }

    public MatchTeam getWinner() {
        return winner;
    }

    public List<UUID> getPlayers() {
        return new ArrayList<>(players);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public Arena getArena() {
        return arena;
    }
}