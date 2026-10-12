package org.kylan1940.thebridge.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.kylan1940.thebridge.TheBridge;
import org.kylan1940.thebridge.arena.ArenaManager;
import org.kylan1940.thebridge.arena.ArenaState;
import org.kylan1940.thebridge.match.Match;
import org.kylan1940.thebridge.match.MatchManager;
import org.kylan1940.thebridge.message.MessageUtil;

import java.util.Locale;

/**
 * Everything that happens TO a player: moving (goals and the void), taking damage, dying,
 * quitting, dropping items, running commands...
 *
 * The listener contains almost no game logic. It checks "is this player in a match?" and then
 * asks MatchManager or Match what to do. Players who are not in a match are never affected.
 */
public class PlayerListener implements Listener {

    private final TheBridge plugin;
    private final ArenaManager arenaManager;
    private final MatchManager matchManager;

    public PlayerListener(TheBridge plugin, ArenaManager arenaManager, MatchManager matchManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.matchManager = matchManager;
    }

    // ------------------------------------------------------------------
    // Joining and leaving the server
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // One tick later, when the player is fully in the world. Gives back their items if the
        // server crashed while they were in a match.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                matchManager.recoverAfterCrash(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // A disconnect counts as leaving the match. The player gets their own state back right now,
        // so what the server saves for them is their normal inventory, not the game inventory.
        matchManager.leave(player);
        arenaManager.clearSelection(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Movement: goals, void and the frozen countdown
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // Most players are not in a match: leave immediately, this event fires very often.
        if (!matchManager.isInMatch(player.getUniqueId())) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (matchManager.handleMovement(player, event.getFrom(), to)) {
            event.setCancelled(true);
        }
    }

    /** No teleporting out of the arena with pearls, chorus fruit, commands or the spectator menu. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!matchManager.isInMatch(event.getPlayer().getUniqueId())) {
            return;
        }
        // Compared by name on purpose: chorus fruit was CHORUS_FRUIT in older Paper versions and
        // is CONSUMABLE_EFFECT in newer ones. Writing both names directly would not compile on one of them.
        // Teleports made by plugins (including TheBridge itself) are fine.
        switch (event.getCause().name()) {
            case "ENDER_PEARL", "CHORUS_FRUIT", "CONSUMABLE_EFFECT", "COMMAND", "SPECTATE" -> event.setCancelled(true);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // Damage and death
    // ------------------------------------------------------------------

    /**
     * All damage to players in a match goes through here.
     * (EntityDamageByEntityEvent is a kind of EntityDamageEvent, so one method handles both.)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Match match = matchManager.getMatch(victim.getUniqueId());
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity ? getAttacker(byEntity.getDamager()) : null;

        // Nobody from outside may hurt a player in a match, and players in a match may not hurt outsiders.
        if (match == null) {
            if (attacker != null && matchManager.isInMatch(attacker.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }

        // No damage in the lobby, during countdowns, while waiting to respawn or on the results screen.
        if (match.getArena().getState() != ArenaState.IN_GAME || match.isFrozen() || match.isRespawning(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (attacker != null) {
            // The attacker must be in the same match (a player from another arena cannot hurt this one).
            if (matchManager.getMatch(attacker.getUniqueId()) != match) {
                event.setCancelled(true);
                return;
            }
            boolean sameTeam = match.getTeam(attacker.getUniqueId()) == match.getTeam(victim.getUniqueId());
            if (sameTeam && !attacker.equals(victim) && !plugin.getSettings().friendlyFire) {
                event.setCancelled(true);
                return;
            }
            if (!attacker.equals(victim)) {
                matchManager.recordHit(victim, attacker);
            }
        }

        // Lethal damage: cancel it and run our own death. This way there is no death screen,
        // nothing is dropped and the player never leaves the arena.
        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            matchManager.handleDeath(victim, matchManager.findKiller(victim));
        }
    }

    private Player getAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /**
     * Safety net. Some deaths cannot be cancelled in onDamage (for example /kill). The player
     * still keeps everything, is brought back at once and then handled like a normal death.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!matchManager.isInMatch(player.getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.deathMessage(null);

        Player killer = matchManager.findKiller(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isDead()) {
                player.spigot().respawn();
            }
            matchManager.handleDeath(player, killer);
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location location = matchManager.getRespawnLocation(event.getPlayer());
        if (location != null) {
            event.setRespawnLocation(location);
        }
    }

    // ------------------------------------------------------------------
    // Keeping the game inventory intact
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (matchManager.isInMatch(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && matchManager.isInMatch(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && matchManager.isInMatch(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    /** Armour cannot be taken off. */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && matchManager.isInMatch(player.getUniqueId())
                && event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    /** Players in a match cannot open chests, doors and so on, and cannot trample things. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!matchManager.isInMatch(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            event.setCancelled(true);
            return;
        }
        Block clicked = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null && isUsableBlock(clicked)) {
            // Only the block use is denied, so placing a block against it still works.
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    private boolean isUsableBlock(Block block) {
        Material type = block.getType();
        return block.getState() instanceof Container
                || Tag.DOORS.isTagged(type)
                || Tag.TRAPDOORS.isTagged(type)
                || Tag.FENCE_GATES.isTagged(type)
                || Tag.BUTTONS.isTagged(type)
                || Tag.BEDS.isTagged(type)
                || Tag.ANVIL.isTagged(type)
                || type == Material.CRAFTING_TABLE
                || type == Material.ENCHANTING_TABLE
                || type == Material.ENDER_CHEST
                || type == Material.LEVER
                || type == Material.NOTE_BLOCK
                || type == Material.JUKEBOX;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (matchManager.isInMatch(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    /** During a match only /thebridge (and the commands listed in config.yml) can be used. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!matchManager.isInMatch(player.getUniqueId()) || player.hasPermission("thebridge.admin")) {
            return;
        }

        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        // "/minecraft:msg" and "/msg" are the same command.
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }

        if (isOwnCommand(label) || plugin.getSettings().allowedCommands.contains(label)) {
            return;
        }
        event.setCancelled(true);
        MessageUtil.send(player, "messages.command-blocked");
    }

    private boolean isOwnCommand(String label) {
        if (label.equals("thebridge")) {
            return true;
        }
        return plugin.getCommand("thebridge") != null && plugin.getCommand("thebridge").getAliases().contains(label);
    }
}