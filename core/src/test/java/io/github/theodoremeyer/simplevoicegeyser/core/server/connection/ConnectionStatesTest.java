package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientIdentity;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionStatesTest {

    @Test
    void connectionMessagesSerializeTypeAsLowercaseJsonString() {
        CapturingSession session = new CapturingSession();
        SvgConnection connection = new SvgConnection(
                session.proxy(),
                new FakePlayer(),
                null,
                ClientIdentity.web("0.1.3", "test-build")
        );

        connection.sendStatus("Connected as Player.");

        JSONObject json = new JSONObject(session.lastMessage());
        assertEquals("status", json.getString("type"));
    }

    @Test
    void rawMessagesSerializeTypeAsLowercaseJsonString() throws Exception {
        CapturingSession session = new CapturingSession();
        JettyWebSocket socket = new JettyWebSocket();
        Field field = JettyWebSocket.class.getDeclaredField("session");
        field.setAccessible(true);
        field.set(socket, session.proxy());

        socket.sendRaw(ConnectionStates.MessageType.ERROR, "Invalid input.", false);

        JSONObject json = new JSONObject(session.lastMessage());
        assertEquals("error", json.getString("type"));
    }

    private static final class CapturingSession {
        private final AtomicReference<String> lastMessage = new AtomicReference<>();
        private final RemoteEndpoint remote = ConnectionStatesTest.proxy(RemoteEndpoint.class, invocation -> {
            if ("sendString".equals(invocation.methodName())) {
                lastMessage.set((String) invocation.args()[0]);
            }
            return defaultValue(invocation.returnType());
        });
        private final Session session = ConnectionStatesTest.proxy(Session.class, invocation -> switch (invocation.methodName()) {
            case "isOpen" -> true;
            case "getRemote" -> remote;
            default -> defaultValue(invocation.returnType());
        });

        private Session proxy() {
            return session;
        }

        private String lastMessage() {
            return lastMessage.get();
        }
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

    private record Invocation(String methodName, Class<?> returnType, Object[] args) {
    }

    private interface Handler {
        Object handle(Invocation invocation) throws Throwable;
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
                    return handler.handle(new Invocation(method.getName(), method.getReturnType(), args));
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
}
