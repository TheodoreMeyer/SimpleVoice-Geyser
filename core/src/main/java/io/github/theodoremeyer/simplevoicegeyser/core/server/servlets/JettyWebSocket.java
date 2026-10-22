package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioTransportMode;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.MicAudioFormat;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.ConnectionAuthenticator;
import io.github.theodoremeyer.simplevoicegeyser.core.server.packets.PacketHandler;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@WebSocket
public final class JettyWebSocket {

    public static final ConnectionAuthenticator AUTHENTICATOR =
            new ConnectionAuthenticator();

    private static final PacketHandler packetHandler = new PacketHandler();

    private Session session;
    private SvgConnection connection;
    private UUID pendingJoinUuid;
    private long binaryFrameCount = 0;
    private long binaryByteCount = 0;
    private long controlMessageCount = 0;
    private long joinAttemptCount = 0;
    private long capabilityMessageCount = 0;
    private AudioSessionNegotiation audioNegotiation;

    public JettyWebSocket() {}

    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(SvgCore.getConfig().IDLE_TIMEOUT.get()));
        AudioTransportMode preference = AudioTransportMode.fromConfig(SvgCore.getConfig());
        boolean allowLegacyFallback = Boolean.TRUE.equals(SvgCore.getConfig().AUDIO_ALLOW_LEGACY_FALLBACK.get());

        this.audioNegotiation = new AudioSessionNegotiation(preference, allowLegacyFallback,
                Boolean.TRUE.equals(SvgCore.getConfig().AUDIO_MIC_ALLOW_LEGACY.get()));
        SvgCore.getLogger().info("[Websocket] WebSocket connected: " + session.getRemoteAddress());
        SvgCore.getLogger().debug("WebSocket: Session opened remote=" + session.getRemoteAddress());
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        controlMessageCount++;

        if (message == null || message.trim().isEmpty()) {
            return;
        }

        message = message.trim();

        if (!message.startsWith("{")) {
            sendRaw(ConnectionStates.MessageType.ERROR,
                    "Invalid input. Expected a JSON object.", false);
            return;
        }

        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");
            SvgCore.getLogger().debug("WebSocket: Control message #" + controlMessageCount + " type=" + type);

            packetHandler.handle(this, json);

        } catch (Exception e) {
            SvgCore.getLogger().severe("[VCBridge] Exception: " + e.getMessage());
            SvgCore.getLogger().debug("VCBridge: error reading client data", e);
        }
    }

    @OnWebSocketMessage
    public void onMessage(byte[] buffer, int offset, int length) {
        if (connection == null || !connection.isAuthenticated()) {
            SvgCore.getLogger().debug("WebSocket: Dropping pre-auth binary frame bytes=" + length);
            return;
        }

        if (!enforceMicFormatPolicy()) {
            return;
        }

        binaryFrameCount++;
        binaryByteCount += length;
        if (binaryFrameCount % 100 == 0) {
            SvgCore.getLogger().debug(
                    "WebSocket: binary stats uuid=" + connection.getUuid()
                            + " frames=" + binaryFrameCount
                            + " bytes=" + binaryByteCount
            );
        }

        if (connection.getAudioSender() != null) {
            MicAudioFormat format = audioNegotiation == null
                    ? MicAudioFormat.PCM16LE
                    : audioNegotiation.getSelectedMicFormat();
            if (!format.acceptsPacketLength(length)) {
                SvgCore.getLogger().debug("WebSocket: Dropping malformed " + format.protocolName()
                        + " microphone frame bytes=" + length);
                return;
            }
            byte[] packet = Arrays.copyOfRange(buffer, offset, offset + length);
            if (format == MicAudioFormat.OPUS) {
                connection.getAudioSender().sendEncodedOpus(packet);
            } else {
                connection.getAudioSender().sendPcm16le(packet);
            }
        } else {
            SvgCore.getLogger().debug("WebSocket: audioSender is null for uuid=" + connection.getUuid() + ", dropping binary frame");
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        SvgCore.getLogger().debug(
                "WebSocket: Session close status=" + statusCode
                        + " reason=" + reason
                        + " authenticated=" + (connection != null && connection.isAuthenticated())
                        + " controlMessages=" + controlMessageCount
                        + " binaryFrames=" + binaryFrameCount
                        + " transport=" + (audioNegotiation == null ? "n/a" : audioNegotiation.summary())
        );

        if (pendingJoinUuid != null) {
            SvgCore.getPendingJoinWaiter().cancelForSocket(pendingJoinUuid, this);
            pendingJoinUuid = null;
        }

        if (connection != null) {
            SvgCore.getLogger().info(
                    "[WebSocket] Closed for "
                            + connection.getPlayer().getName()
                            + ": "
                            + statusCode
                            + " - "
                            + reason
            );

            connection.disconnect(statusCode, reason);
            SvgCore.getConnectionManager().remove(connection);
        } else {
            SvgCore.getLogger().info("[WebSocket] Closed unknown session: " + reason);
        }
    }

    @OnWebSocketError
    public void onError(Throwable error) {
        if (error instanceof org.eclipse.jetty.websocket.api.exceptions.WebSocketTimeoutException
                || error instanceof org.eclipse.jetty.websocket.core.exception.WebSocketTimeoutException) {

            String sessionName = connection != null ? connection.getPlayer().getName() : "unknown session";
            SvgCore.getLogger().info("[WebSocket] Session timed out for " + sessionName);
            return;
        }

        if (isBenignDisconnect(error)) {
            SvgCore.getLogger().debug("WebSocket: benign disconnect", error);
            return;
        }

        String type = error == null
                ? "Unknown"
                : error.getClass().getSimpleName();

        String message = error == null || error.getMessage() == null
                ? "<no message>"
                : error.getMessage();

        SvgCore.getLogger().debug("WebSocket: websocket error", error);
        SvgCore.getLogger().info("[WebSocket] Error: " + type + ": " + message);
    }

    private static boolean isBenignDisconnect(Throwable error) {
        if (error == null) {
            return false;
        }
        if (error instanceof java.nio.channels.ClosedChannelException
                || error instanceof java.io.EOFException) {
            return true;
        }
        if (error instanceof IOException) {
            String message = error.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                return lower.contains("closed") || lower.contains("broken pipe");
            }
        }
        return false;
    }

    public void sendRaw(ConnectionStates.MessageType type, String message, boolean fatal) {
        if (session == null || !session.isOpen()) {
            return;
        }

        JSONObject json = new JSONObject();
        json.put("type", type.getJsonString());
        json.put("message", message);
        json.put("fatal", fatal);

        try {
            session.getRemote().sendString(json.toString());
        } catch (IOException e) {
            SvgCore.getLogger().debug("WebSocket: Failed to send raw packet", e);
        }
    }

    public boolean sendJson(JSONObject json) {
        if (session == null || !session.isOpen()) {
            return false;
        }

        try {
            session.getRemote().sendString(json.toString());
            return true;
        } catch (IOException e) {
            SvgCore.getLogger().debug("WebSocket: Failed to send JSON packet", e);
            return false;
        }
    }

    public boolean enforceMicFormatPolicy() {
        boolean allowed = audioNegotiation == null
                ? Boolean.TRUE.equals(SvgCore.getConfig().AUDIO_MIC_ALLOW_LEGACY.get())
                : audioNegotiation.isMicFormatAllowed();
        if (allowed) {
            return true;
        }
        if (session != null && session.isOpen()) {
            sendRaw(ConnectionStates.MessageType.ERROR,
                    "This server requires Opus microphone audio. Use an Opus-capable client.", true);
            session.close(ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(),
                    "Opus microphone audio required");
        }
        return false;
    }


    public SvgConnection getConnection() {
        return connection;
    }

    public AudioSessionNegotiation getAudioNegotiation() {
        return audioNegotiation;
    }

    public Session getSession() {
        return session;
    }

    public void setConnection(SvgConnection connection) {
        this.connection = connection;
    }

    /**
     * Marks this session as waiting for the player to join Minecraft.
     *
     * @param uuid player uuid
     */
    public void setPendingJoinUuid(UUID uuid) {
        this.pendingJoinUuid = uuid;
    }

    /**
     * Clears the pending-join marker when it still matches {@code uuid}.
     *
     * @param uuid player uuid
     */
    public void clearPendingJoinUuid(UUID uuid) {
        if (uuid != null && uuid.equals(this.pendingJoinUuid)) {
            this.pendingJoinUuid = null;
        }
    }

    public long addJoinAttempt() {
        ++joinAttemptCount;
        return joinAttemptCount;
    }

    public long addCapabilityMessage() {
        ++capabilityMessageCount;
        return capabilityMessageCount;
    }

    public void setAudioNegotiation(AudioSessionNegotiation audioNegotiation) {
        this.audioNegotiation = audioNegotiation;
    }
}
