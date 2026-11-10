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

/**
 * Represents a WebSocket connection to a client, handling both control messages and binary audio frames.
 * This class is responsible for managing the WebSocket lifecycle, including connection establishment,
 * message handling, and connection closure. It also manages the negotiation of audio transport modes and
 * microphone formats based on the client's capabilities and the server's configuration.
 */
@WebSocket
public final class JettyWebSocket {

    /**
     * Authenticator for validating incoming WebSocket connections. This is used to ensure that only authorized clients can establish a connection.
     * The authenticator checks the client's credentials and determines whether the connection should be accepted or rejected
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
     * Create a client connection instance.
     */
    public JettyWebSocket() {}

    /**
     * Handles connection establishment.
     * @param session the WebSocket session
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        try {
            this.session = session;
            session.setIdleTimeout(Duration.ofMinutes(SvgCore.getConfig().IDLE_TIMEOUT.get()));
            AudioTransportMode preference = AudioTransportMode.fromConfig(SvgCore.getConfig());
            boolean allowLegacyFallback = Boolean.TRUE.equals(SvgCore.getConfig().AUDIO_ALLOW_LEGACY_FALLBACK.get());

            this.audioNegotiation = new AudioSessionNegotiation(preference, allowLegacyFallback,
                    Boolean.TRUE.equals(SvgCore.getConfig().AUDIO_MIC_ALLOW_LEGACY.get()));
            SvgCore.getLogger().info("[Websocket] WebSocket connected: " + session.getRemoteAddress());
            SvgCore.getLogger().debug("WebSocket: Session opened remote=" + session.getRemoteAddress());
        } catch (RuntimeException e) {
            SvgCore.getLogger().severe("[WebSocket] Failed to initialize connection: " + e.getMessage());
            closeWithError("Server audio configuration is invalid.");
        }
    }

    /**
     * Handles incoming messages from the client. This method processes both control messages (JSON) and binary audio frames.
     * @param message incoming message from the client
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

        } catch (RuntimeException e) {
            SvgCore.getLogger().severe("[VCBridge] Exception: " + e.getMessage());
            SvgCore.getLogger().debug("VCBridge: error reading client data", e);
            sendRaw(ConnectionStates.MessageType.ERROR, "Unable to process websocket message.", false);
        }
    }

    /**
     * Handles incoming binary messages from the client. This method processes audio frames sent by the client.
     * @param buffer the byte array containing the binary data
     * @param offset the starting offset in the buffer
     * @param length the number of bytes to read from the buffer
     */
    @OnWebSocketMessage
    public void onMessage(byte[] buffer, int offset, int length) {
        if (buffer == null || offset < 0 || length < 0 || offset > buffer.length - length) {
            SvgCore.getLogger().debug("WebSocket: Dropping invalid binary frame");
            return;
        }
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

        try {
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
        } catch (RuntimeException e) {
            SvgCore.getLogger().debug("WebSocket: Failed to queue audio frame", e);
        }
    }

    /**
     * Handles the closure of the WebSocket connection. This method is called when the connection is closed, either by the client or the server.
     * @param statusCode the status code indicating the reason for closure
     * @param reason the reason for closure
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
     * Handles errors that occur during the WebSocket connection. This method is called when an error is encountered.
     * @param error the Throwable representing the error that occurred
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

    //Senders

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

    private void closeWithError(String message) {
        sendRaw(ConnectionStates.MessageType.ERROR, message, true);
        if (session != null && session.isOpen()) {
            try {
                session.close(ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(), "server_initialization_failed");
            } catch (Exception ignored) {
            }
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

    /**
     * Enforces the microphone format policy based on the server's configuration and the client's capabilities.
     * If the selected microphone format is not allowed, an error message is sent to the client and the connection is closed.
     * @return true if the microphone format is allowed, false otherwise
     */
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

    /**
     * get API connection object for this websocket
     * @return {@link SvgConnection} object
     */
    public SvgConnection getConnection() {
        return connection;
    }

    /**
     * Get the audio negotiation state.
     * @return audio negotiation
     */
    public AudioSessionNegotiation getAudioNegotiation() {
        return audioNegotiation;
    }

    /**
     * Get the underlying Session.
     * @return Session
     */
    public Session getSession() {
        return session;
    }

    /**
     * Set the associated SvgConnection.
     * @param connection connection
     */
    public void setConnection(SvgConnection connection) {
        this.connection = connection;
    }

    /**
     * Increment the join attempt count and return the new value.
     * @return the updated join attempt count
     */
    public long addJoinAttempt() {
        ++joinAttemptCount;
        return joinAttemptCount;
    }

    /**
     * Increment the capability message count and return the new value.
     * @return the updated capability message count
     */
    public long addCapabilityMessage() {
        ++capabilityMessageCount;
        return capabilityMessageCount;
    }

    /**
     * Set the audio negotiation state for this WebSocket.
     * @param audioNegotiation the AudioSessionNegotiation instance to set
     */
    public void setAudioNegotiation(AudioSessionNegotiation audioNegotiation) {
        this.audioNegotiation = audioNegotiation;
    }
}
