package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
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

    private final UUID uuid;
    private final Session session;
    private final SvgPlayer player;
    private SvgAudioSender audioSender;
    private SvgAudioListener audioListener;
    private final AudioSessionNegotiation audioNegotiation;
    private volatile boolean authenticated;
    private volatile boolean closed;

    SvgConnection(Session session, SvgPlayer player, AudioSessionNegotiation audioNegotiation) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.session = session;
        this.audioNegotiation = audioNegotiation;
    }

    public synchronized void authenticate() throws IllegalStateException {
        if (authenticated) {
            return;
        }

        VoicechatServerApi api = SvgCore.getBridge().getVcServerApi();
        if (api == null) {
            throw new IllegalStateException("VoicechatServerApi is null");
        }

        VoicechatConnection connection = api.getConnectionOf(uuid);
        if (connection == null) {
            throw new IllegalStateException("VoicechatConnection is null for: " + uuid);
        }

        if (connection.isInstalled()) {
            throw new IllegalStateException("Player has Simple Voice Chat mod installed");
        }

        audioListener = new SvgAudioListener(uuid, session, api, audioNegotiation);
        if (!audioListener.registerListener()) {
            throw new IllegalStateException("Failed to register audio listener for: " + uuid);
        }

        audioSender = new SvgAudioSender(api, uuid);
        authenticated = true;

        SvgCore.getLogger().debug("SvgConnection: Authenticated connection: " + uuid);
    }

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

        SvgCore.getLogger().debug("SvgConnection: Disconnected connection: " + uuid + " (" + reason + ")");
    }

    public void sendJson(JSONObject json) {
        if (closed || !session.isOpen()) {
            return;
        }

        try {
            session.getRemote().sendString(json.toString());
        } catch (IOException e) {
            SvgCore.getLogger().debug("SvgConnection: Failed to send json packet", e);
            disconnect(ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(), "Packet send failure");
        }
    }

    public void sendMessage(ConnectionStates.MessageType type, String message, boolean fatal) {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("message", message);
        json.put("fatal", fatal);
        sendJson(json);
    }

    public void sendError(String message, boolean fatal) {
        sendMessage(ConnectionStates.MessageType.ERROR, message, fatal);
    }

    public void sendStatus(String message) {
        sendMessage(ConnectionStates.MessageType.STATUS, message, false);
    }

    public void sendChat(String message) {
        sendMessage(ConnectionStates.MessageType.CHAT, message, false);
    }

    public void sendPlayerMessage(String message) {
        if (player == null) {
            return;
        }

        player.sendMessage(SvgCore.getPrefix() + SvgColor.translateAltColorCodes('&', message));
    }

    public void sendFatal(String message, int closeCode, String closeReason) {
        sendError(message, true);
        disconnect(closeCode, closeReason);
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
