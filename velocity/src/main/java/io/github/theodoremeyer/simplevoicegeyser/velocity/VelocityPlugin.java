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
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.ProxyAuthToken;
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

    private VelocityConfigFile configFile;
    private ProxyPasswordStore passwordStore;
    private ProxyJettyServer webServer;

    @Inject
    public VelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        server.getEventManager().register(this, this);

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

        int port = configFile.getInt("proxy.web.port", 8081);
        String host = configFile.getString("proxy.web.bind-address", "0.0.0.0");
        Duration idleTimeout = Duration.ofMinutes(configFile.getInt("proxy.web.idle-timeout-minutes", 2));

        this.webServer = new ProxyJettyServer(host, port, idleTimeout);
        try {
            webServer.start(this);
            logger.info("[Proxy] Web frontend started on {}:{}", host, port);
        } catch (Exception e) {
            logger.error("[Proxy] Failed to start web frontend", e);
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        ProxyWebSocket socket = activeSessions.get(event.getPlayer().getUniqueId());
        if (socket == null) {
            return;
        }

        String backendUrl = resolveBackendUrl(event.getServer().getServerInfo().getName());
        if (backendUrl != null && !backendUrl.isBlank()) {
            socket.reconnectBackend(backendUrl);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        ProxyWebSocket socket = activeSessions.remove(event.getPlayer().getUniqueId());
        if (socket != null) {
            socket.onProxyDisconnect();
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
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

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public ProxyPasswordStore getPasswordStore() {
        return passwordStore;
    }

    public int getProxyIdleTimeoutMinutes() {
        return configFile.getInt("proxy.web.idle-timeout-minutes", 2);
    }

    public void registerSession(UUID uuid, ProxyWebSocket socket) {
        activeSessions.put(uuid, socket);
    }

    public void unregisterSession(UUID uuid, ProxyWebSocket socket) {
        activeSessions.computeIfPresent(uuid, (ignored, current) -> current == socket ? null : current);
    }

    public String resolveBackendUrl(Player player) {
        String serverName = player.getCurrentServer()
                .map(conn -> conn.getServerInfo().getName())
                .orElse("default");
        return resolveBackendUrl(serverName);
    }

    public String resolveBackendUrl(String routeName) {
        String normalized = routeName == null ? "default" : routeName.trim();
        if (normalized.isEmpty()) {
            normalized = "default";
        }

        String routeKey = "proxy.routes." + normalized + ".url";
        String backendUrl = configFile.getString(routeKey, "");
        if (!backendUrl.isBlank()) {
            return backendUrl;
        }

        backendUrl = configFile.getString("proxy.routes.default.url", "");
        if (!backendUrl.isBlank()) {
            return backendUrl;
        }

        return configFile.getString("proxy.backend.url", "");
    }

    public String createProxyToken(UUID uuid, String username) {
        String secret = configFile.getString("proxy.shared-secret", "simplevoice-geyser-proxy-secret");
        int ttlSeconds = configFile.getInt("proxy.token-ttl-seconds", 120);
        return ProxyAuthToken.create(uuid, username, secret, Duration.ofSeconds(ttlSeconds));
    }

    public String createProxyToken(Player player) {
        return createProxyToken(player.getUniqueId(), player.getUsername());
    }

    private void ensureProxyDefaults() {
        ensureDefault("proxy.web.port", 8081);
        ensureDefault("proxy.web.bind-address", "0.0.0.0");
        ensureDefault("proxy.web.idle-timeout-minutes", 2);
        ensureDefault("proxy.shared-secret", "simplevoice-geyser-proxy-secret");
        ensureDefault("proxy.token-ttl-seconds", 120);
        ensureDefault("proxy.backend.url", "ws://127.0.0.1:8080/ws");
        ensureDefault("proxy.routes.default.url", "ws://127.0.0.1:8080/ws");
        configFile.save();
    }

    private void ensureDefault(String key, Object value) {
        if (!configFile.has(key)) {
            configFile.set(key, value);
        }
    }
}
