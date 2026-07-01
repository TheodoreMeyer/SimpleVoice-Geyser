package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioTransportMode;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioTransportPreference;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.ConnectionAuthenticator;
import io.github.theodoremeyer.simplevoicegeyser.core.server.packets.PacketHandler;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.exceptions.WebSocketException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

/**
 * Wrapper for handling the session with player
 */
@WebSocket
public final class JettyWebSocket {

    /**
     * The authenticator instance used for validating join attempts. This is static and shared across all connections, but is designed to be thread-safe and handle concurrent requests appropriately.
     */
    public static final ConnectionAuthenticator AUTHENTICATOR =
            new ConnectionAuthenticator();

    private final ConnectionManager connectionManager =
            SvgCore.getConnectionManager();

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
     * Create an instance of the class
     */
    public JettyWebSocket() {}

    /**
     * What to do when a player connects
     * @param session the connected session
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(SvgCore.getConfig().IDLE_TIMEOUT.get()));
        AudioTransportPreference preference = AudioTransportPreference.fromConfig(
                SvgCore.getConfig().AUDIO_TRANSPORT_MODE.get()
        );
        boolean allowLegacyFallback = Boolean.TRUE.equals(SvgCore.getConfig().AUDIO_ALLOW_LEGACY_FALLBACK.get());
        this.audioNegotiation = new AudioSessionNegotiation(preference, allowLegacyFallback);
        SvgCore.getLogger().info("[Websocket] WebSocket connected: " + session.getRemoteAddress());
        SvgCore.getLogger().debug("WebSocket: Session opened remote=" + session.getRemoteAddress());
    }

    /**
     * Handles string messages from client
     * @param message message from client
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
     * Handles byte messages from client
     * @param buffer byte buffer
     * @param offset offset
     * @param length length
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
     * Runs to close everything when websocket closes
     * @param statusCode code
     * @param reason why it closed
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
            connectionManager.remove(connection);
        } else {
            SvgCore.getLogger().info("[WebSocket] Closed unknown session: " + reason);
        }
    }

    /**
     * What to do on an error
     * @param error error thrown
     */
    @OnWebSocketError
    public void onError(Throwable error) {
        if (error instanceof WebSocketException) {
            SvgCore.getLogger().debug("Websocket Timeout: " + error.getMessage());
        }

        SvgCore.getLogger().debug("WebSocket: websocket error", error);
        SvgCore.getLogger().info("Error: " + error.getMessage());
    }

    private void capabilities(JSONObject json) {
        JSONObject audio = json.optJSONObject("audio");
        if (audio == null) {
            sendRaw(ConnectionStates.MessageType.ERROR, "Invalid capabilities payload.", false);
            return;
        }

        JSONArray protocols = audio.optJSONArray("protocols");
        boolean supportsLegacy = true;
        boolean supportsSvgV2 = false;
        if (protocols != null) {
            supportsLegacy = false;
            for (int i = 0; i < protocols.length(); i++) {
                String protocol = String.valueOf(protocols.opt(i)).trim().toLowerCase(Locale.ROOT);
                if ("legacy".equals(protocol)) {
                    supportsLegacy = true;
                } else if ("svg-v2".equals(protocol) || "svg_v2".equals(protocol) || "v2".equals(protocol)) {
                    supportsSvgV2 = true;
                }
            }
        }

        JSONObject decoder = audio.optJSONObject("decoder");
        boolean wasm = decoder != null && decoder.optBoolean("opusWasm", false);
        boolean webCodecs = decoder != null && decoder.optBoolean("webCodecs", false);
        boolean supportsOpusDecoder = wasm || webCodecs || audio.optBoolean("supportsOpusDecoder", false);
        boolean secureContext = audio.optBoolean("secureContext", false);

        if (audioNegotiation == null) {
            AudioTransportPreference preference = AudioTransportPreference.fromConfig(
                    SvgCore.getConfig().AUDIO_TRANSPORT_MODE.get()
            );
            boolean allowLegacyFallback = Boolean.TRUE.equals(SvgCore.getConfig().AUDIO_ALLOW_LEGACY_FALLBACK.get());
            audioNegotiation = new AudioSessionNegotiation(preference, allowLegacyFallback);
        }

        audioNegotiation.updateClientCapabilities(
                supportsLegacy,
                supportsSvgV2,
                supportsOpusDecoder,
                secureContext
        );

        AudioTransportMode selected = audioNegotiation.getSelectedMode();
        JSONObject ack = new JSONObject();
        ack.put("type", "capabilities_ack");
        ack.put("selectedMode", selected == AudioTransportMode.SVG_V2 ? "svg-v2" : "legacy");
        ack.put("fallbackCount", audioNegotiation.getFallbackCount());

        try {
            session.getRemote().sendString(ack.toString());
        } catch (IOException e) {
            SvgCore.getLogger().debug("WebSocket: Failed to send capabilities ack", e);
        }

        SvgCore.getLogger().debug(
                "WebSocket: Capabilities #" + capabilityMessageCount
                        + " uuid=" + (connection == null ? "pending" : connection.getUuid())
                        + " legacy=" + supportsLegacy
                        + " svgV2=" + supportsSvgV2
                        + " opusDecoder=" + supportsOpusDecoder
                        + " secure=" + secureContext
                        + " selected=" + selected.name().toLowerCase(Locale.ROOT)
        );
    }

    //Senders

    public void sendRaw(ConnectionStates.MessageType type, String message, boolean fatal) {
        if (session == null || !session.isOpen()) {
            return;
        }

        JSONObject json = new JSONObject();
        json.put("type", type);
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

    //Getters. ONLY used in packet handlers, not for external use.
    // These should not be exposed to any outside classes.
    public SvgConnection getConnection() {
        return connection;
    }

    public AudioSessionNegotiation getAudioNegotiation() {
        return audioNegotiation;
    }

    public Session getSession() {
        return session;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public void setConnection(SvgConnection connection) {
        this.connection = connection;
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
