package org.kylan1940.thebridge.storage;

import org.kylan1940.thebridge.stats.MatchRecord;
import org.kylan1940.thebridge.stats.PlayerResult;
import org.kylan1940.thebridge.stats.PlayerStats;

import java.util.List;
import java.util.UUID;

public interface Storage {

    /** Creates the tables if they do not exist. Throws if the database cannot be used. */
    void initialize() throws StorageException;

    void saveMatch(MatchRecord record, List<PlayerResult> results) throws StorageException;

    /** Returns null when the player has no stats yet. */
    PlayerStats loadStats(UUID uuid) throws StorageException;

    /** Case-insensitive name lookup. Returns null when nobody with that name has stats. */
    PlayerStats loadStatsByName(String name) throws StorageException;

    void close();
}
