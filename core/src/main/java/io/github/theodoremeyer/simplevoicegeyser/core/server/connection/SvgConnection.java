package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.SvgAudioListener;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.SvgAudioSender;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;

/**
 * Represents a single active websocket + voice connection.
 * <p>
 * This is the authoritative runtime state for a client.
 */
public final class SvgConnection {

    /**
     * UUID associated with this connection.
     */
    private final UUID uuid;

    /**
     * Associated websocket session.
     */
    private final Session session;

    /**
     * Associated player.
     */
    private final SvgPlayer player;

    /**
     * Voice sender.
     */
    private SvgAudioSender audioSender;

    /**
     * Voice listener.
     */
    private SvgAudioListener audioListener;

    /**
     * Whether authentication completed.
     */
    private volatile boolean authenticated;

    /**
     * Whether this connection has been closed.
     */
    private volatile boolean closed;

    /**
     * Creates a new active connection.
     *
     * @param uuid player uuid
     * @param session websocket session
     * @param player associated player
     */
    public SvgConnection(UUID uuid, Session session, SvgPlayer player) {
        this.uuid = uuid;
        this.session = session;
        this.player = player;
    }


    /**
     * Initializes voice chat state.
     */
    public synchronized void authenticate() throws IllegalStateException {

        if (authenticated) {
            return;
        }

        VoicechatServerApi api =
                SvgCore.getBridge().getVcServerApi();

        if (api == null) {
            throw new IllegalStateException(
                    "VoicechatServerApi is null"
            );
        }

        VoicechatConnection connection =
                api.getConnectionOf(uuid);

        if (connection == null) {
            throw new IllegalStateException(
                    "VoicechatConnection is null for: " + uuid
            );
        }

        if (connection.isInstalled()) {
            throw new IllegalStateException(
                    "Player has Simple Voice Chat mod installed"
            );
        }

        audioListener = new SvgAudioListener(
                uuid, session, api);

        audioListener.registerListener();

        audioSender = new SvgAudioSender(
                api, uuid);

        authenticated = true;

        SvgCore.getLogger().debug(
                "SvgConnection: Authenticated connection: " + uuid
        );
    }

    /**
     * Disconnects and cleans up this connection.
     * <p>
     * Safe to call multiple times.
     *
     * @param code websocket close code
     * @param reason websocket close reason
     */
    public synchronized void disconnect(int code, String reason) {
        if (closed) {
            return;
        }

        closed = true;
        authenticated = false;

        if (audioSender != null) {
            try {
                audioSender.unregister();
            } catch (Exception e) {
                SvgCore.getLogger().debug("SvgConnection: Failed to unregister audio sender", e);
            }
        }

        if (audioListener != null) {
            try {
                audioListener.unRegister();
            } catch (Exception e) {
                SvgCore.getLogger().debug("SvgConnection: Failed to unregister audio listener", e);
            }
        }

        if (session.isOpen()) {
            try {
                session.close(code, reason);
            } catch (Exception e) {
                SvgCore.getLogger().debug("SvgConnection: Failed to close websocket session", e);
            }
        }

        SvgCore.getLogger().debug("SvgConnection: Disconnected connection: " +
                        uuid + " (" + reason + ")"
        );
    }

    /**
     * Sends a JSON packet to the client.
     *
     * @param json packet
     */
    public void sendJson(JSONObject json) {

        if (closed || !session.isOpen()) {
            return;
        }

        try {
            session.getRemote().sendString(
                    json.toString()
            );
        } catch (IOException e) {
            SvgCore.getLogger().debug("SvgConnection: Failed to send json packet", e);
            disconnect(ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(), "Packet send failure");
        }
    }

    /**
     * Sends a packet to the client.
     *
     * @param type packet type
     * @param message message
     * @param fatal fatal error
     */
    public void sendMessage(
            ConnectionStates.MessageType type,
            String message,
            boolean fatal
    ) {

        JSONObject json = new JSONObject();

        json.put("type", type);
        json.put("message", message);
        json.put("fatal", fatal);

        sendJson(json);
    }

    /**
     * Sends an error message.
     *
     * @param message error message
     * @param fatal whether fatal
     */
    public void sendError(String message, boolean fatal) {
        sendMessage(
                ConnectionStates.MessageType.ERROR,
                message,
                fatal
        );
    }

    /**
     * Sends a status message.
     *
     * @param message status message
     */
    public void sendStatus(String message) {

        sendMessage(
                ConnectionStates.MessageType.STATUS,
                message,
                false
        );
    }

    /**
     * Sends a chat message.
     *
     * @param message chat message
     */
    public void sendChat(String message) {

        sendMessage(
                ConnectionStates.MessageType.CHAT,
                message,
                false
        );
    }

    /**
     * Sends a synced in-game message.
     *
     * @param message message
     */
    public void sendPlayerMessage(String message) {
        if (player == null) {
            return;
        }

        player.sendMessage(SvgCore.getPrefix() +
                SvgColor.translateAltColorCodes('&', message)
        );
    }

    public void sendFatal(
            String message,
            int closeCode,
            String closeReason
    ) {

        sendError(message, true);

        disconnect(
                closeCode,
                closeReason
        );
    }

    public UUID getUuid() {
        return uuid;
    }

    public Session getSession() {
        return session;
    }

    public SvgPlayer getPlayer() {
        return player;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public boolean isClosed() {
        return closed;
    }

    public SvgAudioSender getAudioSender() {
        return audioSender;
    }
}