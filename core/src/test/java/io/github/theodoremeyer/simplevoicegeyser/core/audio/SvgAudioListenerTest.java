package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiolistener.AudioListener;
import de.maxhenkel.voicechat.api.audiolistener.PlayerAudioListener;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgAudioListenerTest {

    @Test
    void registersAndUnregistersSvcPlayerAudioListener() {
        UUID playerUuid = UUID.randomUUID();
        FakeVoicechatApi api = new FakeVoicechatApi(playerUuid);
        new SvgCore(new FakePlatform());

        try {
            SvgAudioListener listener = new SvgAudioListener(playerUuid, proxy(Session.class), api.proxy(), null);

            assertTrue(listener.registerListener());
            listener.unRegister();

            assertEquals(playerUuid, api.builder.playerUuid);
            assertSame(api.listener, api.registeredListener);
            assertSame(api.listener, api.unregisteredListener);
        } finally {
            SvgCore.disable();
        }
    }

    private static final class FakeVoicechatApi {
        private final FakeBuilder builder;
        private final PlayerAudioListener listener;
        private AudioListener registeredListener;
        private AudioListener unregisteredListener;

        private FakeVoicechatApi(UUID playerUuid) {
            this.listener = SvgAudioListenerTest.proxy(PlayerAudioListener.class, invocation -> switch (invocation.methodName()) {
                case "getListenerId", "getPlayerUuid" -> playerUuid;
                default -> defaultValue(invocation.returnType());
            });
            this.builder = new FakeBuilder(listener);
        }

        private VoicechatServerApi proxy() {
            return SvgAudioListenerTest.proxy(VoicechatServerApi.class, invocation -> switch (invocation.methodName()) {
                case "createDecoder" -> SvgAudioListenerTest.proxy(OpusDecoder.class);
                case "playerAudioListenerBuilder" -> builder.proxy();
                case "registerAudioListener" -> {
                    registeredListener = (AudioListener) invocation.args()[0];
                    yield true;
                }
                case "unregisterAudioListener" -> {
                    unregisteredListener = (AudioListener) invocation.args()[0];
                    yield true;
                }
                default -> defaultValue(invocation.returnType());
            });
        }
    }

    private static final class FakeBuilder {
        private final PlayerAudioListener listener;
        private UUID playerUuid;

        private FakeBuilder(PlayerAudioListener listener) {
            this.listener = listener;
        }

        private PlayerAudioListener.Builder proxy() {
            return SvgAudioListenerTest.proxy(PlayerAudioListener.Builder.class, invocation -> switch (invocation.methodName()) {
                case "setPlayer" -> {
                    playerUuid = (UUID) invocation.args()[0];
                    yield invocation.proxy();
                }
                case "setPacketListener" -> invocation.proxy();
                case "build" -> listener;
                default -> defaultValue(invocation.returnType());
            });
        }
    }

    private record Invocation(Object proxy, String methodName, Class<?> returnType, Object[] args) {
    }

    private interface Handler {
        Object handle(Invocation invocation);
    }

    private static <T> T proxy(Class<T> type) {
        return proxy(type, invocation -> defaultValue(invocation.returnType()));
    }

    private static <T> T proxy(Class<T> type, Handler handler) {
        Object proxy = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (instance, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName() + "Proxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(instance);
                    }
                    if ("equals".equals(method.getName())) {
                        return instance == args[0];
                    }
                    return handler.handle(new Invocation(instance, method.getName(), method.getReturnType(), args));
                }
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

    private static final class FakePlatform implements Platform {
        private final SvgFile config = new FakeSvgFile();
        private final SvgLogger logger = new NoopLogger();

        @Override
        public void disable() {
        }

        @Override
        public String getPrefix() {
            return "";
        }

        @Override
        public String getServerMcVersion() {
            return "test";
        }

        @Override
        public String getServerPlatform() {
            return "test";
        }

        @Override
        public VoiceChatBridge registerVcBridge() {
            return null;
        }

        @Override
        public SvgLogger getSvgLogger() {
            return logger;
        }

        @Override
        public SvgFile getFile(DataType type) {
            return config;
        }

        @Override
        public File getDataFolder() {
            return new File(".");
        }

        @Override
        public boolean isDependencyEnabled(String name) {
            return false;
        }
    }

    private static final class FakeSvgFile extends SvgFile {
        private final Map<String, Object> values = new LinkedHashMap<>(Map.of("updatechecker.enable", false));

        @Override
        public Set<String> getKeys() {
            return values.keySet();
        }

        @Override
        public boolean has(String key) {
            return values.containsKey(key);
        }

        @Override
        public void set(String path, Object value) {
            values.put(path, value);
        }

        @Override
        public String getString(String path) {
            return getString(path, null);
        }

        @Override
        public String getString(String path, String def) {
            Object value = values.get(path);
            return value == null ? def : String.valueOf(value);
        }

        @Override
        public boolean getBoolean(String path, boolean def) {
            Object value = values.get(path);
            return value instanceof Boolean b ? b : def;
        }

        @Override
        public int getInt(String path, int def) {
            Object value = values.get(path);
            return value instanceof Number n ? n.intValue() : def;
        }

        @Override
        public void save() {
        }

        @Override
        public void reload() {
        }

        @Override
        public File getFile() {
            return new File("test-config.yml");
        }

        @Override
        public double getDouble(String path, double def) {
            Object value = values.get(path);
            return value instanceof Number n ? n.doubleValue() : def;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<String> getStringList(String path, List<String> def) {
            Object value = values.get(path);
            return value instanceof List<?> list ? (List<String>) list : def;
        }
    }

    private static final class NoopLogger implements SvgLogger {
        @Override
        public void info(String msg) {
        }

        @Override
        public void warning(String msg) {
        }

        @Override
        public void error(String msg) {
        }

        @Override
        public void severe(String msg) {
        }

        @Override
        public void error(String msg, Throwable t) {
        }
    }
}
