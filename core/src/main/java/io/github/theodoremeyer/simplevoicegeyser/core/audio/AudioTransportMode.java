package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgConfig;

import java.util.Locale;

public enum AudioTransportMode {

    /**
     * Legacy transport.
     *
     * @deprecated Only used for clients that don't support SVG-V2.
     */
    @Deprecated
    LEGACY,

    /**
     * Modern SVG-V2 transport.
     */
    SVG_V2;

    public static AudioTransportMode fromConfig(SvgConfig config) {
        String rawValue = config.AUDIO_TRANSPORT_MODE.get();
        if (rawValue == null || rawValue.isBlank()) {
            return SVG_V2;
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "legacy" -> LEGACY;
            case "svg-v2", "svg_v2", "v2", "auto" -> SVG_V2;
            default -> SVG_V2;
        };
    }
}