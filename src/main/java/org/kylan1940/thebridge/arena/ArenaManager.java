package org.kylan1940.thebridge.arena;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.kylan1940.thebridge.match.MatchTeam;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Keeps all arenas in memory and saves each one to its own file: plugins/TheBridge/arenas/<name>.yml
 * (arena settings are separate from config.yml on purpose, because every arena is different).
 *
 * Also remembers the pos1 / pos2 selections admins make before /thebridge setgoal and setbounds.
 */
public class ArenaManager {

    private final JavaPlugin plugin;
    private final File arenaFolder;

    /** Key is the lower case arena name, so "Lava" and "lava" are the same arena. */
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    /** pos1 and pos2 of every admin who is setting up an arena. Index 0 = pos1, 1 = pos2. */
    private final Map<UUID, Point[]> selections = new HashMap<>();

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.arenaFolder = new File(plugin.getDataFolder(), "arenas");
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    public Arena get(String name) {
        if (name == null) {
            return null;
        }
        return arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Arena> getAll() {
        return arenas.values();
    }

    /** Names are used as file names, so only safe characters are allowed. */
    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    /**
     * The arena (that is not disabled) whose bounds contain this position, or null.
     * Block listeners use this to protect arenas that are in use.
     */
    public Arena getArenaAt(String worldName, double x, double y, double z) {
        for (Arena arena : arenas.values()) {
            if (arena.getState() == ArenaState.DISABLED || arena.getBounds() == null) {
                continue;
            }
            if (arena.getWorldName().equals(worldName) && arena.getBounds().contains(x, y, z)) {
                return arena;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Create / delete
    // ------------------------------------------------------------------

    /** Returns null if the name is invalid or already used. */
    public Arena create(String name, String worldName) {
        if (!isValidName(name) || get(name) != null) {
            return null;
        }
        Arena arena = new Arena(name, worldName);
        arenas.put(name.toLowerCase(Locale.ROOT), arena);
        save(arena);
        return arena;
    }

    public void delete(Arena arena) {
        arenas.remove(arena.getName().toLowerCase(Locale.ROOT));
        File file = fileFor(arena.getName());
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + file.getName());
        }
    }

    // ------------------------------------------------------------------
    // Selections (pos1 / pos2)
    // ------------------------------------------------------------------

    public void setSelection(UUID admin, int index, Point point) {
        selections.computeIfAbsent(admin, key -> new Point[2])[index] = point;
    }

    /** Returns the region between pos1 and pos2, or null if one of them is missing or they are in different worlds. */
    public Region getSelectedRegion(UUID admin, String worldName) {
        Point[] points = selections.get(admin);
        if (points == null || points[0] == null || points[1] == null) {
            return null;
        }
        if (!worldName.equals(points[0].world()) || !worldName.equals(points[1].world())) {
            return null;
        }
        return Region.of(points[0], points[1]);
    }

    /** Called when an admin leaves the server, so the map does not grow forever. */
    public void clearSelection(UUID admin) {
        selections.remove(admin);
    }

    // ------------------------------------------------------------------
    // Saving and loading
    // ------------------------------------------------------------------

    public void save(Arena arena) {
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("world", arena.getWorldName());
        yaml.set("enabled", arena.isEnabled());
        yaml.set("team-size", arena.getTeamSize());
        yaml.set("score-limit", arena.getScoreLimit());
        yaml.set("duration", arena.getDurationSeconds());

        writePoint(yaml, "lobby", arena.getLobby());
        writePoint(yaml, "spawns.red", arena.getSpawn(MatchTeam.RED));
        writePoint(yaml, "spawns.blue", arena.getSpawn(MatchTeam.BLUE));
        writeRegion(yaml, "goals.red", arena.getGoal(MatchTeam.RED));
        writeRegion(yaml, "goals.blue", arena.getGoal(MatchTeam.BLUE));
        writeRegion(yaml, "bounds", arena.getBounds());

        try {
            arenaFolder.mkdirs();
            yaml.save(fileFor(arena.getName()));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arena " + arena.getName(), exception);
        }
    }

    /** Loads every arenas/*.yml. Arenas start DISABLED; TheBridge activates enabled ones once all worlds are loaded. */
    public void loadAll() {
        arenas.clear();
        arenaFolder.mkdirs();

        File[] files = arenaFolder.listFiles((dir, fileName) -> fileName.endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - 4);
            try {
                Arena arena = load(name, YamlConfiguration.loadConfiguration(file));
                arenas.put(name.toLowerCase(Locale.ROOT), arena);
            } catch (RuntimeException exception) {
                // One broken file must not stop the other arenas from loading.
                plugin.getLogger().log(Level.SEVERE, "Could not load arena file " + file.getName(), exception);
            }
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    private Arena load(String name, YamlConfiguration yaml) {
        Arena arena = new Arena(name, yaml.getString("world", Bukkit.getWorlds().get(0).getName()));

        arena.setEnabled(yaml.getBoolean("enabled", false));
        arena.setTeamSize(Math.max(1, yaml.getInt("team-size", 1)));
        arena.setScoreLimit(Math.max(0, yaml.getInt("score-limit", 0)));
        arena.setDurationSeconds(Math.max(0, yaml.getInt("duration", 0)));

        arena.setLobby(readPoint(yaml.getConfigurationSection("lobby")));
        arena.setSpawn(MatchTeam.RED, readPoint(yaml.getConfigurationSection("spawns.red")));
        arena.setSpawn(MatchTeam.BLUE, readPoint(yaml.getConfigurationSection("spawns.blue")));
        arena.setGoal(MatchTeam.RED, readRegion(yaml.getConfigurationSection("goals.red")));
        arena.setGoal(MatchTeam.BLUE, readRegion(yaml.getConfigurationSection("goals.blue")));
        arena.setBounds(readRegion(yaml.getConfigurationSection("bounds")));
        return arena;
    }

    private File fileFor(String arenaName) {
        return new File(arenaFolder, arenaName + ".yml");
    }

    private void writePoint(YamlConfiguration yaml, String path, Point point) {
        if (point == null) {
            return;
        }
        yaml.set(path + ".world", point.world());
        yaml.set(path + ".x", point.x());
        yaml.set(path + ".y", point.y());
        yaml.set(path + ".z", point.z());
        yaml.set(path + ".yaw", (double) point.yaw());
        yaml.set(path + ".pitch", (double) point.pitch());
    }

    private Point readPoint(ConfigurationSection section) {
        if (section == null || !section.contains("world")) {
            return null;
        }
        return new Point(
                section.getString("world"),
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    private void writeRegion(YamlConfiguration yaml, String path, Region region) {
        if (region == null) {
            return;
        }
        yaml.set(path + ".min-x", region.minX());
        yaml.set(path + ".min-y", region.minY());
        yaml.set(path + ".min-z", region.minZ());
        yaml.set(path + ".max-x", region.maxX());
        yaml.set(path + ".max-y", region.maxY());
        yaml.set(path + ".max-z", region.maxZ());
    }

    private Region readRegion(ConfigurationSection section) {
        if (section == null || !section.contains("min-x")) {
            return null;
        }
        return new Region(
                section.getInt("min-x"), section.getInt("min-y"), section.getInt("min-z"),
                section.getInt("max-x"), section.getInt("max-y"), section.getInt("max-z")
        );
    }

    /** Names of all arenas, for tab completion. */
    public List<String> getNames() {
        List<String> names = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            names.add(arena.getName());
        }
        return names;
    }
}
