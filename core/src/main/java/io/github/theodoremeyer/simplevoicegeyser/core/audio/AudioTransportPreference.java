package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.util.Locale;

/**
 * Server transport preference from config.
 */
public enum AudioTransportPreference {
    AUTO,
    LEGACY,
    SVG_V2;

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
