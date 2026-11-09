package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.util.Locale;

/**
 * Represents the negotiation state of an audio session between a client and server.
 * It manages the selection of audio transport modes and microphone formats based on the capabilities of both parties.
 */
public final class AudioSessionNegotiation {

    private final AudioTransportMode serverPreference;
    private final boolean allowLegacyFallback;
    private final boolean allowLegacyMic;

    private volatile boolean clientCapsReceived = false;
    private volatile boolean clientSupportsLegacy = true;
    private volatile boolean clientSupportsSvgV2 = false;
    private volatile boolean clientSupportsOpusDecoder = false;
    private volatile boolean secureContext = false;
    private volatile AudioTransportMode selectedMode = AudioTransportMode.LEGACY;
    private volatile MicAudioFormat selectedMicFormat = MicAudioFormat.PCM16LE;
    private volatile long fallbackCount = 0;

    /**
     * Initializes a new instance of the AudioSessionNegotiation class.
     * @param serverPreference the preferred audio transport mode of the server
     * @param allowLegacyFallback whether to allow fallback to legacy mode if the client does not support SVG-V2
     */
    public AudioSessionNegotiation(AudioTransportMode serverPreference, boolean allowLegacyFallback) {
        this(serverPreference, allowLegacyFallback, true);
    }

    /**
     * Initializes a new instance of the AudioSessionNegotiation class with microphone format consideration.
     * @param serverPreference the preferred audio transport mode of the server
     * @param allowLegacyFallback whether to allow fallback to legacy mode if the client does not support SVG-V2
     * @param allowLegacyMic whether to allow legacy microphone format if the client does not support Opus microphone format
     */
    public AudioSessionNegotiation(AudioTransportMode serverPreference, boolean allowLegacyFallback, boolean allowLegacyMic) {
        this.serverPreference = serverPreference;
        this.allowLegacyFallback = allowLegacyFallback;
        this.allowLegacyMic = allowLegacyMic;
        this.selectedMode = resolveMode();
    }

    /**
     * Compatibility overload for clients without microphone encoder capabilities.
     *
     * @param supportsLegacy whether the client supports legacy audio transport mode
     * @param supportsSvgV2 whether the client supports SVG-V2 audio transport mode
     * @param supportsOpusDecoder whether the client supports Opus audio decoding
     * @param isSecureContext whether the client is in a secure context (e.g., HTTPS)
     */
    public synchronized void updateClientCapabilities(
            boolean supportsLegacy,
            boolean supportsSvgV2,
            boolean supportsOpusDecoder,
            boolean isSecureContext
    ) {
        updateClientCapabilities(supportsLegacy, supportsSvgV2, supportsOpusDecoder, isSecureContext, false);
    }

    /**
     * Updates the client's capabilities based on the information received from the client.
     * @param supportsLegacy whether the client supports legacy audio transport mode
     * @param supportsSvgV2 whether the client supports SVG-V2 audio transport mode
     * @param supportsOpusDecoder whether the client supports Opus audio decoding
     * @param isSecureContext whether the client is in a secure context (e.g., HTTPS)
     * @param supportsOpusMic whether the client supports Opus microphone format
     */
    public synchronized void updateClientCapabilities(
            boolean supportsLegacy,
            boolean supportsSvgV2,
            boolean supportsOpusDecoder,
            boolean isSecureContext,
            boolean supportsOpusMic
    ) {
        clientCapsReceived = true;
        clientSupportsLegacy = supportsLegacy;
        clientSupportsSvgV2 = supportsSvgV2;
        clientSupportsOpusDecoder = supportsOpusDecoder;
        secureContext = isSecureContext;
        selectedMicFormat = supportsOpusMic ? MicAudioFormat.OPUS : MicAudioFormat.PCM16LE;

        AudioTransportMode previous = selectedMode;
        selectedMode = resolveMode();
        if (previous != selectedMode && previous == AudioTransportMode.SVG_V2 && selectedMode == AudioTransportMode.LEGACY) {
            fallbackCount++;
        }
    }

    /**
     * Returns the selected audio transport mode based on the client's capabilities and the server's configuration.
     * @return the selected audio transport mode.
     */
    public AudioTransportMode getSelectedMode() {
        return selectedMode;
    }

    /**
     * Returns the selected microphone format based on the client's capabilities and the server's configuration.
     * @return the selected microphone format.
     */
    public MicAudioFormat getSelectedMicFormat() {
        return selectedMicFormat;
    }

    /**
     * Returns whether the selected microphone format is allowed based on the server's configuration and the client's capabilities.
     * @return allowed status of the selected microphone format.
     */
    public boolean isMicFormatAllowed() {
        return allowLegacyMic || (clientCapsReceived && selectedMicFormat == MicAudioFormat.OPUS);
    }

    /**
     * Returns whether the client has sent its capabilities to the server.
     * @return true if client capabilities have been received, false otherwise.
     */
    public boolean isClientCapsReceived() {
        return clientCapsReceived;
    }

    /**
     * Returns the number of times the negotiation has fallen back to legacy mode due to client capabilities.
     * @return count of legacy fallbacks during negotiation.
     */
    public long getFallbackCount() {
        return fallbackCount;
    }

    /**
     * Provides a summary of the current negotiation state, including server preference, selected mode, client capabilities, and fallback count.
     * @return String summary of the negotiation state.
     */
    public String summary() {
        return "pref=" + serverPreference.name().toLowerCase(Locale.ROOT)
                + " selected=" + selectedMode.name().toLowerCase(Locale.ROOT)
                + " capsReceived=" + clientCapsReceived
                + " legacy=" + clientSupportsLegacy
                + " svgV2=" + clientSupportsSvgV2
                + " opusDecoder=" + clientSupportsOpusDecoder
                + " micFormat=" + selectedMicFormat.protocolName()
                + " micLegacyAllowed=" + allowLegacyMic
                + " secure=" + secureContext
                + " fallbackAllowed=" + allowLegacyFallback
                + " fallbackCount=" + fallbackCount;
    }

    private AudioTransportMode resolveMode() {
        if (serverPreference == AudioTransportMode.LEGACY) {
            return AudioTransportMode.LEGACY;
        }


        if (!clientCapsReceived) {
            return allowLegacyFallback
                    ? AudioTransportMode.LEGACY
                    : AudioTransportMode.SVG_V2;
        }

        boolean v2Capable =
                clientSupportsSvgV2 &&
                        clientSupportsOpusDecoder;

        if (v2Capable) {
            return AudioTransportMode.SVG_V2;
        }

        return allowLegacyFallback
                ? AudioTransportMode.LEGACY
                : AudioTransportMode.SVG_V2;
    }
}
