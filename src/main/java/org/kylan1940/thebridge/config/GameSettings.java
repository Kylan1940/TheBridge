package org.kylan1940.thebridge.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The gameplay numbers from config.yml, read ONCE (at startup and on /thebridge reload).
 *
 * Why not read the config every time like MessageUtil does? Settings such as the
 * allowed blocks are needed on every block placement. Parsing the material names
 * again for every event would be wasteful, and this is also the one place where
 * bad values are checked and fixed with a warning in the console.
 */
public class GameSettings {

    // match
    public final int countdown;
    public final int roundCountdown;
    public final int duration;
    public final int scoreLimit;
    public final int respawnDelay;
    public final int endingDelay;
    public final int killCreditSeconds;
    public final int maxTeamSize;
    public final boolean friendlyFire;
    public final boolean soloTest;
    public final Set<String> allowedCommands;

    // build
    public final Set<Material> allowedBlocks;
    public final boolean breakOnlyPlaced;
    public final boolean protectGoals;

    // goal
    public final int goalMargin;

    // reset
    public final int blocksPerTick;
    public final boolean resetAfterScore;
    public final boolean clearEntities;
    public final int journalIntervalSeconds;

    // stats and rewards
    public final boolean statsEnabled;
    public final String databaseFile;
    public final boolean rewardsEnabled;
    public final List<String> winnerCommands;
    public final List<String> loserCommands;

    public final boolean debug;

    public GameSettings(FileConfiguration config, Logger logger) {
        countdown = number(config, logger, "match.countdown", 10, 1, 300);
        roundCountdown = number(config, logger, "match.round-countdown", 3, 0, 60);
        duration = number(config, logger, "match.duration", 900, 30, 86400);
        scoreLimit = number(config, logger, "match.score-limit", 5, 1, 100);
        respawnDelay = number(config, logger, "match.respawn-delay", 3, 0, 60);
        endingDelay = number(config, logger, "match.ending-delay", 8, 1, 120);
        killCreditSeconds = number(config, logger, "match.kill-credit-seconds", 10, 0, 120);
        maxTeamSize = number(config, logger, "match.max-team-size", 2, 1, 8);
        friendlyFire = config.getBoolean("match.friendly-fire", false);
        soloTest = config.getBoolean("match.solo-test", false);

        allowedCommands = new HashSet<>();
        for (String command : config.getStringList("match.allowed-commands")) {
            allowedCommands.add(command.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }

        allowedBlocks = readMaterials(config.getStringList("build.allowed-blocks"), logger);
        breakOnlyPlaced = config.getBoolean("build.break-only-placed", true);
        protectGoals = config.getBoolean("build.protect-goals", true);

        goalMargin = number(config, logger, "goal.margin", 0, 0, 5);

        blocksPerTick = number(config, logger, "reset.blocks-per-tick", 500, 10, 20000);
        resetAfterScore = config.getBoolean("reset.after-score", false);
        clearEntities = config.getBoolean("reset.clear-entities", true);
        journalIntervalSeconds = number(config, logger, "reset.journal-interval", 5, 1, 300);

        statsEnabled = config.getBoolean("stats.enabled", true);
        databaseFile = config.getString("stats.database-file", "stats.db");
        rewardsEnabled = config.getBoolean("rewards.enabled", false);
        winnerCommands = config.getStringList("rewards.winner-commands");
        loserCommands = config.getStringList("rewards.loser-commands");

        debug = config.getBoolean("debug", false);
    }

    /** Reads an int and keeps it between min and max. Out of range values are fixed and reported. */
    private static int number(FileConfiguration config, Logger logger, String path, int fallback, int min, int max) {
        int value = config.getInt(path, fallback);
        if (value < min || value > max) {
            int fixed = Math.max(min, Math.min(max, value));
            logger.warning("config.yml: '" + path + "' is " + value + " but must be between " + min + " and " + max
                    + ". Using " + fixed + ".");
            return fixed;
        }
        return value;
    }

    private static Set<Material> readMaterials(List<String> names, Logger logger) {
        Set<Material> materials = new HashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material == null || !material.isBlock()) {
                logger.warning("config.yml: build.allowed-blocks contains '" + name + "' which is not a block. Ignored.");
                continue;
            }
            materials.add(material);
        }
        return materials;
    }
}
