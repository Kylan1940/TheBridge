package org.kylan1940.thebridge.match;

import org.junit.Before;
import org.junit.Test;
import org.kylan1940.thebridge.arena.Arena;
import org.kylan1940.thebridge.arena.ArenaState;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchTest {

    private Arena arena;
    private Match match;
    private UUID alice;
    private UUID bob;

    /** A 1v1 match that is already running (round active). */
    @Before
    public void setUp() {
        arena = new Arena("test", "world");
        arena.setTeamSize(1);
        arena.setState(ArenaState.WAITING);
        match = new Match(arena, 3, 600);

        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        match.addPlayer(alice, "Alice");
        match.addPlayer(bob, "Bob");
    }

    private void startMatch() {
        match.assignTeams();
        arena.setState(ArenaState.STARTING);
        arena.setState(ArenaState.IN_GAME);
        match.startRound();
    }

    // ---- joining and teams ----

    @Test
    public void cannotJoinWhenFull() {
        assertTrue(match.isFull());
        assertFalse(match.addPlayer(UUID.randomUUID(), "Charlie"));
    }

    @Test
    public void cannotJoinTwice() {
        Arena bigArena = new Arena("big", "world");
        bigArena.setTeamSize(2);
        bigArena.setState(ArenaState.WAITING);
        Match bigMatch = new Match(bigArena, 3, 600);
        UUID id = UUID.randomUUID();
        assertTrue(bigMatch.addPlayer(id, "A"));
        assertFalse(bigMatch.addPlayer(id, "A"));
    }

    @Test
    public void cannotJoinOnceGameStarted() {
        startMatch();
        match.removePlayer(bob);
        assertFalse(match.addPlayer(UUID.randomUUID(), "Late"));
    }

    @Test
    public void oneVsOneGivesEachPlayerOneTeam() {
        match.assignTeams();
        assertEquals(MatchTeam.RED, match.getTeam(alice));
        assertEquals(MatchTeam.BLUE, match.getTeam(bob));
    }

    @Test
    public void twoVsTwoIsBalanced() {
        Arena bigArena = new Arena("big", "world");
        bigArena.setTeamSize(2);
        bigArena.setState(ArenaState.WAITING);
        Match bigMatch = new Match(bigArena, 3, 600);
        for (int i = 0; i < 4; i++) {
            bigMatch.addPlayer(UUID.randomUUID(), "P" + i);
        }
        bigMatch.assignTeams();
        assertEquals(2, bigMatch.getActivePlayers(MatchTeam.RED).size());
        assertEquals(2, bigMatch.getActivePlayers(MatchTeam.BLUE).size());
    }

    // ---- scoring ----

    @Test
    public void goalAddsAPointAndPausesTheRound() {
        startMatch();
        assertEquals(Match.GoalResult.SCORED, match.registerGoal(alice));
        assertEquals(1, match.getScore(MatchTeam.RED));
        assertEquals(1, match.getGoals(alice));
        assertTrue(match.isFrozen());
    }

    @Test
    public void duplicateGoalEventsCountOnlyOnce() {
        startMatch();
        assertEquals(Match.GoalResult.SCORED, match.registerGoal(alice));
        assertEquals(Match.GoalResult.IGNORED, match.registerGoal(alice));
        assertEquals(Match.GoalResult.IGNORED, match.registerGoal(alice));
        assertEquals(1, match.getScore(MatchTeam.RED));
    }

    @Test
    public void simultaneousGoalsFirstOneWins() {
        startMatch();
        assertEquals(Match.GoalResult.SCORED, match.registerGoal(alice));
        assertEquals(Match.GoalResult.IGNORED, match.registerGoal(bob));
        assertEquals(1, match.getScore(MatchTeam.RED));
        assertEquals(0, match.getScore(MatchTeam.BLUE));
    }

    @Test
    public void nextRoundAcceptsGoalsAgain() {
        startMatch();
        match.registerGoal(alice);
        match.startRound();
        assertEquals(Match.GoalResult.SCORED, match.registerGoal(bob));
    }

    @Test
    public void reachingScoreLimitWinsTheMatch() {
        startMatch();
        assertEquals(Match.GoalResult.SCORED, match.registerGoal(alice));
        match.startRound();
        assertEquals(Match.GoalResult.SCORED, match.registerGoal(alice));
        match.startRound();
        assertEquals(Match.GoalResult.MATCH_WON, match.registerGoal(alice));
        assertEquals(3, match.getScore(MatchTeam.RED));
    }

    @Test
    public void goalIsIgnoredBeforeTheGameStarts() {
        match.assignTeams();
        assertEquals(Match.GoalResult.IGNORED, match.registerGoal(alice));
    }

    @Test
    public void goalIsIgnoredAfterTheMatchFinished() {
        startMatch();
        match.finish(MatchTeam.RED);
        assertEquals(Match.GoalResult.IGNORED, match.registerGoal(alice));
    }

    @Test
    public void goalFromUnknownOrRespawningPlayerIsIgnored() {
        startMatch();
        assertEquals(Match.GoalResult.IGNORED, match.registerGoal(UUID.randomUUID()));
        match.setRespawning(alice, true);
        assertEquals(Match.GoalResult.IGNORED, match.registerGoal(alice));
    }

    @Test
    public void goalFromPlayerWhoLeftIsIgnored() {
        startMatch();
        match.removePlayer(alice);
        assertEquals(Match.GoalResult.IGNORED, match.registerGoal(alice));
    }

    // ---- time ----

    @Test
    public void clockOnlyRunsDuringAnActiveRound() {
        Arena shortArena = new Arena("short", "world");
        shortArena.setState(ArenaState.WAITING);
        Match shortMatch = new Match(shortArena, 3, 3);
        shortMatch.addPlayer(alice, "A");
        shortMatch.addPlayer(bob, "B");
        shortMatch.assignTeams();
        shortArena.setState(ArenaState.STARTING);
        shortArena.setState(ArenaState.IN_GAME);

        // Round not started yet (countdown): clock is paused.
        assertFalse(shortMatch.tickClock());
        assertEquals(3, shortMatch.getSecondsLeft());

        shortMatch.startRound();
        assertFalse(shortMatch.tickClock());
        assertFalse(shortMatch.tickClock());
        assertTrue(shortMatch.tickClock());
        assertEquals(0, shortMatch.getSecondsLeft());
    }

    @Test
    public void timeoutWinnerIsTheTeamWithMorePoints() {
        startMatch();
        match.registerGoal(alice);
        assertEquals(MatchTeam.RED, match.decideTimeoutWinner());
    }

    @Test
    public void timeoutWithEqualScoreIsADraw() {
        startMatch();
        match.registerGoal(alice);
        match.startRound();
        match.registerGoal(bob);
        assertNull(match.decideTimeoutWinner());
    }

    @Test
    public void countdownStopsAtZero() {
        match.setCountdown(2);
        assertEquals(1, match.tickCountdown());
        assertEquals(0, match.tickCountdown());
        assertEquals(0, match.tickCountdown());
    }

    // ---- leaving ----

    @Test
    public void leavingKeepsTheTeamForStats() {
        startMatch();
        match.removePlayer(alice);
        assertFalse(match.hasPlayer(alice));
        assertEquals(MatchTeam.RED, match.getTeam(alice));
        assertTrue(match.getParticipants().contains(alice));
    }

    @Test
    public void otherTeamWinsWhenOneTeamIsEmpty() {
        startMatch();
        assertNull(match.getForfeitWinner());
        match.removePlayer(alice);
        assertEquals(MatchTeam.BLUE, match.getForfeitWinner());
    }

    @Test
    public void nobodyWinsWhenEveryoneLeft() {
        startMatch();
        match.removePlayer(alice);
        match.removePlayer(bob);
        assertNull(match.getForfeitWinner());
        assertTrue(match.isEmpty());
    }

    @Test
    public void killsAndDeathsAreCounted() {
        startMatch();
        match.addKill(alice);
        match.addDeath(bob);
        match.addDeath(bob);
        assertEquals(1, match.getKills(alice));
        assertEquals(2, match.getDeaths(bob));
    }
}
