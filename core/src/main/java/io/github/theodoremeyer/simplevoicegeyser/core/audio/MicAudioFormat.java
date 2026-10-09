package io.github.theodoremeyer.simplevoicegeyser.core.audio;

/** Client-to-server microphone packet formats. */
public enum MicAudioFormat {
    /** Signed 16-bit little-endian PCM at 48 kHz, mono, 20 ms per packet. */
    PCM16LE("pcm16le"),
    /** Raw Opus at 48 kHz, mono, 20 ms per packet. */
    OPUS("opus");

    private final String protocolName;

    MicAudioFormat(String protocolName) {
        this.protocolName = protocolName;
    }

    /**
     * Get the format name used by the websocket protocol.
     * @return protocol format name
     */
    public String protocolName() {
        return protocolName;
    }

    /**
     * Check whether a binary microphone packet has a valid length for this format.
     * @param length packet byte length
     * @return whether the packet length is accepted
     */
    public boolean acceptsPacketLength(int length) {
        return this == PCM16LE ? length == 1920 : length > 0 && length <= 1275;
    }
}
