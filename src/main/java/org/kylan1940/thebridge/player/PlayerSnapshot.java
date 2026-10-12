package org.kylan1940.thebridge.player;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Everything about a player that TheBridge changes, saved before the match so it can be
 * put back afterwards: inventory (with armour and off hand), game mode, health, food,
 * experience, flying, potion effects and the location they came from.
 *
 * The snapshot is also written to disk (plugins/TheBridge/snapshots/<uuid>.yml). If the server
 * crashes during a match, the player is restored from that file the next time they join.
 *
 * Limitation: things TheBridge never touches (for example scoreboard, custom name tags
 * from other plugins, advancements) are not saved.
 */
public class PlayerSnapshot {

    private final ItemStack[] contents;
    private final GameMode gameMode;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final int level;
    private final float exp;
    private final boolean allowFlight;
    private final boolean flying;
    private final List<PotionEffect> effects;

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    private PlayerSnapshot(
            ItemStack[] contents, GameMode gameMode, double health, int foodLevel, float saturation,
            int level, float exp, boolean allowFlight, boolean flying, List<PotionEffect> effects,
            String worldName, double x, double y, double z, float yaw, float pitch
    ) {
        this.contents = contents;
        this.gameMode = gameMode;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.level = level;
        this.exp = exp;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.effects = effects;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** Takes a snapshot of the player as they are right now. */
    public static PlayerSnapshot capture(Player player) {
        Location location = player.getLocation();
        // clone() so later changes to the inventory do not change our copy.
        ItemStack[] contents = player.getInventory().getContents().clone();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                contents[i] = contents[i].clone();
            }
        }

        return new PlayerSnapshot(
                contents,
                player.getGameMode(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getLevel(),
                player.getExp(),
                player.getAllowFlight(),
                player.isFlying(),
                new ArrayList<>(player.getActivePotionEffects()),
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()
        );
    }

    /** Puts the player back the way they were. Also teleports them to where they came from. */
    @SuppressWarnings("deprecation") // getMaxHealth() works on every 1.21 version (the Attribute name changed in 1.21.3)
    public void restore(Player player) {
        // Clear what the game gave them first.
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0);

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        ItemStack[] restored = Arrays.copyOf(contents, Math.min(contents.length, inventory.getContents().length));
        inventory.setContents(restored);

        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.setHealth(Math.max(1.0, Math.min(health, player.getMaxHealth())));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(exp);

        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }

        // If the world was unloaded in the meantime, send them to the main world's spawn instead.
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            player.teleport(new Location(world, x, y, z, yaw, pitch));
        } else {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        player.updateInventory();
    }

    // ------------------------------------------------------------------
    // Disk copy (crash recovery)
    // ------------------------------------------------------------------

    public void saveTo(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("contents", Arrays.asList(contents));
        yaml.set("game-mode", gameMode.name());
        yaml.set("health", health);
        yaml.set("food", foodLevel);
        yaml.set("saturation", (double) saturation);
        yaml.set("level", level);
        yaml.set("exp", (double) exp);
        yaml.set("allow-flight", allowFlight);
        yaml.set("flying", flying);
        yaml.set("effects", effects);
        yaml.set("location.world", worldName);
        yaml.set("location.x", x);
        yaml.set("location.y", y);
        yaml.set("location.z", z);
        yaml.set("location.yaw", (double) yaw);
        yaml.set("location.pitch", (double) pitch);
        yaml.save(file);
    }

    /** Returns null if the file is missing or cannot be read. */
    public static PlayerSnapshot loadFrom(File file) {
        if (!file.exists()) {
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

            List<?> savedContents = yaml.getList("contents", new ArrayList<>());
            ItemStack[] contents = new ItemStack[savedContents.size()];
            for (int i = 0; i < contents.length; i++) {
                Object item = savedContents.get(i);
                contents[i] = item instanceof ItemStack stack ? stack : null;
            }

            List<PotionEffect> effects = new ArrayList<>();
            for (Object effect : yaml.getList("effects", new ArrayList<>())) {
                if (effect instanceof PotionEffect potionEffect) {
                    effects.add(potionEffect);
                }
            }

            return new PlayerSnapshot(
                    contents,
                    GameMode.valueOf(yaml.getString("game-mode", "SURVIVAL")),
                    yaml.getDouble("health", 20.0),
                    yaml.getInt("food", 20),
                    (float) yaml.getDouble("saturation", 5.0),
                    yaml.getInt("level", 0),
                    (float) yaml.getDouble("exp", 0.0),
                    yaml.getBoolean("allow-flight", false),
                    yaml.getBoolean("flying", false),
                    effects,
                    yaml.getString("location.world", Bukkit.getWorlds().get(0).getName()),
                    yaml.getDouble("location.x"),
                    yaml.getDouble("location.y"),
                    yaml.getDouble("location.z"),
                    (float) yaml.getDouble("location.yaw"),
                    (float) yaml.getDouble("location.pitch")
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
