package io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit;

/**
 * Outcome of a rate-limit check.
 *
 * @param allowed whether the operation may proceed
 * @param retryAfterMs suggested client backoff when rejected
 */
public record RateLimitResult(boolean allowed, long retryAfterMs) {

    /**
     * @return allowed result
     */
    public static RateLimitResult ok() {
        return new RateLimitResult(true, 0L);
    }

    /**
     * @param retryAfterMs backoff hint
     * @return rejected result
     */
    public static RateLimitResult limited(long retryAfterMs) {
        return new RateLimitResult(false, Math.max(1L, retryAfterMs));
    }
}
