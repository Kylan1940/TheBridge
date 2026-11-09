package org.kylan1940.thebridge.storage;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.kylan1940.thebridge.stats.MatchRecord;
import org.kylan1940.thebridge.stats.PlayerResult;
import org.kylan1940.thebridge.stats.PlayerStats;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Needs the sqlite-jdbc driver (test dependency in pom.xml). */
public class SQLiteStorageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private SQLiteStorage storage;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Before
    public void setUp() throws Exception {
        storage = new SQLiteStorage(folder.newFile("stats.db"));
        storage.initialize();
    }

    private MatchRecord record() {
        return new MatchRecord("arena", "RED", 3, 1, 120, System.currentTimeMillis());
    }

    @Test
    public void unknownPlayerHasNoStats() throws Exception {
        assertNull(storage.loadStats(UUID.randomUUID()));
        assertNull(storage.loadStatsByName("Nobody"));
    }

    @Test
    public void firstMatchCreatesTheRows() throws Exception {
        storage.saveMatch(record(), Arrays.asList(
                new PlayerResult(alice, "Alice", true, false, 3, 2, 1),
                new PlayerResult(bob, "Bob", false, true, 1, 1, 2)
        ));

        PlayerStats a = storage.loadStats(alice);
        assertEquals(1, a.wins());
        assertEquals(0, a.losses());
        assertEquals(3, a.goals());
        assertEquals(1, a.matchesPlayed());
        assertEquals(1, a.winStreak());
        assertEquals(1, a.bestWinStreak());

        PlayerStats b = storage.loadStats(bob);
        assertEquals(1, b.losses());
        assertEquals(0, b.winStreak());
    }

    @Test
    public void laterMatchesAreAddedUp() throws Exception {
        PlayerResult win = new PlayerResult(alice, "Alice", true, false, 3, 2, 1);
        storage.saveMatch(record(), Arrays.asList(win));
        storage.saveMatch(record(), Arrays.asList(win));

        PlayerStats a = storage.loadStats(alice);
        assertEquals(2, a.wins());
        assertEquals(6, a.goals());
        assertEquals(4, a.kills());
        assertEquals(2, a.matchesPlayed());
        assertEquals(2, a.winStreak());
    }

    @Test
    public void lossResetsStreakButKeepsBest() throws Exception {
        PlayerResult win = new PlayerResult(alice, "Alice", true, false, 0, 0, 0);
        PlayerResult loss = new PlayerResult(alice, "Alice", false, true, 0, 0, 0);
        storage.saveMatch(record(), Arrays.asList(win));
        storage.saveMatch(record(), Arrays.asList(win));
        storage.saveMatch(record(), Arrays.asList(loss));

        PlayerStats a = storage.loadStats(alice);
        assertEquals(0, a.winStreak());
        assertEquals(2, a.bestWinStreak());
    }

    @Test
    public void drawCountsAsPlayedAndKeepsTheStreak() throws Exception {
        PlayerResult win = new PlayerResult(alice, "Alice", true, false, 0, 0, 0);
        PlayerResult draw = new PlayerResult(alice, "Alice", false, false, 1, 0, 0);
        storage.saveMatch(record(), Arrays.asList(win));
        storage.saveMatch(record(), Arrays.asList(draw));

        PlayerStats a = storage.loadStats(alice);
        assertEquals(2, a.matchesPlayed());
        assertEquals(1, a.wins());
        assertEquals(0, a.losses());
        assertEquals(1, a.winStreak());
    }

    @Test
    public void lookupByNameIgnoresCase() throws Exception {
        storage.saveMatch(record(), Arrays.asList(new PlayerResult(alice, "Alice", true, false, 0, 0, 0)));
        assertEquals(alice, storage.loadStatsByName("aLiCe").uuid());
    }

    @Test
    public void nameChangeIsStored() throws Exception {
        storage.saveMatch(record(), Arrays.asList(new PlayerResult(alice, "OldName", true, false, 0, 0, 0)));
        storage.saveMatch(record(), Arrays.asList(new PlayerResult(alice, "NewName", true, false, 0, 0, 0)));
        assertEquals("NewName", storage.loadStats(alice).name());
    }

    @Test
    public void weirdNamesCannotBreakTheQuery() throws Exception {
        String evil = "x'; DROP TABLE player_stats; --";
        storage.saveMatch(record(), Arrays.asList(new PlayerResult(alice, evil, true, false, 0, 0, 0)));
        assertEquals(evil, storage.loadStats(alice).name());
    }
}
