package org.kylan1940.thebridge.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.kylan1940.thebridge.TheBridge;
import org.kylan1940.thebridge.arena.Arena;
import org.kylan1940.thebridge.arena.ArenaManager;
import org.kylan1940.thebridge.arena.ArenaState;
import org.kylan1940.thebridge.match.MatchManager;
import org.kylan1940.thebridge.match.MatchTeam;
import org.kylan1940.thebridge.message.MessageUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SetupMenu implements InventoryHolder {

    // The menu is only buttons. Every button runs the normal /thebridge command, so the rules
    // (permission, "arena must be disabled", messages) are exactly the same as typing the command.

    private static final int SIZE = 36;

    private static final int SLOT_INFO = 4;
    private static final int SLOT_LOBBY = 10;
    private static final int SLOT_RED_SPAWN = 11;
    private static final int SLOT_BLUE_SPAWN = 12;
    private static final int SLOT_POS1 = 14;
    private static final int SLOT_POS2 = 15;
    private static final int SLOT_RED_GOAL = 19;
    private static final int SLOT_BLUE_GOAL = 20;
    private static final int SLOT_BOUNDS = 21;
    private static final int SLOT_TEAM_SIZE = 23;
    private static final int SLOT_SCORE_LIMIT = 24;
    private static final int SLOT_DURATION = 25;
    private static final int SLOT_TOGGLE = 31;
    private static final int SLOT_CLOSE = 35;

    private final TheBridge plugin;
    private final ArenaManager arenaManager;
    private final MatchManager matchManager;
    private final String arenaName;
    private final UUID viewer;
    private final Inventory inventory;

    public SetupMenu(TheBridge plugin, ArenaManager arenaManager, MatchManager matchManager, String arenaName, UUID viewer) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.matchManager = matchManager;
        this.arenaName = arenaName;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, SIZE, shortTitle(color("&6Setup: &e" + arenaName)));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        refresh(player);
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    public void refresh(Player player) {
        Arena arena = arenaManager.get(arenaName);
        if (arena == null) {
            return;
        }

        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, "&r ", Collections.<String>emptyList()));
        }

        boolean editable = arena.getState() == ArenaState.DISABLED;
        String lockedLine = editable ? "" : "&cDisable the arena first to change this.";

        inventory.setItem(SLOT_INFO, infoItem(arena));

        inventory.setItem(SLOT_LOBBY, item(Material.COMPASS, "&eLobby",
                lines("&7Click to set the lobby where you stand.", status(arena.getLobby() != null), lockedLine)));
        inventory.setItem(SLOT_RED_SPAWN, item(Material.RED_BED, "&cRed spawn",
                lines("&7Click to set the red spawn where you stand.", status(arena.getSpawn(MatchTeam.RED) != null), lockedLine)));
        inventory.setItem(SLOT_BLUE_SPAWN, item(Material.BLUE_BED, "&9Blue spawn",
                lines("&7Click to set the blue spawn where you stand.", status(arena.getSpawn(MatchTeam.BLUE) != null), lockedLine)));

        // The goals and the bounds use the box between position 1 and position 2.
        boolean hasSelection = arenaManager.getSelectedRegion(viewer, arena.getWorldName()) != null;
        String selectionLine = "&7Box selected: " + (hasSelection ? "&ayes" : "&cno");
        inventory.setItem(SLOT_POS1, item(Material.WOODEN_AXE, "&bPosition 1",
                lines("&7Click to set corner 1 where you stand.", selectionLine)));
        inventory.setItem(SLOT_POS2, item(Material.STONE_AXE, "&bPosition 2",
                lines("&7Click to set corner 2 where you stand.", selectionLine)));

        inventory.setItem(SLOT_RED_GOAL, item(Material.RED_CONCRETE, "&cRed goal",
                lines("&7Click to use the selected box as the red goal.", status(arena.getGoal(MatchTeam.RED) != null), lockedLine)));
        inventory.setItem(SLOT_BLUE_GOAL, item(Material.BLUE_CONCRETE, "&9Blue goal",
                lines("&7Click to use the selected box as the blue goal.", status(arena.getGoal(MatchTeam.BLUE) != null), lockedLine)));
        inventory.setItem(SLOT_BOUNDS, item(Material.GLASS, "&fArena bounds",
                lines("&7Click to use the selected box as the arena.", "&7The bottom of the box is the void.",
                        status(arena.getBounds() != null), lockedLine)));

        inventory.setItem(SLOT_TEAM_SIZE, item(Material.ARMOR_STAND, "&eTeam size",
                lines("&7Now: &f" + arena.getTeamSize() + "v" + arena.getTeamSize(),
                        "&7Left click: +1  Right click: -1", lockedLine)));
        inventory.setItem(SLOT_SCORE_LIMIT, item(Material.GOLD_INGOT, "&eScore limit",
                lines("&7Now: &f" + describeScore(arena), "&7Left click: +1  Right click: -1",
                        "&7Shift: 5 at a time. 0 = use config.yml", lockedLine)));
        inventory.setItem(SLOT_DURATION, item(Material.CLOCK, "&eMatch duration",
                lines("&7Now: &f" + describeDuration(arena), "&7Left click: +30s  Right click: -30s",
                        "&7Shift: 5 minutes at a time. 0 = use config.yml", lockedLine)));

        if (arena.isEnabled()) {
            inventory.setItem(SLOT_TOGGLE, item(Material.RED_WOOL, "&cDisable arena",
                    lines("&7The arena is open. Click to close it.", "&7A running match is cancelled.")));
        } else {
            inventory.setItem(SLOT_TOGGLE, item(Material.LIME_WOOL, "&aEnable arena",
                    lines("&7The arena is closed. Click to open it.", "&7It only opens when the setup is complete.")));
        }

        inventory.setItem(SLOT_CLOSE, item(Material.BARRIER, "&cClose", Collections.<String>emptyList()));
    }

    private ItemStack infoItem(Arena arena) {
        List<String> lore = new ArrayList<>();
        lore.add("&7State: &f" + arena.getState() + (arena.isEnabled() ? " &a(enabled)" : " &c(disabled)"));
        lore.add("&7World: &f" + arena.getWorldName());

        List<String> problems = arena.getProblems(Bukkit.getWorld(arena.getWorldName()) != null);
        if (problems.isEmpty()) {
            lore.add("&aSetup is complete.");
        } else {
            lore.add("&cStill missing (" + problems.size() + "):");
            // Only the first few, a tooltip that fills the whole screen helps nobody.
            for (int i = 0; i < Math.min(3, problems.size()); i++) {
                lore.add("&c- " + problems.get(i));
            }
        }
        return item(Material.PAPER, "&6" + arena.getName(), lore);
    }

    private String describeScore(Arena arena) {
        if (arena.getScoreLimit() == 0) {
            return "config.yml (" + matchManager.getScoreLimit(arena) + ")";
        }
        return String.valueOf(arena.getScoreLimit());
    }

    private String describeDuration(Arena arena) {
        if (arena.getDurationSeconds() == 0) {
            return "config.yml (" + matchManager.getDuration(arena) + "s)";
        }
        return arena.getDurationSeconds() + "s";
    }

    // ------------------------------------------------------------------
    // Clicking
    // ------------------------------------------------------------------

    public void handleClick(Player player, int slot, ClickType click) {
        // The player could have lost the permission, or the arena could have been deleted, while the menu was open.
        if (!player.hasPermission("thebridge.admin")) {
            player.closeInventory();
            MessageUtil.send(player, "messages.no-permission");
            return;
        }
        Arena arena = arenaManager.get(arenaName);
        if (arena == null) {
            player.closeInventory();
            MessageUtil.send(player, "messages.unknown-arena", "%arena%", arenaName);
            return;
        }
        String name = arena.getName();

        switch (slot) {
            // These save where the player stands, so the menu closes: the player has to walk to the next spot.
            case SLOT_LOBBY: {
                positionClick(player, "setlobby " + name);
                return;
            }
            case SLOT_RED_SPAWN: {
                positionClick(player, "setspawn " + name + " red");
                return;
            }
            case SLOT_BLUE_SPAWN: {
                positionClick(player, "setspawn " + name + " blue");
                return;
            }
            case SLOT_POS1: {
                positionClick(player, "pos1");
                return;
            }
            case SLOT_POS2: {
                positionClick(player, "pos2");
                return;
            }

            case SLOT_RED_GOAL:
                run(player, "setgoal " + name + " red");
                break;
            case SLOT_BLUE_GOAL:
                run(player, "setgoal " + name + " blue");
                break;
            case SLOT_BOUNDS:
                run(player, "setbounds " + name);
                break;

            case SLOT_TEAM_SIZE: {
                int next = arena.getTeamSize() + direction(click);
                next = Math.max(1, Math.min(plugin.getSettings().maxTeamSize, next));
                if (direction(click) != 0 && next != arena.getTeamSize()) {
                    run(player, "teamsize " + name + " " + next);
                }
                break;
            }
            case SLOT_SCORE_LIMIT: {
                int step = isShift(click) ? 5 : 1;
                int next = arena.getScoreLimit() + direction(click) * step;
                next = Math.max(0, Math.min(100, next));
                if (direction(click) != 0 && next != arena.getScoreLimit()) {
                    run(player, "scorelimit " + name + " " + next);
                }
                break;
            }
            case SLOT_DURATION: {
                int step = isShift(click) ? 300 : 30;
                int next = arena.getDurationSeconds() + direction(click) * step;
                // 0 means "use config.yml". Anything below 30 seconds is not allowed, so it snaps to 0 or 30.
                if (next <= 0) {
                    next = 0;
                } else if (next < 30) {
                    next = 30;
                }
                next = Math.min(86400, next);
                if (direction(click) != 0 && next != arena.getDurationSeconds()) {
                    run(player, "duration " + name + " " + next);
                }
                break;
            }

            case SLOT_TOGGLE:
                run(player, (arena.isEnabled() ? "disable " : "enable ") + name);
                break;
            case SLOT_CLOSE: {
                player.closeInventory();
                return;
            }
            default: {
                return;
            }
        }

        // Draw again so the buttons show the new values.
        refresh(player);
    }

    // Servers before 1.14 refuse an inventory title longer than 32 characters, so a long arena name is cut.
    private static String shortTitle(String title) {
        if (title.length() <= 32) {
            return title;
        }
        String cut = title.substring(0, 32);
        // Do not leave half a colour code ("§" without its letter) at the end.
        if (cut.endsWith("\u00a7")) {
            cut = cut.substring(0, 31);
        }
        return cut;
    }

    private void positionClick(Player player, String command) {
        run(player, command);
        player.closeInventory();
        MessageUtil.sendText(player, "&7Walk to the next spot, then use &e/thebridge setup " + arenaName + " &7to continue.");
    }

    private void run(Player player, String command) {
        player.performCommand("thebridge " + command);
    }

    // Left click goes up, right click goes down, anything else does nothing.
    private int direction(ClickType click) {
        if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            return 1;
        }
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            return -1;
        }
        return 0;
    }

    private boolean isShift(ClickType click) {
        return click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private String status(boolean isSet) {
        return "&7Status: " + (isSet ? "&aset" : "&cnot set");
    }

    // Builds a lore list and skips empty lines.
    private List<String> lines(String... text) {
        List<String> result = new ArrayList<>();
        for (String line : text) {
            if (!line.isEmpty()) {
                result.add(line);
            }
        }
        return result;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(color(line));
            }
            meta.setLore(colored);
            item.setItemMeta(meta);
        }
        return item;
    }

    // "&r" first: item names and lore are italic by default, &r removes that.
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', "&r" + text);
    }
}
