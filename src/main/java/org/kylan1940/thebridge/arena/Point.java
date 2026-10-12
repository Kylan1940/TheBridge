package org.kylan1940.thebridge.arena;

/**
 * A saved position in a world.
 *
 * This is a plain Java class (no Bukkit types) so the arena data can be
 * tested without a running server. MatchManager converts it to a Bukkit
 * Location when a player has to be teleported.
 */
public record Point(String world, double x, double y, double z, float yaw, float pitch) {

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }
}
