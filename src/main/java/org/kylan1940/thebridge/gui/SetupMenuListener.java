package org.kylan1940.thebridge.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.kylan1940.thebridge.TheBridge;

public class SetupMenuListener implements Listener {

    private final TheBridge plugin;

    public SetupMenuListener(TheBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        // Only our own menu. Every other inventory is left alone.
        if (!(event.getInventory().getHolder() instanceof SetupMenu menu)) {
            return;
        }
        // Nothing may be taken out, put in or moved, also not with shift click or the number keys.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // A click in the player's own inventory (the bottom part of the screen) does nothing.
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) {
            return;
        }

        int slot = event.getSlot();
        ClickType click = event.getClick();

        // One tick later: closing or redrawing an inventory inside its own click event is fragile.
        Bukkit.getScheduler().runTask(plugin, () -> menu.handleClick(player, slot, click));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SetupMenu) {
            event.setCancelled(true);
        }
    }
}
