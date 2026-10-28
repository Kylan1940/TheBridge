package org.kylan1940.thebridge.reset;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.kylan1940.thebridge.TheBridge;
import org.kylan1940.thebridge.arena.Arena;
import org.kylan1940.thebridge.arena.Region;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Puts an arena back the way it was after a match.
 *
 * How it works (see BlockTracker): while a match runs, every block a player places or
 * breaks is written down together with its ORIGINAL state. At reset time those blocks
 * are set back, a few hundred per tick, so the server never lags.
 *
 * What is restored: the type and block state (rotation, waterlogged, ...) of changed blocks.
 * What is NOT restored (documented limitation):
 *   - items inside containers (containers are protected from players instead),
 *   - entities other than dropped items and arrows, which are simply removed,
 *   - fluid flow, fire spread and pistons are cancelled inside arenas by BlockListener, so they
 *     should never change blocks. Gravity blocks (sand, gravel) and redstone machines are NOT handled:
 *     do not put them in build.allowed-blocks,
 *   - changes made by other plugins or by commands.
 *
 * Crash safety: the list of changed blocks is saved to journals/<arena>.journal every few
 * seconds. If the server crashes mid-match, the arena is restored from the journal on the
 * next start. A crash in the 'few seconds' between two saves can leave the newest blocks behind.
 */
public class ArenaResetter {

    private final TheBridge plugin;
    private final File journalFolder;

    /** One tracker per arena (key = lower case arena name). */
    private final Map<String, BlockTracker> trackers = new HashMap<>();
    /** Restores that are running right now, so they can be cancelled on shutdown. */
    private final Map<String, BukkitTask> restoreTasks = new HashMap<>();

    private BukkitTask journalTask;

    public ArenaResetter(TheBridge plugin) {
        this.plugin = plugin;
        this.journalFolder = new File(plugin.getDataFolder(), "journals");
    }

    // ------------------------------------------------------------------
    // Recording changes (called by BlockListener)
    // ------------------------------------------------------------------

    private BlockTracker tracker(Arena arena) {
        return trackers.computeIfAbsent(arena.getName().toLowerCase(Locale.ROOT), key -> new BlockTracker());
    }

    /** replacedData is the block state that was there BEFORE the player placed the new block. */
    public void recordPlaced(Arena arena, Block block, BlockData replacedData) {
        tracker(arena).recordPlaced(keyOf(block), replacedData.getAsString());
    }

    public void recordBroken(Arena arena, Block block) {
        tracker(arena).recordBroken(keyOf(block), block.getBlockData().getAsString());
    }

    public boolean isPlaced(Arena arena, Block block) {
        return tracker(arena).isPlaced(keyOf(block));
    }

    private BlockKey keyOf(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    // ------------------------------------------------------------------
    // Restoring
    // ------------------------------------------------------------------

    /**
     * Restores all changed blocks of the arena, a few per tick, then runs whenDone.
     * whenDone always runs on the main thread. If nothing changed it runs immediately.
     */
    public void restore(Arena arena, Runnable whenDone) {
        String key = arena.getName().toLowerCase(Locale.ROOT);
        BlockTracker tracker = tracker(arena);

        removeLeftoverEntities(arena);

        if (tracker.isEmpty()) {
            deleteJournal(arena.getName());
            whenDone.run();
            return;
        }

        if (plugin.getSettings().debug) {
            plugin.getLogger().info("[debug] Restoring " + tracker.size() + " block(s) in arena " + arena.getName());
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                applyBatch(tracker.takeBatch(plugin.getSettings().blocksPerTick));

                if (tracker.isEmpty()) {
                    cancel();
                    restoreTasks.remove(key);
                    deleteJournal(arena.getName());
                    whenDone.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        restoreTasks.put(key, task);
    }

    /** Restores everything immediately (no batching). Only used when the server is shutting down. */
    public void restoreAllNow() {
        for (BukkitTask task : restoreTasks.values()) {
            task.cancel();
        }
        restoreTasks.clear();

        for (Map.Entry<String, BlockTracker> entry : trackers.entrySet()) {
            BlockTracker tracker = entry.getValue();
            while (!tracker.isEmpty()) {
                applyBatch(tracker.takeBatch(10000));
            }
            deleteJournal(entry.getKey());
        }
    }

    private void applyBatch(List<Map.Entry<BlockKey, String>> batch) {
        for (Map.Entry<BlockKey, String> entry : batch) {
            BlockKey key = entry.getKey();
            World world = Bukkit.getWorld(key.world());
            if (world == null) {
                plugin.getLogger().warning("Cannot restore a block: world '" + key.world() + "' is not loaded.");
                continue;
            }

            BlockData data;
            try {
                data = Bukkit.createBlockData(entry.getValue());
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Unknown block state '" + entry.getValue() + "'. Replacing it with air.");
                data = Bukkit.createBlockData("minecraft:air");
            }
            // false = do not trigger block updates, so restoring a torch next to a block never pops it off.
            world.getBlockAt(key.x(), key.y(), key.z()).setBlockData(data, false);
        }
    }

    /** Dropped items and arrows are not tracked, so they are removed. */
    private void removeLeftoverEntities(Arena arena) {
        Region bounds = arena.getBounds();
        World world = Bukkit.getWorld(arena.getWorldName());
        if (!plugin.getSettings().clearEntities || bounds == null || world == null) {
            return;
        }
        // The box is given as a centre plus half of its size on every axis (this works on every version).
        double halfX = (bounds.maxX() - bounds.minX() + 1) / 2.0;
        double halfY = (bounds.maxY() - bounds.minY() + 1) / 2.0;
        double halfZ = (bounds.maxZ() - bounds.minZ() + 1) / 2.0;
        Location centre = new Location(world, bounds.minX() + halfX, bounds.minY() + halfY, bounds.minZ() + halfZ);

        for (Entity entity : world.getNearbyEntities(centre, halfX, halfY, halfZ)) {
            if (entity instanceof Item || entity instanceof Projectile) {
                entity.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // Journals (crash recovery)
    // ------------------------------------------------------------------

    /** Starts the repeating task that saves the journals. */
    public void start() {
        long ticks = plugin.getSettings().journalIntervalSeconds * 20L;
        journalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveJournals, ticks, ticks);
    }

    public void stop() {
        if (journalTask != null) {
            journalTask.cancel();
            journalTask = null;
        }
    }

    /** Writes the journal of every arena whose tracker changed since the last save. */
    public void saveJournals() {
        for (Map.Entry<String, BlockTracker> entry : trackers.entrySet()) {
            BlockTracker tracker = entry.getValue();
            if (!tracker.isDirty()) {
                continue;
            }
            if (tracker.isEmpty()) {
                deleteJournal(entry.getKey());
                continue;
            }
            try {
                tracker.saveTo(journalFile(entry.getKey()));
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not save the reset journal of arena " + entry.getKey(), exception);
            }
        }
    }

    public boolean hasJournal(Arena arena) {
        return journalFile(arena.getName()).exists();
    }

    /** Loads the journal of an arena (left behind by a crash) and restores it. */
    public void recover(Arena arena, Runnable whenDone) {
        try {
            trackers.put(arena.getName().toLowerCase(Locale.ROOT), BlockTracker.loadFrom(journalFile(arena.getName())));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not read the reset journal of arena " + arena.getName(), exception);
            whenDone.run();
            return;
        }
        plugin.getLogger().info("Recovering arena " + arena.getName() + " from its reset journal after an unclean shutdown...");
        restore(arena, whenDone);
    }

    private File journalFile(String arenaName) {
        return new File(journalFolder, arenaName.toLowerCase(Locale.ROOT) + ".journal");
    }

    private void deleteJournal(String arenaName) {
        File file = journalFile(arenaName);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + file.getName());
        }
    }
}
