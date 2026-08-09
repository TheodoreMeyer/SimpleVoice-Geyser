package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioTransportMode;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.DirectTaskScheduler;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.TaskScheduler;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientIdentity;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionManagerConcurrencyTest {

    @BeforeEach
    void setUp() {
        new SvgCore(new FakePlatform());
    }

    @AfterEach
    void tearDown() {
        SvgCore.disable();
    }

    @Test
    void shutdownRejectsNewConnectionsIdempotently() {
        ConnectionManager manager = new ConnectionManager();
        FakePlayer player = new FakePlayer();

        SvgConnection first = manager.connect(
                openSession(),
                player,
                negotiation(),
                ClientIdentity.web("0.1.3", "test")
        );
        assertSame(first, manager.get(player.getUniqueId()));

        manager.disconnectAll();
        manager.disconnectAll();

        assertFalse(manager.isAcceptingConnections());
        assertNull(manager.get(player.getUniqueId()));
        assertThrows(IllegalStateException.class, () -> manager.connect(
                openSession(),
                player,
                negotiation(),
                ClientIdentity.web("0.1.3", "test")
        ));
    }

    @Test
    void sessionReplacementDisconnectsPreviousConnection() {
        ConnectionManager manager = new ConnectionManager();
        FakePlayer player = new FakePlayer();

        SvgConnection first = manager.connect(
                openSession(),
                player,
                negotiation(),
                ClientIdentity.web("0.1.3", "test")
        );
        SvgConnection second = manager.connect(
                openSession(),
                player,
                negotiation(),
                ClientIdentity.web("0.1.3", "test")
        );

        assertNotSame(first, second);
        assertSame(second, manager.get(player.getUniqueId()));
        assertTrue(first.isClosed());
        assertFalse(second.isClosed());
    }

    @Test
    void concurrentJoinAndLeaveDoNotCorruptMap() throws Exception {
        ConnectionManager manager = new ConnectionManager();
        FakePlayer player = new FakePlayer();
        UUID uuid = player.getUniqueId();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger connects = new AtomicInteger();

        try {
            for (int i = 0; i < 20; i++) {
                pool.execute(() -> {
                    await(start);
                    try {
                        manager.connect(
                                openSession(),
                                player,
                                negotiation(),
                                ClientIdentity.web("0.1.3", "test")
                        );
                        connects.incrementAndGet();
                    } catch (IllegalStateException ignored) {
                    }
                });
                pool.execute(() -> {
                    await(start);
                    manager.disconnect(uuid, 4003, "leave");
                });
            }

            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

            SvgConnection remaining = manager.get(uuid);
            if (remaining != null) {
                assertFalse(remaining.isClosed());
            }
            assertTrue(connects.get() > 0);
        } finally {
            pool.shutdownNow();
            manager.disconnectAll();
        }
    }

    @Test
    void removeIgnoresStaleConnectionAfterReplacement() {
        ConnectionManager manager = new ConnectionManager();
        FakePlayer player = new FakePlayer();

        SvgConnection first = manager.connect(
                openSession(),
                player,
                negotiation(),
                ClientIdentity.web("0.1.3", "test")
        );
        SvgConnection second = manager.connect(
                openSession(),
                player,
                negotiation(),
                ClientIdentity.web("0.1.3", "test")
        );

        manager.remove(first);
        assertSame(second, manager.get(player.getUniqueId()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static AudioSessionNegotiation negotiation() {
        return new AudioSessionNegotiation(AudioTransportMode.SVG_V2, true);
    }

    private static Session openSession() {
        RemoteEndpoint remote = proxy(RemoteEndpoint.class, invocation -> null);
        return proxy(Session.class, invocation -> switch (invocation.methodName()) {
            case "isOpen" -> true;
            case "getRemote" -> remote;
            case "close" -> null;
            default -> defaultValue(invocation.returnType());
        });
    }

    private static final class FakePlayer extends SvgPlayer {
        private final UUID uuid = UUID.randomUUID();

        @Override
        public UUID getUniqueId() {
            return uuid;
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public void chat(String message) {
        }

        @Override
        public boolean isOnline() {
            return true;
        }

        @Override
        public Object getPlayer() {
            return null;
        }

        @Override
        public void sendMessage(String message) {
        }

        @Override
        public String getName() {
            return "Player";
        }
    }

    private static final class FakePlatform implements Platform {
        private final SvgFile config = new FakeSvgFile();
        private final TaskScheduler scheduler = new DirectTaskScheduler();
        private final SvgLogger logger = new NoopLogger();

        @Override public void disable() {}
        @Override public String getPrefix() { return ""; }
        @Override public String getServerMcVersion() { return "test"; }
        @Override public String getServerPlatform() { return "test"; }
        @Override public VoiceChatBridge registerVcBridge() { return null; }
        @Override public SvgLogger getSvgLogger() { return logger; }
        @Override public SvgFile getFile(DataType type) { return config; }
        @Override public File getDataFolder() { return new File("."); }
        @Override public boolean isDependencyEnabled(String name) { return false; }
        @Override public TaskScheduler getTaskScheduler() { return scheduler; }
    }

    private static final class FakeSvgFile extends SvgFile {
        private final Map<String, Object> values = new LinkedHashMap<>(Map.of("updatechecker.enable", false));

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
        @Override public void save() {}
        @Override public void reload() {}
        @Override public File getFile() { return new File("test-config.yml"); }
        @Override public String backup() { return ""; }
        @Override public double getDouble(String path, double def) {
            Object value = values.get(path);
            return value instanceof Number number ? number.doubleValue() : def;
        }
        @Override
        @SuppressWarnings("unchecked")
        public List<String> getStringList(String path, List<String> def) {
            Object value = values.get(path);
            return value instanceof List<?> list ? (List<String>) list : def;
        }
    }

    private static final class NoopLogger implements SvgLogger {
        @Override public void info(String msg) {}
        @Override public void warning(String msg) {}
        @Override public void error(String msg) {}
        @Override public void severe(String msg) {}
        @Override public void error(String msg, Throwable t) {}
    }

    private record Invocation(String methodName, Class<?> returnType, Object[] args) {
    }

    private interface Handler {
        Object handle(Invocation invocation) throws Throwable;
    }

    private static <T> T proxy(Class<T> type, Handler handler) {
        Object proxy = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxyObj, method, args) -> handler.handle(
                        new Invocation(method.getName(), method.getReturnType(), args == null ? new Object[0] : args)
                )
        );
        return type.cast(proxy);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == long.class || type == float.class || type == double.class) {
            return 0;
        }
        return null;
    }
}
