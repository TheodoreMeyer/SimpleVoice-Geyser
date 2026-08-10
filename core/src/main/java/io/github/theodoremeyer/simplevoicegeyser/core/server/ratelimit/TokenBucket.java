package io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit;

/**
 * Thread-safe token bucket using monotonic {@link System#nanoTime()}.
 */
public final class TokenBucket {

    private final double capacity;
    private final double refillPerSecond;
    private final Object lock = new Object();

    private double tokens;
    private long lastRefillNanos;

    /**
     * @param burst maximum burst capacity
     * @param refillPerSecond steady refill rate
     */
    public TokenBucket(double burst, double refillPerSecond) {
        this.capacity = Math.max(0, burst);
        this.refillPerSecond = Math.max(0, refillPerSecond);
        this.tokens = this.capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Attempt to consume {@code cost} tokens.
     *
     * @param cost tokens to consume (minimum 1)
     * @return empty when allowed; otherwise milliseconds until retry
     */
    public long tryConsume(int cost) {
        if (capacity <= 0 || refillPerSecond <= 0) {
            return 0L;
        }

        int amount = Math.max(1, cost);
        long now = System.nanoTime();

        synchronized (lock) {
            refill(now);

            if (tokens >= amount) {
                tokens -= amount;
                return 0L;
            }

            double deficit = amount - tokens;
            double seconds = deficit / refillPerSecond;
            return Math.max(1L, (long) Math.ceil(seconds * 1000.0));
        }
    }

    private void refill(long now) {
        if (now <= lastRefillNanos) {
            return;
        }
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
        lastRefillNanos = now;
    }
}
