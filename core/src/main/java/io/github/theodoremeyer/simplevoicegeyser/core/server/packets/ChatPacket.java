package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

public class ChatPacket implements Packet {

    private static final int MAX_WEB_CHAT_LENGTH = 200;

    @Override
    public String getType() {
        return "chat";
    }

    @Override
    public void handle(JettyWebSocket socket, JSONObject json) {

        if (socket.getConnection() == null || !socket.getConnection().isAuthenticated()) {
            socket.sendRaw(
                    ConnectionStates.MessageType.ERROR,
                    "Access Denied: Not authenticated.",
                    false
            );
            return;
        }

        SanitizedChat sanitized = sanitizeChatMessage(json.optString("message", ""));

        SvgCore.getLogger().debug(
                "WebChat: uuid=" + socket.getConnection().getUuid()
                        + " originalLen=" + sanitized.originalLength
                        + " sanitizedLen=" + sanitized.sanitized.length()
                        + " removed=" + sanitized.removedCount
                        + " truncated=" + sanitized.truncated
                        + " dropped=" + sanitized.sanitized.isEmpty()
        );

        if (sanitized.sanitized.isEmpty()) {
            socket.getConnection().sendError(
                    "Message contained unsupported characters and was not sent.",
                    false
            );
            return;
        }

        SvgPlayer player = socket.getConnection().getPlayer();
        String displayName = player != null
                ? player.getName()
                : socket.getConnection().getUuid().toString();

        String outbound = "[Web Chat] " + displayName + ": " + sanitized.sanitized;

        try {
            socket.getConnection().sendChat("You: " + sanitized.sanitized);

            if (player != null) {
                player.chat(outbound);
            } else {
                socket.getConnection().sendStatus("You are not in-game. Message was broadcast.");

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


    private SanitizedChat sanitizeChatMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return new SanitizedChat("", 0, false, 0);
        }

        int originalLength = raw.length();
        StringBuilder sanitized = new StringBuilder(Math.min(originalLength, MAX_WEB_CHAT_LENGTH));
        int removedCount = 0;
        boolean truncated = false;
        boolean prevSpace = false;

        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);

            if (ch == '\r' || ch == '\n' || ch == '\t') {
                ch = ' ';
            }

            if (ch == '\u00A7' || ch < 0x20 || ch == 0x7F) {
                removedCount++;
                continue;
            }

            if (ch > 0x7E) {
                removedCount++;
                continue;
            }

            if (ch == ' ') {
                if (prevSpace) {
                    continue;
                }
                prevSpace = true;
            } else {
                prevSpace = false;
            }

            if (sanitized.length() >= MAX_WEB_CHAT_LENGTH) {
                truncated = true;
                break;
            }

            sanitized.append(ch);
        }

        String result = sanitized.toString().trim();
        return new SanitizedChat(result, originalLength, truncated, removedCount);
    }

    private record SanitizedChat(
            String sanitized,
            int originalLength,
            boolean truncated,
            int removedCount
    ) {}
}
