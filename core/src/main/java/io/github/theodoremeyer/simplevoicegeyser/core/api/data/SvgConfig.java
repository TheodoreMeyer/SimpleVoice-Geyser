package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

import java.util.List;

public final class SvgConfig {

    private static SvgFile FILE;

    private static boolean initialized = false;

    // ---- INIT ----
    public static void init(SvgFile file) {
        if (!initialized) {
            initialized = true;
            FILE = file;
            applyDefaults();
        } else {
            throw new IllegalStateException("SvgConfig already initialized");
        }
    }

    static SvgFile getFile() {
        if (FILE == null) {
            throw new IllegalStateException("SvgConfig not initialized");
        }
        return FILE;
    }

    // ---- KEYS ----

    public static final ConfigKey<Integer> VC_TIMEOUT =
            new ConfigKey<>("client.vctimeout", 30);

    public static final ConfigKey<Integer> IDLE_TIMEOUT =
            new ConfigKey<>("client.idletimeout", 2);

    public static final ConfigKey<Boolean> REQUIRE_BEDROCK =
            new ConfigKey<>("client.requireBedrock", false);

    public static final ConfigKey<Boolean> USE_EMOTE =
            new ConfigKey<>("client.useEmoteForSVG", true);

    public static final ConfigKey<Boolean> DEFAULT_GROUP_ENABLED =
            new ConfigKey<>("server.group.default.enabled", true);

    public static final ConfigKey<String> DEFAULT_GROUP_PASSWORD =
            new ConfigKey<>("server.group.default.password", "1a2b");

    public static final ConfigKey<Integer> PORT =
            new ConfigKey<>("server.port", 8080);

    public static final ConfigKey<String> BIND_ADDRESS =
            new ConfigKey<>("server.bind-address", "0.0.0.0");

    public static final ConfigKey<Boolean> DEBUG =
            new ConfigKey<>("debug", false);

    private static final ConfigKey<int[]> CONFIG_VERSION =
            new ConfigKey<>("config-version", new int[]{0, 1, 0});

    private static final List<ConfigKey<?>> ALL_KEYS = List.of(
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

    private static void applyDefaults() {
        SvgFile file = getFile();

        for (ConfigKey<?> key : ALL_KEYS) {
            if (!file.getKeys().contains(key.path())) {
                file.set(key.path(), key.def());
            }
        }

        file.save();
    }
}