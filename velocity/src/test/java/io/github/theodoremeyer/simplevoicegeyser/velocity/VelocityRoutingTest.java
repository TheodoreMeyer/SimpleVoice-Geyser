package io.github.theodoremeyer.simplevoicegeyser.velocity;

import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data.VelocityConfigFile;
import io.github.theodoremeyer.simplevoicegeyser.velocity.proxy.PlayerState;
import io.github.theodoremeyer.simplevoicegeyser.velocity.proxy.ProxySessionManager;
import io.github.theodoremeyer.simplevoicegeyser.velocity.proxy.ProxyWebSocket;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class VelocityRoutingTest {
    @TempDir Path directory;

    @Test
    void routesHonorEnabledAndFallbackOnlyForAbsentEntries() throws Exception {
        try (Fixture f = new Fixture()) {
            assertEquals("ws://127.0.0.1:8002/ws", f.plugin.resolveClientUrl("test1"));
            assertEquals("ws://127.0.0.1:8002/ws", f.plugin.resolveClientUrl(" test1 "));
            assertEquals("ws://127.0.0.1:8003/ws", f.plugin.resolveClientUrl("implicit"));
            assertEquals("", f.plugin.resolveClientUrl("disabled"));
            assertEquals("", f.plugin.resolveClientUrl("incomplete"));
            assertTrue(f.plugin.resolveClientUrl("blank").isBlank());
            assertEquals("ws://127.0.0.1:8001/ws", f.plugin.resolveClientUrl("unlisted"));
            assertEquals("ws://127.0.0.1:8001/ws", f.plugin.resolveClientUrl("default"));
            assertEquals("ws://127.0.0.1:8001/ws", f.plugin.resolveClientUrl((String) null));
            assertEquals("ws://127.0.0.1:8001/ws", f.plugin.resolveClientUrl(" "));
            f.config.set("clients.default.enabled", false);
            assertEquals("", f.plugin.resolveClientUrl("unlisted"));
            assertEquals("", f.plugin.resolveClientUrl((String) null));
        }
    }

    @Test
    void configPresenceRecognizesNestedEntriesWithoutTreatingMissingUrlsAsMissingEntries() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.config.has("clients"));
            assertTrue(f.config.has("clients.disabled"));
            assertTrue(f.config.has("clients.disabled.enabled"));
            assertTrue(f.config.has("clients.incomplete"));
            assertFalse(f.config.has("clients.incomplete.url"));
            assertFalse(f.config.has("clients.unlisted"));
        }
    }

    @Test
    void absentOrIncompleteDefaultLeavesUnknownBackendsUnavailable() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("clients.default", new JSONObject());
            assertEquals("", f.plugin.resolveClientUrl("unlisted"));
            f.config.set("clients", new JSONObject());
            assertEquals("", f.plugin.resolveClientUrl("unlisted"));
            assertEquals("", f.plugin.resolveClientUrl((String) null));
        }
    }

    @Test
    void rejectedTransferClosesBrowserAndEstablishedBackendWithoutForwardingMoreAudio() throws Exception {
        for (String destination : List.of("disabled", "incomplete", "blank", "unlisted")) {
            try (Fixture f = new Fixture()) {
                f.config.set("clients.default.enabled", false);
                f.transfer(destination);
                assertFalse(f.browserOpen, destination);
                assertEquals(4004, f.closeCode, destination);
                assertTrue(f.backendClosed, destination);
                assertTrue(f.messages.getLast().getBoolean("fatal"), destination);
                assertTrue("error".equalsIgnoreCase(f.messages.getLast().getString("type")));
                assertNull(f.manager.getActiveSession(f.uuid));
                f.socket.onMessage(new byte[1920], 0, 1920);
                assertEquals(0, f.forwardedAudio);
            }
        }
    }

    @Test
    void supportedTransferUsesExplicitOrDefaultRouteAndPreservesBrowser() throws Exception {
        BlockingQueue<String> joins = new LinkedBlockingQueue<>();
        Server server = new Server(new InetSocketAddress("127.0.0.1", 0));
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);
        JettyWebSocketServletContainerInitializer.configure(context, (servlet, container) ->
                container.addMapping("/ws", (request, response) -> new Destination(joins)));
        try {
            server.start();
            String url = "ws://127.0.0.1:" + ((ServerConnector) server.getConnectors()[0]).getLocalPort() + "/ws";
            for (String destination : List.of("test2", "unlisted")) {
                try (Fixture f = new Fixture()) {
                    f.config.set("clients.default.url", url);
                    f.config.set("clients.test2.url", url);
                    f.transfer(destination);
                    assertTrue(f.state.isOn(url));
                    assertNotNull(joins.poll(3, TimeUnit.SECONDS));
                    assertTrue(f.browserOpen);
                    assertTrue(f.backendClosed);
                    assertSame(f.socket, f.manager.getActiveSession(f.uuid));
                }
            }
        } finally {
            server.stop();
        }
    }

    @WebSocket
    public static final class Destination {
        private final BlockingQueue<String> joins;
        Destination(BlockingQueue<String> joins) { this.joins = joins; }
        @OnWebSocketMessage
        public void onMessage(String text) {
            if ("join".equals(new JSONObject(text).optString("type"))) joins.add(text);
        }
    }

    private final class Fixture implements AutoCloseable {
        final UUID uuid = UUID.randomUUID();
        final List<JSONObject> messages = new ArrayList<>();
        final VelocityPlugin plugin = new VelocityPlugin(null, LoggerFactory.getLogger("routing-test"), directory);
        final VelocityConfigFile config;
        final ProxySessionManager manager = new ProxySessionManager(plugin);
        final ProxyWebSocket socket = new ProxyWebSocket(plugin);
        final PlayerState state;
        boolean browserOpen = true;
        boolean backendClosed;
        int closeCode;
        int forwardedAudio;

        Fixture() throws Exception {
            Path file = directory.resolve("config.json");
            Files.writeString(file, """
                    {"clients": {
                      "default": {"enabled": true, "url": "ws://127.0.0.1:8001/ws"},
                      "test1": {"enabled": true, "url": "ws://127.0.0.1:8002/ws"},
                      "implicit": {"url": "ws://127.0.0.1:8003/ws"},
                      "disabled": {"enabled": false, "url": "ws://127.0.0.1:8004/ws"},
                      "incomplete": {"enabled": true},
                      "blank": {"enabled": true, "url": " "}
                    }}
                    """);
            config = new VelocityConfigFile(file.toFile());
            config.set("proxy.shared_secret", UUID.randomUUID().toString());
            set(plugin, "configFile", config);
            set(plugin, "sessionManager", manager);
            RemoteEndpoint remote = proxy(RemoteEndpoint.class, (p, method, args) -> {
                if (method.getName().equals("sendString")) messages.add(new JSONObject((String) args[0]));
                return null;
            });
            Session browser = proxy(Session.class, (p, method, args) -> switch (method.getName()) {
                case "isOpen" -> browserOpen;
                case "getRemote" -> remote;
                case "close" -> {
                    browserOpen = false;
                    closeCode = (int) args[0];
                    socket.onClose(closeCode, (String) args[1]);
                    yield null;
                }
                default -> null;
            });
            socket.onConnect(browser);
            state = new PlayerState(browser, plugin, uuid, "test-player");
            state.setJoinRequest(new JSONObject().put("type", "join"));
            set(state, "backendUrl", "ws://127.0.0.1:8002/ws");
            java.net.http.WebSocket backend = proxy(java.net.http.WebSocket.class, (p, method, args) -> {
                if (method.getName().equals("sendClose")) backendClosed = true;
                if (method.getName().equals("sendBinary")) forwardedAudio++;
                return CompletableFuture.completedFuture(p);
            });
            set(state.relay(), "backendSocket", backend);
            set(socket, "playerState", state);
            manager.registerSession(uuid, socket);
        }

        void transfer(String name) {
            Player player = proxy(Player.class, (p, method, args) -> method.getName().equals("getUniqueId") ? uuid : null);
            RegisteredServer destination = proxy(RegisteredServer.class, (p, method, args) ->
                    method.getName().equals("getServerInfo")
                            ? new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565)) : null);
            manager.onServerConnected(new ServerConnectedEvent(player, destination, null));
        }

        @Override public void close() { manager.shutdown(); }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
