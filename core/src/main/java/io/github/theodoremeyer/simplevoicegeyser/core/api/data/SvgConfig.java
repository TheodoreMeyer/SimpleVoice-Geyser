package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the configuration for Simple Voice Geyser. This class is responsible for loading and saving the configuration file, as well as providing access to the configuration values.
 */
public final class SvgConfig {

    /**
     * Canonical config defaults generated dynamically from registered config key definitions.
     * @return map of dotted config paths to default values
     */
    public static Map<String, Object> codeDefaults() {
        return new SvgConfig(null).getCodeDefaults();
    }

    private final SvgFile file;

    /**
     * Create the Config with an associated config file
     * @param file file; if null, defaults are initialized without file binding
     */
    public SvgConfig(SvgFile file) {
        this.file = file;
        if (file != null) {
            applyDefaults();
        }
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

    /** Voice chat client timeout in seconds. Documented but not enforced yet. */
    public final ConfigKey<Integer> VC_TIMEOUT =
            new ConfigKey <>(this, "client.vctimeout", 30);

    /** How long a connected web client may sit idle before its session is dropped, in minutes. */
    public final ConfigKey<Integer> IDLE_TIMEOUT =
            new ConfigKey <>(this, "client.idletimeout", 2);

    /** Whether clients must be connecting through Geyser (Bedrock) to use the voice server. */
    public final ConfigKey<Boolean> REQUIRE_BEDROCK =
            new ConfigKey <>(this, "client.requireBedrock", false);

    /** Whether the configured emote can be used as a trigger to activate voice on Bedrock clients. */
    public final ConfigKey<Boolean> USE_EMOTE =
            new ConfigKey <>(this, "client.useEmoteForSVG", true);

    /**
     * Whether {@link #CLIENT_ALLOWED_TYPES_LIST} acts as a blacklist ({@code true})
     * or a whitelist ({@code false}).
     */
    public final ConfigKey<Boolean> CLIENT_ALLOWED_TYPES_BLACKLIST =
            new ConfigKey<>(this, "client.allowedTypes.isBlackList", true);

    /** Client types allowed (whitelist) or blocked (blacklist) by the client type policy. */
    public final ConfigKey<List<String>> CLIENT_ALLOWED_TYPES_LIST =
            new ConfigKey<>(this, "client.allowedTypes.list", List.of());

    /** Whether chat sent from the web client is enabled. */
    public final ConfigKey<Boolean> WEB_CHAT_ENABLED =
            new ConfigKey<>(this, "client.web-chat-enabled", true);

    /** Whether players are shown an informational message about SimpleVoice-Geyser when they join. */
    public final ConfigKey<Boolean> JOIN_MESSAGE_ENABLED =
            new ConfigKey<>(this, "client.join-message.enabled", true);

    /** Lines shown to players on join while {@link #JOIN_MESSAGE_ENABLED} is on. */
    public final ConfigKey<List<String>> JOIN_MESSAGE_TEXT =
            new ConfigKey<>(this, "client.join-message.text", List.of(
                    "This Server Uses SimpleVoice-Geyser.",
                    "To set it up, run /svg pswd [password],",
                    "Then join Via the server's SVG website."
            ));

    /** Whether the default voice group exists and can be joined. */
    public final ConfigKey<Boolean> DEFAULT_GROUP_ENABLED =
            new ConfigKey <>(this, "server.group.default.enabled", true);

    /** Password required to join the default voice group. */
    public final ConfigKey<String> DEFAULT_GROUP_PASSWORD =
            new ConfigKey <>(this, "server.group.default.password", "1a2b");

    /** Whether players are placed into the default group automatically when they join via web. */
    public final ConfigKey<Boolean> DEFAULT_GROUP_FORCE_ON_WEB_JOIN =
            new ConfigKey <>(this, "server.group.default.force-on-web-join", false);

    /** TCP port the built-in web server listens on. */
    public final ConfigKey<Integer> PORT =
            new ConfigKey <>(this, "server.port", 8080);

    /** Network address the built-in web server binds to (e.g. {@code 0.0.0.0} for all interfaces). */
    public final ConfigKey<String> BIND_ADDRESS =
            new ConfigKey <>(this, "server.bind-address", "0.0.0.0");

    /** URL context path under which the web UI is served; always normalized to start with {@code /}. */
    public final ConfigKey<String> CONTEXT_PATH =
            new ConfigKey <>(this, "server.context-path", "/");

    /** Failed authentication attempts allowed within the failure window before the client is locked out. */
    public final ConfigKey<Integer> MAX_AUTH_FAILURES =
            new ConfigKey <>(this, "server.security.max-auth-failures", 5);

    /** Length of the window in which failed auth attempts are counted, in minutes. */
    public final ConfigKey<Integer> AUTH_FAILURE_DURATION =
            new ConfigKey <>(this, "server.security.auth-fail-duration", 3);

    /** How long a client stays locked out after exceeding {@link #MAX_AUTH_FAILURES}, in minutes. */
    public final ConfigKey<Integer> AUTH_LOCK_DURATION =
            new ConfigKey <>(this, "server.security.auth-lock-duration", 8);

    /** Audio transport used for voice traffic ({@code svg-v2}, {@code legacy}, or {@code auto}). */
    public final ConfigKey<String> AUDIO_TRANSPORT_MODE =
            new ConfigKey <>(this, "server.audio.transport-mode", "svg-v2");

    /** Whether legacy-only web clients may fall back to the deprecated LEGACY transport. */
    public final ConfigKey<Boolean> AUDIO_ALLOW_LEGACY_FALLBACK =
            new ConfigKey <>(this, "server.audio.allow-legacy-fallback", true);

    /** Whether proxy token authentication is enabled for backends sitting behind a proxy. */
    public final ConfigKey<Boolean> PROXY_ENABLED =
            new ConfigKey <>(this, "proxy.enabled", false);

    /** HTTP URL of the Velocity proxy control endpoint, when proxy integration is enabled. */
    public final ConfigKey<String> PROXY_CONTROL_URL =
            new ConfigKey<>(this, "proxy.control-url", "");

    /** Shared secret used to sign and verify proxy auth tokens (see {@code ProxyAuthToken}). */
    public final ConfigKey<String> PROXY_SHARED_SECRET =
            new ConfigKey <>(this, "proxy.shared-secret", "simplevoice-geyser-proxy-secret");

    /** Whether TLS certificates of the proxy control endpoint are verified (disable for self-signed). */
    public final ConfigKey<Boolean> PROXY_VERIFY_SSL =
            new ConfigKey <>(this, "proxy.verify-ssl", true);

    /** Lifetime of a proxy auth token, in seconds. */
    public final ConfigKey<Integer> PROXY_TOKEN_TTL_SECONDS =
            new ConfigKey <>(this, "proxy.token-ttl-seconds", 120);

    /** Whether verbose debug logging is enabled. */
    public final ConfigKey<Boolean> DEBUG =
            new ConfigKey <>(this, "debug", false);

    /** Whether an update check runs against the project's release feed at startup. */
    public final ConfigKey<Boolean> UPDATE_CHECKER_ENABLED =
            new ConfigKey <>(this, "updatechecker.enable", true);

    /** Version marker of the config schema; updated automatically on migration. */
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
            PROXY_ENABLED,
            PROXY_CONTROL_URL,
            PROXY_SHARED_SECRET,
            PROXY_VERIFY_SSL,
            PROXY_TOKEN_TTL_SECONDS,
            DEBUG,
            UPDATE_CHECKER_ENABLED,
            CONFIG_VERSION
    );

    /**
     * Map of dotted config paths to default values generated dynamically from registered keys.
     * @return map of dotted config paths to default values
     */
    public Map<String, Object> getCodeDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (ConfigKey<?> key : ALL_KEYS) {
            defaults.put(key.path(), key.def());
        }
        return defaults;
    }

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
