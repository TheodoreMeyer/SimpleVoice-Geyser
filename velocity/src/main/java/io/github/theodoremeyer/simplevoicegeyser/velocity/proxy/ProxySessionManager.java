package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import io.github.theodoremeyer.simplevoicegeyser.velocity.VelocityPlugin;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data.ProxyPasswordStore;
import org.json.JSONObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages active WebSocket browser sessions, server change pending states,
 * and transfer timeouts for the Velocity proxy.
 */
public final class ProxySessionManager {

    private final VelocityPlugin plugin;
    private final Map<UUID, ProxyWebSocket> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingServerChanges = new ConcurrentHashMap<>();
    private final ScheduledExecutorService transferTimeouts = Executors.newSingleThreadScheduledExecutor();

    /**
     * Create a new session manager for the Velocity plugin.
     *
     * @param plugin the parent Velocity plugin instance
     */
    public ProxySessionManager(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Track an authenticated browser session for a player. If another session
     * is already registered for that player, it is disconnected first.
     *
     * @param uuid   UUID of the player
     * @param socket authenticated browser session
     */
    public void registerSession(UUID uuid, ProxyWebSocket socket) {
        ProxyWebSocket replaced = activeSessions.put(uuid, socket);
        if (replaced != null && replaced != socket) {
            replaced.onProxyDisconnect();
        }
    }

    /**
     * Remove a browser session from tracking, but only if it is still the one registered.
     *
     * @param uuid   UUID of the player
     * @param socket session expected to currently be registered
     */
    public void unregisterSession(UUID uuid, ProxyWebSocket socket) {
        activeSessions.computeIfPresent(uuid, (ignored, current) -> current == socket ? null : current);
    }

    /**
     * Get active session for a player UUID.
     *
     * @param uuid UUID of the player
     * @return active WebSocket session or null
     */
    public ProxyWebSocket getActiveSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    /**
     * Move a player's authenticated browser session when they switch servers.
     *
     * @param event server connected event
     */
    public void onServerConnected(ServerConnectedEvent event) {
        ProxyWebSocket socket = activeSessions.get(event.getPlayer().getUniqueId());
        if (socket == null) {
            return;
        }

        String backendUrl = plugin.resolveClientUrl(event.getServer().getServerInfo().getName());
        if (backendUrl != null && !backendUrl.isBlank()) {
            socket.reconnectBackend(event.getServer().getServerInfo().getName(), backendUrl);
        }
    }

    /**
     * Clean up session tracking when a player disconnects from the proxy network.
     *
     * @param event disconnect event
     */
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingServerChanges.remove(uuid);
        ProxyWebSocket socket = activeSessions.remove(uuid);
        if (socket != null) {
            socket.onProxyDisconnect();
        }
    }

    /**
     * Update player state (password set status and server change pending status)
     * called by backend control endpoints.
     *
     * @param uuid                  UUID of the player
     * @param username              username of the player
     * @param joined                whether player joined or left
     * @param passwordStore         password store
     * @param player                player instance or null
     * @param transferTimeoutSeconds timeout window in seconds
     * @return JSONObject containing status state
     */
    public JSONObject updatePlayerState(UUID uuid, String username, boolean joined,
                                        ProxyPasswordStore passwordStore, Player player,
                                        int transferTimeoutSeconds) {
        if (player == null || !player.getUsername().equalsIgnoreCase(username)) {
            return new JSONObject().put("passwordSet", false).put("changingServer", false);
        }

        if (joined) {
            boolean changingServer = pendingServerChanges.remove(uuid) != null;
            return new JSONObject()
                    .put("passwordSet", passwordStore.isPasswordSet(username))
                    .put("changingServer", changingServer);
        }

        ProxyWebSocket socket = activeSessions.get(uuid);
        if (socket == null) {
            return new JSONObject().put("passwordSet", passwordStore.isPasswordSet(username)).put("changingServer", false);
        }

        long deadline = System.currentTimeMillis() + transferTimeoutSeconds * 1000L;
        pendingServerChanges.put(uuid, deadline);
        socket.beginServerChange();
        transferTimeouts.schedule(() -> {
            Long currentDeadline = pendingServerChanges.get(uuid);
            if (currentDeadline != null && currentDeadline == deadline) {
                pendingServerChanges.remove(uuid);
                activeSessions.remove(uuid, socket);
                socket.onProxyDisconnect();
            }
        }, transferTimeoutSeconds, TimeUnit.SECONDS);

        return new JSONObject()
                .put("passwordSet", passwordStore.isPasswordSet(username))
                .put("changingServer", true);
    }

    /**
     * Shut down all session tracking, transfer timeouts, and disconnect active WebSockets.
     */
    public void shutdown() {
        transferTimeouts.shutdownNow();
        activeSessions.values().forEach(ProxyWebSocket::onProxyDisconnect);
        activeSessions.clear();
        pendingServerChanges.clear();
    }
}
