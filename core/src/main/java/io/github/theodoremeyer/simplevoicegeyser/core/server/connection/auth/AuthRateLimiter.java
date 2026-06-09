package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple rolling auth limiter.
 */
final class AuthRateLimiter {

    /**
     * Maximum failures before lock.
     */
    private final int maxFailures;

    /**
     * Rolling auth window.
     */
    private final long windowMillis;

    /**
     * Lock duration.
     */
    private final long lockMillis;

    /**
     * Auth entries.
     */
    private final ConcurrentHashMap<String, Entry>
            entries = new ConcurrentHashMap<>();

    /**
     * Creates the limiter.
     *
     * @param maxFailures max failures
     * @param window window duration
     * @param lockDuration lock duration
     */
    AuthRateLimiter(
            int maxFailures,
            Duration window,
            Duration lockDuration
    ) {

        this.maxFailures = maxFailures;
        this.windowMillis = window.toMillis();
        this.lockMillis = lockDuration.toMillis();
    }

    /**
     * Gets whether login is allowed.
     *
     * @param username username
     * @return true if allowed
     */
    boolean allow(String username) {

        long now = System.currentTimeMillis();

        cleanup(now);

        Entry entry =
                entries.computeIfAbsent(
                        username,
                        ignored -> new Entry()
                );

        synchronized (entry) {

            entry.lastSeen = now;

            // lock still active
            if (now < entry.lockUntil) {
                return false;
            }

            // reset expired rolling window
            if (now - entry.windowStart >
                    windowMillis) {

                entry.windowStart = now;
                entry.failures = 0;
                entry.lockUntil = 0;
            }

            return true;
        }
    }

    /**
     * Records failed auth attempt.
     *
     * @param username username
     */
    void recordFailure(String username) {

        long now = System.currentTimeMillis();

        cleanup(now);

        Entry entry =
                entries.computeIfAbsent(
                        username,
                        ignored -> new Entry()
                );

        synchronized (entry) {

            entry.lastSeen = now;

            // reset expired rolling window
            if (now - entry.windowStart >
                    windowMillis) {

                entry.windowStart = now;
                entry.failures = 0;
                entry.lockUntil = 0;
            }

            entry.failures++;

            if (entry.failures >= maxFailures) {

                entry.lockUntil =
                        now + lockMillis;

                entry.failures = 0;
                entry.windowStart = now;

                SvgCore.getLogger().warning(
                        "[Authenticator] " +
                                "Account temporarily locked: " +
                                username
                );
            }
        }
    }

    /**
     * Clears failures for a username.
     *
     * @param username username
     */
    void reset(String username) {
        entries.remove(username);
    }

    /**
     * Removes stale entries.
     *
     * @param now current timestamp
     */
    private void cleanup(long now) {

        long maxAge =
                windowMillis + lockMillis;

        entries.entrySet().removeIf(entry ->

                now - entry.getValue().lastSeen >
                        maxAge &&

                        now >= entry.getValue().lockUntil
        );
    }

    /**
     * Rate limit entry.
     */
    private static final class Entry {

        /**
         * Window start timestamp.
         */
        private long windowStart;

        /**
         * Failure count.
         */
        private int failures = 0;

        /**
         * Lock expiration timestamp.
         */
        private volatile long lockUntil = 0;

        /**
         * Last seen timestamp.
         */
        private volatile long lastSeen;

        /**
         * Load an Entry
         */
        private Entry() {
            long now = System.currentTimeMillis();
            lastSeen = now;
            windowStart = now;
        }
    }
}