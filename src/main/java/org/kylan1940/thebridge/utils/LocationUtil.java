package org.kylan1940.thebridge.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.kylan1940.thebridge.arena.Point;
import org.kylan1940.thebridge.reset.BlockKey;

/** Converts between the plugin's plain data classes and Bukkit's Location and Block. */
public final class LocationUtil {

    private LocationUtil() {
    }

    public static Point toPoint(Location location) {
        return new Point(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    /** Returns null when the world of the point is not loaded. */
    public static Location toLocation(Point point) {
        if (point == null) {
            return null;
        }
        World world = Bukkit.getWorld(point.world());
        if (world == null) {
            return null;
        }
        return new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }

    public static BlockKey toKey(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
