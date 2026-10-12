package org.kylan1940.thebridge.arena;

/**
 * A box (cuboid) made of whole blocks. Used for the arena bounds and the two goals.
 * Both corners are inclusive, so a region from 0,0,0 to 0,0,0 is exactly one block.
 */
public record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** Builds a region from any two opposite corners (the order does not matter). */
    public static Region of(Point a, Point b) {
        return new Region(
                Math.min(a.blockX(), b.blockX()),
                Math.min(a.blockY(), b.blockY()),
                Math.min(a.blockZ(), b.blockZ()),
                Math.max(a.blockX(), b.blockX()),
                Math.max(a.blockY(), b.blockY()),
                Math.max(a.blockZ(), b.blockZ())
        );
    }

    /** True when the block that contains this position is inside the region. */
    public boolean contains(double x, double y, double z) {
        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y);
        int blockZ = (int) Math.floor(z);

        return blockX >= minX && blockX <= maxX
                && blockY >= minY && blockY <= maxY
                && blockZ >= minZ && blockZ <= maxZ;
    }

    public boolean contains(Point point) {
        return contains(point.x(), point.y(), point.z());
    }

    /** True when the two regions share at least one block. */
    public boolean overlaps(Region other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /** A bigger region that is n blocks larger on every side (used for the goal margin setting). */
    public Region expand(int n) {
        return new Region(minX - n, minY - n, minZ - n, maxX + n, maxY + n, maxZ + n);
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
