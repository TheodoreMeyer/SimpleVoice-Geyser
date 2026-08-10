package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
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
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.AuthException;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientIdentity;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionModeAuthTest {

    private ControllableTaskScheduler scheduler;
    private SvgCore core;
    private FakeBridge bridge;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new ControllableTaskScheduler();
        core = new SvgCore(new FakePlatform(scheduler));
        bridge = new FakeBridge();
        Field bridgeField = SvgCore.class.getDeclaredField("vcBridge");
        bridgeField.setAccessible(true);
        bridgeField.set(core, bridge);
    }

    @AfterEach
    void tearDown() {
        SvgCore.disable();
    }

    @Test
    void nativeInstalledPlayerAuthenticatesAsControllerWithoutAudio() throws AuthException {
        FakePlayer player = new FakePlayer();
        SvgCore.getPlayerManager().addPlayer(player);
        bridge.putConnection(player.getUniqueId(), fakeVcConnection(true));

        SvgConnection connection = SvgCore.getConnectionManager().connect(
                openSession(new AtomicBoolean(true)),
                player,
                new AudioSessionNegotiation(AudioTransportMode.SVG_V2, false),
                ClientIdentity.web("0.1.3", "test")
        );

        connection.authenticate();

        assertTrue(connection.isAuthenticated());
        assertEquals(SessionMode.NATIVE_VOICE_CONTROLLER, connection.getSessionMode());
        assertNull(connection.getAudioSender());
    }

    private static VoicechatConnection fakeVcConnection(boolean installed) {
        return (VoicechatConnection) Proxy.newProxyInstance(
                VoicechatConnection.class.getClassLoader(),
                new Class<?>[]{VoicechatConnection.class},
                (proxy, method, args) -> {
                    if ("isInstalled".equals(method.getName())) {
                        return installed;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Session openSession(AtomicBoolean open) {
        RemoteEndpoint remote = (RemoteEndpoint) Proxy.newProxyInstance(
                RemoteEndpoint.class.getClassLoader(),
                new Class<?>[]{RemoteEndpoint.class},
                (proxy, method, args) -> null
        );
        return (Session) Proxy.newProxyInstance(
                Session.class.getClassLoader(),
                new Class<?>[]{Session.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isOpen" -> open.get();
                    case "getRemote" -> remote;
                    case "close" -> {
                        open.set(false);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == long.class) {
            return 0;
        }
        return null;
    }

    private static final class FakeBridge extends VoiceChatBridge {
        private final Map<UUID, VoicechatConnection> connections = new LinkedHashMap<>();
        private final VoicechatServerApi api = (VoicechatServerApi) Proxy.newProxyInstance(
                VoicechatServerApi.class.getClassLoader(),
                new Class<?>[]{VoicechatServerApi.class},
                (proxy, method, args) -> {
                    if ("getConnectionOf".equals(method.getName()) && args != null && args.length == 1) {
                        return connections.get((UUID) args[0]);
                    }
                    return defaultValue(method.getReturnType());
                }
        );

        void putConnection(UUID uuid, VoicechatConnection connection) {
            connections.put(uuid, connection);
        }

        @Override
        public VoicechatServerApi getVcServerApi() {
            return api;
        }
    }

    private static final class FakePlayer extends SvgPlayer {
        private final UUID uuid = UUID.randomUUID();

        @Override public UUID getUniqueId() { return uuid; }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public void chat(String message) {}
        @Override public boolean isOnline() { return true; }
        @Override public Object getPlayer() { return null; }
        @Override public void sendMessage(String message) {}
        @Override public String getName() { return "NativePlayer"; }
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
}
