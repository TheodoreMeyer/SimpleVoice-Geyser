package io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Audio-frame ingress gate that tolerates browser/WebSocket bursts.
 * <p>
 * Normal ~50 fps speech with short coalesced bursts must produce zero drops.
 * Only sustained flooding across a multi-second window is rejected.
 */
public final class AudioIngressLimiter {

    private final boolean enabled;
    private final int maxFrameBytes;
    private final int normalBurstFrames;
    private final int sustainedMaxFrames;
    private final long sustainedWindowMillis;
    private final int disconnectAfterAbuseWindows;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong dropMalformed = new AtomicLong();
    private final AtomicLong dropSustainedAbuse = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong lastAggregateLogNanos = new AtomicLong();

    /**
     * @param enabled whether limiting is active
     * @param maxFrameBytes maximum accepted payload size
     * @param normalBurstFrames short-term burst allowance (informational / soft)
     * @param sustainedMaxFps sustained frames/sec ceiling
     * @param sustainedWindowSeconds window length for sustained detection
     * @param disconnectAfterAbuseWindows consecutive abusive windows before hard reject streak
     */
    public AudioIngressLimiter(
            boolean enabled,
            int maxFrameBytes,
            int normalBurstFrames,
            int sustainedMaxFps,
            int sustainedWindowSeconds,
            int disconnectAfterAbuseWindows
    ) {
        this.enabled = enabled;
        this.maxFrameBytes = Math.max(1, maxFrameBytes);
        this.normalBurstFrames = Math.max(1, normalBurstFrames);
        int windowSec = Math.max(1, sustainedWindowSeconds);
        this.sustainedWindowMillis = windowSec * 1000L;
        this.sustainedMaxFrames = Math.max(normalBurstFrames, Math.max(1, sustainedMaxFps) * windowSec);
        this.disconnectAfterAbuseWindows = Math.max(1, disconnectAfterAbuseWindows);
    }

    /**
     * @param connectionKey connection id
     * @param frameBytes payload size
     * @return rate-limit result
     */
    public RateLimitResult tryAccept(String connectionKey, int frameBytes) {
        if (frameBytes <= 0 || frameBytes > maxFrameBytes) {
            dropMalformed.incrementAndGet();
            return RateLimitResult.limited(0L);
        }
        if (!enabled) {
            accepted.incrementAndGet();
            return RateLimitResult.ok();
        }

        long now = System.currentTimeMillis();
        Session session = sessions.computeIfAbsent(connectionKey, ignored -> new Session());
        synchronized (session) {
            prune(session, now);
            if (session.timestamps.size() >= sustainedMaxFrames) {
                session.abuseWindows++;
                dropSustainedAbuse.incrementAndGet();
                long retry = Math.max(1L, session.timestamps.peekFirst() + sustainedWindowMillis - now);
                return RateLimitResult.limited(retry);
            }
            session.timestamps.addLast(now);
            // Soft burst tracking only — do not reject normal coalesced wake-ups.
            if (session.timestamps.size() > normalBurstFrames) {
                session.sawLargeBurst = true;
            }
            if (session.abuseWindows > 0 && session.timestamps.size() < sustainedMaxFrames / 2) {
                session.abuseWindows = Math.max(0, session.abuseWindows - 1);
            }
            accepted.incrementAndGet();
            return RateLimitResult.ok();
        }
    }

    /**
     * @return whether the key has exceeded consecutive abuse windows
     */
    public boolean shouldDisconnectForAbuse(String connectionKey) {
        Session session = sessions.get(connectionKey);
        return session != null && session.abuseWindows >= disconnectAfterAbuseWindows;
    }

    public long getAccepted() {
        return accepted.get();
    }

    public long getDropMalformed() {
        return dropMalformed.get();
    }

    public long getDropSustainedAbuse() {
        return dropSustainedAbuse.get();
    }

    /**
     * Rate-limited aggregate log line (never includes payload bytes content).
     *
     * @return summary or null when not due
     */
    public String maybeAggregateDropSummary() {
        long now = System.nanoTime();
        long previous = lastAggregateLogNanos.get();
        if (now - previous < 2_000_000_000L) {
            return null;
        }
        if (!lastAggregateLogNanos.compareAndSet(previous, now)) {
            return null;
        }
        long abuse = dropSustainedAbuse.get();
        long malformed = dropMalformed.get();
        if (abuse == 0L && malformed == 0L) {
            return null;
        }
        return "AudioIngress: accepted=" + accepted.get()
                + " dropMalformed=" + malformed
                + " dropSustainedAbuse=" + abuse;
    }

    public void clear(String connectionKey) {
        sessions.remove(connectionKey);
    }

    private void prune(Session session, long now) {
        long cutoff = now - sustainedWindowMillis;
        while (!session.timestamps.isEmpty() && session.timestamps.peekFirst() < cutoff) {
            session.timestamps.removeFirst();
        }
    }

    private static final class Session {
        private final ArrayDeque<Long> timestamps = new ArrayDeque<>();
        private int abuseWindows;
        private boolean sawLargeBurst;
    }
}
