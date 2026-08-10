package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable microphone frame metadata plus PCM payload.
 */
public final class MicFrame {

    private final int protocolVersion;
    private final long streamGeneration;
    private final long sequence;
    private final int sampleRate;
    private final int channels;
    private final byte pcmFormat;
    private final int sampleCount;
    private final byte[] payload;
    private final long arrivalNanos;
    private final boolean legacy;

    /**
     * Create a validated microphone frame.
     *
     * @param protocolVersion framing protocol version
     * @param streamGeneration stream generation (resets sequence state when increased)
     * @param sequence monotonic frame sequence within the generation
     * @param sampleRate PCM sample rate
     * @param channels channel count
     * @param pcmFormat PCM format code ({@link AudioFormatConstants#PCM_FORMAT_PCM16LE})
     * @param sampleCount number of PCM samples represented by the payload
     * @param payload PCM bytes (defensively copied)
     * @param arrivalNanos arrival timestamp in nanoseconds
     * @param legacy whether this frame originated from a raw legacy 1920-byte packet
     */
    public MicFrame(
            int protocolVersion,
            long streamGeneration,
            long sequence,
            int sampleRate,
            int channels,
            byte pcmFormat,
            int sampleCount,
            byte[] payload,
            long arrivalNanos,
            boolean legacy
    ) {
        Objects.requireNonNull(payload, "payload");
        this.protocolVersion = protocolVersion;
        this.streamGeneration = streamGeneration;
        this.sequence = sequence;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.pcmFormat = pcmFormat;
        this.sampleCount = sampleCount;
        this.payload = Arrays.copyOf(payload, payload.length);
        this.arrivalNanos = arrivalNanos;
        this.legacy = legacy;
        validateOrThrow();
    }

    /**
     * @return true when the frame matches the fixed browser mic contract
     */
    public boolean isValidFormat() {
        return protocolVersion == AudioFormatConstants.PROTOCOL_VERSION
                && sampleRate == AudioFormatConstants.SAMPLE_RATE
                && channels == AudioFormatConstants.CHANNELS
                && pcmFormat == AudioFormatConstants.PCM_FORMAT_PCM16LE
                && sampleCount == AudioFormatConstants.FRAME_SAMPLES
                && payload.length == AudioFormatConstants.FRAME_BYTES
                && payload.length == sampleCount * 2;
    }

    /**
     * @throws IllegalArgumentException when format invariants are violated
     */
    public void validateOrThrow() {
        if (!isValidFormat()) {
            throw new IllegalArgumentException(
                    "Invalid mic frame: version=" + protocolVersion
                            + " rate=" + sampleRate
                            + " channels=" + channels
                            + " format=" + pcmFormat
                            + " samples=" + sampleCount
                            + " bytes=" + payload.length
            );
        }
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public long getStreamGeneration() {
        return streamGeneration;
    }

    public long getSequence() {
        return sequence;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public byte getPcmFormat() {
        return pcmFormat;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    /**
     * @return a defensive copy of the PCM payload
     */
    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    /**
     * Package-private zero-copy access for the encode path.
     */
    byte[] payloadRef() {
        return payload;
    }

    public long getArrivalNanos() {
        return arrivalNanos;
    }

    public boolean isLegacy() {
        return legacy;
    }
}
