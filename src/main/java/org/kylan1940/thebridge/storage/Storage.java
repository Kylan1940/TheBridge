package org.kylan1940.thebridge.storage;

import org.kylan1940.thebridge.stats.MatchRecord;
import org.kylan1940.thebridge.stats.PlayerResult;
import org.kylan1940.thebridge.stats.PlayerStats;

import java.util.List;
import java.util.UUID;

/**
 * Where statistics are saved. Today there is only SQLiteStorage. A MySQL version can
 * implement this same interface later without touching the rest of the plugin.
 *
 * All methods block while they work. StatsManager only calls them from a background
 * thread, never from the main server thread.
 */
public interface Storage {

    /** Creates the tables if they do not exist. Throws if the database cannot be used. */
    void initialize() throws StorageException;

    /** Adds the results of one match to the lifetime stats and stores the match history entry (one transaction). */
    void saveMatch(MatchRecord record, List<PlayerResult> results) throws StorageException;

    /** Returns null when the player has no stats yet. */
    PlayerStats loadStats(UUID uuid) throws StorageException;

    /** Case-insensitive name lookup. Returns null when nobody with that name has stats. */
    PlayerStats loadStatsByName(String name) throws StorageException;

    void close();
}
