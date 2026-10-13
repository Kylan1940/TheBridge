package org.kylan1940.thebridge.match;

public enum MatchTeam {

    RED("&c", "Red"),
    BLUE("&9", "Blue");

    private final String colorCode;
    private final String displayName;

    MatchTeam(String colorCode, String displayName) {
        this.colorCode = colorCode;
        this.displayName = displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColoredName() {
        return colorCode + displayName;
    }

    public MatchTeam opposite() {
        return this == RED ? BLUE : RED;
    }

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
