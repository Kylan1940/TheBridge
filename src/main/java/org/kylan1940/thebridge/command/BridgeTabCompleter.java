package org.kylan1940.thebridge.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.kylan1940.thebridge.TheBridge;
import org.kylan1940.thebridge.arena.ArenaManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Tab completion for /thebridge. Only suggests what the sender is allowed to use. */
public class BridgeTabCompleter implements TabCompleter {

    /** Sub commands whose second word is an arena name. */
    private static final Set<String> ARENA_COMMANDS = Set.of(
            "join", "delete", "setlobby", "setspawn", "setgoal", "setbounds", "teamsize",
            "scorelimit", "duration", "enable", "disable", "stop", "info"
    );

    private final TheBridge plugin;
    private final ArenaManager arenaManager;

    public BridgeTabCompleter(TheBridge plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        if (args.length == 1) {
            List<String> options = new ArrayList<>(BridgeCommand.PLAYER_COMMANDS);
            if (sender.hasPermission("thebridge.admin")) {
                options.addAll(BridgeCommand.ADMIN_COMMANDS);
            }
            return filter(options, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean isAdminCommand = BridgeCommand.ADMIN_COMMANDS.contains(sub);
        if (isAdminCommand && !sender.hasPermission("thebridge.admin")) {
            return List.of();
        }

        if (args.length == 2) {
            if (ARENA_COMMANDS.contains(sub)) {
                return filter(arenaManager.getNames(), args[1]);
            }
            if (sub.equals("stats")) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return filter(names, args[1]);
            }
        }

        if (args.length == 3) {
            if (sub.equals("setspawn") || sub.equals("setgoal")) {
                return filter(List.of("red", "blue"), args[2]);
            }
            if (sub.equals("teamsize")) {
                List<String> sizes = new ArrayList<>();
                for (int size = 1; size <= plugin.getSettings().maxTeamSize; size++) {
                    sizes.add(String.valueOf(size));
                }
                return filter(sizes, args[2]);
            }
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))) {
                result.add(option);
            }
        }
        return result;
    }
}