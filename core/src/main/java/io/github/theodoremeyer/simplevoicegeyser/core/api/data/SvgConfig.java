package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

import java.util.List;

/**
 * Represents the configuration for Simple Voice Geyser. This class is responsible for loading and saving the configuration file, as well as providing access to the configuration values.
 */
public final class SvgConfig {

    /**
     * The file config exists on
     */
    private final SvgFile file;

    /**
     * Create config manager
     * @param file file
     */
    public SvgConfig(SvgFile file) {
        this.file = file;
        applyDefaults();
    }

    /**
     * Get the underlying file
     * @return config file
     */
    SvgFile getFile() {
        if (file == null) {
            throw new IllegalStateException("SvgConfig not initialized");
        }
        return file;
    }

    // ---- KEYS ----
    /**
     * config path: config-info.
     * Represents help center for config
     */
    private final ConfigKey<String> CONFIG_INFO =
            new ConfigKey <>(this, "config-info", "This file is used to configure Simple Voice Geyser. " +
                    "For more information, see the wiki: https://theodoremeyer.github.io/projects/simplevoicegeyser/");

    /**
     * Config path: client.vctimeout
     */
    public final ConfigKey<Integer> VC_TIMEOUT =
            new ConfigKey <>(this, "client.vctimeout", 30);

    /**
     * Config path: client.idletimeout
     */
    public final ConfigKey<Integer> IDLE_TIMEOUT =
            new ConfigKey <>(this, "client.idletimeout", 2);

    /**
     * Config path: client.requireBedrock
     */
    public final ConfigKey<Boolean> REQUIRE_BEDROCK =
            new ConfigKey <>(this, "client.requireBedrock", false);

    /**
     * Config path: client.useEmoteForSVG
     */
    public final ConfigKey<Boolean> USE_EMOTE =
            new ConfigKey <>(this, "client.useEmoteForSVG", true);

    /**
     * Config path: server.group.default.enabled
     */
    public final ConfigKey<Boolean> DEFAULT_GROUP_ENABLED =
            new ConfigKey <>(this, "server.group.default.enabled", true);

    /**
     * Config path: server.group.default.password
     */
    public final ConfigKey<String> DEFAULT_GROUP_PASSWORD =
            new ConfigKey <>(this, "server.group.default.password", "1a2b");

    /**
     * Config path: server.port
     */
    public final ConfigKey<Integer> PORT =
            new ConfigKey <>(this, "server.port", 8080);

    /**
     * Config path: server.bind-address
     */
    public final ConfigKey<String> BIND_ADDRESS =
            new ConfigKey <>(this, "server.bind-address", "0.0.0.0");

    /**
     * Config path: debug
     */
    public final ConfigKey<Boolean> DEBUG =
            new ConfigKey <>(this, "debug", false);

    /**
     * Config path: updatechecker.enable
     */
    public final ConfigKey<Boolean> UPDATE_CHECKER_ENABLED =
            new ConfigKey <>(this, "updatechecker.enable", true);


    /**
     * Config path: config-version
     */
    private final ConfigKey<String> CONFIG_VERSION =
            new ConfigKey <>(this, "config-version", "0.1.0");

    /**
     * Represents all the keys.
     */
    private final List<ConfigKey<?>> ALL_KEYS = List.of(
            CONFIG_INFO,
            VC_TIMEOUT,
            IDLE_TIMEOUT,
            REQUIRE_BEDROCK,
            USE_EMOTE,
            DEFAULT_GROUP_ENABLED,
            DEFAULT_GROUP_PASSWORD,
            PORT,
            BIND_ADDRESS,
            DEBUG,
            CONFIG_VERSION
    );

    // ---- DEFAULTS ----

    /**
     * Apply missing config keys/values
     */
    private void applyDefaults() {
        SvgFile file = getFile();

        for (ConfigKey<?> key : ALL_KEYS) {
            if (!file.has(key.path())) {
                file.set(key.path(), key.def());
            }
        }

        file.save();
    }
}