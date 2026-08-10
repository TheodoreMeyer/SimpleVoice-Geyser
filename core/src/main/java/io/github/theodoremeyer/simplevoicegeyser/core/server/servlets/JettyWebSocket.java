package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioTransportMode;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.HandshakeState;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.HandshakeTracer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SessionMode;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.ConnectionAuthenticator;
import io.github.theodoremeyer.simplevoicegeyser.core.server.packets.PacketHandler;
import io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit.RateLimitResult;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.exceptions.WebSocketException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class that handles talking to the Client
 */
@WebSocket
public final class JettyWebSocket {

    private static final AtomicLong CORRELATION_SEQUENCE = new AtomicLong();

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
    private HandshakeTracer handshake;
    private final AtomicReference<String> closeInitiator = new AtomicReference<>("none");
    private final AtomicBoolean closeLogged = new AtomicBoolean(false);

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
        this.handshake = new HandshakeTracer("ws-" + CORRELATION_SEQUENCE.incrementAndGet());
        session.setIdleTimeout(Duration.ofMinutes(SvgCore.getConfig().IDLE_TIMEOUT.get()));
        AudioTransportMode preference = AudioTransportMode.fromConfig(SvgCore.getConfig());
        boolean allowLegacyFallback = Boolean.TRUE.equals(SvgCore.getConfig().AUDIO_ALLOW_LEGACY_FALLBACK.get());

        this.audioNegotiation = new AudioSessionNegotiation(preference, allowLegacyFallback);
        SvgCore.getLogger().info("[Websocket] WebSocket connected corr=" + handshake.getCorrelationId());
        SvgCore.getLogger().debug(
                "WebSocket: Session opened corr=" + handshake.getCorrelationId()
                        + " thread=" + Thread.currentThread().getName()
        );
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
            if (connection != null && connection.getUuid() != null) {
                RateLimitResult controlLimit = SvgCore.getRateLimitService().tryControlPacket(
                        connection.getUuid().toString(),
                        message.getBytes(StandardCharsets.UTF_8).length
                );
                if (!controlLimit.allowed()) {
                    String operationId = json.optString("operationId", null);
                    if (operationId != null && connection.isAuthenticated()) {
                        long revision = SvgCore.getGroupManager() == null
                                ? 0L
                                : SvgCore.getGroupManager().getRevision();
                        connection.sendJson(new JSONObject()
                                .put("type", "operation_result")
                                .put("operationId", operationId)
                                .put("success", false)
                                .put("errorCode", "RATE_LIMITED")
                                .put("message", "Please wait before trying again.")
                                .put("retryAfterMs", controlLimit.retryAfterMs())
                                .put("revision", revision));
                    }
                    return;
                }
            }

            String type = json.getString("type");
            SvgCore.getLogger().debug(
                    "WebSocket: Control message #" + controlMessageCount
                            + " type=" + type
                            + " corr=" + correlationId()
                            + " state=" + handshakeState()
                            + " thread=" + Thread.currentThread().getName()
            );

            packetHandler.handle(this, json);

        } catch (Exception e) {
            SvgCore.getLogger().severe("[VCBridge] Exception: " + e.getMessage());
            SvgCore.getLogger().debug("VCBridge: error reading client data", e);
            if (handshake != null) {
                handshake.fail("control-exception", playerUuidOrNull(), isSocketOpen(), e);
            }
            markCloseInitiator("server-exception");
            rejectAndClose(
                    ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(),
                    "handler_exception",
                    "Internal voice-chat handler error."
            );
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
        if (connection == null || !connection.isAuthenticated() || handshakeState() != HandshakeState.READY) {
            SvgCore.getLogger().debug(
                    "WebSocket: Dropping pre-ready binary frame bytes=" + length
                            + " corr=" + correlationId()
                            + " state=" + handshakeState()
                            + " authenticated=" + (connection != null && connection.isAuthenticated())
            );
            return;
        }

        if (connection.getSessionMode() != SessionMode.WEB_VOICE) {
            SvgCore.getLogger().debug(
                    "WebSocket: Rejecting binary audio for sessionMode="
                            + connection.getSessionMode()
                            + " uuid=" + connection.getUuid()
            );
            return;
        }

        RateLimitResult audioLimit = SvgCore.getRateLimitService().tryAudioFrame(
                connection.getUuid().toString(),
                length
        );
        if (!audioLimit.allowed()) {
            // Aggregated by AudioIngressLimiter — avoid per-frame spam.
            String summary = SvgCore.getRateLimitService().getAudioIngressLimiter().maybeAggregateDropSummary();
            if (summary != null) {
                SvgCore.getLogger().debug(
                        summary + " uuid=" + connection.getUuid()
                );
            }
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
        if (!closeLogged.compareAndSet(false, true)) {
            return;
        }

        String initiator = closeInitiator.get();
        if ("none".equals(initiator)) {
            // Jetty reports 1005 when the peer disappeared without a close frame.
            initiator = statusCode == 1005 ? "client-or-network-no-close-frame" : "client-close-frame";
            closeInitiator.compareAndSet("none", initiator);
        }

        if (handshake != null) {
            handshake.transition(
                    HandshakeState.CLOSED,
                    "onClose:" + initiator,
                    playerUuidOrNull(),
                    false,
                    false
            );
        }

        SvgCore.getLogger().debug(
                "WebSocket: Session close status=" + statusCode
                        + " reason=" + reason
                        + " initiator=" + closeInitiator.get()
                        + " corr=" + correlationId()
                        + " state=" + handshakeState()
                        + " authenticated=" + (connection != null && connection.isAuthenticated())
                        + " controlMessages=" + controlMessageCount
                        + " binaryFrames=" + binaryFrameCount
                        + " transport=" + (audioNegotiation == null ? "n/a" : audioNegotiation.summary())
                        + " thread=" + Thread.currentThread().getName()
        );

        if (connection != null) {
            SvgCore.getLogger().info(
                    "[WebSocket] Closed for "
                            + connection.getPlayer().getName()
                            + ": "
                            + statusCode
                            + " - "
                            + reason
                            + " initiator=" + closeInitiator.get()
            );

            connection.disconnect(statusCode, reason);
            SvgCore.getConnectionManager().remove(connection);
        } else {
            SvgCore.getLogger().info(
                    "[WebSocket] Closed unknown session corr=" + correlationId()
                            + " initiator=" + closeInitiator.get()
                            + " reason=" + reason
            );
        }
    }

    /**
     * Code that runs when an error is received from the client
     * @param error error received
     */
    @OnWebSocketError
    public void onError(Throwable error) {
        if (error instanceof WebSocketException) {
            SvgCore.getLogger().debug("Websocket Timeout: " + error.getMessage());
        }

        markCloseInitiator("server-endpoint-error");
        if (handshake != null) {
            handshake.fail("onError", playerUuidOrNull(), isSocketOpen(), error);
        }
        SvgCore.getLogger().debug("WebSocket: websocket error corr=" + correlationId(), error);
        SvgCore.getLogger().info("Error: " + error.getMessage());
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

    /**
     * Reject the join/auth attempt and close with an explicit application code.
     * Never uses status 1005 (that is a local Jetty/browser placeholder only).
     */
    public void rejectAndClose(int closeCode, String closeReason, String clientMessage) {
        markCloseInitiator("server-reject:" + closeReason);
        if (handshake != null) {
            handshake.fail("reject:" + closeReason, playerUuidOrNull(), isSocketOpen(), null);
        }
        if (clientMessage != null && !clientMessage.isBlank()) {
            sendRaw(ConnectionStates.MessageType.ERROR, clientMessage, true);
        }
        Session active = session;
        if (active != null && active.isOpen()) {
            try {
                active.close(closeCode, closeReason);
            } catch (Exception e) {
                SvgCore.getLogger().debug("WebSocket: Failed to close after reject", e);
            }
        }
    }

    /**
     * Mark who initiated the close before invoking Session#close.
     */
    public void markCloseInitiator(String initiator) {
        closeInitiator.compareAndSet("none", initiator == null ? "server" : initiator);
    }

    public HandshakeTracer getHandshake() {
        return handshake;
    }

    public HandshakeState handshakeState() {
        return handshake == null ? HandshakeState.OPEN : handshake.getState();
    }

    public String correlationId() {
        return handshake == null ? "unassigned" : handshake.getCorrelationId();
    }

    public boolean isSocketOpen() {
        return session != null && session.isOpen();
    }

    public boolean isReady() {
        return connection != null
                && connection.isAuthenticated()
                && handshakeState() == HandshakeState.READY;
    }

    private UUID playerUuidOrNull() {
        return connection == null ? null : connection.getUuid();
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
     * Add the amount of Capability messages received from the Client
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
