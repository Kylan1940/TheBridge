package org.kylan1940.thebridge.arena;

import org.kylan1940.thebridge.match.MatchTeam;

import java.util.ArrayList;
import java.util.List;

public class Arena {

    private final String name;

    private String worldName;
    private Point lobby;

    private Point redSpawn;
    private Point blueSpawn;
    private Region redGoal;
    private Region blueGoal;
    private Region bounds;

    private int teamSize = 1;
    private int scoreLimit = 0;
    private int durationSeconds = 0;

    private boolean enabled = false;
    private ArenaState state = ArenaState.DISABLED;

    public Arena(String name, String worldName) {
        this.name = name;
        this.worldName = worldName;
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    public ArenaState getState() {
        return state;
    }

    public void setState(ArenaState newState) {
        if (state == newState) {
            return;
        }
        if (!state.canChangeTo(newState)) {
            throw new IllegalStateException("Arena " + name + " cannot change from " + state + " to " + newState);
        }
        state = newState;
    }

    public void forceState(ArenaState newState) {
        state = newState;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /**
     * Returns every reason why this arena cannot be enabled. An empty list means the
     * arena is ready. The messages are shown to the admin as they are.
     *
     * @param worldLoaded whether the arena's world exists on this server right now
     */
    public List<String> getProblems(boolean worldLoaded) {
        List<String> problems = new ArrayList<>();

        if (worldName == null || worldName.isBlank()) {
            problems.add("No world is set.");
        } else if (!worldLoaded) {
            problems.add("World '" + worldName + "' is not loaded on this server.");
        }

        if (lobby == null) {
            problems.add("Lobby is not set (/thebridge setlobby " + name + ").");
        }
        if (redSpawn == null) {
            problems.add("Red spawn is not set (/thebridge setspawn " + name + " red).");
        }
        if (blueSpawn == null) {
            problems.add("Blue spawn is not set (/thebridge setspawn " + name + " blue).");
        }
        if (redGoal == null) {
            problems.add("Red goal is not set (/thebridge setgoal " + name + " red).");
        }
        if (blueGoal == null) {
            problems.add("Blue goal is not set (/thebridge setgoal " + name + " blue).");
        }
        if (bounds == null) {
            problems.add("Bounds are not set (/thebridge setbounds " + name + ").");
        }

        if (redSpawn == null || blueSpawn == null || redGoal == null || blueGoal == null || bounds == null) {
            return problems;
        }

        if (!worldName.equals(redSpawn.world()) || !worldName.equals(blueSpawn.world())) {
            problems.add("Both spawns must be in the arena world '" + worldName + "'.");
        }
        if (!bounds.contains(redSpawn) || !bounds.contains(blueSpawn)) {
            problems.add("Both spawns must be inside the bounds.");
        }
        if (redGoal.overlaps(blueGoal)) {
            problems.add("The red goal and the blue goal overlap.");
        }
        if (redGoal.contains(redSpawn) || redGoal.contains(blueSpawn)
                || blueGoal.contains(redSpawn) || blueGoal.contains(blueSpawn)) {
            problems.add("A spawn is inside a goal. Players would score the moment they spawn.");
        }
        if (!bounds.overlaps(redGoal) || !bounds.overlaps(blueGoal)) {
            problems.add("Both goals must be inside the bounds.");
        }
        if (teamSize < 1) {
            problems.add("Team size must be at least 1.");
        }
        return problems;
    }

    // ------------------------------------------------------------------
    // Helpers that pick the red or the blue value
    // ------------------------------------------------------------------

    public Point getSpawn(MatchTeam team) {
        return team == MatchTeam.RED ? redSpawn : blueSpawn;
    }

    public void setSpawn(MatchTeam team, Point spawn) {
        if (team == MatchTeam.RED) {
            redSpawn = spawn;
        } else {
            blueSpawn = spawn;
        }
    }

    public Region getGoal(MatchTeam team) {
        return team == MatchTeam.RED ? redGoal : blueGoal;
    }

    public void setGoal(MatchTeam team, Region goal) {
        if (team == MatchTeam.RED) {
            redGoal = goal;
        } else {
            blueGoal = goal;
        }
    }

    // ------------------------------------------------------------------
    // Plain getters and setters
    // ------------------------------------------------------------------

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public Point getLobby() {
        return lobby;
    }

    public void setLobby(Point lobby) {
        this.lobby = lobby;
    }

    public Region getBounds() {
        return bounds;
    }

    public void setBounds(Region bounds) {
        this.bounds = bounds;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(int teamSize) {
        this.teamSize = teamSize;
    }

    public int getMaxPlayers() {
        return teamSize * 2;
    }

    public int getScoreLimit() {
        return scoreLimit;
    }

    public void setScoreLimit(int scoreLimit) {
        this.scoreLimit = scoreLimit;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
