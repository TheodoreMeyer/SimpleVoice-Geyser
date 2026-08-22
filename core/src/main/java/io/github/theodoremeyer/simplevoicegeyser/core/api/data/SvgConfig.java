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
     * Canonical config defaults used by all platforms.
     * @return map of dotted config paths to default values
     * <p>
     * May be removed
     */
    public static Map<String, Object> codeDefaults() {

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("config-info", "This file is used to configure Simple Voice Geyser. "
                + "For more information, see the wiki: https://theodoremeyer.github.io/projects/simplevoicegeyser/");
        defaults.put("client.vctimeout", 30);
        defaults.put("client.idletimeout", 2);
        defaults.put("client.requireBedrock", false);
        defaults.put("client.useEmoteForSVG", true);
        defaults.put("client.web-chat-enabled", true);
        defaults.put("server.group.default.enabled", true);
        defaults.put("server.group.default.password", "1a2b");
        defaults.put("server.group.default.force-on-web-join", false);
        defaults.put("server.port", 8080);
        defaults.put("server.bind-address", "0.0.0.0");
        defaults.put("server.context-path", "/");
        defaults.put("server.security.max-auth-failures", 5);
        defaults.put("server.security.auth-fail-duration", 3);
        defaults.put("server.security.auth-lock-duration", 8);
        defaults.put("server.audio.transport-mode", "auto");
        defaults.put("server.audio.allow-legacy-fallback", true);
        defaults.put("proxy.enabled", false);
        defaults.put("proxy.shared-secret", "simplevoice-geyser-proxy-secret");
        defaults.put("proxy.token-ttl-seconds", 120);
        defaults.put("debug", false);
        defaults.put("updatechecker.enable", true);
        defaults.put("config-version", "0.1.1-dev-migration1");
        return defaults;
    }

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

    public final ConfigKey<Boolean> JOIN_MESSAGE_ENABLED =
            new ConfigKey<>(this, "client.join-message.enabled", true);

    public final ConfigKey<List<String>> JOIN_MESSAGE_TEXT =
            new ConfigKey<>(this, "client.join-message.text", List.of(
                    "This Server Uses SimpleVoice-Geyser.",
                    "To set it up, run /svg pswd [password],",
                    "Then join Via the server's SVG website."
            ));

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

    public final ConfigKey<Boolean> PROXY_ENABLED =
            new ConfigKey <>(this, "proxy.enabled", false);

    public final ConfigKey<String> PROXY_SHARED_SECRET =
            new ConfigKey <>(this, "proxy.shared-secret", "simplevoice-geyser-proxy-secret");

    public final ConfigKey<Integer> PROXY_TOKEN_TTL_SECONDS =
            new ConfigKey <>(this, "proxy.token-ttl-seconds", 120);

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
            PROXY_ENABLED,
            PROXY_SHARED_SECRET,
            PROXY_TOKEN_TTL_SECONDS,
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
