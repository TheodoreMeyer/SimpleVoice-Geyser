package io.github.theodoremeyer.simplevoicegeyser.core.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgConfig;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.data.PlayerVcPswd;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.JoinMessageHandler;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientIdentity;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ProxyControlClientTest {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @TempDir
    Path temporaryDirectory;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private MemoryConfig config;
    private final AtomicReference<RequestHandler> requestHandler = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        config = new MemoryConfig();
        config.set("proxy.enabled", true);
        config.set("proxy.shared-secret", "test-secret");
        config.set("client.join-message.enabled", true);
        config.set("client.join-message.text", List.of("Welcome"));
        SvgCore core = new SvgCore(new TestPlatform(config, temporaryDirectory.toFile()));
        Field passwordManager = SvgCore.class.getDeclaredField("playerVcPswd");
        passwordManager.setAccessible(true);
        passwordManager.set(core, new PlayerVcPswd(core));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/player-state", exchange -> {
            RequestHandler handler = requestHandler.get();
            if (handler == null) {
                writeResponse(exchange, 500, "");
                return;
            }
            try {
                handler.handle(exchange);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writeResponse(exchange, 500, "");
            }
        });
        server.start();
        config.set("proxy.control-url", "http://127.0.0.1:" + server.getAddress().getPort() + "/player-state");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        SvgCore.disable();
    }

    @Test
    void requestReturnsBeforeDelayedEndpointRespondsAndCompletesOffCallerThread() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        CountDownLatch callbackRan = new CountDownLatch(1);
        AtomicReference<String> callbackThread = new AtomicReference<>();
        AtomicReference<JSONObject> receivedPayload = new AtomicReference<>();
        AtomicReference<String> receivedSecret = new AtomicReference<>();
        requestHandler.set(exchange -> {
            receivedSecret.set(exchange.getRequestHeaders().getFirst("X-SVG-Proxy-Secret"));
            receivedPayload.set(new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            requestReceived.countDown();
            assertTrue(releaseResponse.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
            writeResponse(exchange, 200, "{\"passwordSet\":true,\"changingServer\":false}");
        });

        String callerThread = Thread.currentThread().getName();
        long startedAt = System.nanoTime();
        CompletableFuture<ProxyPlayerState> future = ProxyControlClient.updatePlayerState(
                UUID.randomUUID(), "TestPlayer", true
        );
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        future.thenAccept(state -> {
            callbackThread.set(Thread.currentThread().getName());
            callbackRan.countDown();
        });

        assertTrue(requestReceived.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        assertFalse(future.isDone(), "the request must remain pending until the endpoint is released");
        assertTrue(elapsedMillis < 1_000, "starting a delayed request must not wait for the two-second HTTP timeout");
        assertEquals("test-secret", receivedSecret.get());
        assertEquals("TestPlayer", receivedPayload.get().getString("username"));
        assertTrue(receivedPayload.get().getBoolean("joined"));

        releaseResponse.countDown();
        assertEquals(new ProxyPlayerState(true, false), future.get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        assertTrue(callbackRan.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        assertNotEquals(callerThread, callbackThread.get());
    }

    @Test
    void parsesAllSupportedSuccessfulStates() throws Exception {
        assertState("{\"passwordSet\":true,\"changingServer\":false}", new ProxyPlayerState(true, false));
        assertState("{\"passwordSet\":false,\"changingServer\":true}", new ProxyPlayerState(false, true));
        assertState("{\"passwordSet\":false,\"changingServer\":false}", ProxyPlayerState.unavailable());
    }

    @Test
    void unsuccessfulAndMalformedResponsesAreUnavailable() throws Exception {
        requestHandler.set(exchange -> writeResponse(exchange, 503, "{\"passwordSet\":true}"));
        assertEquals(ProxyPlayerState.unavailable(), update().get(WAIT.toMillis(), TimeUnit.MILLISECONDS));

        requestHandler.set(exchange -> writeResponse(exchange, 200, "not-json"));
        assertEquals(ProxyPlayerState.unavailable(), update().get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Test
    void connectionFailuresAndTimeoutsAreUnavailable() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        config.set("proxy.control-url", "http://127.0.0.1:" + unusedPort + "/player-state");
        assertEquals(ProxyPlayerState.unavailable(), update().get(WAIT.toMillis(), TimeUnit.MILLISECONDS));

        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        requestHandler.set(exchange -> {
            requestReceived.countDown();
            assertTrue(releaseResponse.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
            writeResponse(exchange, 200, "{\"passwordSet\":true}");
        });
        config.set("proxy.control-url", "http://127.0.0.1:" + server.getAddress().getPort() + "/player-state");

        try {
            CompletableFuture<ProxyPlayerState> future = update();
            assertTrue(requestReceived.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
            assertEquals(ProxyPlayerState.unavailable(), future.get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        } finally {
            releaseResponse.countDown();
        }
    }

    @Test
    void staleJoinResponseDoesNotMessageAPlayerWhoDisconnectedOrReconnected() throws Exception {
        CountDownLatch firstJoinRequest = new CountDownLatch(1);
        CountDownLatch releaseFirstJoin = new CountDownLatch(1);
        CountDownLatch secondJoinRequest = new CountDownLatch(1);
        CountDownLatch secondJoinResponse = new CountDownLatch(1);
        requestHandler.set(exchange -> {
            JSONObject request = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (request.getBoolean("joined") && firstJoinRequest.getCount() > 0) {
                firstJoinRequest.countDown();
                assertTrue(releaseFirstJoin.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
                writeResponse(exchange, 200, "{\"passwordSet\":false,\"changingServer\":false}");
            } else if (request.getBoolean("joined")) {
                secondJoinRequest.countDown();
                writeResponse(exchange, 200, "{\"passwordSet\":false,\"changingServer\":false}");
                secondJoinResponse.countDown();
            } else {
                writeResponse(exchange, 200, "{\"passwordSet\":false,\"changingServer\":false}");
            }
        });

        UUID uuid = UUID.randomUUID();
        TestPlayer disconnectedPlayer = new TestPlayer(uuid, "TestPlayer");
        SvgCore.getPlayerManager().addPlayer(disconnectedPlayer);
        CompletableFuture<Void> staleJoin = new JoinMessageHandler().sendJoinMessage(disconnectedPlayer);
        assertTrue(firstJoinRequest.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

        SvgCore.getPlayerManager().removePlayer(disconnectedPlayer);
        TestPlayer reconnectedPlayer = new TestPlayer(uuid, "TestPlayer");
        SvgCore.getPlayerManager().addPlayer(reconnectedPlayer);
        CompletableFuture<Void> currentJoin = new JoinMessageHandler().sendJoinMessage(reconnectedPlayer);
        assertTrue(secondJoinRequest.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

        releaseFirstJoin.countDown();
        assertTrue(secondJoinResponse.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        currentJoin.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        staleJoin.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(reconnectedPlayer.messageReceived.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        assertTrue(disconnectedPlayer.messages.isEmpty());
        assertEquals(List.of("Welcome"), reconnectedPlayer.messages);
    }

    @Test
    void staleQuitResponseCannotDisconnectAReplacementVoiceConnection() throws Exception {
        CountDownLatch quitRequest = new CountDownLatch(1);
        CountDownLatch releaseQuitResponse = new CountDownLatch(1);
        requestHandler.set(exchange -> {
            JSONObject request = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertFalse(request.getBoolean("joined"));
            quitRequest.countDown();
            assertTrue(releaseQuitResponse.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
            writeResponse(exchange, 200, "{\"passwordSet\":false,\"changingServer\":false}");
        });

        UUID uuid = UUID.randomUUID();
        TestPlayer firstPlayer = new TestPlayer(uuid, "TestPlayer");
        SvgCore.getPlayerManager().addPlayer(firstPlayer);
        TestSession firstSession = new TestSession();
        SvgCore.getConnectionManager().connect(
                firstSession.proxy(), firstPlayer, null, new ClientIdentity("test", "test", null)
        );

        long startedAt = System.nanoTime();
        CompletableFuture<Void> removal = SvgCore.getPlayerManager().removePlayer(firstPlayer);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertTrue(quitRequest.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        assertFalse(removal.isDone());
        assertTrue(elapsedMillis < 1_000, "quit handling must not wait for proxy state");

        TestPlayer replacementPlayer = new TestPlayer(uuid, "TestPlayer");
        SvgCore.getPlayerManager().addPlayer(replacementPlayer);
        TestSession replacementSession = new TestSession();
        SvgCore.getConnectionManager().connect(
                replacementSession.proxy(), replacementPlayer, null, new ClientIdentity("test", "test", null)
        );

        releaseQuitResponse.countDown();
        removal.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(0, replacementSession.closeCalls, "a stale quit response must not close a replacement session");
    }

    @Test
    void quitClosesVoiceConnectionUnlessProxyReportsServerChange() throws Exception {
        for (boolean changingServer : new boolean[] {false, true}) {
            requestHandler.set(exchange -> writeResponse(exchange, 200, "{\"changingServer\":" + changingServer + "}"));
            TestPlayer player = new TestPlayer(UUID.randomUUID(), "TestPlayer");
            SvgCore.getPlayerManager().addPlayer(player);
            TestSession session = new TestSession();
            SvgCore.getConnectionManager().connect(
                    session.proxy(), player, null, new ClientIdentity("test", "test", null)
            );

            SvgCore.getPlayerManager().removePlayer(player).get(WAIT.toMillis(), TimeUnit.MILLISECONDS);

            assertEquals(changingServer ? 0 : 1, session.closeCalls);
        }
    }

    private void assertState(String body, ProxyPlayerState expected) throws Exception {
        requestHandler.set(exchange -> writeResponse(exchange, 200, body));
        assertEquals(expected, update().get(WAIT.toMillis(), TimeUnit.MILLISECONDS));
    }

    private CompletableFuture<ProxyPlayerState> update() {
        return ProxyControlClient.updatePlayerState(UUID.randomUUID(), "TestPlayer", true);
    }

    private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface RequestHandler {
        void handle(HttpExchange exchange) throws IOException, InterruptedException;
    }

    private static final class TestPlayer extends SvgPlayer {
        private final UUID uuid;
        private final String name;
        private final List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final CountDownLatch messageReceived = new CountDownLatch(1);

        private TestPlayer(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        @Override public UUID getUniqueId() { return uuid; }
        @Override public String getName() { return name; }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public void chat(String message) {}
        @Override public boolean isOnline() { return true; }
        @Override public Object getPlayer() { return null; }
        @Override public void sendMessage(String message) {
            messages.add(message);
            messageReceived.countDown();
        }
    }

    private static final class TestSession {
        private volatile boolean open = true;
        private volatile int closeCalls;

        private Session proxy() {
            return (Session) Proxy.newProxyInstance(
                    Session.class.getClassLoader(),
                    new Class<?>[]{Session.class},
                    (instance, method, args) -> switch (method.getName()) {
                        case "isOpen" -> open;
                        case "close" -> {
                            open = false;
                            closeCalls++;
                            yield null;
                        }
                        case "getRemote" -> Proxy.newProxyInstance(RemoteEndpoint.class.getClassLoader(),
                                new Class<?>[]{RemoteEndpoint.class}, (remote, call, callArgs) -> null);
                        case "hashCode" -> System.identityHashCode(instance);
                        case "equals" -> instance == args[0];
                        case "toString" -> "TestSession";
                        default -> null;
                    }
            );
        }
    }

    private static final class TestPlatform implements Platform {
        private final MemoryConfig config;
        private final File dataFolder;

        private TestPlatform(MemoryConfig config, File dataFolder) {
            this.config = config;
            this.dataFolder = dataFolder;
        }

        @Override public void platformDisable() {}
        @Override public String getPrefix() { return ""; }
        @Override public String getServerMcVersion() { return "test"; }
        @Override public String getServerPlatform() { return "test"; }
        @Override public VoiceChatBridge registerVcBridge() { return null; }
        @Override public SvgLogger getSvgLogger() { return new NoopLogger(); }
        @Override public SvgFile getFile(DataType type) { return config; }
        @Override public File getDataFolder() { return dataFolder; }
        @Override public boolean isDependencyEnabled(String name) { return false; }
    }

    private static final class MemoryConfig extends SvgFile {
        private final Map<String, Object> values = new java.util.concurrent.ConcurrentHashMap<>(SvgConfig.codeDefaults());

        @Override public Set<String> getKeys() { return values.keySet(); }
        @Override public boolean has(String key) { return values.containsKey(key); }
        @Override public void set(String path, Object value) { values.put(path, value); }
        @Override public String getString(String path) { return getString(path, null); }
        @Override public String getString(String path, String def) {
            Object value = values.get(path);
            return value == null ? def : String.valueOf(value);
        }
        @Override public boolean getBoolean(String path, boolean def) {
            Object value = values.get(path);
            return value instanceof Boolean bool ? bool : def;
        }
        @Override public int getInt(String path, int def) {
            Object value = values.get(path);
            return value instanceof Number number ? number.intValue() : def;
        }
        @Override public double getDouble(String path, double def) {
            Object value = values.get(path);
            return value instanceof Number number ? number.doubleValue() : def;
        }
        @Override public List<String> getStringList(String path, List<String> def) {
            Object value = values.get(path);
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            return def;
        }
        @Override public void save() {}
        @Override public void reload() {}
        @Override public File getFile() { return null; }
        @Override public String backup() { return null; }
    }

    private static final class NoopLogger implements SvgLogger {
        @Override public void info(String msg) {}
        @Override public void warning(String msg) {}
        @Override public void error(String msg) {}
        @Override public void severe(String msg) {}
        @Override public void error(String msg, Throwable throwable) {}
    }
}
