package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

/**
 * The Class handling the ChatPackets
 */
public class ChatPacket implements Packet {

    /**
     * A no arg Constructor
     */
    public ChatPacket() {}

    @Override
    public String getType() {
        return "chat";
    }

    @Override
    public void handle(JettyWebSocket socket, JSONObject json) {

        if (!SvgCore.getConfig().WEB_CHAT_ENABLED.get()) {
            socket.sendRaw(
                    ConnectionStates.MessageType.ERROR,
                    "Web chat is disabled on this server.",
                    false
            );
            return;
        }

        if (socket.getConnection() == null || !socket.getConnection().isAuthenticated()) {
            socket.sendRaw(
                    ConnectionStates.MessageType.ERROR,
                    "Access Denied: Not authenticated.",
                    false
            );
            return;
        }

        ChatSanitize.SanitizedChat sanitized = ChatSanitize.sanitize(json.optString("message", ""));

        SvgCore.getLogger().debug(
                "WebChat: uuid=" + socket.getConnection().getUuid()
                        + " originalLen=" + sanitized.originalLength()
                        + " sanitizedLen=" + sanitized.sanitized().length()
                        + " removed=" + sanitized.removedCount()
                        + " truncated=" + sanitized.truncated()
                        + " dropped=" + sanitized.sanitized().isEmpty()
        );

        if (sanitized.sanitized().isEmpty()) {
            socket.getConnection().sendError(
                    "Message contained unsupported characters and was not sent.",
                    false
            );
            return;
        }

        SvgPlayer player = socket.getConnection().getPlayer();
        String outbound = ChatSanitize.WEB_CHAT_PREFIX + sanitized.sanitized();

        try {
            // Client already shows "[You] …" locally; Minecraft chat event fans out to peers.
            if (player != null) {
                player.chat(outbound);
            } else {
                socket.getConnection().sendStatus("You are not in-game. Message was broadcast.");

                String displayName = socket.getConnection().getUuid() != null
                        ? String.valueOf(socket.getConnection().getUuid())
                        : "Web";
                // Prefer connection player name when known later; use short label for offline web.
                ChatBroadcaster.broadcast(displayName, sanitized.sanitized(), socket.getConnection().getUuid());

                for (SvgPlayer other : SvgCore.getPlayerManager().getAllPlayers()) {
                    other.sendMessage(outbound);
                }
            }
        } catch (Exception e) {
            SvgCore.getLogger().debug(
                    "WebChat: Failed to forward chat for uuid="
                            + socket.getConnection().getUuid(),
                    e
            );

            socket.getConnection().sendError(
                    "Failed to send chat message safely.",
                    false
            );
        }
    }
}
