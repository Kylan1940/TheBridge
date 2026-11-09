package org.kylan1940.thebridge.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.kylan1940.thebridge.TheBridge;
import org.kylan1940.thebridge.arena.Arena;
import org.kylan1940.thebridge.arena.ArenaManager;
import org.kylan1940.thebridge.arena.ArenaState;
import org.kylan1940.thebridge.match.Match;
import org.kylan1940.thebridge.match.MatchManager;
import org.kylan1940.thebridge.match.MatchTeam;
import org.kylan1940.thebridge.stats.PlayerStats;
import org.kylan1940.thebridge.stats.StatsManager;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TheBridgeExpansion extends PlaceholderExpansion {

    // Only created when PlaceholderAPI is installed (see TheBridge.hookPlaceholderApi).
    //
    // Stats live in the database and are read in the background. A scoreboard asks for a placeholder
    // many times per second, so the answer is remembered for a few seconds and refreshed in the background.
    // The very first request of a player answers 0 while the stats are still loading.

    private static final long CACHE_MILLIS = 10_000L;
    private static final int CACHE_MAX_PLAYERS = 500;

    private final TheBridge plugin;
    private final ArenaManager arenaManager;
    private final MatchManager matchManager;
    private final StatsManager statsManager;

    // Other plugins may ask for placeholders from another thread, so these two are thread safe.
    private final Map<UUID, CachedStats> cache = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    private static final class CachedStats {
        private final PlayerStats stats;
        private final long time;

        private CachedStats(PlayerStats stats, long time) {
            this.stats = stats;
            this.time = time;
        }

        private PlayerStats stats() {
            return stats;
        }

        private long time() {
            return time;
        }
    }

    public TheBridgeExpansion(TheBridge plugin, ArenaManager arenaManager, MatchManager matchManager, StatsManager statsManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.matchManager = matchManager;
        this.statsManager = statsManager;
    }

    @Override
    public String getIdentifier() {
        return "thebridge";
    }

    @Override
    public String getAuthor() {
        return "Kylan1940";
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    // Stay registered when someone runs /papi reload.
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        try {
            return resolve(player, params.toLowerCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            // A placeholder must never break the scoreboard or chat of another plugin.
            return "";
        }
    }

    private String resolve(OfflinePlayer player, String params) {

        // ----- Server wide, no player needed -----

        if (params.equals("total_players")) {
            int total = 0;
            for (Arena arena : arenaManager.getAll()) {
                Match match = matchManager.getMatch(arena);
                total += match == null ? 0 : match.getPlayerCount();
            }
            return String.valueOf(total);
        }
        if (params.equals("arenas_available")) {
            int free = 0;
            for (Arena arena : arenaManager.getAll()) {
                Match match = matchManager.getMatch(arena);
                if (arena.getState().isJoinable() && (match == null || !match.isFull())) {
                    free++;
                }
            }
            return String.valueOf(free);
        }

        // %thebridge_arena_players_<arena>%, %thebridge_arena_max_<arena>%, %thebridge_arena_state_<arena>%
        if (params.startsWith("arena_")) {
            String rest = params.substring("arena_".length());
            int underscore = rest.indexOf('_');
            if (underscore < 1) {
                return null;
            }
            Arena arena = arenaManager.get(rest.substring(underscore + 1));
            if (arena == null) {
                return "";
            }
            Match match = matchManager.getMatch(arena);
            switch (rest.substring(0, underscore)) {
                case "players":
                    return String.valueOf(match == null ? 0 : match.getPlayerCount());
                case "max":
                    return String.valueOf(arena.getMaxPlayers());
                case "state":
                    return arena.getState().name();
                default:
                    return null;
            }
        }

        // ----- Lifetime statistics: %thebridge_stats_wins% and so on -----

        if (player == null) {
            return "";
        }
        if (params.startsWith("stats_")) {
            return statistic(player.getUniqueId(), params.substring("stats_".length()));
        }

        // ----- The match the player is in right now -----

        UUID uuid = player.getUniqueId();
        Match match = matchManager.getMatch(uuid);

        if (params.equals("in_game")) {
            return String.valueOf(match != null);
        }
        if (match == null) {
            // Not in a match: every other placeholder is empty, so scoreboards can hide the lines.
            return "";
        }

        MatchTeam team = match.getTeam(uuid);
        boolean playing = match.getArena().getState() == ArenaState.IN_GAME;

        switch (params) {
            case "arena":
                return match.getArena().getName();
            case "state":
                return match.getArena().getState().name();
            case "team":
                return team == null ? "" : team.getDisplayName();
            case "team_colored":
                return team == null ? "" : team.getColoredName();
            case "players":
                return String.valueOf(match.getPlayerCount());
            case "max_players":
                return String.valueOf(match.getArena().getMaxPlayers());
            case "score_red":
                return String.valueOf(match.getScore(MatchTeam.RED));
            case "score_blue":
                return String.valueOf(match.getScore(MatchTeam.BLUE));
            case "score_own":
                return team == null ? "0" : String.valueOf(match.getScore(team));
            case "score_enemy":
                return team == null ? "0" : String.valueOf(match.getScore(team.opposite()));
            case "score_limit":
                return String.valueOf(match.getScoreLimit());
            case "countdown":
                return String.valueOf(match.getCountdown());
            case "time_left":
                return String.valueOf(playing ? match.getSecondsLeft() : 0);
            case "time_left_formatted":
                return formatTime(playing ? match.getSecondsLeft() : 0);
            case "match_goals":
                return String.valueOf(match.getGoals(uuid));
            case "match_kills":
                return String.valueOf(match.getKills(uuid));
            case "match_deaths":
                return String.valueOf(match.getDeaths(uuid));
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------
    // Statistics
    // ------------------------------------------------------------------

    private String statistic(UUID uuid, String name) {
        PlayerStats stats = loadStats(uuid);

        int wins = stats == null ? 0 : stats.wins();
        int losses = stats == null ? 0 : stats.losses();
        int kills = stats == null ? 0 : stats.kills();
        int deaths = stats == null ? 0 : stats.deaths();

        switch (name) {
            case "wins":
                return String.valueOf(wins);
            case "losses":
                return String.valueOf(losses);
            case "goals":
                return String.valueOf(stats == null ? 0 : stats.goals());
            case "kills":
                return String.valueOf(kills);
            case "deaths":
                return String.valueOf(deaths);
            case "matches":
                return String.valueOf(stats == null ? 0 : stats.matchesPlayed());
            case "winstreak":
                return String.valueOf(stats == null ? 0 : stats.winStreak());
            case "beststreak":
                return String.valueOf(stats == null ? 0 : stats.bestWinStreak());
            // Same calculation as /thebridge stats: with 0 deaths the ratio is just the kills.
            case "kd":
                return String.format(Locale.ROOT, "%.2f", deaths == 0 ? (double) kills : (double) kills / deaths);
            case "winrate": {
                int games = wins + losses;
                return String.format(Locale.ROOT, "%.1f", games == 0 ? 0.0 : wins * 100.0 / games);
            }
            default:
                return null;
        }
    }

    // Returns what is known right now (or null) and starts a background refresh when it is old.
    private PlayerStats loadStats(UUID uuid) {
        if (!statsManager.isAvailable()) {
            return null;
        }
        CachedStats cached = cache.get(uuid);
        boolean old = cached == null || System.currentTimeMillis() - cached.time() > CACHE_MILLIS;

        // loading.add() is false when a refresh for this player is already running.
        if (old && loading.add(uuid)) {
            if (cache.size() > CACHE_MAX_PLAYERS) {
                cache.clear();
            }
            statsManager.loadByUuid(uuid,
                    result -> {
                        cache.put(uuid, new CachedStats(result, System.currentTimeMillis()));
                        loading.remove(uuid);
                    },
                    () -> loading.remove(uuid));
        }
        return cached == null ? null : cached.stats();
    }

    private String formatTime(int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
