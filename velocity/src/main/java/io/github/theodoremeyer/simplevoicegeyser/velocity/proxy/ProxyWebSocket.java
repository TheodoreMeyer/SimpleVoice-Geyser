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

    public ProxyWebSocket(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(plugin.getProxyIdleTimeoutMinutes()));
        plugin.getLogger().info("[Proxy] WebSocket connected: " + session.getRemoteAddress());
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

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

    @OnWebSocketMessage
    public void onMessage(byte[] buffer, int offset, int length) {
        if (relay == null) {
            return;
        }
        relay.forwardBinary(buffer, offset, length);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        plugin.getLogger().debug("[Proxy] Session close status=" + statusCode + " reason=" + reason);
        if (relay != null) {
            relay.close(statusCode, reason);
            relay = null;
        }
        if (playerUuid != null) {
            plugin.unregisterSession(playerUuid, this);
        }
        currentBackendUrl = null;
        lastJoinRequest = null;
        lastCapabilitiesRequest = null;
        playerUuid = null;
        playerName = null;
    }

    @OnWebSocketError
    public void onError(Throwable error) {
        plugin.getLogger().debug("[Proxy] websocket error", error);
    }

    public synchronized void onProxyDisconnect() {
        if (relay != null) {
            relay.close(ConnectionStates.DisconnectCodes.PLAYER_LEAVE.getCode(), "left the game");
            relay = null;
        }
        currentBackendUrl = null;
        lastJoinRequest = null;
        lastCapabilitiesRequest = null;
    }

    public synchronized void reconnectBackend(String backendUrl) {
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

        if (!plugin.getPasswordStore().validatePassword(username, password)) {
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
        this.lastJoinRequest = new JSONObject(json.toString());

        JSONObject backendJoin = buildBackendJoinPayload(clientName);
        this.relay = new BackendRelay(session, plugin.getLogger());
        this.currentBackendUrl = backendUrl;
        plugin.registerSession(playerUuid, this);
        relay.connect(backendUrl, backendJoin.toString());
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
