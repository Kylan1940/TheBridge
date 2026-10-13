package org.kylan1940.thebridge.arena;

public record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

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

    public boolean overlaps(Region other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public Region expand(int n) {
        return new Region(minX - n, minY - n, minZ - n, maxX + n, maxY + n, maxZ + n);
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
