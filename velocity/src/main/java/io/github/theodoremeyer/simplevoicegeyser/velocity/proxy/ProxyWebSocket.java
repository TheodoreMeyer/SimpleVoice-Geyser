package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import com.velocitypowered.api.proxy.Player;
import io.github.theodoremeyer.simplevoicegeyser.velocity.VelocityPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Server-side handler for one browser WebSocket session.
 * <p>
 * Until a session is authenticated via a {@code join} message it only accepts
 * that handshake; afterwards all text and binary frames are relayed to the
 * player's current backend through a {@link BackendRelay}. When the player
 * switches servers, {@link #reconnectBackend(String)} moves the same browser
 * session onto the new backend.
 */
@WebSocket
public final class ProxyWebSocket {

    private static final int MAX_WEB_CHAT_LENGTH = 200;

    private final VelocityPlugin plugin;

    private Session session;
    private BackendRelay relay;
    private UUID playerUuid;
    private String playerName;
    private JSONObject lastJoinRequest;
    private JSONObject lastCapabilitiesRequest;
    private String currentBackendUrl;
    private final Object lifecycleLock = new Object();

    /**
     * Create a handler for a new browser session.
     *
     * @param plugin the plugin instance used for config, auth, and session tracking
     */
    public ProxyWebSocket(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when the browser connects; applies the configured idle timeout.
     *
     * @param session the newly opened Jetty WebSocket session
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(plugin.getProxyIdleTimeoutMinutes()));
        plugin.getLogger().info("[Proxy] WebSocket connected: " + session.getRemoteAddress());
    }

    /**
     * Handle a text frame from the browser. Before authentication only a
     * {@code join} request is accepted; afterwards frames are relayed to the
     * backend, with {@code capabilities} messages also cached for reconnects.
     *
     * @param message raw text frame received from the browser
     */
    @OnWebSocketMessage
    public void onMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        synchronized (lifecycleLock) {
            if (relay != null) {
                message = message.trim();
                if (message.startsWith("{")) {
                    try {
                        JSONObject json = new JSONObject(message);
                        if ("capabilities".equals(json.optString("type", ""))) {
                            this.lastCapabilitiesRequest = new JSONObject(json.toString());
                            relay.updateCapabilitiesPayload(json.toString());
                        }
                    } catch (Exception ignored) {
                    }
                }
                relay.forwardText(message);
                return;
            }
        }

        message = message.trim();
        if (!message.startsWith("{")) {
            sendRaw(ConnectionStates.MessageType.ERROR, "Invalid input. Expected a JSON object.", false);
            return;
        }

        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");

            if (!"join".equals(type)) {
                sendRaw(ConnectionStates.MessageType.ERROR, "Access Denied: Not authenticated.", false);
                return;
            }

            join(json);
        } catch (Exception e) {
            plugin.getLogger().debug("[Proxy] Error reading client data", e);
            sendRaw(ConnectionStates.MessageType.ERROR, "Malformed message.", false);
        }
    }

    /**
     * Handle a binary frame from the browser; ignored until authenticated.
     *
     * @param buffer buffer containing the frame
     * @param offset start offset of the frame within {@code buffer}
     * @param length number of bytes in the frame
     */
    @OnWebSocketMessage
    public void onMessage(byte[] buffer, int offset, int length) {
        synchronized (lifecycleLock) {
            if (relay == null) {
                return;
            }
            relay.forwardBinary(buffer, offset, length);
        }
    }

    /**
     * Called when the browser disconnects; tears down the backend relay and
     * unregisters the session from the plugin.
     *
     * @param statusCode WebSocket close code sent by the browser
     * @param reason     human-readable close reason
     */
    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        plugin.getLogger().debug("[Proxy] Session close status=" + statusCode + " reason=" + reason);
        synchronized (lifecycleLock) {
            if (relay != null) {
                relay.close(statusCode, reason);
                relay = null;
            }
            currentBackendUrl = null;
            lastJoinRequest = null;
            lastCapabilitiesRequest = null;
        }
        if (playerUuid != null) {
            plugin.unregisterSession(playerUuid, this);
        }
        playerUuid = null;
        playerName = null;
    }

    /**
     * Called on WebSocket transport errors; logged at debug level only.
     *
     * @param error the error thrown by the transport
     */
    @OnWebSocketError
    public void onError(Throwable error) {
        plugin.getLogger().debug("[Proxy] websocket error", error);
    }

    /**
     * Tear down the backend relay because the player left the network,
     * closing the browser session with {@link ConnectionStates.DisconnectCodes#PLAYER_LEAVE}.
     */
    public synchronized void onProxyDisconnect() {
        synchronized (lifecycleLock) {
            if (relay != null) {
                relay.close(ConnectionStates.DisconnectCodes.PLAYER_LEAVE.getCode(), "left the game");
                relay = null;
            }
            currentBackendUrl = null;
            lastJoinRequest = null;
            lastCapabilitiesRequest = null;
        }
    }

    /**
     * Move an authenticated browser session to a different backend, e.g. when
     * the player switched Velocity servers. Rebuilds the join payload with a
     * fresh proxy token for the new backend.
     *
     * @param backendUrl WebSocket URL of the new backend
     */
    public void reconnectBackend(String backendUrl) {
        synchronized (lifecycleLock) {
            if (relay == null || lastJoinRequest == null || playerUuid == null) {
                return;
            }

        if (backendUrl == null || backendUrl.isBlank() || backendUrl.equals(currentBackendUrl)) {
            return;
        }

        String clientName = playerUuid == null ? "default" : plugin.getServer().getPlayer(playerUuid)
                .flatMap(player -> player.getCurrentServer())
                .map(connection -> connection.getServerInfo().getName())
                .orElse("default");
        JSONObject join = buildBackendJoinPayload(clientName);
        relay.updateJoinPayload(join.toString());
        if (lastCapabilitiesRequest != null) {
            relay.updateCapabilitiesPayload(lastCapabilitiesRequest.toString());
        }
        relay.reconnect(backendUrl);
        currentBackendUrl = backendUrl;
        }
    }

    private void join(@NonNull JSONObject json) {
        if (relay != null) {
            sendRaw(ConnectionStates.MessageType.ERROR, "Already authenticated.", false);
            return;
        }

        String username = json.optString("username", "").trim();
        String password = json.optString("password", "");
        if (username.isEmpty()) {
            sendRaw(ConnectionStates.MessageType.ERROR, "Username required.", false);
            return;
        }

        Player player = plugin.getServer().getPlayer(username).orElse(null);
        if (player == null) {
            sendRaw(ConnectionStates.MessageType.ERROR, "Timeout: You didn’t join the server in time.", false);
            return;
        }

        if (!plugin.getPasswordStore().validatePassword(username, password, player.getUniqueId())) {
            sendRaw(ConnectionStates.MessageType.ERROR, "Access Denied: Invalid username or password.", false);
            return;
        }

        String clientName = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("default");
        String backendUrl = plugin.resolveClientUrl(clientName);
        if (backendUrl == null || backendUrl.isBlank()) {
            sendRaw(ConnectionStates.MessageType.ERROR, "Proxy backend is not configured for this server.", false);
            return;
        }

        this.playerUuid = player.getUniqueId();
        this.playerName = player.getUsername();
        JSONObject sanitizedJoin = new JSONObject(json.toString());
        sanitizedJoin.remove("password");
        synchronized (lifecycleLock) {
            this.lastJoinRequest = sanitizedJoin;

        JSONObject backendJoin = buildBackendJoinPayload(clientName);
        this.relay = new BackendRelay(session, plugin.getLogger());
        this.currentBackendUrl = backendUrl;
        plugin.registerSession(playerUuid, this);
        relay.connect(backendUrl, backendJoin.toString());
        }
    }

    private JSONObject buildBackendJoinPayload(String clientName) {
        JSONObject join = new JSONObject(lastJoinRequest.toString());
        join.put("password", "");
        join.put("proxyToken", plugin.createProxyToken(playerUuid, playerName, clientName));
        return join;
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
            plugin.getLogger().debug("[Proxy] Failed to send raw packet", e);
        }
    }
}
