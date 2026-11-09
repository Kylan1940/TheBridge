package org.kylan1940.thebridge.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.kylan1940.thebridge.match.MatchTeam;

/**
 * Reads item names from config.yml on Minecraft 1.8 to 1.12.
 *
 * Before 1.13 the colour of a block or item is stored in a number (the "data value"), not in its name.
 * So a red block is written like this in config.yml:
 *
 *   STONE                 plain material
 *   WOOL:14               material with a data value (14 = red wool)
 *   STAINED_CLAY:{team}   {team} becomes 14 for the red team and 11 for the blue team
 *
 * These numbers are the same for wool, stained clay, stained glass and carpet.
 */
public final class LegacyItems {

    private static final int RED_DATA = 14;
    private static final int BLUE_DATA = 11;

    private LegacyItems() {
    }

    /** The material part of "NAME" or "NAME:data". Returns null when the name does not exist. */
    public static Material materialOf(String spec) {
        if (spec == null) {
            return null;
        }
        String name = spec;
        int colon = spec.indexOf(':');
        if (colon >= 0) {
            name = spec.substring(0, colon);
        }
        return Material.matchMaterial(name.trim());
    }

    /** The data value part of "NAME:data". Returns 0 when there is none or when it is not a number. */
    public static short dataOf(String spec, MatchTeam team) {
        int colon = spec.indexOf(':');
        if (colon < 0) {
            return 0;
        }
        String text = spec.substring(colon + 1).trim();
        if (text.equalsIgnoreCase("{team}")) {
            return (short) (team == MatchTeam.RED ? RED_DATA : BLUE_DATA);
        }
        try {
            return Short.parseShort(text);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /** Builds the item. Returns null when the material does not exist or is air. */
    public static ItemStack create(String spec, MatchTeam team, int amount) {
        Material material = materialOf(spec);
        if (material == null || material == Material.AIR) {
            return null;
        }
        return new ItemStack(material, amount, dataOf(spec, team));
    }
}
