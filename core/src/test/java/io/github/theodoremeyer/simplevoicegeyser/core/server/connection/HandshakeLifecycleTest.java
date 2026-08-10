package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioTransportMode;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.ControllableTaskScheduler;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakeLifecycleTest {

    private ControllableTaskScheduler scheduler;
    private FakePlatform platform;

    @BeforeEach
    void setUp() {
        scheduler = new ControllableTaskScheduler();
        platform = new FakePlatform(scheduler);
        new SvgCore(platform);
    }

    @AfterEach
    void tearDown() {
        SvgCore.disable();
    }

    @Test
    void deferredEntityNotifyDoesNotBlockSessionRegistration() {
        FakePlayer player = new FakePlayer();
        SvgCore.getPlayerManager().addPlayer(player);
        scheduler.setDeferEntityTasks(true);

        ConnectionManager manager = SvgCore.getConnectionManager();
        SvgConnection connection = manager.connect(
                openSession(new AtomicBoolean(true)),
                player,
                new AudioSessionNegotiation(AudioTransportMode.SVG_V2, false),
                ClientIdentity.web("0.1.3", "test")
        );

        assertSame(connection, manager.get(player.getUniqueId()));
        assertEquals(0, scheduler.deferredCount());

        player.sendMessage("Connected!");
        assertEquals(1, scheduler.deferredCount());
        assertFalse(player.messageDelivered.get());

        scheduler.flushDeferredEntityTasks();
        assertTrue(player.messageDelivered.get());
    }

    @Test
    void socketCloseBeforeDeferredWorkDoesNotReviveSession() {
        FakePlayer player = new FakePlayer();
        SvgCore.getPlayerManager().addPlayer(player);
        scheduler.setDeferEntityTasks(true);

        ConnectionManager manager = SvgCore.getConnectionManager();
        AtomicBoolean open = new AtomicBoolean(true);
        SvgConnection first = manager.connect(
                openSession(open),
                player,
                new AudioSessionNegotiation(AudioTransportMode.SVG_V2, false),
                ClientIdentity.web("0.1.3", "test")
        );

        open.set(false);
        manager.disconnect(player.getUniqueId(), 1001, "closed");
        assertNull(manager.get(player.getUniqueId()));
        assertTrue(first.isClosed());

        player.sendMessage("late");
        scheduler.flushDeferredEntityTasks();
        assertNull(manager.get(player.getUniqueId()));
    }

    @Test
    void replacementKeepsNewerSessionWhenOlderCloses() {
        FakePlayer player = new FakePlayer();
        ConnectionManager manager = SvgCore.getConnectionManager();

        SvgConnection first = manager.connect(
                openSession(new AtomicBoolean(true)),
                player,
                new AudioSessionNegotiation(AudioTransportMode.SVG_V2, false),
                ClientIdentity.web("0.1.3", "test")
        );
        SvgConnection second = manager.connect(
                openSession(new AtomicBoolean(true)),
                player,
                new AudioSessionNegotiation(AudioTransportMode.SVG_V2, false),
                ClientIdentity.web("0.1.3", "test")
        );

        assertNotSame(first, second);
        manager.remove(first);
        assertSame(second, manager.get(player.getUniqueId()));
    }

    @Test
    void entityRetirementRunsCallbackAndSkipsTask() {
        UUID missing = UUID.randomUUID();
        ControllableTaskScheduler local = new ControllableTaskScheduler(uuid -> false);
        AtomicBoolean ran = new AtomicBoolean(false);
        AtomicBoolean retired = new AtomicBoolean(false);

        local.executeForEntity("svg/test", missing, () -> ran.set(true), () -> retired.set(true));

        assertFalse(ran.get());
        assertTrue(retired.get());
    }

    @Test
    void shutdownRejectsEntityWork() {
        ControllableTaskScheduler local = new ControllableTaskScheduler();
        local.shutdown();
        AtomicInteger runs = new AtomicInteger();
        local.executeForEntity("svg/test", UUID.randomUUID(), runs::incrementAndGet, null);
        assertEquals(0, runs.get());
        assertFalse(local.isAcceptingTasks());
    }

    @Test
    void handshakeTracerIsMonotonic() {
        HandshakeTracer tracer = new HandshakeTracer("corr-1");
        assertTrue(tracer.transition(HandshakeState.JOIN_RECEIVED, "join", null, true, false));
        assertTrue(tracer.transition(HandshakeState.COMPATIBILITY_ACCEPTED, "compat", null, true, false));
        assertTrue(tracer.transition(HandshakeState.AUTHENTICATING, "auth", null, true, false));
        assertTrue(tracer.transition(HandshakeState.AUTHENTICATED, "ok", UUID.randomUUID(), true, false));
        assertTrue(tracer.transition(HandshakeState.READY, "ready", UUID.randomUUID(), true, false));
        assertFalse(tracer.transition(HandshakeState.AUTHENTICATING, "stale", UUID.randomUUID(), true, false));
        assertEquals(HandshakeState.READY, tracer.getState());
    }

    @Test
    void audioNegotiationInitialSvgV2WithoutCapsIsValidWhenFallbackDisabled() {
        AudioSessionNegotiation negotiation = new AudioSessionNegotiation(AudioTransportMode.SVG_V2, false);
        assertFalse(negotiation.isClientCapsReceived());
        assertEquals(AudioTransportMode.SVG_V2, negotiation.getSelectedMode());
        assertTrue(negotiation.summary().contains("capsReceived=false"));
        assertTrue(negotiation.summary().contains("selected=svg_v2"));
    }

    private static Session openSession(AtomicBoolean open) {
        AtomicReference<String> last = new AtomicReference<>();
        RemoteEndpoint remote = proxy(RemoteEndpoint.class, invocation -> {
            if ("sendString".equals(invocation.methodName())) {
                last.set((String) invocation.args()[0]);
            }
            return null;
        });
        return proxy(Session.class, invocation -> switch (invocation.methodName()) {
            case "isOpen" -> open.get();
            case "getRemote" -> remote;
            case "close" -> {
                open.set(false);
                yield null;
            }
            default -> defaultValue(invocation.returnType());
        });
    }

    private static final class FakePlayer extends SvgPlayer {
        private final UUID uuid = UUID.randomUUID();
        private final AtomicBoolean online = new AtomicBoolean(true);
        private final AtomicBoolean messageDelivered = new AtomicBoolean(false);

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
            return online.get();
        }

        @Override
        public Object getPlayer() {
            return null;
        }

        @Override
        public void sendMessage(String message) {
            SvgCore.getTaskScheduler().executeForEntity(
                    "svg/test/send-message",
                    uuid,
                    () -> messageDelivered.set(true),
                    null
            );
        }

        @Override
        public String getName() {
            return "Player";
        }
    }

    private static final class FakePlatform implements Platform {
        private final SvgFile config = new FakeSvgFile();
        private final TaskScheduler scheduler;
        private final SvgLogger logger = new NoopLogger();

        private FakePlatform(TaskScheduler scheduler) {
            this.scheduler = scheduler;
        }

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

    private record Invocation(String methodName, Class<?> returnType, Object[] args) {}

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
