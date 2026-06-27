package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

public final class BackendRelay {

    private final Session clientSession;
    private final Logger logger;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile WebSocket backendSocket;
    private volatile String backendUrl;
    private volatile String joinPayload;
    private volatile boolean suppressClientClose;

    public BackendRelay(Session clientSession, Logger logger) {
        this.clientSession = clientSession;
        this.logger = logger;
    }

    public synchronized void connect(String backendUrl, String joinPayload) {
        this.backendUrl = backendUrl;
        this.joinPayload = joinPayload;

        try {
            this.backendSocket = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(backendUrl), new Listener())
                    .join();
            if (joinPayload != null && !joinPayload.isBlank()) {
                backendSocket.sendText(joinPayload, true);
            }
        } catch (Exception e) {
            logger.error("Failed to connect backend relay to {}", backendUrl, e);
            closeClient(1011, "backend_connect_failed");
        }
    }

    public synchronized void reconnect(String newBackendUrl) {
        suppressClientClose = true;
        closeBackend(1000, "backend_switch");
        connect(newBackendUrl, joinPayload);
        suppressClientClose = false;
    }

    public void forwardText(String text) {
        WebSocket socket = backendSocket;
        if (socket != null) {
            socket.sendText(text, true);
        }
    }

    public void forwardBinary(byte[] bytes, int offset, int length) {
        WebSocket socket = backendSocket;
        if (socket != null) {
            socket.sendBinary(ByteBuffer.wrap(bytes, offset, length), true);
        }
    }

    public synchronized void close(int code, String reason) {
        closeBackend(code, reason);
        closeClient(code, reason);
    }

    public void updateJoinPayload(String joinPayload) {
        this.joinPayload = joinPayload;
    }

    private void closeBackend(int code, String reason) {
        WebSocket socket = backendSocket;
        backendSocket = null;
        if (socket != null) {
            try {
                socket.sendClose(code, reason);
            } catch (Exception e) {
                logger.debug("Failed to close backend websocket cleanly", e);
            }
        }
    }

    private void closeClient(int code, String reason) {
        if (suppressClientClose) {
            return;
        }
        if (clientSession.isOpen()) {
            try {
                clientSession.close(code, reason);
            } catch (Exception e) {
                logger.debug("Failed to close client websocket cleanly", e);
            }
        }
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String text = textBuffer.toString();
                textBuffer.setLength(0);
                try {
                    clientSession.getRemote().sendString(text);
                } catch (Exception e) {
                    logger.debug("Failed to forward backend text frame", e);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            try {
                clientSession.getRemote().sendBytes(ByteBuffer.wrap(bytes));
            } catch (Exception e) {
                logger.debug("Failed to forward backend binary frame", e);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeClient(statusCode, reason == null ? "backend_closed" : reason);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.debug("Backend websocket error", error);
            closeClient(1011, "backend_error");
        }
    }
}
