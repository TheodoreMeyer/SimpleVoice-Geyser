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

/**
 * Bridges a browser WebSocket session to the WebSocket of a SimpleVoice-Geyser
 * backend server, forwarding text and binary frames in both directions.
 * <p>
 * Stale connections (e.g. after {@link #reconnect(String)}) are detected via
 * monotonically increasing connection ids and closed instead of forwarded.
 */
public final class BackendRelay {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024;
    private final Session clientSession;
    private final Logger logger;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private final AtomicLong connectionIdCounter = new AtomicLong();
    private volatile WebSocket backendSocket;
    private volatile long currentConnectionId;
    private volatile String backendUrl;
    private volatile String joinPayload;
    private volatile String capabilitiesPayload;
    private CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);

    /**
     * Create a relay for the given browser session.
     *
     * @param clientSession the browser WebSocket session to serve
     * @param logger        logger used for diagnostics
     */
    public BackendRelay(Session clientSession, Logger logger) {
        this.clientSession = clientSession;
        this.logger = logger;
    }

    /**
     * Open a WebSocket connection to a backend and forward the join payload to it.
     * Any previous connection is invalidated; its socket is closed if it completes later.
     *
     * @param backendUrl  WebSocket URL of the backend to connect to
     * @param joinPayload JSON join message sent to the backend once connected, or {@code null}
     */
    public void connect(String backendUrl, String joinPayload) {
        long id;
        synchronized (this) {
            this.backendUrl = backendUrl;
            this.joinPayload = joinPayload;
            id = connectionIdCounter.incrementAndGet();
            currentConnectionId = id;
        }
        HTTP_CLIENT.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .buildAsync(URI.create(backendUrl), new Listener(id))
                    .whenComplete((socket, error) -> {
                        synchronized (BackendRelay.this) {
                            if (currentConnectionId != id) {
                                if (socket != null) socket.sendClose(1000, "stale_connection");
                                return;
                            }
                            if (error != null) {
                                logger.error("Failed to connect backend relay to {}", backendUrl, error);
                                closeBackend(1011, "backend_connect_failed");
                                closeClient(1011, "backend_connect_failed");
                                return;
                            }
                            WebSocket previousSocket = backendSocket;
                            backendSocket = socket;
                            if (previousSocket != null) {
                                try {
                                    previousSocket.sendClose(1000, "backend_switch");
                                } catch (Exception e) {
                                    logger.debug("Failed to close previous backend websocket cleanly", e);
                                }
                            }
                            if (joinPayload != null && !joinPayload.isBlank()) sendText(socket, joinPayload, id);
                            if (capabilitiesPayload != null && !capabilitiesPayload.isBlank()) sendText(socket, capabilitiesPayload, id);
                        }
                    });
    }

    /**
     * Switch to a different backend without closing the browser session.
     * Re-sends the stored join and capability payloads on the new connection.
     *
     * @param newBackendUrl WebSocket URL of the backend to switch to
     */
    public synchronized void reconnect(String newBackendUrl) {
        connect(newBackendUrl, joinPayload);
    }

    /**
     * Forward a text frame from the browser to the backend, if connected.
     *
     * @param text raw text frame to relay
     */
    public synchronized void forwardText(String text) { WebSocket socket = backendSocket; if (socket != null) sendText(socket, text, currentConnectionId); }

    /**
     * Forward a binary frame from the browser to the backend, if connected.
     *
     * @param bytes  source buffer
     * @param offset start offset of the frame within {@code bytes}
     * @param length number of bytes in the frame
     */
    public synchronized void forwardBinary(byte[] bytes, int offset, int length) { WebSocket socket = backendSocket; if (socket != null) { byte[] copy = java.util.Arrays.copyOfRange(bytes, offset, offset + length); sendBinary(socket, ByteBuffer.wrap(copy), currentConnectionId); } }

    /**
     * Close both the backend socket and the browser session.
     *
     * @param code   WebSocket close code
     * @param reason human-readable close reason
     */
    public synchronized void close(int code, String reason) { closeBackend(code, reason); closeClient(code, reason); }

    /**
     * Replace the join payload sent when (re)connecting to a backend.
     *
     * @param payload new JSON join payload, or {@code null} to clear it
     */
    public void updateJoinPayload(String payload) { joinPayload = payload; }

    /**
     * Replace the capabilities payload sent after the join payload.
     *
     * @param payload new JSON capabilities payload, or {@code null} to clear it
     */
    public void updateCapabilitiesPayload(String payload) { capabilitiesPayload = payload; }

    private void closeBackend(int code, String reason) {
        WebSocket socket = backendSocket; backendSocket = null;
        if (socket != null) try { socket.sendClose(code, reason); } catch (Exception e) { logger.debug("Failed to close backend websocket cleanly", e); }
    }
    private void sendText(WebSocket socket, String text, long connectionId) {
        sendChain = sendChain.handle((ignored, error) -> null)
                .thenCompose(ignored -> socket.sendText(text, true))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        logger.debug("Failed to send backend text frame", error);
                        if (connectionId == currentConnectionId && backendSocket == socket) {
                            closeClient(1011, "backend_send_failed");
                        }
                    }
                });
    }
    private void sendBinary(WebSocket socket, ByteBuffer data, long connectionId) {
        sendChain = sendChain.handle((ignored, error) -> null)
                .thenCompose(ignored -> socket.sendBinary(data, true))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        logger.debug("Failed to send backend binary frame", error);
                        if (connectionId == currentConnectionId && backendSocket == socket) {
                            closeClient(1011, "backend_send_failed");
                        }
                    }
                });
    }
    private void closeClient(int code, String reason) {
        if (!clientSession.isOpen()) return;
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
            if (binaryBuffer == null) binaryBuffer = ByteBuffer.allocate(Math.max(1, frameSize));
            else if (binaryBuffer.remaining() < frameSize) {
                int required = binaryBuffer.position() + frameSize;
                int capacity = binaryBuffer.capacity();
                while (capacity < required) capacity = Math.min(MAX_MESSAGE_SIZE, Math.max(required, Math.max(1, capacity * 2)));
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
