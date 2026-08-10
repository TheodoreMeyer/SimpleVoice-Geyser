package io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.ControllableTaskScheduler;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.TaskScheduler;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        ControllableTaskScheduler scheduler = new ControllableTaskScheduler();
        new SvgCore(new FakePlatform(scheduler));
        service = SvgCore.getRateLimitService();
    }

    @AfterEach
    void tearDown() {
        SvgCore.disable();
    }

    @Test
    void groupsRefreshCooldownBlocksImmediateSecondRequest() {
        String key = "player-1";
        RateLimitResult first = service.tryGroupsRefresh(key);
        assertTrue(first.allowed());

        service.markGroupsRefresh(key);

        RateLimitResult second = service.tryGroupsRefresh(key);
        assertFalse(second.allowed());
        assertTrue(second.retryAfterMs() > 0L);
    }

    @Test
    void audioLimiterAllowsBrowserWakeupBurstWithoutAlternatingDrops() {
        String key = "audio-player";
        // Normal speech coalescing: 50 frames in one wake must all pass.
        for (int i = 0; i < 50; i++) {
            RateLimitResult result = service.tryAudioFrame(key, 1920);
            assertTrue(result.allowed(), "frame " + i + " should be accepted within normal burst");
        }
        assertFalse(service.isAudioBypassEnabled());
        assertEquals(0L, service.getAudioIngressLimiter().getDropSustainedAbuse());
    }

    @Test
    void audioLimiterStillRejectsOversizedFrames() {
        RateLimitResult result = service.tryAudioFrame("audio-player", 1921);
        assertFalse(result.allowed());
        assertTrue(service.getAudioIngressLimiter().getDropMalformed() >= 1L);
    }

    @Test
    void audioLimiterRejectsSustainedFloodAboveCeiling() {
        String key = "flooder";
        // 75 fps * 5s = 375 max; push past the window budget.
        int rejected = 0;
        for (int i = 0; i < 400; i++) {
            if (!service.tryAudioFrame(key, 1920).allowed()) {
                rejected++;
            }
        }
        assertTrue(rejected > 0, "sustained flood must eventually be rejected");
        assertTrue(service.getAudioIngressLimiter().getDropSustainedAbuse() > 0L);
    }

    private static final class FakePlatform implements Platform {
        private final SvgFile config = new FakeSvgFile();
        private final TaskScheduler scheduler;
        private final SvgLogger logger = new NoopLogger();

        FakePlatform(TaskScheduler scheduler) {
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
