package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.List;

/**
 * Represents the configuration for Simple Voice Geyser. This class is responsible for loading and saving the configuration file, as well as providing access to the configuration values.
 */
public final class SvgConfig {

    private final SvgFile file;

    /**
     * Create the Config with an associated config file
     * @param file file
     */
    public SvgConfig(SvgFile file) {
        this.file = file;
        applyDefaults();
    }

    /**
     * Get the underlying file
     * @return SvgFile
     */
    public SvgFile getFile() {
        if (file == null) {
            throw new IllegalStateException("SvgConfig not initialized");
        }
        return file;
    }

    private final ConfigKey<String> CONFIG_INFO =
            new ConfigKey <>(this, "config-info", "This file is used to configure Simple Voice Geyser. " +
                    "For more information, see the wiki: https://theodoremeyer.github.io/projects/simplevoicegeyser/");

    public final ConfigKey<Integer> VC_TIMEOUT =
            new ConfigKey <>(this, "client.vctimeout", 30);

    public final ConfigKey<Integer> IDLE_TIMEOUT =
            new ConfigKey <>(this, "client.idletimeout", 2);

    public final ConfigKey<Boolean> REQUIRE_BEDROCK =
            new ConfigKey <>(this, "client.requireBedrock", false);

    public final ConfigKey<Boolean> USE_EMOTE =
            new ConfigKey <>(this, "client.useEmoteForSVG", true);

    public final ConfigKey<Boolean> CLIENT_ALLOWED_TYPES_BLACKLIST =
            new ConfigKey<>(this, "client.allowedTypes.isBlackList", true);

    public final ConfigKey<List<String>> CLIENT_ALLOWED_TYPES_LIST =
            new ConfigKey<>(this, "client.allowedTypes.list", List.of());

    public final ConfigKey<Boolean> WEB_CHAT_ENABLED =
            new ConfigKey<>(this, "client.web-chat-enabled", true);

    public final ConfigKey<Boolean> DEFAULT_GROUP_ENABLED =
            new ConfigKey <>(this, "server.group.default.enabled", true);

    public final ConfigKey<String> DEFAULT_GROUP_PASSWORD =
            new ConfigKey <>(this, "server.group.default.password", "1a2b");

    public final ConfigKey<Boolean> DEFAULT_GROUP_FORCE_ON_WEB_JOIN =
            new ConfigKey <>(this, "server.group.default.force-on-web-join", false);

    public final ConfigKey<Integer> PORT =
            new ConfigKey <>(this, "server.port", 8080);

    public final ConfigKey<String> BIND_ADDRESS =
            new ConfigKey <>(this, "server.bind-address", "0.0.0.0");

    public final ConfigKey<String> CONTEXT_PATH =
            new ConfigKey <>(this, "server.context-path", "/");

    public final ConfigKey<Integer> MAX_AUTH_FAILURES =
            new ConfigKey <>(this, "server.security.max-auth-failures", 5);

    public final ConfigKey<Integer> AUTH_FAILURE_DURATION =
            new ConfigKey <>(this, "server.security.auth-fail-duration", 3);

    public final ConfigKey<Integer> AUTH_LOCK_DURATION =
            new ConfigKey <>(this, "server.security.auth-lock-duration", 8);

    public final ConfigKey<String> AUDIO_TRANSPORT_MODE =
            new ConfigKey <>(this, "server.audio.transport-mode", "svg-v2");

    public final ConfigKey<Boolean> AUDIO_ALLOW_LEGACY_FALLBACK =
            new ConfigKey <>(this, "server.audio.allow-legacy-fallback", true);

    /**
     * Diagnostic-only. Outbound mic sample rate is fixed at {@code AudioFormatConstants.SAMPLE_RATE};
     * mismatched config values are ignored.
     */
    public final ConfigKey<Integer> AUDIO_OUTBOUND_SAMPLE_RATE =
            new ConfigKey<>(this, "server.audio.outbound-sample-rate", 48000);

    /**
     * Diagnostic-only. Outbound mic frame size is fixed at {@code AudioFormatConstants.FRAME_SAMPLES};
     * mismatched config values are ignored.
     */
    public final ConfigKey<Integer> AUDIO_OUTBOUND_FRAME_SAMPLES =
            new ConfigKey<>(this, "server.audio.outbound-frame-samples", 960);

    /**
     * Bounded outbound mic encode queue capacity per session.
     * Unsafe values are clamped in {@code SessionAudioPipeline.clampQueueCapacity}.
     */
    public final ConfigKey<Integer> AUDIO_OUTBOUND_QUEUE_CAPACITY =
            new ConfigKey<>(this, "server.audio.outbound-queue-capacity", 32);

    public final ConfigKey<Boolean> GROUPS_ALLOW_WEB_CREATION =
            new ConfigKey<>(this, "groups.allow-web-creation", true);

    public final ConfigKey<Integer> GROUPS_MAX_ACTIVE =
            new ConfigKey<>(this, "groups.max-active-groups", 100);

    public final ConfigKey<Integer> GROUPS_MAX_CREATED_PER_PLAYER =
            new ConfigKey<>(this, "groups.max-created-per-player", 3);

    public final ConfigKey<Integer> GROUPS_CREATION_COOLDOWN_SECONDS =
            new ConfigKey<>(this, "groups.creation-cooldown-seconds", 10);

    public final ConfigKey<Boolean> RATE_LIMITS_ENABLED =
            new ConfigKey<>(this, "rate-limits.enabled", true);

    public final ConfigKey<Integer> RATE_LIMITS_AUTH_ATTEMPTS =
            new ConfigKey<>(this, "rate-limits.authentication.attempts", 5);

    public final ConfigKey<Integer> RATE_LIMITS_AUTH_WINDOW_SECONDS =
            new ConfigKey<>(this, "rate-limits.authentication.window-seconds", 60);

    public final ConfigKey<Integer> RATE_LIMITS_AUTH_LOCKOUT_SECONDS =
            new ConfigKey<>(this, "rate-limits.authentication.lockout-seconds", 30);

    public final ConfigKey<Integer> RATE_LIMITS_CHAT_BURST =
            new ConfigKey<>(this, "rate-limits.chat.burst", 4);

    public final ConfigKey<Integer> RATE_LIMITS_CHAT_REFILL_PER_SECOND =
            new ConfigKey<>(this, "rate-limits.chat.refill-per-second", 1);

    public final ConfigKey<Integer> RATE_LIMITS_CHAT_MAX_LENGTH =
            new ConfigKey<>(this, "rate-limits.chat.maximum-length", 500);

    public final ConfigKey<Integer> RATE_LIMITS_GROUPS_REFRESH_COOLDOWN_SECONDS =
            new ConfigKey<>(this, "rate-limits.groups-refresh.cooldown-seconds", 3);

    public final ConfigKey<Integer> RATE_LIMITS_GROUP_CREATE_COOLDOWN_SECONDS =
            new ConfigKey<>(this, "rate-limits.group-create.cooldown-seconds", 10);

    public final ConfigKey<Integer> RATE_LIMITS_GROUP_CREATE_BURST =
            new ConfigKey<>(this, "rate-limits.group-create.burst", 2);

    public final ConfigKey<Integer> RATE_LIMITS_GROUP_CREATE_WINDOW_SECONDS =
            new ConfigKey<>(this, "rate-limits.group-create.window-seconds", 60);

    public final ConfigKey<Integer> RATE_LIMITS_GROUP_JOIN_COOLDOWN_MS =
            new ConfigKey<>(this, "rate-limits.group-join.cooldown-milliseconds", 750);

    public final ConfigKey<Integer> RATE_LIMITS_GROUP_LEAVE_COOLDOWN_MS =
            new ConfigKey<>(this, "rate-limits.group-leave.cooldown-milliseconds", 750);

    public final ConfigKey<Integer> RATE_LIMITS_GROUP_PASSWORD_ATTEMPTS =
            new ConfigKey<>(this, "rate-limits.group-password.attempts", 5);

    public final ConfigKey<Integer> RATE_LIMITS_GROUP_PASSWORD_WINDOW_SECONDS =
            new ConfigKey<>(this, "rate-limits.group-password.window-seconds", 60);

    public final ConfigKey<Integer> RATE_LIMITS_GROUP_PASSWORD_LOCKOUT_SECONDS =
            new ConfigKey<>(this, "rate-limits.group-password.lockout-seconds", 30);

    public final ConfigKey<Integer> RATE_LIMITS_CONTROL_BURST =
            new ConfigKey<>(this, "rate-limits.control-packets.burst", 20);

    public final ConfigKey<Integer> RATE_LIMITS_CONTROL_REFILL_PER_SECOND =
            new ConfigKey<>(this, "rate-limits.control-packets.refill-per-second", 10);

    public final ConfigKey<Integer> RATE_LIMITS_CONTROL_MAX_BYTES =
            new ConfigKey<>(this, "rate-limits.control-packets.maximum-bytes", 16384);

    public final ConfigKey<Integer> RATE_LIMITS_AUDIO_EXPECTED_FPS =
            new ConfigKey<>(this, "rate-limits.audio.expected-frames-per-second", 50);

    /** @deprecated Prefer {@link #RATE_LIMITS_AUDIO_NORMAL_BURST_FRAMES}; kept for migration. */
    public final ConfigKey<Integer> RATE_LIMITS_AUDIO_BURST_FRAMES =
            new ConfigKey<>(this, "rate-limits.audio.burst-frames", 50);

    public final ConfigKey<Boolean> RATE_LIMITS_AUDIO_ENABLED =
            new ConfigKey<>(this, "rate-limits.audio.enabled", true);

    public final ConfigKey<Integer> RATE_LIMITS_AUDIO_NORMAL_BURST_FRAMES =
            new ConfigKey<>(this, "rate-limits.audio.normal-burst-frames", 50);

    public final ConfigKey<Integer> RATE_LIMITS_AUDIO_SUSTAINED_MAX_FPS =
            new ConfigKey<>(this, "rate-limits.audio.sustained-maximum-frames-per-second", 75);

    public final ConfigKey<Integer> RATE_LIMITS_AUDIO_SUSTAINED_WINDOW_SECONDS =
            new ConfigKey<>(this, "rate-limits.audio.sustained-window-seconds", 5);

    public final ConfigKey<Integer> RATE_LIMITS_AUDIO_ABUSE_WINDOWS =
            new ConfigKey<>(this, "rate-limits.audio.disconnect-after-consecutive-abuse-windows", 3);

    public final ConfigKey<Integer> RATE_LIMITS_AUDIO_MAX_FRAME_BYTES =
            new ConfigKey<>(this, "rate-limits.audio.maximum-frame-bytes", 1920);

    /**
     * DEBUG matrix: when true, audio frames skip sustained-abuse limiting
     * (byte-size check still applies). Chat/control limits are unchanged.
     */
    public final ConfigKey<Boolean> RATE_LIMITS_AUDIO_BYPASS =
            new ConfigKey<>(this, "rate-limits.audio.bypass", false);

    public final ConfigKey<Integer> RATE_LIMITS_DUPLICATE_RETENTION_SECONDS =
            new ConfigKey<>(this, "rate-limits.duplicate-operation-retention-seconds", 120);

    public final ConfigKey<Integer> GROUPS_MAX_NAME_LENGTH =
            new ConfigKey<>(this, "groups.max-name-length", 32);

    public final ConfigKey<List<String>> GROUPS_ALLOWED_TYPES =
            new ConfigKey<>(this, "groups.allowed-types", List.of("NORMAL", "OPEN", "ISOLATED"));

    public final ConfigKey<Boolean> DEBUG =
            new ConfigKey <>(this, "debug", false);

    public final ConfigKey<Boolean> UPDATE_CHECKER_ENABLED =
            new ConfigKey <>(this, "updatechecker.enable", true);

    public final ConfigKey<String> CONFIG_VERSION =
            new ConfigKey <>(this, "config-version", SvgCore.VERSION);

    private final List<ConfigKey<?>> ALL_KEYS = List.of(
            CONFIG_INFO,
            VC_TIMEOUT,
            IDLE_TIMEOUT,
            REQUIRE_BEDROCK,
            USE_EMOTE,
            WEB_CHAT_ENABLED,
            CLIENT_ALLOWED_TYPES_BLACKLIST,
            CLIENT_ALLOWED_TYPES_LIST,
            DEFAULT_GROUP_ENABLED,
            DEFAULT_GROUP_PASSWORD,
            DEFAULT_GROUP_FORCE_ON_WEB_JOIN,
            PORT,
            BIND_ADDRESS,
            CONTEXT_PATH,
            AUTH_FAILURE_DURATION,
            AUTH_LOCK_DURATION,
            MAX_AUTH_FAILURES,
            AUDIO_TRANSPORT_MODE,
            AUDIO_ALLOW_LEGACY_FALLBACK,
            AUDIO_OUTBOUND_SAMPLE_RATE,
            AUDIO_OUTBOUND_FRAME_SAMPLES,
            AUDIO_OUTBOUND_QUEUE_CAPACITY,
            GROUPS_ALLOW_WEB_CREATION,
            GROUPS_MAX_ACTIVE,
            GROUPS_MAX_CREATED_PER_PLAYER,
            GROUPS_CREATION_COOLDOWN_SECONDS,
            RATE_LIMITS_ENABLED,
            RATE_LIMITS_AUTH_ATTEMPTS,
            RATE_LIMITS_AUTH_WINDOW_SECONDS,
            RATE_LIMITS_AUTH_LOCKOUT_SECONDS,
            RATE_LIMITS_CHAT_BURST,
            RATE_LIMITS_CHAT_REFILL_PER_SECOND,
            RATE_LIMITS_CHAT_MAX_LENGTH,
            RATE_LIMITS_GROUPS_REFRESH_COOLDOWN_SECONDS,
            RATE_LIMITS_GROUP_CREATE_COOLDOWN_SECONDS,
            RATE_LIMITS_GROUP_CREATE_BURST,
            RATE_LIMITS_GROUP_CREATE_WINDOW_SECONDS,
            RATE_LIMITS_GROUP_JOIN_COOLDOWN_MS,
            RATE_LIMITS_GROUP_LEAVE_COOLDOWN_MS,
            RATE_LIMITS_GROUP_PASSWORD_ATTEMPTS,
            RATE_LIMITS_GROUP_PASSWORD_WINDOW_SECONDS,
            RATE_LIMITS_GROUP_PASSWORD_LOCKOUT_SECONDS,
            RATE_LIMITS_CONTROL_BURST,
            RATE_LIMITS_CONTROL_REFILL_PER_SECOND,
            RATE_LIMITS_CONTROL_MAX_BYTES,
            RATE_LIMITS_AUDIO_EXPECTED_FPS,
            RATE_LIMITS_AUDIO_BURST_FRAMES,
            RATE_LIMITS_AUDIO_ENABLED,
            RATE_LIMITS_AUDIO_NORMAL_BURST_FRAMES,
            RATE_LIMITS_AUDIO_SUSTAINED_MAX_FPS,
            RATE_LIMITS_AUDIO_SUSTAINED_WINDOW_SECONDS,
            RATE_LIMITS_AUDIO_ABUSE_WINDOWS,
            RATE_LIMITS_AUDIO_MAX_FRAME_BYTES,
            RATE_LIMITS_AUDIO_BYPASS,
            RATE_LIMITS_DUPLICATE_RETENTION_SECONDS,
            GROUPS_MAX_NAME_LENGTH,
            GROUPS_ALLOWED_TYPES,
            DEBUG,
            UPDATE_CHECKER_ENABLED,
            CONFIG_VERSION
    );

    /**
     * Apply Defaults to the file if any are missing
     */
    public void applyDefaults() {
        SvgFile file = getFile();

        String backupPath = null;
        int addedKeys = 0;
        boolean modified = false;

        // Add any missing config keys
        for (ConfigKey<?> key : ALL_KEYS) {
            if (!key.exists()) {

                if (!modified) {
                    backupPath = file.backup();
                    modified = true;
                }

                file.set(key.path(), key.def());
                addedKeys++;
            }
        }

        // Normalize context path if needed
        String currentContext = CONTEXT_PATH.get();
        String normalizedContext = normalizeContextPath(currentContext);
        if (!normalizedContext.equals(currentContext)) {

            if (!modified) {
                backupPath = file.backup();
                modified = true;
            }

            file.set(CONTEXT_PATH.path(), normalizedContext);
        }

        // Diagnostic sample rate: clamp non-48000 values back to 48000.
        Integer sampleRate = AUDIO_OUTBOUND_SAMPLE_RATE.get();
        if (sampleRate != null && sampleRate != 48000) {
            if (!modified) {
                backupPath = file.backup();
                modified = true;
            }
            file.set(AUDIO_OUTBOUND_SAMPLE_RATE.path(), 48000);
            SvgCore.getLogger().warning(
                    "[Config] server.audio.outbound-sample-rate clamped to 48000 (was "
                            + sampleRate + ")"
            );
        }

        // Migrate obsolete capacity-8 audio queues to the 32-frame jitter buffer.
        Integer queueCap = AUDIO_OUTBOUND_QUEUE_CAPACITY.get();
        if (queueCap != null && queueCap > 0 && queueCap < 16) {
            if (!modified) {
                backupPath = file.backup();
                modified = true;
            }
            file.set(AUDIO_OUTBOUND_QUEUE_CAPACITY.path(), 32);
            SvgCore.getLogger().warning(
                    "[Config] server.audio.outbound-queue-capacity migrated from "
                            + queueCap + " to 32 (jitter buffer)"
            );
        }

        // Update config version if needed
        if (!SvgCore.VERSION.equals(CONFIG_VERSION.get())) {

            if (!modified) {
                backupPath = file.backup();
                modified = true;
            }

            file.set(CONFIG_VERSION.path(), SvgCore.VERSION);
        }

        if (modified) {
            file.save();

            SvgCore.getLogger().info(
                    "[Config] Configuration updated."
                            + (addedKeys > 0 ? " Added " + addedKeys + " missing key(s)." : "")
                            + (backupPath == null || backupPath.isBlank()
                            ? ""
                            : " Backup: " + backupPath)
            );
        }
    }

    /**
     * Take the ContextPath and try to normalize it
     * @param contextPath path to normalize
     * @return the normalized version
     */
    public static String normalizeContextPath(String contextPath) {
        if (contextPath == null) {
            return "/";
        }

        String normalized = contextPath.trim();
        if (normalized.isEmpty()) {
            return "/";
        }

        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
