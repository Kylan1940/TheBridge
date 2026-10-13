package org.kylan1940.thebridge.arena;

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
