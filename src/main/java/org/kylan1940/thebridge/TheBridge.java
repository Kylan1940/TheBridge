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
import org.kylan1940.thebridge.gui.SetupMenuListener;
import org.kylan1940.thebridge.hook.TheBridgeExpansion;
import org.kylan1940.thebridge.listener.BlockListener;
import org.kylan1940.thebridge.listener.PlayerListener;
import org.kylan1940.thebridge.match.MatchManager;
import org.kylan1940.thebridge.player.EquipmentGiver;
import org.kylan1940.thebridge.reset.ArenaResetter;
import org.kylan1940.thebridge.stats.StatsManager;
import org.kylan1940.thebridge.utils.ConfigUpdater;

import java.util.List;

public final class TheBridge extends JavaPlugin {

    private static TheBridge instance;

    private GameSettings settings;
    private ArenaManager arenaManager;
    private ArenaResetter arenaResetter;
    private EquipmentGiver equipmentGiver;
    private StatsManager statsManager;
    private MatchManager matchManager;
    private TheBridgeExpansion placeholders;

    @Override
    public void onEnable() {

        // BlockData (used to restore the arena) exists since Minecraft 1.13. On an older server the plugin
        // would fail later with a confusing error, so it says clearly what is wrong and switches itself off.
        if (!isClassAvailable("org.bukkit.block.data.BlockData")) {
            getLogger().severe("TheBridge needs Minecraft 1.13 or newer. This server is too old, so the plugin is disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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

        getServer().getPluginManager().registerEvents(new SetupMenuListener(this), this);

        getCommand("thebridge").setExecutor(new BridgeCommand(this, arenaManager, matchManager, statsManager));
        getCommand("thebridge").setTabCompleter(new BridgeTabCompleter(this, arenaManager));

        // Wait one tick: by then every other plugin (for example a world manager) has loaded its worlds,
        // so arena worlds can be found.
        getServer().getScheduler().runTask(this, this::activateArenas);

        hookPlaceholderApi();

        getLogger().info("TheBridge enabled.");
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregister();
        }
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

    private boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

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

    // PlaceholderAPI is optional. The expansion class is only touched when the plugin is installed,
    // so the server does not need PlaceholderAPI to run TheBridge.
    private void hookPlaceholderApi() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholders = new TheBridgeExpansion(this, arenaManager, matchManager, statsManager);
        placeholders.register();
        getLogger().info("PlaceholderAPI found: %thebridge_...% placeholders are available.");
    }

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
