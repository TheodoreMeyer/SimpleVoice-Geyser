package io.github.theodoremeyer.simplevoicegeyser.core.audio;

/**
 * Runtime transport mode selected for one websocket audio session.
 */
public enum AudioTransportMode {

    /**
     * Legacy transportation mode of pure audio, no special stuff
     * @deprecated Only used for clients that don't support the new protocol, and will be removed in a future release.
     */
    LEGACY,

    /**
     * Modern Audio system with support for opus, panning, gain, and more.
     */
    SVG_V2
}
