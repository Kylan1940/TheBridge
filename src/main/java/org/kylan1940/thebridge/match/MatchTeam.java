package org.kylan1940.thebridge.match;

/** The two teams of a Bridge match. */
public enum MatchTeam {

    RED("&c", "Red"),
    BLUE("&9", "Blue");

    private final String colorCode;
    private final String displayName;

    MatchTeam(String colorCode, String displayName) {
        this.colorCode = colorCode;
        this.displayName = displayName;
    }

    /** The legacy colour code ("&c" or "&9"). */
    public String getColorCode() {
        return colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Coloured name, for example "&cRed". */
    public String getColoredName() {
        return colorCode + displayName;
    }

    public MatchTeam opposite() {
        return this == RED ? BLUE : RED;
    }

    /** Parses "red" or "blue" (any case). Returns null for anything else. */
    public static MatchTeam fromString(String text) {
        if (text == null) {
            return null;
        }
        for (MatchTeam team : values()) {
            if (team.name().equalsIgnoreCase(text)) {
                return team;
            }
        }
        return null;
    }
}
