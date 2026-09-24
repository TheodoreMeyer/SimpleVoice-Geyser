package io.github.theodoremeyer.simplevoicegeyser.velocity.impl.auth;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling auth rate limiter for the Velocity proxy authentication endpoints.
 */
public final class ProxyAuthRateLimiter {

    private final int maxFailures;
    private final long windowMillis;
    private final long lockMillis;
    private final Logger logger;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Creates the limiter.
     *
     * @param maxFailures max allowed failures in window
     * @param window      window duration
     * @param lockDuration lock duration on exceeding max failures
     * @param logger      logger for warning outputs
     */
    public ProxyAuthRateLimiter(int maxFailures, Duration window, Duration lockDuration, Logger logger) {
        this.maxFailures = maxFailures;
        this.windowMillis = window.toMillis();
        this.lockMillis = lockDuration.toMillis();
        this.logger = logger;
    }

    /**
     * Gets whether login is currently allowed for the username.
     *
     * @param username normalized username
     * @return true if allowed
     */
    public boolean allow(String username) {
        long now = System.currentTimeMillis();
        cleanup(now);

        Entry entry = entries.computeIfAbsent(username, ignored -> new Entry(now));
        synchronized (entry) {
            entry.lastSeen = now;

            if (now < entry.lockUntil) {
                return false;
            }

            if (now - entry.windowStart > windowMillis) {
                entry.windowStart = now;
                entry.failures = 0;
                entry.lockUntil = 0;
            }

            return true;
        }
    }

    /**
     * Records a failed authentication attempt.
     *
     * @param username normalized username
     */
    public void recordFailure(String username) {
        long now = System.currentTimeMillis();
        cleanup(now);

        Entry entry = entries.computeIfAbsent(username, ignored -> new Entry(now));
        synchronized (entry) {
            entry.lastSeen = now;

            if (now - entry.windowStart > windowMillis) {
                entry.windowStart = now;
                entry.failures = 0;
                entry.lockUntil = 0;
            }

            entry.failures++;

            if (entry.failures >= maxFailures) {
                entry.lockUntil = now + lockMillis;
                entry.failures = 0;
                entry.windowStart = now;

                if (logger != null) {
                    logger.warn("[Authenticator] Account temporarily locked due to failed auth: {}", username);
                }
            }
        }
    }

    /**
     * Clears failure records for a username upon successful auth.
     *
     * @param username normalized username
     */
    public void reset(String username) {
        entries.remove(username);
    }

    private void cleanup(long now) {
        long maxAge = windowMillis + lockMillis;
        entries.entrySet().removeIf(entry ->
                now - entry.getValue().lastSeen > maxAge &&
                        now >= entry.getValue().lockUntil
        );
    }

    private static final class Entry {
        private long windowStart;
        private int failures = 0;
        private volatile long lockUntil = 0;
        private volatile long lastSeen;

        private Entry(long now) {
            this.lastSeen = now;
            this.windowStart = now;
        }
    }
}
