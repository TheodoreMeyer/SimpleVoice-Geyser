package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

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
        defaults.put("debug", false);
        defaults.put("updatechecker.enable", true);
        defaults.put("config-version", "0.1.1-dev-migration1");
        return defaults;
    }

    private final SvgFile file;

    public SvgConfig(SvgFile file) {
        this.file = file;
        applyDefaults();
    }

    SvgFile getFile() {
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
            new ConfigKey <>(this, "server.audio.transport-mode", "auto");

    public final ConfigKey<Boolean> AUDIO_ALLOW_LEGACY_FALLBACK =
            new ConfigKey <>(this, "server.audio.allow-legacy-fallback", true);

    public final ConfigKey<Boolean> DEBUG =
            new ConfigKey <>(this, "debug", false);

    public final ConfigKey<Boolean> UPDATE_CHECKER_ENABLED =
            new ConfigKey <>(this, "updatechecker.enable", true);

    private final ConfigKey<String> CONFIG_VERSION =
            new ConfigKey <>(this, "config-version", "0.1.1-dev-migration1");

    private final List<ConfigKey<?>> ALL_KEYS = List.of(
            CONFIG_INFO,
            VC_TIMEOUT,
            IDLE_TIMEOUT,
            REQUIRE_BEDROCK,
            USE_EMOTE,
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
            DEBUG,
            UPDATE_CHECKER_ENABLED,
            CONFIG_VERSION
    );

    public void applyDefaults() {
        SvgFile file = getFile();

        for (ConfigKey<?> key : ALL_KEYS) {
            if (!file.has(key.path())) {
                file.set(key.path(), key.def());
            }
        }

        file.set(CONTEXT_PATH.path(), normalizeContextPath(CONTEXT_PATH.get()));
        file.set(CONFIG_VERSION.path(), CONFIG_VERSION.get());
        file.save();
    }

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
