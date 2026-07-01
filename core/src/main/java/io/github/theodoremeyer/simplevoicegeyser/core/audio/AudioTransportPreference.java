package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.util.Locale;

/**
 * Server transport preference from config.
 */
public enum AudioTransportPreference {

    /**
     * Allows both types, but prefers v2.
     * @deprecated no longer needed when LEGACY is removed
     */
    @Deprecated
    AUTO,

    /**
     * Represents the old pure audio transport system, with no support for opus, panning, gain, or any of the new features.
     * @deprecated Only used for clients that don't support the new protocol, and will be removed in a future release.
     */
    @Deprecated
    LEGACY,

    /**
     * Represents modern audio protocol system
     */
    SVG_V2;


    /**
     * Figure put the transport preference from the config string, with some leniency for formatting.
     * @param rawValue raw config value to parse
     * @return AudioPreference
     */
    public static AudioTransportPreference fromConfig(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return AUTO;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "legacy" -> LEGACY;
            case "svg-v2", "svg_v2", "v2" -> SVG_V2;
            default -> AUTO;
        };
    }
}
