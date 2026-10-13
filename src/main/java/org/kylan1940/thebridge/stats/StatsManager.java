package org.kylan1940.thebridge.stats;

import org.bukkit.Bukkit;
import org.kylan1940.thebridge.TheBridge;
import org.kylan1940.thebridge.match.Match;
import org.kylan1940.thebridge.match.MatchTeam;
import org.kylan1940.thebridge.storage.SQLiteStorage;
import org.kylan1940.thebridge.storage.Storage;
import org.kylan1940.thebridge.storage.StorageException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public class StatsManager {

    private final TheBridge plugin;
    private Storage storage;
    private ExecutorService executor;
    private volatile boolean available = false;

    public StatsManager(TheBridge plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getSettings().statsEnabled) {
            plugin.getLogger().info("Statistics are disabled in config.yml.");
            return;
        }

        File databaseFile = new File(plugin.getDataFolder(), plugin.getSettings().databaseFile);
        storage = new SQLiteStorage(databaseFile);

        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TheBridge-Storage");
            thread.setDaemon(true);
            return thread;
        });

        executor.submit(() -> {
            try {
                plugin.getDataFolder().mkdirs();
                storage.initialize();
                available = true;
                plugin.getLogger().info("SQLite database ready.");
            } catch (StorageException exception) {
                plugin.getLogger().log(Level.SEVERE, "Statistics are unavailable: " + exception.getMessage(), exception.getCause());
            }
        });
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Turns a finished match into database rows and saves them in the background.
     * The data is copied on the main thread first, so the background thread never touches the Match.
     *
     * @param winner null means a draw
     */
    public void recordMatch(Match match, MatchTeam winner) {
        if (executor == null) {
            return;
        }

        List<PlayerResult> results = new ArrayList<>();
        for (UUID uuid : match.getParticipants()) {
            MatchTeam team = match.getTeam(uuid);
            // Players who left early are on the losing side too (they gave up). In a draw nobody wins or loses.
            boolean won = winner != null && team == winner;
            boolean lost = winner != null && team != winner;

            results.add(new PlayerResult(
                    uuid,
                    match.getName(uuid),
                    won,
                    lost,
                    match.getGoals(uuid),
                    match.getKills(uuid),
                    match.getDeaths(uuid)
            ));
        }

        MatchRecord record = new MatchRecord(
                match.getArena().getName(),
                winner == null ? "DRAW" : winner.name(),
                match.getScore(MatchTeam.RED),
                match.getScore(MatchTeam.BLUE),
                match.getElapsedSeconds(),
                System.currentTimeMillis()
        );

        executor.submit(() -> {
            if (!available) {
                plugin.getLogger().warning("Match results were not saved because the database is unavailable.");
                return;
            }
            try {
                storage.saveMatch(record, results);
            } catch (StorageException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not save match results: " + exception.getMessage(), exception.getCause());
            }
        });
    }

    /**
     * Looks a player up by name. onResult runs on the main thread with the stats (or null if
     * the player has none). onError runs on the main thread if the database could not be read.
     */
    public void loadByName(String name, Consumer<PlayerStats> onResult, Runnable onError) {
        runQuery(() -> storage.loadStatsByName(name), onResult, onError);
    }

    public void loadByUuid(UUID uuid, Consumer<PlayerStats> onResult, Runnable onError) {
        runQuery(() -> storage.loadStats(uuid), onResult, onError);
    }

    private interface Query {
        PlayerStats run() throws StorageException;
    }

    private void runQuery(Query query, Consumer<PlayerStats> onResult, Runnable onError) {
        if (executor == null || !available) {
            onError.run();
            return;
        }
        executor.submit(() -> {
            PlayerStats stats = null;
            boolean failed = false;
            try {
                stats = query.run();
            } catch (StorageException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not load stats: " + exception.getMessage(), exception.getCause());
                failed = true;
            }

            // Bukkit may only be touched from the main thread, so hand the result back.
            if (!plugin.isEnabled()) {
                return;
            }
            PlayerStats finalStats = stats;
            boolean finalFailed = failed;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalFailed) {
                    onError.run();
                } else {
                    onResult.accept(finalStats);
                }
            });
        });
    }

    public void shutdown() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Statistics queue did not finish within 10 seconds.");
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        storage.close();
    }
}
