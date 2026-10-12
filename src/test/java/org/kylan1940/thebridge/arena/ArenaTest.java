package org.kylan1940.thebridge.arena;

import org.junit.Test;
import org.kylan1940.thebridge.match.MatchTeam;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ArenaTest {

    private Arena readyArena() {
        Arena arena = new Arena("test", "world");
        arena.setLobby(new Point("world", 0, 100, 0, 0, 0));
        arena.setSpawn(MatchTeam.RED, new Point("world", -20, 64, 0, 0, 0));
        arena.setSpawn(MatchTeam.BLUE, new Point("world", 20, 64, 0, 0, 0));
        arena.setGoal(MatchTeam.RED, new Region(-30, 60, -2, -26, 66, 2));
        arena.setGoal(MatchTeam.BLUE, new Region(26, 60, -2, 30, 66, 2));
        arena.setBounds(new Region(-35, 40, -15, 35, 90, 15));
        return arena;
    }

    @Test
    public void completeArenaHasNoProblems() {
        assertTrue(readyArena().getProblems(true).isEmpty());
    }

    @Test
    public void emptyArenaListsEverythingMissing() {
        Arena arena = new Arena("empty", "world");
        List<String> problems = arena.getProblems(true);
        // lobby, 2 spawns, 2 goals, bounds
        assertEquals(6, problems.size());
    }

    @Test
    public void unloadedWorldIsReported() {
        List<String> problems = readyArena().getProblems(false);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("not loaded"));
    }

    @Test
    public void spawnOutsideBoundsIsReported() {
        Arena arena = readyArena();
        arena.setSpawn(MatchTeam.RED, new Point("world", -100, 64, 0, 0, 0));
        assertFalse(arena.getProblems(true).isEmpty());
    }

    @Test
    public void overlappingGoalsAreReported() {
        Arena arena = readyArena();
        arena.setGoal(MatchTeam.BLUE, arena.getGoal(MatchTeam.RED));
        assertFalse(arena.getProblems(true).isEmpty());
    }

    @Test
    public void spawnInsideGoalIsReported() {
        Arena arena = readyArena();
        arena.setSpawn(MatchTeam.RED, new Point("world", -28, 64, 0, 0, 0));
        assertFalse(arena.getProblems(true).isEmpty());
    }

    @Test
    public void maxPlayersFollowsTeamSize() {
        Arena arena = new Arena("test", "world");
        arena.setTeamSize(2);
        assertEquals(4, arena.getMaxPlayers());
    }

    // ---- state machine ----

    @Test
    public void normalMatchCycleIsAllowed() {
        Arena arena = new Arena("test", "world");
        arena.setState(ArenaState.WAITING);
        arena.setState(ArenaState.STARTING);
        arena.setState(ArenaState.IN_GAME);
        arena.setState(ArenaState.ENDING);
        arena.setState(ArenaState.RESETTING);
        arena.setState(ArenaState.WAITING);
        assertEquals(ArenaState.WAITING, arena.getState());
    }

    @Test
    public void startingCanFallBackToWaiting() {
        Arena arena = new Arena("test", "world");
        arena.setState(ArenaState.WAITING);
        arena.setState(ArenaState.STARTING);
        arena.setState(ArenaState.WAITING);
        assertEquals(ArenaState.WAITING, arena.getState());
    }

    @Test
    public void illegalTransitionsThrow() {
        Arena arena = new Arena("test", "world");
        assertThrows(IllegalStateException.class, () -> arena.setState(ArenaState.IN_GAME));
        arena.setState(ArenaState.WAITING);
        assertThrows(IllegalStateException.class, () -> arena.setState(ArenaState.RESETTING));
    }

    @Test
    public void inGameCannotBeDisabledDirectly() {
        assertFalse(ArenaState.IN_GAME.canChangeTo(ArenaState.DISABLED));
        assertFalse(ArenaState.ENDING.canChangeTo(ArenaState.WAITING));
    }

    @Test
    public void onlyWaitingAndStartingAreJoinable() {
        for (ArenaState state : ArenaState.values()) {
            boolean expected = state == ArenaState.WAITING || state == ArenaState.STARTING;
            assertEquals(state.name(), expected, state.isJoinable());
        }
    }

    // ---- region ----

    @Test
    public void regionCornersAreInclusive() {
        Region region = new Region(0, 0, 0, 2, 2, 2);
        assertTrue(region.contains(0, 0, 0));
        assertTrue(region.contains(2.9, 2.9, 2.9));
        assertFalse(region.contains(3, 0, 0));
        assertFalse(region.contains(-0.1, 0, 0));
    }

    @Test
    public void regionFromCornersIgnoresOrder() {
        Region region = Region.of(new Point("w", 5, 5, 5, 0, 0), new Point("w", 1, 9, 3, 0, 0));
        assertEquals(new Region(1, 5, 3, 5, 9, 5), region);
    }

    @Test
    public void regionOverlapDetectsTouchingBlocks() {
        Region a = new Region(0, 0, 0, 5, 5, 5);
        assertTrue(a.overlaps(new Region(5, 5, 5, 8, 8, 8)));
        assertFalse(a.overlaps(new Region(6, 0, 0, 8, 5, 5)));
    }
}
