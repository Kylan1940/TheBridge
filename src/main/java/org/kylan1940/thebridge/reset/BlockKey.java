package org.kylan1940.thebridge.reset;

import java.util.Objects;

/** The position of one block (world name plus block coordinates). Used as a map key. */
public final class BlockKey {

    private final String world;
    private final int x;
    private final int y;
    private final int z;

    public BlockKey(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        BlockKey that = (BlockKey) other;
        return Objects.equals(world, that.world)
                && x == that.x
                && y == that.y
                && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }

    @Override
    public String toString() {
        return "BlockKey[" + "world=" + world
                + ", x=" + x
                + ", y=" + y
                + ", z=" + z + "]";
    }
}
