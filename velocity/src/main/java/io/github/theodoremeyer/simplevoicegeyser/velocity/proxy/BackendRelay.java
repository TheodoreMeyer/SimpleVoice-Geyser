package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

public final class BackendRelay {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024;
    private final Session clientSession;
    private final Logger logger;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicLong connectionIdCounter = new AtomicLong();
    private volatile WebSocket backendSocket;
    private volatile long currentConnectionId;
    private volatile String backendUrl;
    private volatile String joinPayload;
    private volatile String capabilitiesPayload;
    private volatile boolean suppressClientClose;
    private CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);

    public BackendRelay(Session clientSession, Logger logger) {
        this.clientSession = clientSession;
        this.logger = logger;
    }

    public synchronized void connect(String backendUrl, String joinPayload) {
        this.backendUrl = backendUrl;
        this.joinPayload = joinPayload;
        long id = connectionIdCounter.incrementAndGet();
        currentConnectionId = id;
        try {
            backendSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .buildAsync(URI.create(backendUrl), new Listener(id)).join();
            if (this.joinPayload != null && !this.joinPayload.isBlank()) sendText(backendSocket, this.joinPayload);
            if (capabilitiesPayload != null && !capabilitiesPayload.isBlank()) sendText(backendSocket, capabilitiesPayload);
        } catch (Exception e) {
            if (currentConnectionId == id) {
                logger.error("Failed to connect backend relay to {}", backendUrl, e);
                closeBackend(1011, "backend_connect_failed");
                closeClient(1011, "backend_connect_failed");
            }
        }
    }

    public synchronized void reconnect(String newBackendUrl) {
        currentConnectionId = connectionIdCounter.incrementAndGet();
        suppressClientClose = true;
        try { closeBackend(1000, "backend_switch"); }
        finally { suppressClientClose = false; }
        connect(newBackendUrl, joinPayload);
    }

    public synchronized void forwardText(String text) { WebSocket socket = backendSocket; if (socket != null) sendText(socket, text); }
    public synchronized void forwardBinary(byte[] bytes, int offset, int length) { WebSocket socket = backendSocket; if (socket != null) sendBinary(socket, ByteBuffer.wrap(bytes, offset, length)); }
    public synchronized void close(int code, String reason) { closeBackend(code, reason); closeClient(code, reason); }
    public void updateJoinPayload(String payload) { joinPayload = payload; }
    public void updateCapabilitiesPayload(String payload) { capabilitiesPayload = payload; }

    private void closeBackend(int code, String reason) {
        WebSocket socket = backendSocket; backendSocket = null;
        if (socket != null) try { socket.sendClose(code, reason); } catch (Exception e) { logger.debug("Failed to close backend websocket cleanly", e); }
    }
    private void sendText(WebSocket socket, String text) {
        sendChain = sendChain.handle((ignored, error) -> null)
                .thenCompose(ignored -> socket.sendText(text, true))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        logger.debug("Failed to send backend text frame", error);
                        closeClient(1011, "backend_send_failed");
                    }
                });
    }
    private void sendBinary(WebSocket socket, ByteBuffer data) {
        sendChain = sendChain.handle((ignored, error) -> null)
                .thenCompose(ignored -> socket.sendBinary(data, true))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        logger.debug("Failed to send backend binary frame", error);
                        closeClient(1011, "backend_send_failed");
                    }
                });
    }
    private void closeClient(int code, String reason) {
        if (suppressClientClose || !clientSession.isOpen()) return;
        try { clientSession.close(code, reason); } catch (Exception e) { logger.debug("Failed to close client websocket cleanly", e); }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();
        private ByteBuffer binaryBuffer;
        private int textSize;
        private int binarySize;
        private final long connectionId;
        Listener(long connectionId) { this.connectionId = connectionId; }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            if (textSize > MAX_MESSAGE_SIZE - data.length()) {
                textBuffer.setLength(0); textSize = 0;
                closeClient(1009, "backend_message_too_large");
                socket.sendClose(1009, "message_too_large");
                return CompletableFuture.completedFuture(null);
            }
            textBuffer.append(data);
            textSize += data.length();
            if (last) {
                String text = textBuffer.toString(); textBuffer.setLength(0); textSize = 0;
                if (connectionId == currentConnectionId) try { clientSession.getRemote().sendString(text); } catch (Exception e) { logger.debug("Failed to forward backend text frame", e); }
            }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            if (connectionId != currentConnectionId) { socket.request(1); return CompletableFuture.completedFuture(null); }
            int frameSize = data.remaining();
            if (binarySize > MAX_MESSAGE_SIZE - frameSize) {
                binaryBuffer = null; binarySize = 0;
                closeClient(1009, "backend_message_too_large");
                socket.sendClose(1009, "message_too_large");
                return CompletableFuture.completedFuture(null);
            }
            if (binaryBuffer == null) binaryBuffer = ByteBuffer.allocate(frameSize);
            else if (binaryBuffer.remaining() < frameSize) {
                int required = binaryBuffer.position() + frameSize;
                int capacity = binaryBuffer.capacity();
                while (capacity < required) capacity = Math.min(MAX_MESSAGE_SIZE, capacity * 2);
                ByteBuffer expanded = ByteBuffer.allocate(capacity); binaryBuffer.flip(); expanded.put(binaryBuffer); binaryBuffer = expanded;
            }
            binaryBuffer.put(data);
            binarySize += frameSize;
            if (last) {
                binaryBuffer.flip(); byte[] bytes = new byte[binaryBuffer.remaining()]; binaryBuffer.get(bytes); binaryBuffer = null; binarySize = 0;
                try { clientSession.getRemote().sendBytes(ByteBuffer.wrap(bytes)); } catch (Exception e) { logger.debug("Failed to forward backend binary frame", e); }
            }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            if (connectionId == currentConnectionId) closeClient(code, reason == null ? "backend_closed" : reason);
            return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket socket, Throwable error) {
            if (connectionId == currentConnectionId) { logger.debug("Backend websocket error", error); closeClient(1011, "backend_error"); }
        }
    }
}
