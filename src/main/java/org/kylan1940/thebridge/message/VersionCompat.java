package org.kylan1940.thebridge.message;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.kylan1940.thebridge.TheBridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Titles and the action bar are different on every Minecraft version between 1.8 and 1.12:
 *
 *   1.11 and newer : player.sendTitle(title, subtitle, fadeIn, stay, fadeOut)
 *   1.8 to 1.10    : only a packet works (this class builds it with reflection)
 *   1.9 and newer  : action bar through the BungeeCord chat API
 *   1.8            : action bar through a packet
 *
 * Everything is looked up by name at runtime, so the plugin still loads on a version that lacks something.
 * When one of these cannot work, a warning is printed ONCE and that effect is skipped. The game keeps running.
 */
public final class VersionCompat {

    private static final String SERVER_VERSION = detectServerVersion();

    private static boolean titleWarned;
    private static boolean actionBarWarned;

    private VersionCompat() {
    }

    // ------------------------------------------------------------------
    // Titles
    // ------------------------------------------------------------------

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            // 1.11 and newer have this method.
            Method method = Player.class.getMethod("sendTitle",
                    String.class, String.class, int.class, int.class, int.class);
            method.invoke(player, title, subtitle, fadeIn, stay, fadeOut);
            return;
        } catch (NoSuchMethodException ignored) {
            // Older version: use the packet below.
        } catch (ReflectiveOperationException exception) {
            warnTitle(exception);
            return;
        }

        try {
            sendTitlePacket(player, "TITLE", title, fadeIn, stay, fadeOut);
            sendTitlePacket(player, "SUBTITLE", subtitle, fadeIn, stay, fadeOut);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnTitle(exception);
        }
    }

    private static void sendTitlePacket(Player player, String action, String text, int fadeIn, int stay, int fadeOut)
            throws ReflectiveOperationException {
        Class<?> packetClass = nms("PacketPlayOutTitle");
        Class<?> actionClass = nms("PacketPlayOutTitle$EnumTitleAction");
        Class<?> componentClass = nms("IChatBaseComponent");

        Object enumValue = actionClass.getMethod("valueOf", String.class).invoke(null, action);
        Object component = toComponent(text);

        Constructor<?> constructor = packetClass.getConstructor(
                actionClass, componentClass, int.class, int.class, int.class);
        sendPacket(player, constructor.newInstance(enumValue, component, fadeIn, stay, fadeOut));
    }

    // ------------------------------------------------------------------
    // Action bar
    // ------------------------------------------------------------------

    public static void sendActionBar(Player player, String text) {
        try {
            // 1.9 and newer: player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components)
            Class<?> typeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBar = typeClass.getField("ACTION_BAR").get(null);
            Player.Spigot spigot = player.spigot();
            Method send = Player.Spigot.class.getMethod("sendMessage", typeClass, BaseComponent[].class);
            send.invoke(spigot, actionBar, TextComponent.fromLegacyText(text));
            return;
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException ignored) {
            // 1.8: use the packet below.
        } catch (ReflectiveOperationException exception) {
            warnActionBar(exception);
            return;
        }

        try {
            Class<?> packetClass = nms("PacketPlayOutChat");
            Class<?> componentClass = nms("IChatBaseComponent");
            Constructor<?> constructor = packetClass.getConstructor(componentClass, byte.class);
            // The number 2 means "action bar".
            sendPacket(player, constructor.newInstance(toComponent(text), (byte) 2));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnActionBar(exception);
        }
    }

    // ------------------------------------------------------------------
    // Packet helpers (Minecraft internals, found by name)
    // ------------------------------------------------------------------

    private static Object toComponent(String text) throws ReflectiveOperationException {
        Class<?> serializerClass = nms("IChatBaseComponent$ChatSerializer");
        String json = "{\"text\":\"" + escape(text) + "\"}";
        return serializerClass.getMethod("a", String.class).invoke(null, json);
    }

    private static void sendPacket(Player player, Object packet) throws ReflectiveOperationException {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Field connectionField = handle.getClass().getField("playerConnection");
        Object connection = connectionField.get(handle);
        connection.getClass().getMethod("sendPacket", nms("Packet")).invoke(connection, packet);
    }

    private static Class<?> nms(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + SERVER_VERSION + "." + name);
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // The package of the server class ends with the version, for example "v1_8_R3".
    private static String detectServerVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private static void warnTitle(Exception exception) {
        if (!titleWarned) {
            titleWarned = true;
            TheBridge.getInstance().getLogger().warning("Titles do not work on this server version, so they are skipped ("
                    + exception + "). You can switch them off with effects.titles: false.");
        }
    }

    private static void warnActionBar(Exception exception) {
        if (!actionBarWarned) {
            actionBarWarned = true;
            TheBridge.getInstance().getLogger().warning("The action bar does not work on this server version, so it is skipped ("
                    + exception + "). You can switch it off with effects.action-bar: false.");
        }
    }
}
