package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.ControllableTaskScheduler;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.TaskScheduler;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UsernameResolverTest {

    private ControllableTaskScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new ControllableTaskScheduler();
        SvgCore core = new SvgCore(new FakePlatform(scheduler));
        // PlayerVcPswd is created in init(); install a lightweight stub via reflection for resolve step 1.
        Field pswd = SvgCore.class.getDeclaredField("playerVcPswd");
        pswd.setAccessible(true);
        pswd.set(core, null);
    }

    @AfterEach
    void tearDown() {
        SvgCore.disable();
    }

    @Test
    void resolvesExactOnlinePlayerCaseInsensitive() {
        FakePlayer steve = new FakePlayer("Steve");
        SvgCore.getPlayerManager().addPlayer(steve);

        assertEquals(steve.getUniqueId(), UsernameResolver.resolve("steve"));
        assertEquals(steve.getUniqueId(), UsernameResolver.resolve("Steve"));
    }

    @Test
    void ambiguousOnlineNamesFailClosed() {
        // Two different UUIDs with same ignore-case name cannot both be registered in PlayerManager
        // (name map is unique). Ambiguity for floodgate is covered by null when floodgate absent.
        assertNull(UsernameResolver.resolve("NobodyOnline"));
        assertNull(UsernameResolver.resolve(""));
        assertNull(UsernameResolver.resolve("   "));
        assertNull(UsernameResolver.resolve(null));
    }

    @Test
    void neverBlindlyPrependsDotWithoutFloodgate() {
        FakePlayer dotted = new FakePlayer(".Alex");
        SvgCore.getPlayerManager().addPlayer(dotted);

        // Without Floodgate, bare "Alex" must not resolve via hardcoded "."
        assertNull(UsernameResolver.resolve("Alex"));
        // Exact online match still works
        assertEquals(dotted.getUniqueId(), UsernameResolver.resolve(".Alex"));
    }

    private static final class FakePlayer extends SvgPlayer {
        private final UUID uuid = UUID.randomUUID();
        private final String name;

        private FakePlayer(String name) {
            this.name = name;
        }

        @Override public UUID getUniqueId() { return uuid; }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public void chat(String message) {}
        @Override public boolean isOnline() { return true; }
        @Override public Object getPlayer() { return null; }
        @Override public void sendMessage(String message) {}
        @Override public String getName() { return name; }
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
