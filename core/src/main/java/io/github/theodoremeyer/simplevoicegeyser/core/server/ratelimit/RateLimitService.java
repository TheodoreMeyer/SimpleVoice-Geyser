package io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Central non-blocking rate limit service keyed by connection or auth identifiers.
 */
public final class RateLimitService {

    private final boolean enabled;
    private final boolean audioBypass;
    private final DuplicateOperationTracker duplicateOperations;

    private final SlidingWindowCounter authentication;
    private final int chatMaxLength;
    private final CooldownGate groupsRefresh;
    private final CooldownGate groupCreateCooldown;
    private final SlidingWindowCounter groupCreateWindow;
    private final CooldownGate groupJoin;
    private final CooldownGate groupLeave;
    private final SlidingWindowCounter groupPassword;
    private final int controlMaxBytes;
    private final AudioIngressLimiter audioIngress;

    private final ConcurrentHashMap<String, TokenBucket> chatBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucket> controlBuckets = new ConcurrentHashMap<>();

    /**
     * Create limiters from current config.
     */
    public RateLimitService() {
        SvgConfig config = SvgCore.getConfig();
        enabled = Boolean.TRUE.equals(config.RATE_LIMITS_ENABLED.get());
        audioBypass = Boolean.TRUE.equals(config.RATE_LIMITS_AUDIO_BYPASS.get());

        duplicateOperations = new DuplicateOperationTracker(
                config.RATE_LIMITS_DUPLICATE_RETENTION_SECONDS.get()
        );

        authentication = new SlidingWindowCounter(
                config.RATE_LIMITS_AUTH_ATTEMPTS.get(),
                config.RATE_LIMITS_AUTH_WINDOW_SECONDS.get() * 1000L,
                config.RATE_LIMITS_AUTH_LOCKOUT_SECONDS.get() * 1000L
        );

        chatMaxLength = config.RATE_LIMITS_CHAT_MAX_LENGTH.get();

        groupsRefresh = new CooldownGate(config.RATE_LIMITS_GROUPS_REFRESH_COOLDOWN_SECONDS.get() * 1000L);

        groupCreateCooldown = new CooldownGate(
                config.RATE_LIMITS_GROUP_CREATE_COOLDOWN_SECONDS.get() * 1000L
        );
        groupCreateWindow = new SlidingWindowCounter(
                config.RATE_LIMITS_GROUP_CREATE_BURST.get(),
                config.RATE_LIMITS_GROUP_CREATE_WINDOW_SECONDS.get() * 1000L,
                0L
        );

        groupJoin = new CooldownGate(config.RATE_LIMITS_GROUP_JOIN_COOLDOWN_MS.get());
        groupLeave = new CooldownGate(config.RATE_LIMITS_GROUP_LEAVE_COOLDOWN_MS.get());

        groupPassword = new SlidingWindowCounter(
                config.RATE_LIMITS_GROUP_PASSWORD_ATTEMPTS.get(),
                config.RATE_LIMITS_GROUP_PASSWORD_WINDOW_SECONDS.get() * 1000L,
                config.RATE_LIMITS_GROUP_PASSWORD_LOCKOUT_SECONDS.get() * 1000L
        );

        controlMaxBytes = config.RATE_LIMITS_CONTROL_MAX_BYTES.get();

        int normalBurst = config.RATE_LIMITS_AUDIO_NORMAL_BURST_FRAMES.get() != null
                ? config.RATE_LIMITS_AUDIO_NORMAL_BURST_FRAMES.get()
                : config.RATE_LIMITS_AUDIO_BURST_FRAMES.get();
        boolean audioEnabled = enabled
                && !audioBypass
                && Boolean.TRUE.equals(config.RATE_LIMITS_AUDIO_ENABLED.get());
        audioIngress = new AudioIngressLimiter(
                audioEnabled,
                config.RATE_LIMITS_AUDIO_MAX_FRAME_BYTES.get(),
                normalBurst,
                config.RATE_LIMITS_AUDIO_SUSTAINED_MAX_FPS.get(),
                config.RATE_LIMITS_AUDIO_SUSTAINED_WINDOW_SECONDS.get(),
                config.RATE_LIMITS_AUDIO_ABUSE_WINDOWS.get()
        );

        // Store config-derived bucket parameters for lazy per-key buckets.
        this.chatBurst = config.RATE_LIMITS_CHAT_BURST.get();
        this.chatRefill = config.RATE_LIMITS_CHAT_REFILL_PER_SECOND.get();
        this.controlBurst = config.RATE_LIMITS_CONTROL_BURST.get();
        this.controlRefill = config.RATE_LIMITS_CONTROL_REFILL_PER_SECOND.get();
    }

    private final double chatBurst;
    private final double chatRefill;
    private final double controlBurst;
    private final double controlRefill;

    /**
     * @return whether rate limiting is active
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param connectionKey connection or player key
     * @param operationId client operation id
     * @return true when the mutation may proceed (not a recent duplicate)
     */
    public boolean registerMutation(String connectionKey, String operationId) {
        if (!enabled) {
            return true;
        }
        duplicateOperations.cleanup();
        return duplicateOperations.register(connectionKey, operationId);
    }

    /**
     * Authentication attempt gate (before password check).
     *
     * @param authKey normalized username key
     * @return rate limit result
     */
    public RateLimitResult tryAuthentication(String authKey) {
        if (!enabled) {
            return RateLimitResult.ok();
        }
        long retry = authentication.tryAcquire(authKey);
        return retry > 0 ? RateLimitResult.limited(retry) : RateLimitResult.ok();
    }

    /**
     * Record failed authentication.
     *
     * @param authKey normalized username key
     */
    public void recordAuthenticationFailure(String authKey) {
        if (!enabled) {
            return;
        }
        authentication.recordFailure(authKey);
    }

    /**
     * Clear authentication failures after success.
     *
     * @param authKey normalized username key
     */
    public void resetAuthentication(String authKey) {
        authentication.reset(authKey);
    }

    /**
     * Chat message gate.
     *
     * @param connectionKey connection key
     * @param messageLength sanitized message length
     * @return rate limit result
     */
    public RateLimitResult tryChat(String connectionKey, int messageLength) {
        if (!enabled) {
            return RateLimitResult.ok();
        }
        if (messageLength > chatMaxLength) {
            return RateLimitResult.limited(0L);
        }
        TokenBucket bucket = chatBuckets.computeIfAbsent(
                connectionKey,
                ignored -> new TokenBucket(chatBurst, chatRefill)
        );
        long retry = bucket.tryConsume(1);
        return retry > 0 ? RateLimitResult.limited(retry) : RateLimitResult.ok();
    }

    /**
     * Groups refresh gate.
     *
     * @param connectionKey connection key
     * @return rate limit result
     */
    public RateLimitResult tryGroupsRefresh(String connectionKey) {
        if (!enabled) {
            return RateLimitResult.ok();
        }
        long retry = groupsRefresh.tryAcquire(connectionKey);
        return retry > 0 ? RateLimitResult.limited(retry) : RateLimitResult.ok();
    }

    /**
     * Mark a successful groups refresh.
     *
     * @param connectionKey connection key
     */
    public void markGroupsRefresh(String connectionKey) {
        if (!enabled) {
            return;
        }
        groupsRefresh.markSuccess(connectionKey);
    }

    /**
     * Group create gate.
     *
     * @param connectionKey connection key
     * @return rate limit result
     */
    public RateLimitResult tryGroupCreate(String connectionKey) {
        if (!enabled) {
            return RateLimitResult.ok();
        }
        long cooldownRetry = groupCreateCooldown.tryAcquire(connectionKey);
        if (cooldownRetry > 0) {
            return RateLimitResult.limited(cooldownRetry);
        }
        long windowRetry = groupCreateWindow.tryRecordSuccess(
                connectionKey,
                SvgCore.getConfig().RATE_LIMITS_GROUP_CREATE_BURST.get()
        );
        return windowRetry > 0 ? RateLimitResult.limited(windowRetry) : RateLimitResult.ok();
    }

    /**
     * Mark successful group create for cooldown accounting.
     *
     * @param connectionKey connection key
     */
    public void markGroupCreate(String connectionKey) {
        if (!enabled) {
            return;
        }
        groupCreateCooldown.markSuccess(connectionKey);
    }

    /**
     * Group join gate.
     *
     * @param connectionKey connection key
     * @return rate limit result
     */
    public RateLimitResult tryGroupJoin(String connectionKey) {
        if (!enabled) {
            return RateLimitResult.ok();
        }
        long retry = groupJoin.tryAcquire(connectionKey);
        return retry > 0 ? RateLimitResult.limited(retry) : RateLimitResult.ok();
    }

    /**
     * Mark successful group join.
     *
     * @param connectionKey connection key
     */
    public void markGroupJoin(String connectionKey) {
        if (!enabled) {
            return;
        }
        groupJoin.markSuccess(connectionKey);
    }

    /**
     * Group leave gate.
     *
     * @param connectionKey connection key
     * @return rate limit result
     */
    public RateLimitResult tryGroupLeave(String connectionKey) {
        if (!enabled) {
            return RateLimitResult.ok();
        }
        long retry = groupLeave.tryAcquire(connectionKey);
        return retry > 0 ? RateLimitResult.limited(retry) : RateLimitResult.ok();
    }

    /**
     * Mark successful group leave.
     *
     * @param connectionKey connection key
     */
    public void markGroupLeave(String connectionKey) {
        if (!enabled) {
            return;
        }
        groupLeave.markSuccess(connectionKey);
    }

    /**
     * Group password failure gate.
     *
     * @param connectionKey connection key
     * @return rate limit result
     */
    public RateLimitResult tryGroupPassword(String connectionKey) {
        if (!enabled) {
            return RateLimitResult.ok();
        }
        long retry = groupPassword.tryAcquire(connectionKey);
        return retry > 0 ? RateLimitResult.limited(retry) : RateLimitResult.ok();
    }

    /**
     * Record failed group password attempt.
     *
     * @param connectionKey connection key
     * @return retry after ms when locked
     */
    public long recordGroupPasswordFailure(String connectionKey) {
        if (!enabled) {
            return 0L;
        }
        return groupPassword.recordFailure(connectionKey);
    }

    /**
     * Reset group password failures after success.
     *
     * @param connectionKey connection key
     */
    public void resetGroupPassword(String connectionKey) {
        groupPassword.reset(connectionKey);
    }

    /**
     * Control websocket JSON gate.
     *
     * @param connectionKey connection key
     * @param byteLength UTF-8 byte length of payload
     * @return rate limit result
     */
    public RateLimitResult tryControlPacket(String connectionKey, int byteLength) {
        if (!enabled) {
            return RateLimitResult.ok();
        }
        if (byteLength > controlMaxBytes) {
            return RateLimitResult.limited(1000L);
        }
        TokenBucket bucket = controlBuckets.computeIfAbsent(
                connectionKey,
                ignored -> new TokenBucket(controlBurst, controlRefill)
        );
        long retry = bucket.tryConsume(1);
        return retry > 0 ? RateLimitResult.limited(retry) : RateLimitResult.ok();
    }

    /**
     * Inbound mic frame gate.
     *
     * @param connectionKey connection key
     * @param frameBytes frame size
     * @return rate limit result
     */
    public RateLimitResult tryAudioFrame(String connectionKey, int frameBytes) {
        RateLimitResult result = audioIngress.tryAccept(connectionKey, frameBytes);
        if (!result.allowed()) {
            String summary = audioIngress.maybeAggregateDropSummary();
            if (summary != null) {
                try {
                    SvgCore.getLogger().debug(summary);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return result;
    }

    /**
     * @return whether the audio sustained-abuse limiter is bypassed (DEBUG matrix)
     */
    public boolean isAudioBypassEnabled() {
        return audioBypass;
    }

    /**
     * @return audio ingress limiter for diagnostics/tests
     */
    public AudioIngressLimiter getAudioIngressLimiter() {
        return audioIngress;
    }

    /**
     * Resolve the longest retry hint from multiple checks.
     *
     * @param results candidates
     * @return combined result
     */
    public static RateLimitResult firstLimited(RateLimitResult... results) {
        long maxRetry = 0L;
        for (RateLimitResult result : results) {
            if (result != null && !result.allowed()) {
                maxRetry = Math.max(maxRetry, result.retryAfterMs());
            }
        }
        return maxRetry > 0 ? RateLimitResult.limited(maxRetry) : RateLimitResult.ok();
    }

    /**
     * Lazy per-key bucket factory helper for tests.
     *
     * @param map bucket map
     * @param key key
     * @param factory factory
     * @return bucket
     */
    static <K> TokenBucket bucketFor(
            ConcurrentHashMap<K, TokenBucket> map,
            K key,
            Function<K, TokenBucket> factory
    ) {
        return map.computeIfAbsent(key, factory);
    }
}
