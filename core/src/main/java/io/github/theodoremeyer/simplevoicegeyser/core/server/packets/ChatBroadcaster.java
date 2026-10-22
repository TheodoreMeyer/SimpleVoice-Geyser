package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;

import java.util.UUID;

/**
 * Mirrors Minecraft (and peer web) chat lines to authenticated web clients.
 */
public final class ChatBroadcaster {

    private ChatBroadcaster() {}

    /**
     * Broadcast a chat line to every authenticated websocket (optionally excluding one UUID).
     *
     * @param playerName display name of the speaker
     * @param message    raw or already-plain message body
     * @param excludeUuid connection to skip (e.g. web sender who already has a local echo)
     */
    public static void broadcast(String playerName, String message, UUID excludeUuid) {
        if (!Boolean.TRUE.equals(SvgCore.getConfig().WEB_CHAT_ENABLED.get())) {
            return;
        }
        if (SvgCore.getConnectionManager() == null) {
            return;
        }

        String name = sanitizeName(playerName);
        ChatSanitize.SanitizedChat body = ChatSanitize.sanitize(SvgColor.strip(message == null ? "" : message));
        if (body.sanitized().isEmpty()) {
            return;
        }

        String line = "<" + name + "> " + body.sanitized();
        for (SvgConnection connection : SvgCore.getConnectionManager().getAll()) {
            if (connection == null || !connection.isAuthenticated()) {
                continue;
            }
            if (excludeUuid != null && excludeUuid.equals(connection.getUuid())) {
                continue;
            }
            try {
                connection.sendChat(line);
            } catch (Exception e) {
                SvgCore.getLogger().debug(
                        "ChatBroadcaster: failed to send chat to " + connection.getUuid(),
                        e
                );
            }
        }
    }

    /**
     * Handle a Minecraft public chat message (may be a web-forwarded {@code [Web Chat]:} line).
     */
    public static void onMinecraftChat(String playerName, UUID playerUuid, String message) {
        if (!Boolean.TRUE.equals(SvgCore.getConfig().WEB_CHAT_ENABLED.get())) {
            return;
        }
        String stripped = ChatSanitize.stripWebChatPrefix(message);
        if (stripped != null) {
            // Web → game already echoed "You:" / "[You]" to the sender; fan out to peers only.
            broadcast(playerName, stripped, playerUuid);
            return;
        }
        broadcast(playerName, message, null);
    }

    private static String sanitizeName(String playerName) {
        String raw = playerName == null ? "" : SvgColor.strip(playerName).trim();
        ChatSanitize.SanitizedChat sanitized = ChatSanitize.sanitize(raw);
        if (sanitized.sanitized().isEmpty()) {
            return "Player";
        }
        return sanitized.sanitized();
    }
}
