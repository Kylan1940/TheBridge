package org.kylan1940.thebridge.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.kylan1940.thebridge.TheBridge;
import org.kylan1940.thebridge.arena.Arena;
import org.kylan1940.thebridge.arena.ArenaManager;
import org.kylan1940.thebridge.arena.ArenaState;
import org.kylan1940.thebridge.arena.Point;
import org.kylan1940.thebridge.arena.Region;
import org.kylan1940.thebridge.gui.SetupMenu;
import org.kylan1940.thebridge.match.Match;
import org.kylan1940.thebridge.match.MatchManager;
import org.kylan1940.thebridge.match.MatchTeam;
import org.kylan1940.thebridge.message.MessageUtil;
import org.kylan1940.thebridge.stats.PlayerStats;
import org.kylan1940.thebridge.stats.StatsManager;
import org.kylan1940.thebridge.utils.LocationUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class BridgeCommand implements CommandExecutor {

    public static final Set<String> ADMIN_COMMANDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "create", "setup", "delete", "setspawn", "setgoal", "setlobby", "setbounds", "pos1", "pos2",
            "teamsize", "scorelimit", "duration", "enable", "disable", "stop", "info", "reload"
    )));

    public static final Set<String> PLAYER_COMMANDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "join", "leave", "list", "stats", "help"
    )));

    private final TheBridge plugin;
    private final ArenaManager arenaManager;
    private final MatchManager matchManager;
    private final StatsManager statsManager;

    public BridgeCommand(
            TheBridge plugin,
            ArenaManager arenaManager,
            MatchManager matchManager,
            StatsManager statsManager
    ) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.matchManager = matchManager;
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (ADMIN_COMMANDS.contains(sub) && !sender.hasPermission("thebridge.admin")) {
            MessageUtil.send(sender, "messages.no-permission");
            return true;
        }

        switch (sub) {
            case "help":
                sendHelp(sender);
                break;
            case "list":
                list(sender);
                break;
            case "join":
                join(sender, args);
                break;
            case "leave":
                leave(sender);
                break;
            case "stats":
                stats(sender, args);
                break;

            case "create":
                create(sender, args);
                break;
            case "setup":
                setup(sender, args);
                break;
            case "delete":
                delete(sender, args);
                break;
            case "setlobby":
                setLobby(sender, args);
                break;
            case "setspawn":
                setSpawn(sender, args);
                break;
            case "setgoal":
                setGoal(sender, args);
                break;
            case "setbounds":
                setBounds(sender, args);
                break;
            case "pos1":
                setPosition(sender, 0);
                break;
            case "pos2":
                setPosition(sender, 1);
                break;
            case "teamsize":
                setTeamSize(sender, args);
                break;
            case "scorelimit":
                setScoreLimit(sender, args);
                break;
            case "duration":
                setDuration(sender, args);
                break;
            case "enable":
                enable(sender, args);
                break;
            case "disable":
                disable(sender, args);
                break;
            case "stop":
                stop(sender, args);
                break;
            case "info":
                info(sender, args);
                break;
            case "reload":
                reload(sender);
                break;

            default:
                MessageUtil.send(sender, "messages.unknown-command");
                break;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Commands for everybody
    // ------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        MessageUtil.send(sender, "messages.help-player");
        if (sender.hasPermission("thebridge.admin")) {
            MessageUtil.send(sender, "messages.help-admin");
        }
    }

    private void list(CommandSender sender) {
        if (!sender.hasPermission("thebridge.join") && !sender.hasPermission("thebridge.admin")) {
            MessageUtil.send(sender, "messages.no-permission");
            return;
        }
        if (arenaManager.getAll().isEmpty()) {
            MessageUtil.send(sender, "messages.no-arenas");
            return;
        }
        MessageUtil.send(sender, "messages.list-header");
        for (Arena arena : arenaManager.getAll()) {
            Match match = matchManager.getMatch(arena);
            int players = match == null ? 0 : match.getPlayerCount();
            MessageUtil.send(sender, "messages.list-entry",
                    "%arena%", arena.getName(),
                    "%state%", arena.getState().name(),
                    "%mode%", arena.getTeamSize() + "v" + arena.getTeamSize(),
                    "%players%", String.valueOf(players),
                    "%max%", String.valueOf(arena.getMaxPlayers()));
        }
    }

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, "messages.player-only");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("thebridge.join")) {
            MessageUtil.send(player, "messages.no-permission");
            return;
        }
        Arena arena;
        if (args.length < 2) {
            // No arena typed: pick one for the player.
            if (matchManager.isInMatch(player.getUniqueId())) {
                MessageUtil.send(player, "messages.already-in-match");
                return;
            }
            arena = pickRandomArena();
            if (arena == null) {
                MessageUtil.send(player, "messages.no-free-arena");
                return;
            }
        } else {
            arena = findArena(player, args[1]);
            if (arena == null) {
                return;
            }
        }

        MatchManager.JoinResult result = matchManager.join(player, arena);
        switch (result) {
            case JOINED:
                MessageUtil.send(player, "messages.joined-arena", "%arena%", arena.getName());
                break;
            case ALREADY_IN_MATCH:
                MessageUtil.send(player, "messages.already-in-match");
                break;
            case FULL:
                MessageUtil.send(player, "messages.arena-full");
                break;
            case UNAVAILABLE:
                MessageUtil.send(player, "messages.arena-unavailable", "%arena%", arena.getName());
                break;
        }
    }

    // A random arena that can be joined right now. Arenas that already have players come first, so two
    // players who both type /thebridge join end up together instead of waiting in two empty arenas.
    private Arena pickRandomArena() {
        List<Arena> withPlayers = new ArrayList<>();
        List<Arena> empty = new ArrayList<>();
        for (Arena arena : arenaManager.getAll()) {
            if (!arena.getState().isJoinable()) {
                continue;
            }
            Match match = matchManager.getMatch(arena);
            if (match != null && match.isFull()) {
                continue;
            }
            if (match != null && match.getPlayerCount() > 0) {
                withPlayers.add(arena);
            } else {
                empty.add(arena);
            }
        }
        List<Arena> choices = withPlayers.isEmpty() ? empty : withPlayers;
        if (choices.isEmpty()) {
            return null;
        }
        return choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, "messages.player-only");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("thebridge.leave")) {
            MessageUtil.send(player, "messages.no-permission");
            return;
        }
        // leave() sends the "you left" message itself.
        if (!matchManager.leave(player)) {
            MessageUtil.send(player, "messages.not-in-match");
        }
    }

    private void stats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("thebridge.stats")) {
            MessageUtil.send(sender, "messages.no-permission");
            return;
        }
        if (!statsManager.isAvailable()) {
            MessageUtil.send(sender, "messages.stats-unavailable");
            return;
        }

        MessageUtil.send(sender, "messages.stats-loading");

        // The database is read in the background. These two callbacks run on the main thread again.
        if (args.length >= 2) {
            String name = args[1];
            statsManager.loadByName(name,
                    result -> showStats(sender, name, result),
                    () -> MessageUtil.send(sender, "messages.stats-unavailable"));
            return;
        }

        if (!(sender instanceof Player)) {
            sendUsage(sender, "/thebridge stats <player>");
            return;
        }
        Player player = (Player) sender;
        statsManager.loadByUuid(player.getUniqueId(),
                result -> showStats(sender, player.getName(), result),
                () -> MessageUtil.send(sender, "messages.stats-unavailable"));
    }

    private void showStats(CommandSender sender, String requestedName, PlayerStats stats) {
        if (stats == null) {
            MessageUtil.send(sender, "messages.stats-none", "%player%", requestedName);
            return;
        }

        int games = stats.wins() + stats.losses();
        double winRate = games == 0 ? 0.0 : stats.wins() * 100.0 / games;
        // Kills divided by deaths. Nobody divides by zero: with 0 deaths the ratio is just the kills.
        double kd = stats.deaths() == 0 ? stats.kills() : (double) stats.kills() / stats.deaths();

        MessageUtil.send(sender, "messages.stats-format",
                "%player%", stats.name(),
                "%wins%", String.valueOf(stats.wins()),
                "%losses%", String.valueOf(stats.losses()),
                "%goals%", String.valueOf(stats.goals()),
                "%kills%", String.valueOf(stats.kills()),
                "%deaths%", String.valueOf(stats.deaths()),
                "%matches%", String.valueOf(stats.matchesPlayed()),
                "%streak%", String.valueOf(stats.winStreak()),
                "%best%", String.valueOf(stats.bestWinStreak()),
                "%kd%", String.format(Locale.ROOT, "%.2f", kd),
                "%winrate%", String.format(Locale.ROOT, "%.1f", winRate));
    }

    // ------------------------------------------------------------------
    // Setup commands (admin)
    // ------------------------------------------------------------------

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, "messages.player-only");
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            sendUsage(player, "/thebridge create <arena>");
            return;
        }
        // The arena lives in the world where the admin is standing.
        Arena arena = arenaManager.create(args[1], player.getWorld().getName());
        if (arena == null) {
            MessageUtil.send(player, "messages.arena-exists");
            return;
        }
        MessageUtil.send(player, "messages.arena-created",
                "%arena%", arena.getName(),
                "%world%", arena.getWorldName());
    }

    // Opens the setup menu. Every button inside runs one of the commands below.
    private void setup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, "messages.player-only");
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            sendUsage(player, "/thebridge setup <arena>");
            return;
        }
        Arena arena = findArena(player, args[1]);
        if (arena == null) {
            return;
        }
        new SetupMenu(plugin, arenaManager, matchManager, arena.getName(), player.getUniqueId()).open(player);
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/thebridge delete <arena>");
            return;
        }
        Arena arena = findArena(sender, args[1]);
        if (arena == null) {
            return;
        }
        // Safe cancellation: a running, resetting or enabled arena is never deleted directly.
        // /thebridge disable cancels the match, sends the players back and restores the blocks.
        if (arena.getState() != ArenaState.DISABLED || arena.isEnabled() || matchManager.getMatch(arena) != null) {
            MessageUtil.send(sender, "messages.arena-active", "%arena%", arena.getName());
            return;
        }
        arenaManager.delete(arena);
        MessageUtil.send(sender, "messages.arena-deleted", "%arena%", arena.getName());
    }

    private void setLobby(CommandSender sender, String[] args) {
        Arena arena = findEditableArena(sender, args, "/thebridge setlobby <arena>", 2);
        if (arena == null) {
            return;
        }
        Player player = (Player) sender;
        arena.setLobby(LocationUtil.toPoint(player.getLocation()));
        arenaManager.save(arena);
        MessageUtil.send(player, "messages.arena-saved", "%what%", "lobby of " + arena.getName());
    }

    private void setSpawn(CommandSender sender, String[] args) {
        Arena arena = findEditableArena(sender, args, "/thebridge setspawn <arena> <red|blue>", 3);
        if (arena == null) {
            return;
        }
        Player player = (Player) sender;
        MatchTeam team = MatchTeam.fromString(args[2]);
        if (team == null) {
            MessageUtil.send(player, "messages.invalid-team");
            return;
        }
        if (!isInArenaWorld(player, arena)) {
            return;
        }
        arena.setSpawn(team, LocationUtil.toPoint(player.getLocation()));
        arenaManager.save(arena);
        MessageUtil.send(player, "messages.arena-saved", "%what%", team.getDisplayName() + " spawn of " + arena.getName());
    }

    private void setGoal(CommandSender sender, String[] args) {
        Arena arena = findEditableArena(sender, args, "/thebridge setgoal <arena> <red|blue>", 3);
        if (arena == null) {
            return;
        }
        Player player = (Player) sender;
        MatchTeam team = MatchTeam.fromString(args[2]);
        if (team == null) {
            MessageUtil.send(player, "messages.invalid-team");
            return;
        }
        Region region = arenaManager.getSelectedRegion(player.getUniqueId(), arena.getWorldName());
        if (region == null) {
            MessageUtil.send(player, "messages.need-selection");
            return;
        }
        arena.setGoal(team, region);
        arenaManager.save(arena);
        MessageUtil.send(player, "messages.arena-saved", "%what%", team.getDisplayName() + " goal of " + arena.getName());
    }

    private void setBounds(CommandSender sender, String[] args) {
        Arena arena = findEditableArena(sender, args, "/thebridge setbounds <arena>", 2);
        if (arena == null) {
            return;
        }
        Player player = (Player) sender;
        Region region = arenaManager.getSelectedRegion(player.getUniqueId(), arena.getWorldName());
        if (region == null) {
            MessageUtil.send(player, "messages.need-selection");
            return;
        }
        arena.setBounds(region);
        arenaManager.save(arena);
        MessageUtil.send(player, "messages.arena-saved", "%what%", "bounds of " + arena.getName());
    }

    private void setPosition(CommandSender sender, int index) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, "messages.player-only");
            return;
        }
        Player player = (Player) sender;
        Point point = LocationUtil.toPoint(player.getLocation());
        arenaManager.setSelection(player.getUniqueId(), index, point);
        MessageUtil.send(player, "messages.pos-set",
                "%number%", String.valueOf(index + 1),
                "%location%", point.blockX() + ", " + point.blockY() + ", " + point.blockZ());
    }

    private void setTeamSize(CommandSender sender, String[] args) {
        Arena arena = findArenaForOption(sender, args, "/thebridge teamsize <arena> <number>");
        if (arena == null) {
            return;
        }
        int max = plugin.getSettings().maxTeamSize;
        Integer value = parseNumber(sender, args[2], 1, max);
        if (value == null) {
            return;
        }
        arena.setTeamSize(value);
        arenaManager.save(arena);
        MessageUtil.send(sender, "messages.arena-saved", "%what%", value + "v" + value + " for " + arena.getName());
    }

    private void setScoreLimit(CommandSender sender, String[] args) {
        Arena arena = findArenaForOption(sender, args, "/thebridge scorelimit <arena> <goals|0>");
        if (arena == null) {
            return;
        }
        Integer value = parseNumber(sender, args[2], 0, 100);
        if (value == null) {
            return;
        }
        arena.setScoreLimit(value);
        arenaManager.save(arena);
        MessageUtil.send(sender, "messages.arena-saved", "%what%", "score limit of " + arena.getName());
    }

    private void setDuration(CommandSender sender, String[] args) {
        Arena arena = findArenaForOption(sender, args, "/thebridge duration <arena> <seconds|0>");
        if (arena == null) {
            return;
        }
        Integer value = parseNumber(sender, args[2], 0, 86400);
        if (value == null) {
            return;
        }
        // 0 means "use config.yml". Everything else must be a sensible length.
        if (value != 0 && value < 30) {
            MessageUtil.send(sender, "messages.invalid-number",
                    "%value%", args[2], "%min%", "30 (or 0)", "%max%", "86400");
            return;
        }
        arena.setDurationSeconds(value);
        arenaManager.save(arena);
        MessageUtil.send(sender, "messages.arena-saved", "%what%", "duration of " + arena.getName());
    }

    // ------------------------------------------------------------------
    // Enable / disable / stop
    // ------------------------------------------------------------------

    private void enable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/thebridge enable <arena>");
            return;
        }
        Arena arena = findArena(sender, args[1]);
        if (arena == null) {
            return;
        }
        if (arena.getState() != ArenaState.DISABLED) {
            // Either it is already enabled, or a disabled arena is still finishing its reset.
            MessageUtil.send(sender, arena.isEnabled() ? "messages.already-enabled" : "messages.arena-active",
                    "%arena%", arena.getName());
            return;
        }

        // Never enable an arena silently when something is wrong. Tell the admin exactly what.
        List<String> problems = arena.getProblems(Bukkit.getWorld(arena.getWorldName()) != null);
        if (!problems.isEmpty()) {
            MessageUtil.send(sender, "messages.arena-not-ready", "%arena%", arena.getName());
            for (String problem : problems) {
                MessageUtil.send(sender, "messages.arena-problem", "%error%", problem);
            }
            return;
        }

        arena.setEnabled(true);
        arena.setState(ArenaState.WAITING);
        arenaManager.save(arena);
        MessageUtil.send(sender, "messages.arena-enabled", "%arena%", arena.getName());
    }

    private void disable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/thebridge disable <arena>");
            return;
        }
        Arena arena = findArena(sender, args[1]);
        if (arena == null) {
            return;
        }

        arena.setEnabled(false);
        arenaManager.save(arena);

        switch (arena.getState()) {
            case WAITING:
            case STARTING: {
                matchManager.kickAll(arena);
                if (arena.getState() != ArenaState.DISABLED) {
                    arena.setState(ArenaState.DISABLED);
                }
                break;
            }
            case IN_GAME:
                matchManager.stop(arena);
                break;
            default: {
                // DISABLED already, or ENDING / RESETTING which become DISABLED by themselves.
                break;
            }
        }
        MessageUtil.send(sender, "messages.arena-disabled", "%arena%", arena.getName());
    }

    private void stop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/thebridge stop <arena>");
            return;
        }
        Arena arena = findArena(sender, args[1]);
        if (arena == null) {
            return;
        }
        if (matchManager.stop(arena)) {
            MessageUtil.send(sender, "messages.match-stopped", "%arena%", arena.getName());
        } else {
            MessageUtil.send(sender, "messages.nothing-to-stop", "%arena%", arena.getName());
        }
    }

    // ------------------------------------------------------------------
    // Info and reload
    // ------------------------------------------------------------------

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/thebridge info <arena>");
            return;
        }
        Arena arena = findArena(sender, args[1]);
        if (arena == null) {
            return;
        }

        Match match = matchManager.getMatch(arena);
        int players = match == null ? 0 : match.getPlayerCount();

        MessageUtil.sendText(sender, "&8&m          &r &6Arena &e" + arena.getName() + " &8&m          ");
        MessageUtil.sendText(sender, "&7State: &f" + arena.getState() + (arena.isEnabled() ? " &a(enabled)" : " &c(disabled)"));
        MessageUtil.sendText(sender, "&7World: &f" + arena.getWorldName());
        MessageUtil.sendText(sender, "&7Mode: &f" + arena.getTeamSize() + "v" + arena.getTeamSize()
                + " &8| &7Players: &f" + players + "/" + arena.getMaxPlayers());
        MessageUtil.sendText(sender, "&7Score limit: &f" + matchManager.getScoreLimit(arena)
                + " &8| &7Duration: &f" + matchManager.getDuration(arena) + "s");
        MessageUtil.sendText(sender, "&7Lobby: &f" + describe(arena.getLobby()));
        MessageUtil.sendText(sender, "&7Red spawn: &f" + describe(arena.getSpawn(MatchTeam.RED)));
        MessageUtil.sendText(sender, "&7Blue spawn: &f" + describe(arena.getSpawn(MatchTeam.BLUE)));
        MessageUtil.sendText(sender, "&7Red goal: &f" + describe(arena.getGoal(MatchTeam.RED)));
        MessageUtil.sendText(sender, "&7Blue goal: &f" + describe(arena.getGoal(MatchTeam.BLUE)));
        MessageUtil.sendText(sender, "&7Bounds: &f" + describe(arena.getBounds()));

        List<String> problems = arena.getProblems(Bukkit.getWorld(arena.getWorldName()) != null);
        if (problems.isEmpty()) {
            MessageUtil.sendText(sender, "&7Setup: &aready to be enabled");
        } else {
            MessageUtil.sendText(sender, "&7Setup problems:");
            for (String problem : problems) {
                MessageUtil.sendText(sender, "&c - " + problem);
            }
        }
    }

    private void reload(CommandSender sender) {
        plugin.reload();
        MessageUtil.send(sender, "messages.reloaded");
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private void sendUsage(CommandSender sender, String usage) {
        MessageUtil.send(sender, "messages.usage", "%command%", usage);
    }

    /** Finds an arena and tells the sender when it does not exist. Returns null in that case. */
    private Arena findArena(CommandSender sender, String name) {
        Arena arena = arenaManager.get(name);
        if (arena == null) {
            MessageUtil.send(sender, "messages.unknown-arena", "%arena%", name);
        }
        return arena;
    }

    /**
     * For setup commands that need a player: checks the sender, the arguments, that the arena exists
     * and that it is disabled. Returns null (after telling the sender why) when something is wrong.
     *
     * @param minArgs how many words the command needs, including the sub command itself
     */
    private Arena findEditableArena(CommandSender sender, String[] args, String usage, int minArgs) {
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, "messages.player-only");
            return null;
        }
        if (args.length < minArgs) {
            sendUsage(sender, usage);
            return null;
        }
        Arena arena = findArena(sender, args[1]);
        if (arena == null) {
            return null;
        }
        if (arena.getState() != ArenaState.DISABLED) {
            MessageUtil.send(sender, "messages.arena-must-be-disabled", "%arena%", arena.getName());
            return null;
        }
        return arena;
    }

    private Arena findArenaForOption(CommandSender sender, String[] args, String usage) {
        if (args.length < 3) {
            sendUsage(sender, usage);
            return null;
        }
        Arena arena = findArena(sender, args[1]);
        if (arena == null) {
            return null;
        }
        if (arena.getState() != ArenaState.DISABLED) {
            MessageUtil.send(sender, "messages.arena-must-be-disabled", "%arena%", arena.getName());
            return null;
        }
        return arena;
    }

    private boolean isInArenaWorld(Player player, Arena arena) {
        if (player.getWorld().getName().equals(arena.getWorldName())) {
            return true;
        }
        MessageUtil.send(player, "messages.wrong-world", "%world%", arena.getWorldName());
        return false;
    }

    /** Returns the number, or null after telling the sender that it is invalid. */
    private Integer parseNumber(CommandSender sender, String text, int min, int max) {
        try {
            int value = Integer.parseInt(text);
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException exception) {
            // Falls through to the error message below.
        }
        MessageUtil.send(sender, "messages.invalid-number",
                "%value%", text, "%min%", String.valueOf(min), "%max%", String.valueOf(max));
        return null;
    }

    private String describe(Point point) {
        if (point == null) {
            return "&cnot set";
        }
        return point.blockX() + ", " + point.blockY() + ", " + point.blockZ();
    }

    private String describe(Region region) {
        if (region == null) {
            return "&cnot set";
        }
        return region.minX() + ", " + region.minY() + ", " + region.minZ()
                + " &7to &f" + region.maxX() + ", " + region.maxY() + ", " + region.maxZ();
    }
}
