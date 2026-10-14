package io.github.theodoremeyer.simplevoicegeyser.core.audio;

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

    MicAudioFormat(String protocolName) {
        this.protocolName = protocolName;
    }

    public String protocolName() {
        return protocolName;
    }

    public boolean acceptsPacketLength(int length) {
        return this == PCM16LE ? length == 1920 : length > 0 && length <= 1275;
    }
}
