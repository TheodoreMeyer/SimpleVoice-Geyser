package io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling attempt counter with optional lockout after too many failures.
 */
public final class SlidingWindowCounter {

    private final int maxAttempts;
    private final long windowMillis;
    private final long lockoutMillis;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * @param maxAttempts failures before lockout (or max attempts in window when lockout is 0)
     * @param windowMillis rolling window
     * @param lockoutMillis lock duration after threshold; 0 disables lockout
     */
    public SlidingWindowCounter(int maxAttempts, long windowMillis, long lockoutMillis) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowMillis = Math.max(1L, windowMillis);
        this.lockoutMillis = Math.max(0L, lockoutMillis);
    }

    /**
     * @param key limit key
     * @return empty when allowed; otherwise milliseconds until retry
     */
    public long tryAcquire(String key) {
        long now = System.currentTimeMillis();
        cleanup(now);

        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry(now));
        synchronized (entry) {
            entry.lastSeen = now;
            resetWindowIfExpired(entry, now);

            if (entry.lockUntil > now) {
                return entry.lockUntil - now;
            }
            return 0L;
        }
    }

    /**
     * Record a failed attempt.
     *
     * @param key limit key
     * @return milliseconds until retry when locked; otherwise 0
     */
    public long recordFailure(String key) {
        long now = System.currentTimeMillis();
        cleanup(now);

        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry(now));
        synchronized (entry) {
            entry.lastSeen = now;
            resetWindowIfExpired(entry, now);
            entry.attempts++;

            if (entry.attempts >= maxAttempts) {
                if (lockoutMillis > 0) {
                    entry.lockUntil = now + lockoutMillis;
                }
                entry.attempts = 0;
                entry.windowStart = now;
                if (entry.lockUntil > now) {
                    return entry.lockUntil - now;
                }
            }
            return 0L;
        }
    }

    /**
     * Record a successful operation against the attempt budget (sliding max without lockout).
     *
     * @param key limit key
     * @param maxInWindow maximum successes per window
     * @return empty when allowed; otherwise milliseconds until retry
     */
    public long tryRecordSuccess(String key, int maxInWindow) {
        long now = System.currentTimeMillis();
        cleanup(now);

        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry(now));
        synchronized (entry) {
            entry.lastSeen = now;
            resetWindowIfExpired(entry, now);

            if (entry.attempts >= maxInWindow) {
                long windowEnd = entry.windowStart + windowMillis;
                return Math.max(1L, windowEnd - now);
            }
            entry.attempts++;
            return 0L;
        }
    }

    /**
     * Clear state for a key.
     *
     * @param key limit key
     */
    public void reset(String key) {
        entries.remove(key);
    }

    private void resetWindowIfExpired(Entry entry, long now) {
        if (now - entry.windowStart > windowMillis) {
            entry.windowStart = now;
            entry.attempts = 0;
            if (now >= entry.lockUntil) {
                entry.lockUntil = 0L;
            }
        }
    }

    private void cleanup(long now) {
        long maxAge = windowMillis + lockoutMillis + 60_000L;
        entries.entrySet().removeIf(e ->
                now - e.getValue().lastSeen > maxAge && now >= e.getValue().lockUntil
        );
    }

    private static final class Entry {
        private long windowStart;
        private int attempts;
        private volatile long lockUntil;
        private volatile long lastSeen;

        private Entry(long now) {
            windowStart = now;
            lastSeen = now;
        }
    }
}
