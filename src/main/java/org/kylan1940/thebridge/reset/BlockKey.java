package org.kylan1940.thebridge.reset;

/** The position of one block (world name plus block coordinates). Used as a map key. */
public record BlockKey(String world, int x, int y, int z) {
}
