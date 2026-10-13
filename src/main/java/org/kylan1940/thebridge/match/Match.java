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

public class Match {

    public enum GoalResult {
        IGNORED,
        SCORED,
        MATCH_WON
    }

    private final Arena arena;
    private final int scoreLimit;

    private final List<UUID> players = new ArrayList<>();
    private final Map<UUID, MatchTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();

    private final Map<MatchTeam, Integer> scores = new EnumMap<>(MatchTeam.class);
    private final Map<UUID, Integer> goals = new LinkedHashMap<>();
    private final Map<UUID, Integer> kills = new LinkedHashMap<>();
    private final Map<UUID, Integer> deaths = new LinkedHashMap<>();
    private final Set<UUID> respawning = new HashSet<>();

    private final int durationSeconds;
    private int secondsLeft;
    private int countdown;
    private boolean roundActive = false;

    private MatchTeam winner;
    private boolean finished = false;

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

    public boolean addPlayer(UUID uuid, String name) {
        if (!arena.getState().isJoinable() || isFull() || players.contains(uuid)) {
            return false;
        }
        players.add(uuid);
        names.put(uuid, name);
        return true;
    }

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

    public int getPlayersNeeded() {
        return soloTest ? 1 : arena.getMaxPlayers();
    }

    public boolean isReadyToStart() {
        return players.size() >= getPlayersNeeded();
    }

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

    public List<UUID> getActivePlayers(MatchTeam team) {
        List<UUID> result = new ArrayList<>();
        for (UUID uuid : players) {
            if (teams.get(uuid) == team) {
                result.add(uuid);
            }
        }
        return result;
    }

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

    public boolean isFrozen() {
        return arena.getState() == ArenaState.IN_GAME && !roundActive;
    }

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

    public int tickCountdown() {
        if (countdown > 0) {
            countdown--;
        }
        return countdown;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public int getElapsedSeconds() {
        return durationSeconds - secondsLeft;
    }

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

    public MatchTeam decideTimeoutWinner() {
        int red = scores.get(MatchTeam.RED);
        int blue = scores.get(MatchTeam.BLUE);
        if (red == blue) {
            return null;
        }
        return red > blue ? MatchTeam.RED : MatchTeam.BLUE;
    }

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