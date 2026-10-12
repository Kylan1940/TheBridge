package org.kylan1940.thebridge;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.kylan1940.thebridge.arena.Arena;
import org.kylan1940.thebridge.arena.ArenaManager;
import org.kylan1940.thebridge.arena.ArenaState;
import org.kylan1940.thebridge.command.BridgeCommand;
import org.kylan1940.thebridge.command.BridgeTabCompleter;
import org.kylan1940.thebridge.config.GameSettings;
import org.kylan1940.thebridge.listener.BlockListener;
import org.kylan1940.thebridge.listener.PlayerListener;
import org.kylan1940.thebridge.match.MatchManager;
import org.kylan1940.thebridge.player.EquipmentGiver;
import org.kylan1940.thebridge.reset.ArenaResetter;
import org.kylan1940.thebridge.stats.StatsManager;
import org.kylan1940.thebridge.utils.ConfigUpdater;

import java.util.List;

/**
 * The main class only wires everything together (create the objects, register commands
 * and listeners) and shuts everything down again. The real work is done by the managers.
 */
public final class TheBridge extends JavaPlugin {

    private static TheBridge instance;

    private GameSettings settings;
    private ArenaManager arenaManager;
    private ArenaResetter arenaResetter;
    private EquipmentGiver equipmentGiver;
    private StatsManager statsManager;
    private MatchManager matchManager;

    @Override
    public void onEnable() {

        instance = this;

        saveDefaultConfig();
        ConfigUpdater.update(this);

        settings = new GameSettings(getConfig(), getLogger());

        equipmentGiver = new EquipmentGiver(this);
        equipmentGiver.load();

        statsManager = new StatsManager(this);
        statsManager.start();

        arenaManager = new ArenaManager(this);
        arenaManager.loadAll();

        arenaResetter = new ArenaResetter(this);
        arenaResetter.start();

        matchManager = new MatchManager(this, arenaResetter, equipmentGiver, statsManager);

        getServer().getPluginManager().registerEvents(new BlockListener(this, arenaManager, matchManager, arenaResetter), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, arenaManager, matchManager), this);

        getCommand("thebridge").setExecutor(new BridgeCommand(this, arenaManager, matchManager, statsManager));
        getCommand("thebridge").setTabCompleter(new BridgeTabCompleter(this, arenaManager));

        // Wait one tick: by then every other plugin (for example a world manager) has loaded its worlds,
        // so arena worlds can be found.
        getServer().getScheduler().runTask(this, this::activateArenas);

        getLogger().info("TheBridge enabled.");
    }

    @Override
    public void onDisable() {
        if (matchManager != null) {
            matchManager.shutdown();
        }
        if (arenaResetter != null) {
            arenaResetter.stop();
        }
        if (statsManager != null) {
            statsManager.shutdown();
        }
        getLogger().info("TheBridge disabled.");

        instance = null;
    }

    /**
     * Makes every arena that was enabled last time playable again. An arena whose setup is no
     * longer valid (for example its world is gone) stays disabled and the reason is logged.
     * If a reset journal exists the server crashed mid-match: the arena is restored first.
     */
    private void activateArenas() {
        for (Arena arena : arenaManager.getAll()) {
            if (!arena.isEnabled()) {
                continue;
            }

            World world = Bukkit.getWorld(arena.getWorldName());
            List<String> problems = arena.getProblems(world != null);
            if (!problems.isEmpty()) {
                getLogger().warning("Arena " + arena.getName() + " stays disabled: " + String.join(" ", problems));
                continue;
            }

            if (arenaResetter.hasJournal(arena)) {
                arena.forceState(ArenaState.RESETTING);
                arenaResetter.recover(arena, () -> arena.setState(ArenaState.WAITING));
            } else {
                arena.setState(ArenaState.WAITING);
            }
        }

        // Players that were online while the server crashed in the middle of a match (rare, but possible with /reload).
        for (Player player : Bukkit.getOnlinePlayers()) {
            matchManager.recoverAfterCrash(player);
        }
    }

    /** /thebridge reload */
    public void reload() {
        reloadConfig();
        settings = new GameSettings(getConfig(), getLogger());
        equipmentGiver.load();
    }

    public static TheBridge getInstance() {
        return instance;
    }

    public GameSettings getSettings() {
        return settings;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }
}
