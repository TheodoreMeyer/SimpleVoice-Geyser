package io.github.theodoremeyer.simplevoicegeyser.core.audio;

/**
 * Represents the microphone audio format used in the SimpleVoice Geyser plugin.
 * This enum defines the supported microphone audio formats, including PCM16LE and Opus.
 * Each format has a protocol name and a method to validate packet lengths.
 */
public enum MicAudioFormat {
    /**
     * Signed PCM16LE: 48 kHz, mono, 20 ms per packet.
     * @deprecated Compatibility for clients without Opus microphone encoding.
     */
    @Deprecated
    PCM16LE("pcm16le"),
    /** Raw Opus: 48 kHz, mono, 20 ms per packet. */
    OPUS("opus");

    private final String protocolName;

    /**
     * Initializes a new instance of the MicAudioFormat enum with the specified protocol name.
     * @param protocolName the protocol name for the audio format
     */
    MicAudioFormat(String protocolName) {
        this.protocolName = protocolName;
    }

    /**
     * Gets the protocol name associated with this microphone audio format.
     * @return the protocol name as a string
     */
    public String protocolName() {
        return protocolName;
    }

    /**
     * Validates whether the given packet length is acceptable for this microphone audio format.
     * @param length the length of the audio packet to validate
     * @return true if the packet length is valid for this format, false otherwise
     */
    public boolean acceptsPacketLength(int length) {
        return this == PCM16LE ? length == 1920 : length > 0 && length <= 1275;
    }
}
