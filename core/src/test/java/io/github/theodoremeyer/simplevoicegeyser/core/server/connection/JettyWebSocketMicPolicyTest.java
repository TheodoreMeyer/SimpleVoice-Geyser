package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiolistener.PlayerAudioListener;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientIdentity;
import io.github.theodoremeyer.simplevoicegeyser.core.server.packets.CapabilitiesPacket;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JettyWebSocketMicPolicyTest {

    @Test
    void missingSettingDefaultsToTrueAndExplicitFalseIsPreserved() throws Exception {
        try (Fixture f = new Fixture(null)) {
            assertTrue(SvgCore.getConfig().AUDIO_MIC_ALLOW_LEGACY.get());
            assertEquals(true, f.config.values.get("server.audio.mic.allowLegacy"));
        }
        try (Fixture f = new Fixture(false)) {
            assertFalse(SvgCore.getConfig().AUDIO_MIC_ALLOW_LEGACY.get());
            assertFalse(f.socket.getAudioNegotiation().isMicFormatAllowed());
        }
    }

    @Test
    void compatibilityAllowsOldAppCapabilitiesAndPcmWithoutCapabilities() throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.authenticate();
            f.socket.onMessage(new byte[1920], 0, 1920);
            f.capabilities(); // SVG-App 1.0.2 has no micFormats field.
            assertEquals("pcm16le", f.messages.getFirst().getString("selectedMicFormat"));
            assertEquals("legacy", f.messages.getFirst().getString("selectedMode"));
            f.socket.onMessage(new byte[1924], 2, 1920);
            assertTrue(f.pcmSent.await(2, TimeUnit.SECONDS));
            assertEquals(2, f.encodeCalls);
            assertEquals(2, f.sentAudio.size());
            assertTrue(f.open);
        }
    }

    @Test
    void strictPolicyRejectsPcmCapabilitiesBeforeAuthentication() throws Exception {
        try (Fixture f = new Fixture(false)) {
            f.capabilities("pcm16le");
            f.assertRejected();
        }
    }

    @Test
    void strictPolicyAllowsAuthenticationBeforeDelayedCapabilitiesButRejectsPcm() throws Exception {
        try (Fixture f = new Fixture(false)) {
            f.authenticate();
            assertTrue(f.socket.getConnection().isAuthenticated());
            assertTrue(f.open);
            assertFalse(f.socket.getAudioNegotiation().isClientCapsReceived());
            f.capabilities();
            f.assertRejected();
        }
    }

    @Test
    void strictPolicyRejectsFirstMicrophonePacketWithoutCapabilities() throws Exception {
        try (Fixture f = new Fixture(false)) {
            f.authenticate();
            f.socket.onMessage(new byte[1920], 0, 1920);
            f.assertRejected();
        }
    }

    @Test
    void strictPolicyAllowsOpusWithLegacyReceiveAndRejectsPcmRenegotiation() throws Exception {
        try (Fixture f = new Fixture(false)) {
            f.authenticate();
            f.capabilities("opus", "pcm16le");
            assertEquals("opus", f.messages.getFirst().getString("selectedMicFormat"));
            assertEquals("legacy", f.messages.getFirst().getString("selectedMode"));
            f.socket.onMessage(new byte[]{0, 10, 20, 0}, 1, 2);
            assertTrue(f.audioSent.await(2, TimeUnit.SECONDS));
            assertArrayEquals(new byte[]{10, 20}, f.sentAudio.getFirst());
            f.socket.onMessage(new byte[1920], 0, 1920);
            assertEquals(1, f.sentAudio.size());
            assertEquals(0, f.encodeCalls);
            f.capabilities("pcm16le");
            assertEquals(2, f.messages.size());
            assertTrue(f.messages.getLast().getBoolean("fatal"));
            assertEquals(4004, f.closeCode);
            assertEquals("Opus microphone audio required", f.closeReason);
            f.capabilities("pcm16le");
            assertEquals(2, f.messages.size());
            assertEquals(1, f.closeCalls);
        }
    }

    @Test
    void strictPolicyAlsoAllowsOpusWithSvgV2Receive() throws Exception {
        try (Fixture f = new Fixture(false, "svg-v2")) {
            f.authenticate();
            JSONObject caps = capabilitiesPacket("opus");
            caps.getJSONObject("audio").put("protocols", new JSONArray(List.of("legacy", "svg-v2")))
                    .put("supportsOpusDecoder", true);
            new CapabilitiesPacket().handle(f.socket, caps);
            assertEquals("svg-v2", f.messages.getFirst().getString("selectedMode"));
            assertEquals("opus", f.messages.getFirst().getString("selectedMicFormat"));
            f.socket.onMessage(new byte[]{10}, 0, 1);
            assertTrue(f.audioSent.await(2, TimeUnit.SECONDS));
            assertEquals(1, f.sentAudio.size());
            assertTrue(f.open);
        }
    }

    @Test
    void missingNegotiationCannotBypassStrictConfig() throws Exception {
        try (Fixture f = new Fixture(false)) {
            f.socket.setAudioNegotiation(null);
            f.capabilities();
            f.assertRejected();
        }
        try (Fixture f = new Fixture(false)) {
            f.authenticate();
            f.socket.setAudioNegotiation(null);
            f.socket.onMessage(new byte[1920], 0, 1920);
            f.assertRejected();
        }
    }

    private static JSONObject capabilitiesPacket(String... formats) {
        JSONObject audio = new JSONObject().put("protocols", new JSONArray(List.of("legacy")));
        if (formats.length > 0) audio.put("micFormats", new JSONArray(List.of(formats)));
        return new JSONObject().put("type", "capabilities").put("audio", audio);
    }

    private static final class Fixture implements AutoCloseable {
        final MemoryConfig config = new MemoryConfig();
        final List<JSONObject> messages = new ArrayList<>();
        final List<byte[]> sentAudio = new CopyOnWriteArrayList<>();
        final CountDownLatch pcmSent = new CountDownLatch(2);
        final CountDownLatch audioSent = new CountDownLatch(1);
        final JettyWebSocket socket;
        final Session session;
        boolean open = true;
        int closeCode;
        int closeCalls;
        volatile int encodeCalls;
        String closeReason;

        Fixture(Boolean allowLegacy) throws Exception { this(allowLegacy, "legacy"); }

        Fixture(Boolean allowLegacy, String receiveMode) throws Exception {
            config.values.put("server.audio.transport-mode", receiveMode);
            if (allowLegacy != null) config.values.put("server.audio.mic.allowLegacy", allowLegacy);
            SvgLogger logger = proxy(SvgLogger.class, (p, m, a) -> null);
            SvgCore core = new SvgCore(proxy(Platform.class, (p, m, a) -> switch (m.getName()) {
                case "getFile" -> config;
                case "getSvgLogger" -> logger;
                case "getDataFolder" -> new File(".");
                case "getPrefix", "getServerMcVersion", "getServerPlatform" -> "test";
                default -> defaultValue(m.getReturnType());
            }));
            socket = new JettyWebSocket();
            RemoteEndpoint remote = proxy(RemoteEndpoint.class, (p, m, a) -> {
                if (m.getName().equals("sendString")) messages.add(new JSONObject((String) a[0]));
                return defaultValue(m.getReturnType());
            });
            session = proxy(Session.class, (p, m, a) -> switch (m.getName()) {
                case "isOpen" -> open;
                case "getRemote" -> remote;
                case "close" -> {
                    open = false;
                    closeCalls++;
                    closeCode = (int) a[0];
                    closeReason = (String) a[1];
                    yield null;
                }
                default -> defaultValue(m.getReturnType());
            });
            VoicechatServerApi api = svcApi();
            Field bridge = SvgCore.class.getDeclaredField("vcBridge");
            bridge.setAccessible(true);
            bridge.set(core, new VoiceChatBridge() {
                @Override public VoicechatServerApi getVcServerApi() { return api; }
            });
            socket.onConnect(session);
        }

        void authenticate() throws Exception {
            SvgPlayer player = new SvgPlayer() {
                private final UUID uuid = UUID.randomUUID();
                @Override public UUID getUniqueId() { return uuid; }
                @Override public String getName() { return "test-player"; }
                @Override public boolean hasPermission(String permission) { return true; }
                @Override public boolean isOnline() { return true; }
                @Override public Object getPlayer() { return null; }
                @Override public void chat(String message) {}
                @Override public void sendMessage(String message) {}
            };
            SvgConnection connection = SvgCore.getConnectionManager().connect(session, player,
                    socket.getAudioNegotiation(), ClientIdentity.web(SvgCore.VERSION, SvgCore.BUILD_ID));
            socket.setConnection(connection);
            connection.authenticate();
        }

        void capabilities(String... formats) { new CapabilitiesPacket().handle(socket, capabilitiesPacket(formats)); }

        void assertRejected() {
            assertEquals(1, messages.size());
            assertEquals("error", messages.getFirst().getString("type"));
            assertTrue(messages.getFirst().getBoolean("fatal"));
            assertTrue(messages.getFirst().getString("message").contains("requires Opus"));
            assertEquals(4004, closeCode);
            assertEquals("Opus microphone audio required", closeReason);
            assertEquals(1, closeCalls);
            assertTrue(sentAudio.isEmpty());
            assertEquals(0, encodeCalls);
        }

        VoicechatServerApi svcApi() {
            VoicechatConnection connection = proxy(VoicechatConnection.class, (p, m, a) -> defaultValue(m.getReturnType()));
            PlayerAudioListener listener = proxy(PlayerAudioListener.class, (p, m, a) -> defaultValue(m.getReturnType()));
            PlayerAudioListener.Builder builder = proxy(PlayerAudioListener.Builder.class,
                    (p, m, a) -> m.getName().equals("build") ? listener : p);
            OpusEncoder encoder = proxy(OpusEncoder.class, (p, m, a) -> {
                if (m.getName().equals("encode")) { encodeCalls++; return new byte[]{42}; }
                return defaultValue(m.getReturnType());
            });
            AudioSender sender = proxy(AudioSender.class, (p, m, a) -> {
                if (m.getName().equals("send")) { sentAudio.add((byte[]) a[0]); pcmSent.countDown(); audioSent.countDown(); return true; }
                return defaultValue(m.getReturnType());
            });
            return proxy(VoicechatServerApi.class, (p, m, a) -> switch (m.getName()) {
                case "getConnectionOf" -> connection;
                case "createEncoder" -> encoder;
                case "createDecoder" -> proxy(OpusDecoder.class, (d, method, args) -> defaultValue(method.getReturnType()));
                case "playerAudioListenerBuilder" -> builder;
                case "registerAudioListener", "registerAudioSender" -> true;
                case "createAudioSender" -> sender;
                case "getAudioConverter" -> proxy(m.getReturnType(), (c, method, args) ->
                        method.getName().equals("bytesToShorts") ? new short[((byte[]) args[0]).length / 2]
                                : defaultValue(method.getReturnType()));
                default -> defaultValue(m.getReturnType());
            });
        }

        @Override public void close() {
            try {
                if (socket.getConnection() != null) socket.onClose(closeCode, closeReason);
            } finally {
                SvgCore.disable();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private static final class MemoryConfig extends SvgFile {
        final Map<String, Object> values = new HashMap<>(Map.of("updatechecker.enable", false));
        @Override public Set<String> getKeys() { return values.keySet(); }
        @Override public boolean has(String key) { return values.containsKey(key); }
        @Override public void set(String path, Object value) { values.put(path, value); }
        @Override public String getString(String path) { return getString(path, null); }
        @Override public String getString(String path, String def) { return (String) values.getOrDefault(path, def); }
        @Override public boolean getBoolean(String path, boolean def) { return (boolean) values.getOrDefault(path, def); }
        @Override public int getInt(String path, int def) { return ((Number) values.getOrDefault(path, def)).intValue(); }
        @Override public double getDouble(String path, double def) { return ((Number) values.getOrDefault(path, def)).doubleValue(); }
        @Override public List<String> getStringList(String path, List<String> def) { return def; }
        @Override public void save() {}
        @Override public void reload() {}
        @Override public File getFile() { return new File("test-config.yml"); }
        @Override public String backup() { return ""; }
    }
}
