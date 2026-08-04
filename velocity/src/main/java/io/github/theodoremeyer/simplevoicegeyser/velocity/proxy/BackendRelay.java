package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

public final class BackendRelay {
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
                    .buildAsync(URI.create(backendUrl), new Listener(id)).join();
            if (this.joinPayload != null && !this.joinPayload.isBlank()) backendSocket.sendText(this.joinPayload, true);
            if (capabilitiesPayload != null && !capabilitiesPayload.isBlank()) backendSocket.sendText(capabilitiesPayload, true);
        } catch (Exception e) {
            if (currentConnectionId == id) {
                logger.error("Failed to connect backend relay to {}", backendUrl, e);
                closeClient(1011, "backend_connect_failed");
            }
        }
    }

    public synchronized void reconnect(String newBackendUrl) {
        suppressClientClose = true;
        try { closeBackend(1000, "backend_switch"); }
        finally { suppressClientClose = false; }
        connect(newBackendUrl, joinPayload);
    }

    public void forwardText(String text) { WebSocket socket = backendSocket; if (socket != null) socket.sendText(text, true); }
    public void forwardBinary(byte[] bytes, int offset, int length) { WebSocket socket = backendSocket; if (socket != null) socket.sendBinary(ByteBuffer.wrap(bytes, offset, length), true); }
    public synchronized void close(int code, String reason) { closeBackend(code, reason); closeClient(code, reason); }
    public void updateJoinPayload(String payload) { joinPayload = payload; }
    public void updateCapabilitiesPayload(String payload) { capabilitiesPayload = payload; }

    private void closeBackend(int code, String reason) {
        WebSocket socket = backendSocket; backendSocket = null;
        if (socket != null) try { socket.sendClose(code, reason); } catch (Exception e) { logger.debug("Failed to close backend websocket cleanly", e); }
    }
    private void closeClient(int code, String reason) {
        if (suppressClientClose || !clientSession.isOpen()) return;
        try { clientSession.close(code, reason); } catch (Exception e) { logger.debug("Failed to close client websocket cleanly", e); }
    }

    private final class Listener implements WebSocket.Listener {        private final StringBuilder textBuffer = new StringBuilder();
        private ByteBuffer binaryBuffer;
        private final long connectionId;
        Listener(long connectionId) { this.connectionId = connectionId; }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String text = textBuffer.toString(); textBuffer.setLength(0);
                if (connectionId == currentConnectionId) try { clientSession.getRemote().sendString(text); } catch (Exception e) { logger.debug("Failed to forward backend text frame", e); }
            }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            if (connectionId != currentConnectionId) { socket.request(1); return CompletableFuture.completedFuture(null); }
            if (binaryBuffer == null) binaryBuffer = ByteBuffer.allocate(data.remaining());
            else if (binaryBuffer.remaining() < data.remaining()) {
                ByteBuffer expanded = ByteBuffer.allocate(binaryBuffer.position() + data.remaining()); binaryBuffer.flip(); expanded.put(binaryBuffer); binaryBuffer = expanded;
            }
            binaryBuffer.put(data);
            if (last) {
                binaryBuffer.flip(); byte[] bytes = new byte[binaryBuffer.remaining()]; binaryBuffer.get(bytes); binaryBuffer = null;
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