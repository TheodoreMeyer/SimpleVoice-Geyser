package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.List;

public final class SvgConfig {

    private final SvgFile file;

    public SvgConfig(SvgFile file) {
        this.file = file;
        applyDefaults();
    }

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
            new ConfigKey <>(this, "client.vctimeout", 60);

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

    /**
     * When {@code true} (default), Svg-App must send {@code clientType.serverVersion} equal to
     * {@link SvgCore#VERSION}. When {@code false}, the same major.minor is accepted (any patch).
     */
    public final ConfigKey<Boolean> REQUIRE_LATEST =
            new ConfigKey<>(this, "client.require-latest", true);

    /** ISO 639-1 code used when the browser has no saved preferredLanguage (en, nl, pl, it, es). */
    public final ConfigKey<String> WEB_DEFAULT_LANGUAGE =
            new ConfigKey<>(this, "client.web.default-language", "en");

    /** Display name shown on the join page hero / document title. */
    public final ConfigKey<String> WEB_SERVER_NAME =
            new ConfigKey<>(this, "client.web.server-name", "Simple Voice Chat");

    /** Optional short subtitle under the server name (empty = hidden). */
    public final ConfigKey<String> WEB_TAGLINE =
            new ConfigKey<>(this, "client.web.tagline", "");

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
            new ConfigKey <>(this, "server.group.default.enabled", false);

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

    /** Disabling PCM excludes clients without Opus microphone encoding. */
    public final ConfigKey<Boolean> AUDIO_MIC_ALLOW_LEGACY =
            new ConfigKey<>(this, "server.audio.mic.allowLegacy", true);

    public final ConfigKey<Boolean> DEBUG =
            new ConfigKey <>(this, "debug", false);

    public final ConfigKey<Boolean> UPDATE_CHECKER_ENABLED =
            new ConfigKey <>(this, "updatechecker.enable", true);

    public final ConfigKey<Boolean> CONFIG_BACKUPS_ENABLED =
            new ConfigKey<>(this, ConfigBackupUtil.ENABLED_PATH, ConfigBackupUtil.DEFAULT_ENABLED);

    public final ConfigKey<Integer> CONFIG_BACKUPS_MAX_FILES =
            new ConfigKey<>(this, ConfigBackupUtil.MAX_FILES_PATH, ConfigBackupUtil.DEFAULT_MAX_FILES);

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
            REQUIRE_LATEST,
            WEB_DEFAULT_LANGUAGE,
            WEB_SERVER_NAME,
            WEB_TAGLINE,
            JOIN_MESSAGE_ENABLED,
            JOIN_MESSAGE_TEXT,
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
            AUDIO_MIC_ALLOW_LEGACY,
            DEBUG,
            UPDATE_CHECKER_ENABLED,
            CONFIG_BACKUPS_ENABLED,
            CONFIG_BACKUPS_MAX_FILES,
            CONFIG_VERSION
    );

    public void applyDefaults() {
        SvgFile file = getFile();

        String backupPath = null;
        int addedKeys = 0;
        boolean modified = false;

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

        String currentContext = CONTEXT_PATH.get();
        String normalizedContext = normalizeContextPath(currentContext);
        if (!normalizedContext.equals(currentContext)) {

            if (!modified) {
                backupPath = file.backup();
                modified = true;
            }

            file.set(CONTEXT_PATH.path(), normalizedContext);
        }

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
