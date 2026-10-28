package org.kylan1940.thebridge.player;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.kylan1940.thebridge.match.MatchTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prepares a player for the lobby or for a round: clears them completely and gives the
 * starting equipment from config.yml (equipment.items and equipment.armor).
 *
 * In an item "material" you can write {team}. It becomes RED or BLUE, so
 * {team}_TERRACOTTA gives every team its own coloured blocks.
 */
public class EquipmentGiver {

    /** One entry of equipment.items, already checked. */
    private static final class ItemSpec {
        private final String material;
        private final int amount;
        private final int slot;
        private final boolean unbreakable;

        private ItemSpec(String material, int amount, int slot, boolean unbreakable) {
            this.material = material;
            this.amount = amount;
            this.slot = slot;
            this.unbreakable = unbreakable;
        }

        private String material() {
            return material;
        }

        private int amount() {
            return amount;
        }

        private int slot() {
            return slot;
        }

        private boolean unbreakable() {
            return unbreakable;
        }
    }

    private final JavaPlugin plugin;
    private final List<ItemSpec> items = new ArrayList<>();
    private boolean giveArmor;
    private boolean dyeArmor;

    public EquipmentGiver(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Reads equipment from config.yml. Called on startup and on /thebridge reload. */
    public void load() {
        items.clear();
        giveArmor = plugin.getConfig().getBoolean("equipment.armor.enabled", true);
        dyeArmor = plugin.getConfig().getBoolean("equipment.armor.dye-by-team", true);

        for (Map<?, ?> entry : plugin.getConfig().getMapList("equipment.items")) {
            Object materialValue = entry.get("material");
            if (materialValue == null) {
                plugin.getLogger().warning("config.yml: an equipment item has no 'material'. Ignored.");
                continue;
            }
            String material = materialValue.toString();

            // Check the name with a real team so typos show up at startup, not in the middle of a match.
            Material test = Material.matchMaterial(material.replace("{team}", "RED"));
            if (test == null || !test.isItem() || test == Material.AIR || test == Material.CAVE_AIR || test == Material.VOID_AIR) {
                plugin.getLogger().warning("config.yml: equipment item '" + material + "' is not a valid item. Ignored.");
                continue;
            }

            items.add(new ItemSpec(
                    material,
                    readInt(entry.get("amount"), 1),
                    readInt(entry.get("slot"), -1),
                    Boolean.parseBoolean(String.valueOf(entry.get("unbreakable")))
            ));
        }
    }

    private int readInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return fallback;
    }

    /** Lobby: empty inventory, full health, adventure mode (cannot build or break anything). */
    public void prepareForLobby(Player player) {
        resetPlayer(player);
        player.setGameMode(GameMode.ADVENTURE);
    }

    /** Round: empty inventory, full health, survival mode and the configured equipment. */
    public void giveRoundEquipment(Player player, MatchTeam team) {
        resetPlayer(player);
        player.setGameMode(GameMode.SURVIVAL);

        PlayerInventory inventory = player.getInventory();

        for (ItemSpec spec : items) {
            Material material = Material.matchMaterial(spec.material().replace("{team}", team.name()));
            if (material == null) {
                continue;
            }
            ItemStack stack = new ItemStack(material, Math.max(1, Math.min(spec.amount(), material.getMaxStackSize())));

            if (spec.unbreakable()) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setUnbreakable(true);
                    stack.setItemMeta(meta);
                }
            }

            if (spec.slot() >= 0 && spec.slot() < 36) {
                inventory.setItem(spec.slot(), stack);
            } else {
                inventory.addItem(stack);
            }
        }

        if (giveArmor) {
            inventory.setHelmet(armorPiece(Material.LEATHER_HELMET, team));
            inventory.setChestplate(armorPiece(Material.LEATHER_CHESTPLATE, team));
            inventory.setLeggings(armorPiece(Material.LEATHER_LEGGINGS, team));
            inventory.setBoots(armorPiece(Material.LEATHER_BOOTS, team));
        }
        player.updateInventory();
    }

    private ItemStack armorPiece(Material material, MatchTeam team) {
        ItemStack piece = new ItemStack(material);
        ItemMeta meta = piece.getItemMeta();
        if (meta == null) {
            return piece;
        }
        meta.setUnbreakable(true);
        if (dyeArmor && meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(team == MatchTeam.RED ? Color.RED : Color.BLUE);
        }
        piece.setItemMeta(meta);
        return piece;
    }

    @SuppressWarnings("deprecation") // getMaxHealth() works on every 1.21 version
    private void resetPlayer(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setLevel(0);
        player.setExp(0f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }
}
