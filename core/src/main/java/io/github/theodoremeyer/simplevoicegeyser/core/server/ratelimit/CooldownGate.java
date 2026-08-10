package io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimum spacing gate between consecutive operations for one key.
 */
public final class CooldownGate {

    private final long cooldownMillis;
    private final ConcurrentHashMap<String, Long> lastSuccessMillis = new ConcurrentHashMap<>();

    /**
     * @param cooldownMillis minimum interval between successes
     */
    public CooldownGate(long cooldownMillis) {
        this.cooldownMillis = Math.max(0L, cooldownMillis);
    }

    /**
     * @param key limit key
     * @return empty when allowed; otherwise milliseconds until retry
     */
    public long tryAcquire(String key) {
        if (cooldownMillis <= 0) {
            return 0L;
        }

        long now = System.currentTimeMillis();
        cleanup(now);

        Long last = lastSuccessMillis.get(key);
        if (last == null) {
            return 0L;
        }

        long elapsed = now - last;
        if (elapsed >= cooldownMillis) {
            return 0L;
        }
        return cooldownMillis - elapsed;
    }

    /**
     * Mark a successful operation, starting the cooldown.
     *
     * @param key limit key
     */
    public void markSuccess(String key) {
        if (cooldownMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        lastSuccessMillis.put(key, now);
        cleanup(now);
    }

    private void cleanup(long now) {
        long maxAge = cooldownMillis + 120_000L;
        lastSuccessMillis.entrySet().removeIf(e -> now - e.getValue() > maxAge);
    }
}
