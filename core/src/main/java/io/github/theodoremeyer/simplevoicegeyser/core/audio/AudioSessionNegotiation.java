package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.util.Locale;

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

    public AudioSessionNegotiation(AudioTransportMode serverPreference, boolean allowLegacyFallback) {
        this(serverPreference, allowLegacyFallback, true);
    }

    public AudioSessionNegotiation(AudioTransportMode serverPreference, boolean allowLegacyFallback, boolean allowLegacyMic) {
        this.serverPreference = serverPreference;
        this.allowLegacyFallback = allowLegacyFallback;
        this.allowLegacyMic = allowLegacyMic;
        this.selectedMode = resolveMode();
    }

    /** Compatibility overload for clients without microphone encoder capabilities. */
    public synchronized void updateClientCapabilities(
            boolean supportsLegacy,
            boolean supportsSvgV2,
            boolean supportsOpusDecoder,
            boolean isSecureContext
    ) {
        updateClientCapabilities(supportsLegacy, supportsSvgV2, supportsOpusDecoder, isSecureContext, false);
    }

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

    public AudioTransportMode getSelectedMode() {
        return selectedMode;
    }

    public MicAudioFormat getSelectedMicFormat() {
        return selectedMicFormat;
    }

    public boolean isMicFormatAllowed() {
        return allowLegacyMic || (clientCapsReceived && selectedMicFormat == MicAudioFormat.OPUS);
    }

    public boolean isClientCapsReceived() {
        return clientCapsReceived;
    }

    public long getFallbackCount() {
        return fallbackCount;
    }

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
