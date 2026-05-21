package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.ConnectionAuthenticator;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;

/**
 * Websocket session Class.
 */
@WebSocket
public final class JettyWebSocket {

    public static final ConnectionAuthenticator AUTHENTICATOR =
            new ConnectionAuthenticator();

    private final ConnectionManager connectionManager =
            SvgCore.getConnectionManager();

    /**
     * The Session that is the audio client.
     */
    private Session session;

    /**
     * The authenticated connection.
     *
     * Null until login succeeds.
     */
    private SvgConnection connection;


    /**
     * Create a websocket connection with a client
     */
    public JettyWebSocket() {}

    /**
     * When the client connects.
     * @param session the websocket session associated with this class.
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(SvgCore.getConfig().IDLE_TIMEOUT.get()));
        SvgCore.getLogger().info("[Websocket] WebSocket connected: " + session.getRemoteAddress());
    }

    /**
     * Handles all NON vc messages sent by client.
     * @param message message sent by client.
     */
    @OnWebSocketMessage
    public void onMessage(String message) {

        //ignore empty messages
        if (message == null || message.trim().isEmpty()) { //make sure the message is not empty
            return;
        }

        message = message.trim();

        //reject non JSON messages early
        if (!message.startsWith("{")) {
            sendRaw(ConnectionStates.MessageType.ERROR,
                    "Invalid input. Expected a JSON object.", false);
            return;
        }

        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            switch (type) {
                case "join" -> join(json); //handle join inputs.
                case "chat" -> chat(json); //handle chat inputs.
                default ->
                        sendRaw(
                                ConnectionStates.MessageType.ERROR,
                                "Unknown message type: " + type,
                                false
                        );
            }

        } catch (Exception e) {
            SvgCore.getLogger().severe("[VCBridge] code: 1, Exception: " + e.getMessage());
            SvgCore.getLogger().debug("VCBridge: error reading client data", e);
        }

    }

    /**
     * this copy is to handle audio data from the client.
     * I Don't really know too much about how to get pcmData from sent byte, all I know is that this is how Simple Voice Chat, and research wants me to do it
     * @param buffer the byte to process
     * @param offset the offset of the byte
     * @param length the int.
     */
    @OnWebSocketMessage
    public void onMessage(byte[] buffer, int offset, int length) {
        if (connection == null || !connection.isAuthenticated()) {
            return;
        }

        if (connection.getAudioSender() != null) { //make sure the sender is not null
            connection.getAudioSender().sendOpus(Arrays.copyOfRange(buffer, offset, offset + length));
        }
    }

    /**
     * What to do when the websocket closes
     * @param statusCode code of how it exited
     * @param reason why the websocket closed
     */
    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
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
            SvgCore.getLogger().info(
                    "[WebSocket] Closed unknown session: "
                            + reason
            );
        }
    }

    /**
     * What to do on a websocket error
     * @param error the error thrown
     */
    @OnWebSocketError
    public void onError(Throwable error) {
        SvgCore.getLogger().debug("WebSocket: websocket error", error);
        SvgCore.getLogger().info("Error: " + error.getMessage());
    }

    private void join(@NonNull JSONObject json) {
        if (connection != null) {
            connection.sendError(
                    "Already authenticated.",
                    false
            );

            return;
        }

        String username = json.optString("username", "").trim();
        String password = json.optString("password", "");

        ConnectionAuthenticator.AuthResponse response =
                AUTHENTICATOR.authenticate(
                        username,
                        password
                );

        if (!response.success()) {
            sendRaw(
                    ConnectionStates.MessageType.ERROR,
                    "Authentication failed: " + response.message(),
                    false
            );
            return;
        }

        SvgConnection newConnection =
                connectionManager.connect(
                        response.uuid(),
                        session,
                        response.player()
                );

        if (newConnection == null) {

            sendRaw(
                    ConnectionStates.MessageType.ERROR,
                    "Failed to create connection.",
                    true
            );

            return;
        }

        this.connection = newConnection;

        try {
            connection.authenticate();
        } catch (Exception e) {

            SvgCore.getLogger().debug("WebSocket: Failed to authenticate voice connection", e);

            connection.sendFatal("Failed to initialize voice chat.",
                    4003, "voice_init_failure"
            );

            return;
        }

        if (SvgCore.getConfig()
                .DEFAULT_GROUP_ENABLED
                .get()) {

            SvgCore.getGroupManager().createGroup(
                    response.player(),
                    "Svg",
                    SvgCore.getConfig()
                            .DEFAULT_GROUP_PASSWORD
                            .get(),
                    de.maxhenkel.voicechat.api.Group.Type.OPEN,
                    false,
                    true
            );
        }

        connection.sendMessage(ConnectionStates.MessageType.STATUS,
                "Connected as " + connection.getPlayer().getName() + ".",
                false
        );

        SvgCore.getLogger().info("[WebSocket] "
                + connection.getPlayer().getName() + " authenticated."
        );
    }


    private void chat(JSONObject json) {
        if (connection == null ||
                !connection.isAuthenticated()) {

            sendRaw(ConnectionStates.MessageType.ERROR,
                    "Access Denied: Not authenticated.", false
            );

            return;
        }

        String message = json.optString("message", "").trim();

        if (message.isEmpty()) {
            return;
        }

        SvgPlayer player = connection.getPlayer();

        connection.sendMessage(ConnectionStates.MessageType.CHAT, "You: " + message, false);

        if (player != null) {
            player.chat("[Web Chat] " + player.getName() + ": " + SvgColor.BLUE + message);
        }
    }

    private void sendRaw(ConnectionStates.MessageType type, String message, boolean fatal) {

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
}
