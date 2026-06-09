package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.util.Locale;

/**
 * Holds per-session client capabilities and resolves active audio transport mode.
 */
public final class AudioSessionNegotiation {

    private final AudioTransportPreference serverPreference;
    private final boolean allowLegacyFallback;

    private volatile boolean clientCapsReceived = false;
    private volatile boolean clientSupportsLegacy = true;
    private volatile boolean clientSupportsSvgV2 = false;
    private volatile boolean clientSupportsOpusDecoder = false;
    private volatile boolean secureContext = false;
    private volatile AudioTransportMode selectedMode = AudioTransportMode.LEGACY;
    private volatile long fallbackCount = 0;

    /**
     * Create a new negotiation instance with the given server preference and fallback policy.
     * @param serverPreference the server preference of type
     * @param allowLegacyFallback whether to allow backwards compatibility.
     */
    public AudioSessionNegotiation(AudioTransportPreference serverPreference, boolean allowLegacyFallback) {
        this.serverPreference = serverPreference;
        this.allowLegacyFallback = allowLegacyFallback;
        this.selectedMode = resolveMode();
    }

    /**
     * Update known capabilities of the client
     * @param supportsLegacy whether client supports legacy
     * @param supportsSvgV2 whether client supports v2
     * @param supportsOpusDecoder whether client can decode opus
     * @param isSecureContext whether client is running in secure context
     */
    public synchronized void updateClientCapabilities(
            boolean supportsLegacy,
            boolean supportsSvgV2,
            boolean supportsOpusDecoder,
            boolean isSecureContext
    ) {
        clientCapsReceived = true;
        clientSupportsLegacy = supportsLegacy;
        clientSupportsSvgV2 = supportsSvgV2;
        clientSupportsOpusDecoder = supportsOpusDecoder;
        secureContext = isSecureContext;

        AudioTransportMode previous = selectedMode;
        selectedMode = resolveMode();
        if (previous != selectedMode && previous == AudioTransportMode.SVG_V2 && selectedMode == AudioTransportMode.LEGACY) {
            fallbackCount++;
        }
    }

    /**
     * Get chosen mode/protocol for client
     * @return selected mode
     */
    public AudioTransportMode getSelectedMode() {
        return selectedMode;
    }

    /**
     * Has client caps been received
     * @return clientCapsReceived
     */
    public boolean isClientCapsReceived() {
        return clientCapsReceived;
    }

    /**
     * Get the amount of audio sent through legacy
     * @return long, the count
     */
    public long getFallbackCount() {
        return fallbackCount;
    }

    /**
     * Get a summary of all client capability data.
     * @return returns summary as a string
     */
    public String summary() {
        return "pref=" + serverPreference.name().toLowerCase(Locale.ROOT)
                + " selected=" + selectedMode.name().toLowerCase(Locale.ROOT)
                + " capsReceived=" + clientCapsReceived
                + " legacy=" + clientSupportsLegacy
                + " svgV2=" + clientSupportsSvgV2
                + " opusDecoder=" + clientSupportsOpusDecoder
                + " secure=" + secureContext
                + " fallbackAllowed=" + allowLegacyFallback
                + " fallbackCount=" + fallbackCount;
    }

    private AudioTransportMode resolveMode() {
        if (serverPreference == AudioTransportPreference.LEGACY) {
            return AudioTransportMode.LEGACY;
        }

        boolean v2Capable = clientCapsReceived
                && clientSupportsSvgV2
                && clientSupportsOpusDecoder;

        if (serverPreference == AudioTransportPreference.SVG_V2) {
            if (v2Capable) {
                return AudioTransportMode.SVG_V2;
            }
            return allowLegacyFallback ? AudioTransportMode.LEGACY : AudioTransportMode.SVG_V2;
        }

        // AUTO mode prefers v2 only when capability is explicitly known.
        if (v2Capable) {
            return AudioTransportMode.SVG_V2;
        }
        return AudioTransportMode.LEGACY;
    }
}
