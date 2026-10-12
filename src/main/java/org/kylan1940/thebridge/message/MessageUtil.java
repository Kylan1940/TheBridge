package org.kylan1940.thebridge.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.kylan1940.thebridge.TheBridge;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Sends the messages written in config.yml. Used exactly like in your other plugins:
 *
 *     MessageUtil.send(player, "messages.arena-full", "%arena%", arena.getName());
 *
 * Text may use & colour codes, %prefix% and the placeholders you pass in pairs.
 * A path may be a single string or a list of lines.
 *
 * Difference from your other plugins: the text is turned into an Adventure Component
 * (the modern Paper way) instead of a legacy String.
 */
public final class MessageUtil {

    private static final LegacyComponentSerializer COLORS = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    public static void send(CommandSender sender, String path, String... placeholders) {
        for (String line : getLines(path, placeholders)) {
            sender.sendMessage(color(line));
        }
    }

    /** Sends one already written line (colour codes still work). Used for dynamic output such as /thebridge info. */
    public static void sendText(CommandSender sender, String text) {
        sender.sendMessage(color(text));
    }

    /** Sends a config message to many players at once. */
    public static void sendToAll(Collection<Player> players, String path, String... placeholders) {
        List<String> lines = getLines(path, placeholders);
        for (Player player : players) {
            for (String line : lines) {
                player.sendMessage(color(line));
            }
        }
    }

    // ------------------------------------------------------------------
    // Titles, action bar, sounds (all can be switched off in config.yml)
    // ------------------------------------------------------------------

    public static void title(Player player, String titlePath, String subtitlePath, String... placeholders) {
        if (!TheBridge.getInstance().getConfig().getBoolean("effects.titles", true)) {
            return;
        }
        Component title = color(getFirstLine(titlePath, placeholders));
        Component subtitle = color(getFirstLine(subtitlePath, placeholders));

        Title.Times times = Title.Times.times(
                Duration.ofMillis(200),
                Duration.ofMillis(1200),
                Duration.ofMillis(300)
        );
        player.showTitle(Title.title(title, subtitle, times));
    }

    public static void actionBar(Player player, String path, String... placeholders) {
        if (!TheBridge.getInstance().getConfig().getBoolean("effects.action-bar", true)) {
            return;
        }
        String text = getFirstLine(path, placeholders);
        if (!text.isEmpty()) {
            player.sendActionBar(color(text));
        }
    }

    /**
     * Plays the sound named in config.yml under effects.sounds.<name>.
     * Sounds are written as vanilla keys ("entity.player.levelup"), which works on every 1.21 version.
     */
    public static void playSound(Player player, String name) {
        if (!TheBridge.getInstance().getConfig().getBoolean("effects.sounds.enabled", true)) {
            return;
        }
        String key = TheBridge.getInstance().getConfig().getString("effects.sounds." + name, "");
        if (key == null || key.isEmpty()) {
            return;
        }
        player.playSound(player.getLocation(), key, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** The message as colour-coded text (first line only). Handy for building bigger messages. */
    public static String get(String path, String... placeholders) {
        return getFirstLine(path, placeholders);
    }

    private static List<String> getLines(String path, String... placeholders) {
        TheBridge plugin = TheBridge.getInstance();
        List<String> messages = plugin.getConfig().getStringList(path);

        if (messages.isEmpty()) {
            String message = plugin.getConfig().getString(path);
            if (message == null || message.isEmpty()) {
                return List.of();
            }
            messages = List.of(message);
        }
        return messages.stream().map(line -> replace(line, placeholders)).toList();
    }

    private static String getFirstLine(String path, String... placeholders) {
        List<String> lines = getLines(path, placeholders);
        return lines.isEmpty() ? "" : lines.get(0);
    }

    private static String replace(String message, String... placeholders) {
        String prefix = TheBridge.getInstance().getConfig().getString("prefix", "");
        message = message.replace("%prefix%", prefix);

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replace(placeholders[i], placeholders[i + 1]);
        }
        return message;
    }

    private static Component color(String message) {
        return COLORS.deserialize(message);
    }
}
