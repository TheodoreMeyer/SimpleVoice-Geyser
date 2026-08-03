package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioTransportMode;
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

/**
 * Class that handles talking to the Client
 */
@WebSocket
public final class JettyWebSocket {

    /**
     * The authenticator instance used for validating join attempts. This is static and shared across all connections, but is designed to be thread-safe and handle concurrent requests appropriately.
     */
    public static final ConnectionAuthenticator AUTHENTICATOR =
            new ConnectionAuthenticator();

    private static final PacketHandler packetHandler = new PacketHandler();

    private Session session;
    private SvgConnection connection;
    private long binaryFrameCount = 0;
    private long binaryByteCount = 0;
    private long controlMessageCount = 0;
    private long joinAttemptCount = 0;
    private long capabilityMessageCount = 0;
    private AudioSessionNegotiation audioNegotiation;

    /**
     * No arg Constructor
     */
    public JettyWebSocket() {}

    /**
     * Code that runs on the Connect
     * @param session connected Session
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(SvgCore.getConfig().IDLE_TIMEOUT.get()));
        AudioTransportMode preference = AudioTransportMode.fromConfig(SvgCore.getConfig());
        boolean allowLegacyFallback = Boolean.TRUE.equals(SvgCore.getConfig().AUDIO_ALLOW_LEGACY_FALLBACK.get());

        this.audioNegotiation = new AudioSessionNegotiation(preference, allowLegacyFallback);
        SvgCore.getLogger().info("[Websocket] WebSocket connected: " + session.getRemoteAddress());
        SvgCore.getLogger().debug("WebSocket: Session opened remote=" + session.getRemoteAddress());
    }

    /**
     * Code triggered on a received message from the Session
     * @param message message received
     */
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

    /**
     * Code that runs on a byte message from session
     * @param buffer bytebuffer
     * @param offset byte offset
     * @param length byte length
     */
    @OnWebSocketMessage
    public void onMessage(byte[] buffer, int offset, int length) {
        if (connection == null || !connection.isAuthenticated()) {
            SvgCore.getLogger().debug("WebSocket: Dropping pre-auth binary frame bytes=" + length);
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
            connection.getAudioSender().sendOpus(Arrays.copyOfRange(buffer, offset, offset + length));
        } else {
            SvgCore.getLogger().debug("WebSocket: audioSender is null for uuid=" + connection.getUuid() + ", dropping binary frame");
        }
    }

    /**
     * Code that runs when client closes the session
     * @param statusCode code
     * @param reason close reason
     */
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

    /**
     * Code that runs when an error is received from the client
     * @param error error received
     */
    @OnWebSocketError
    public void onError(Throwable error) {
        if (error instanceof org.eclipse.jetty.websocket.api.exceptions.WebSocketTimeoutException
                || error instanceof org.eclipse.jetty.websocket.core.exception.WebSocketTimeoutException) {

            String sessionName = connection != null ? connection.getPlayer().getName() : "unknown session";
            SvgCore.getLogger().info("[WebSocket] Session timed out for " + sessionName);
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

    /**
     * Send a Raw message to client
     * @param type message type
     * @param message message content
     * @param fatal whether the message is fatal
     */
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

    /**
     * Send a JSON message to the client
     * @param json message
     * @return success
     */
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

    //Getters. ONLY used in packet handlers, not for external use.
    // These should not be exposed to any outside classes.

    /**
     * Get the associated SvgConnection for this WebSocket
     * @return SvgConnection
     */
    public SvgConnection getConnection() {
        return connection;
    }

    /**
     * Get the Negotiation Session for audio type
     * @return audio negotiation
     */
    public AudioSessionNegotiation getAudioNegotiation() {
        return audioNegotiation;
    }

    /**
     * Get the underlying Session
     * @return Session
     */
    public Session getSession() {
        return session;
    }

    /**
     * Set the Connection associated with the session
     * @param connection connection
     */
    public void setConnection(SvgConnection connection) {
        this.connection = connection;
    }

    /**
     * Add a JoinAttempt
     * @return the join attempts
     */
    public long addJoinAttempt() {
        ++joinAttemptCount;
        return joinAttemptCount;
    }

    /**
     * Add the amount of Capability messages received from the client
     * @return the count
     */
    public long addCapabilityMessage() {
        ++capabilityMessageCount;
        return capabilityMessageCount;
    }

    /**
     * Set the AudioNegotiation as received from the Client
     * @param audioNegotiation the audio Negotiation
     */
    public void setAudioNegotiation(AudioSessionNegotiation audioNegotiation) {
        this.audioNegotiation = audioNegotiation;
    }
}
