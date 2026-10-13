package org.kylan1940.thebridge.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.kylan1940.thebridge.TheBridge;
import org.kylan1940.thebridge.arena.Arena;
import org.kylan1940.thebridge.arena.ArenaManager;
import org.kylan1940.thebridge.arena.ArenaState;
import org.kylan1940.thebridge.arena.Region;
import org.kylan1940.thebridge.match.Match;
import org.kylan1940.thebridge.match.MatchManager;
import org.kylan1940.thebridge.match.MatchTeam;
import org.kylan1940.thebridge.message.MessageUtil;
import org.kylan1940.thebridge.reset.ArenaResetter;

import java.util.Iterator;
import java.util.List;

public class BlockListener implements Listener {

    private final TheBridge plugin;
    private final ArenaManager arenaManager;
    private final MatchManager matchManager;
    private final ArenaResetter resetter;

    public BlockListener(TheBridge plugin, ArenaManager arenaManager, MatchManager matchManager, ArenaResetter resetter) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.matchManager = matchManager;
        this.resetter = resetter;
    }

    // ------------------------------------------------------------------
    // Placing
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        Match match = matchManager.getMatch(player.getUniqueId());

        if (match != null) {
            boolean allowedMaterial = plugin.getSettings().allowedBlocks.contains(block.getType());
            if (!allowedMaterial || !mayChangeBlock(match, player, block)) {
                event.setCancelled(true);
                MessageUtil.actionBar(player, "messages.cannot-build");
            }
            return;
        }
        denyOutsider(player, block, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaceRecord(BlockPlaceEvent event) {
        Match match = matchManager.getMatch(event.getPlayer().getUniqueId());
        if (match != null) {
            resetter.recordPlaced(match.getArena(), event.getBlockPlaced(), event.getBlockReplacedState().getBlockData());
        }
    }

    // ------------------------------------------------------------------
    // Breaking
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Match match = matchManager.getMatch(player.getUniqueId());

        if (match != null) {
            boolean breakable = !plugin.getSettings().breakOnlyPlaced || resetter.isPlaced(match.getArena(), block);
            if (!breakable || !mayChangeBlock(match, player, block)) {
                event.setCancelled(true);
                MessageUtil.actionBar(player, "messages.cannot-build");
                return;
            }
            // No item drops: players already carry their blocks, and drops would have to be cleaned up.
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }
        denyOutsider(player, block, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakRecord(BlockBreakEvent event) {
        Match match = matchManager.getMatch(event.getPlayer().getUniqueId());
        if (match != null) {
            // At MONITOR the block has not been removed yet, so we still see what it looked like.
            resetter.recordBroken(match.getArena(), event.getBlock());
        }
    }

    // ------------------------------------------------------------------
    // Buckets (never allowed: water and lava cannot be restored)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        handleBucket(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        handleBucket(event.getPlayer(), event.getBlock(), event);
    }

    private void handleBucket(Player player, Block block, org.bukkit.event.Cancellable event) {
        if (matchManager.isInMatch(player.getUniqueId())) {
            event.setCancelled(true);
            MessageUtil.actionBar(player, "messages.cannot-build");
            return;
        }
        denyOutsider(player, block, event);
    }

    // ------------------------------------------------------------------
    // Things that would change blocks without a player placing them
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosion(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosion(event.blockList());
    }

    private void filterExplosion(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Arena arena = arenaManager.getArenaAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
            if (arena == null) {
                continue;
            }
            if (arena.getState() == ArenaState.IN_GAME && resetter.isPlaced(arena, block)) {
                resetter.recordBroken(arena, block);
            } else {
                iterator.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (touchesArena(event.getBlock()) || touchesAny(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (touchesArena(event.getBlock()) || touchesAny(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /** Liquids must not flow in or into an arena (they could not be restored). */
    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (touchesArena(event.getBlock()) || touchesArena(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (touchesArena(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (touchesArena(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean mayChangeBlock(Match match, Player player, Block block) {
        Arena arena = match.getArena();

        if (arena.getState() != ArenaState.IN_GAME || match.isFrozen() || match.isRespawning(player.getUniqueId())) {
            return false;
        }
        if (!arena.getBounds().contains(block.getX(), block.getY(), block.getZ())) {
            return false;
        }
        if (plugin.getSettings().protectGoals) {
            for (MatchTeam team : MatchTeam.values()) {
                Region goal = arena.getGoal(team).expand(plugin.getSettings().goalMargin);
                if (goal.contains(block.getX(), block.getY(), block.getZ())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void denyOutsider(Player player, Block block, org.bukkit.event.Cancellable event) {
        Arena arena = arenaManager.getArenaAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (arena == null) {
            return;
        }
        boolean emptyArena = arena.getState() == ArenaState.WAITING && matchManager.getMatch(arena) == null;
        if (emptyArena && player.hasPermission("thebridge.bypass")) {
            return;
        }
        event.setCancelled(true);
        MessageUtil.actionBar(player, "messages.arena-protected");
    }

    private boolean touchesArena(Block block) {
        return arenaManager.getArenaAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()) != null;
    }

    private boolean touchesAny(List<Block> blocks) {
        for (Block block : blocks) {
            if (touchesArena(block)) {
                return true;
            }
        }
        return false;
    }
}
