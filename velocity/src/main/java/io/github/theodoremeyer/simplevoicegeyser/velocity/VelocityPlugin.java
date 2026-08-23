package io.github.theodoremeyer.simplevoicegeyser.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.theodoremeyer.simplevoicegeyser.velocity.proxy.ProxyAuthToken;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data.ProxyPasswordStore;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data.VelocityConfigFile;
import io.github.theodoremeyer.simplevoicegeyser.velocity.proxy.ProxyJettyServer;
import io.github.theodoremeyer.simplevoicegeyser.velocity.proxy.ProxyWebSocket;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/**
 * Velocity plugin entry point for the SimpleVoice-Geyser proxy frontend.
 * <p>
 * Hosts a web server browsers connect to, tracks authenticated browser
 * sessions per player ({@link #activeSessions}), and moves each session to
 * the backend matching the player's current Velocity server. Also registers
 * the {@code /svg} command used to set web client passwords.
 */
@Plugin(
        id = "simplevoice-geyser",
        name = "SimpleVoice-Geyser",
        version = "0.1.2",
        description = "Proxy frontend for Simple Voice Geyser.",
        authors = {"TheodoreMeyer"}
)
public final class VelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private final Map<UUID, ProxyWebSocket> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingServerChanges = new ConcurrentHashMap<>();
    private final ScheduledExecutorService transferTimeouts = Executors.newSingleThreadScheduledExecutor();

    private VelocityConfigFile configFile;
    private ProxyPasswordStore passwordStore;
    private ProxyJettyServer webServer;

    /**
     * Create the plugin container; dependencies are provided by Velocity's
     * injection framework.
     *
     * @param server        Velocity proxy server instance
     * @param logger        SLF4J logger scoped to this plugin
     * @param dataDirectory plugin data directory (created on initialize)
     */
    @Inject
    public VelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * Initialize the plugin on proxy startup: load config, register commands,
     * and start the web frontend.
     *
     * @param event proxy initialization event
     */
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        File dataDir = dataDirectory.toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.configFile = new VelocityConfigFile(new File(dataDir, "config.json"));
        ensureProxyDefaults();
        this.passwordStore = new ProxyPasswordStore(dataDir, logger);

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("svg").build(),
                new VelocityCommand(passwordStore)
        );

        int port = configFile.getInt("proxy.port", 8080);
        String host = configFile.getString("proxy.bind_address", "0.0.0.0");
        Duration idleTimeout = Duration.ofMinutes(2);

        try {
            File certificate = null;
            File key = null;
            if ("file".equalsIgnoreCase(configFile.getString("ssl.type", "none"))) {
                certificate = resolveConfigPath(configFile.getString("ssl.file.cert", "ssl/cert.pem"));
                key = resolveConfigPath(configFile.getString("ssl.file.key", "ssl/key.pem"));
            }
            this.webServer = new ProxyJettyServer(host, port, idleTimeout, certificate, key);
            webServer.start(this);
            logger.info("[Proxy] Web frontend started on {}:{}", host, port);
            logger.info("[Proxy] Serving web client build {}", BuildInfo.BUILD_ID);
        } catch (Exception e) {
            logger.error("[Proxy] Failed to start web frontend", e);
        }
    }

    /**
     * Move a player's authenticated browser session to the backend matching
     * the server they just connected to.
     *
     * @param event server connection event
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        ProxyWebSocket socket = activeSessions.get(event.getPlayer().getUniqueId());
        if (socket == null) {
            return;
        }

        String backendUrl = resolveClientUrl(event.getServer().getServerInfo().getName());
        if (backendUrl != null && !backendUrl.isBlank()) {
            socket.reconnectBackend(event.getServer().getServerInfo().getName(), backendUrl);
        }
    }

    /**
     * Close a player's browser session when they disconnect from the network.
     *
     * @param event player disconnect event
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        pendingServerChanges.remove(event.getPlayer().getUniqueId());
        ProxyWebSocket socket = activeSessions.remove(event.getPlayer().getUniqueId());
        if (socket != null) {
            socket.onProxyDisconnect();
        }
    }

    /**
     * Shut down all browser sessions and stop the web frontend.
     *
     * @param event proxy shutdown event
     */
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        transferTimeouts.shutdownNow();
        activeSessions.values().forEach(ProxyWebSocket::onProxyDisconnect);
        activeSessions.clear();
        try {
            if (webServer != null) {
                webServer.stop();
            }
        } catch (Exception e) {
            logger.debug("[Proxy] Failed to stop web server", e);
        }
    }

    /**
     * Get the proxy server instance.
     * @return Velocity's {@link ProxyServer}
     */
    public ProxyServer getServer() {
        return server;
    }

    /**
     * Get this plugin's logger.
     * @return SLF4J logger scoped to this plugin
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Get the web client password store.
     * @return the shared {@link ProxyPasswordStore}, or {@code null} before initialization
     */
    public ProxyPasswordStore getPasswordStore() {
        return passwordStore;
    }

    /**
     * Get how long a browser session may stay idle before being dropped.
     * @return idle timeout in minutes
     */
    public int getProxyIdleTimeoutMinutes() {
        return 2;
    }

    public boolean isValidControlSecret(String secret) {
        String configured = configFile.getString("proxy.shared_secret", "");
        return secret != null && !configured.isBlank() && configured.equals(secret);
    }

    public JSONObject updatePlayerState(UUID uuid, String username, boolean joined) {
        Player player = server.getPlayer(uuid).orElse(null);
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

        long deadline = System.currentTimeMillis() + getTransferTimeoutSeconds() * 1000L;
        pendingServerChanges.put(uuid, deadline);
        socket.beginServerChange();
        transferTimeouts.schedule(() -> {
            Long currentDeadline = pendingServerChanges.get(uuid);
            if (currentDeadline != null && currentDeadline == deadline) {
                pendingServerChanges.remove(uuid);
                activeSessions.remove(uuid, socket);
                socket.onProxyDisconnect();
            }
        }, getTransferTimeoutSeconds(), TimeUnit.SECONDS);

        return new JSONObject()
                .put("passwordSet", passwordStore.isPasswordSet(username))
                .put("changingServer", true);
    }

    private int getTransferTimeoutSeconds() {
        return Math.max(1, configFile.getInt("proxy.transfer-timeout-seconds", 60));
    }

    /**
     * Track an authenticated browser session for a player. If another session
     * is already registered for that player it is disconnected first.
     *
     * @param uuid   UUID of the player the session belongs to
     * @param socket the authenticated browser session
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
     * Resolve the backend WebSocket URL configured for the server a player is on.
     *
     * @param player player whose current server is used as lookup key
     * @return configured backend URL, or {@code ""} when unconfigured
     */
    public String resolveClientUrl(Player player) {
        String serverName = player.getCurrentServer()
                .map(conn -> conn.getServerInfo().getName())
                .orElse("default");
        return resolveClientUrl(serverName);
    }

    /**
     * Resolve the backend WebSocket URL configured under {@code clients.<name>.url},
     * falling back to {@code default} for blank or unknown names.
     *
     * @param clientName name of the backend client entry in config
     * @return configured backend URL, or {@code ""} when unconfigured
     */
    public String resolveClientUrl(String clientName) {
        String normalized = clientName == null ? "default" : clientName.trim();
        if (normalized.isEmpty()) {
            normalized = "default";
        }

        return configFile.getString("clients." + normalized + ".url", "");
    }

    /**
     * Sign a proxy auth token for a player using the secret configured for the
     * given backend client entry: its per-client secret, or the global
     * {@code proxy.shared_secret} when {@code auth.global} is enabled.
     *
     * @param uuid       UUID of the player to issue the token for
     * @param username   username of the player to issue the token for
     * @param clientName name of the backend client entry the token targets
     * @return the signed token string for the browser to present to the backend
     * @throws IllegalStateException if no non-blank signing secret is configured
     */
    public synchronized String createProxyToken(UUID uuid, String username, String clientName) {
        String normalized = clientName == null ? "default" : clientName.trim();
        if (normalized.isEmpty()) {
            normalized = "default";
        }
        String authPath = "clients." + normalized + ".auth";
        boolean global = configFile.getBoolean(authPath + ".global", true);
        String secret = global ? configFile.getString("proxy.shared_secret", "")
                : configFile.getString(authPath + ".secret", "");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("No nonblank signing secret configured for client " + normalized);
        }
        int ttlSeconds = configFile.getInt("proxy.token-ttl-seconds", 120);
        return ProxyAuthToken.create(uuid, username, secret, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * Sign a proxy auth token for a player targeting the {@code default} client entry.
     *
     * @param player player to issue the token for
     * @return the signed token string
     * @throws IllegalStateException if no signing secret is configured
     */
    public String createProxyToken(Player player) {
        return createProxyToken(player.getUniqueId(), player.getUsername(), "default");
    }

    private void ensureProxyDefaults() {
        configFile.migrateFromBundledDefaults("proxy");
        configFile.save();
    }

    private File resolveConfigPath(String configuredPath) {
        File path = new File(configFile.getFile().getParentFile(), configuredPath);
        if (!path.isFile()) {
            throw new IllegalArgumentException("SSL file does not exist: " + path.getAbsolutePath());
        }
        return path;
    }

    private void ensureDefault(String key, Object value) {
        if (!configFile.has(key)) {
            configFile.set(key, value);
        }
    }

}
