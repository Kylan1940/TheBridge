package org.kylan1940.thebridge.arena;

import java.util.Objects;

/**
 * A saved position in a world.
 *
 * This is a plain Java class (no Bukkit types) so the arena data can be
 * tested without a running server. MatchManager converts it to a Bukkit
 * Location when a player has to be teleported.
 */
public final class Point {

    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public Point(String world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Point that = (Point) other;
        return Objects.equals(world, that.world)
                && Double.compare(x, that.x) == 0
                && Double.compare(y, that.y) == 0
                && Double.compare(z, that.z) == 0
                && Float.compare(yaw, that.yaw) == 0
                && Float.compare(pitch, that.pitch) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z, yaw, pitch);
    }

    @Override
    public String toString() {
        return "Point[" + "world=" + world
                + ", x=" + x
                + ", y=" + y
                + ", z=" + z
                + ", yaw=" + yaw
                + ", pitch=" + pitch + "]";
    }

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
