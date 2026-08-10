package io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks recently processed mutation {@code operationId} values per connection.
 */
public final class DuplicateOperationTracker {

    private final long retentionMillis;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> byConnection =
            new ConcurrentHashMap<>();

    /**
     * @param retentionSeconds how long to remember operation ids
     */
    public DuplicateOperationTracker(int retentionSeconds) {
        this.retentionMillis = Math.max(1L, retentionSeconds) * 1000L;
    }

    /**
     * @param connectionKey connection identifier (typically player uuid)
     * @param operationId client operation id
     * @return true when this operation id has not been seen recently
     */
    public boolean register(String connectionKey, String operationId) {
        if (connectionKey == null || operationId == null || operationId.isBlank()) {
            return true;
        }

        long now = System.currentTimeMillis();
        ConcurrentHashMap<String, Long> ops = byConnection.computeIfAbsent(
                connectionKey,
                ignored -> new ConcurrentHashMap<>()
        );
        Long previous = ops.putIfAbsent(operationId, now);
        cleanupConnection(ops, now);
        if (ops.isEmpty()) {
            byConnection.remove(connectionKey, ops);
        }
        return previous == null;
    }

    private void cleanupConnection(ConcurrentHashMap<String, Long> ops, long now) {
        ops.entrySet().removeIf(e -> now - e.getValue() > retentionMillis);
    }

    /**
     * Bounded cleanup across connections.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        byConnection.entrySet().removeIf(entry -> {
            cleanupConnection(entry.getValue(), now);
            return entry.getValue().isEmpty();
        });
    }
}
