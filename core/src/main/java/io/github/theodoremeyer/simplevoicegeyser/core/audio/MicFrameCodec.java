package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Decodes browser microphone websocket payloads into {@link MicFrame}s.
 * <p>
 * Legacy compatibility: a payload of exactly {@link AudioFormatConstants#FRAME_BYTES}
 * with no framing header is accepted as PCM16LE mono 48 kHz.
 * Versioned packets use the {@code SVTX} header defined below.
 *
 * <pre>
 * Offset  Size  Field
 * 0       4     magic "SVTX"
 * 4       1     protocolVersion
 * 5       1     channels
 * 6       1     pcmFormat
 * 7       1     reserved
 * 8       4     streamGeneration (uint32 LE)
 * 12      4     sequence (uint32 LE)
 * 16      4     sampleRate (uint32 LE)
 * 20      2     sampleCount (uint16 LE)
 * 22      2     reserved
 * 24      N     PCM payload (sampleCount * 2 for PCM16LE)
 * </pre>
 */
public final class MicFrameCodec {

    /**
     * Versioned outbound mic frame magic bytes.
     */
    public static final byte[] MAGIC = {'S', 'V', 'T', 'X'};

    /**
     * Fixed header size for SVTX frames.
     */
    public static final int HEADER_SIZE = 24;

    private MicFrameCodec() {}

    /**
     * Decode a websocket binary payload.
     *
     * @param data raw websocket bytes (already sliced to the message)
     * @param arrivalNanos arrival time
     * @param legacySequenceSupplier supplies the next sequence for legacy frames
     * @return decoded frame, or empty when the payload is unrecognized / invalid
     */
    public static Optional<MicFrame> decode(
            byte[] data,
            long arrivalNanos,
            LongSupplier legacySequenceSupplier
    ) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }

        // Exact FRAME_BYTES with no room for an SVTX header is always legacy PCM.
        if (data.length == AudioFormatConstants.FRAME_BYTES) {
            long sequence = legacySequenceSupplier == null ? 0L : legacySequenceSupplier.getAsLong();
            return Optional.of(decodeLegacy(data, sequence, 0L, arrivalNanos));
        }

        if (hasSvtxMagic(data)) {
            return decodeVersioned(data, arrivalNanos);
        }

        return Optional.empty();
    }

    /**
     * Decode using an atomic legacy sequence counter (get-and-increment).
     *
     * @param data raw payload
     * @param arrivalNanos arrival time
     * @param legacySequenceCounter counter for legacy frames; may be null
     * @return decoded frame, or empty when unrecognized
     */
    public static Optional<MicFrame> decode(
            byte[] data,
            long arrivalNanos,
            AtomicLong legacySequenceCounter
    ) {
        return decode(
                data,
                arrivalNanos,
                legacySequenceCounter == null ? null : legacySequenceCounter::getAndIncrement
        );
    }

    /**
     * Build a legacy frame from raw PCM16LE mono bytes.
     *
     * @param pcm exactly {@link AudioFormatConstants#FRAME_BYTES} bytes
     * @param sequence assigned sequence
     * @param streamGeneration stream generation
     * @param arrivalNanos arrival time
     * @return validated legacy frame
     */
    public static MicFrame decodeLegacy(
            byte[] pcm,
            long sequence,
            long streamGeneration,
            long arrivalNanos
    ) {
        if (pcm == null || pcm.length != AudioFormatConstants.FRAME_BYTES) {
            throw new IllegalArgumentException(
                    "Legacy PCM must be exactly " + AudioFormatConstants.FRAME_BYTES + " bytes"
            );
        }
        return new MicFrame(
                AudioFormatConstants.PROTOCOL_VERSION,
                streamGeneration,
                sequence,
                AudioFormatConstants.SAMPLE_RATE,
                AudioFormatConstants.CHANNELS,
                AudioFormatConstants.PCM_FORMAT_PCM16LE,
                AudioFormatConstants.FRAME_SAMPLES,
                pcm,
                arrivalNanos,
                true
        );
    }

    /**
     * Encode a versioned SVTX frame (primarily for tests / future browser clients).
     *
     * @param frame validated frame
     * @return framed bytes
     */
    public static byte[] encodeVersioned(MicFrame frame) {
        frame.validateOrThrow();
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + frame.payloadRef().length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MAGIC);
        buffer.put((byte) frame.getProtocolVersion());
        buffer.put((byte) frame.getChannels());
        buffer.put(frame.getPcmFormat());
        buffer.put((byte) 0); // reserved
        buffer.putInt((int) frame.getStreamGeneration());
        buffer.putInt((int) frame.getSequence());
        buffer.putInt(frame.getSampleRate());
        buffer.putShort((short) frame.getSampleCount());
        buffer.putShort((short) 0); // reserved
        buffer.put(frame.payloadRef());
        return buffer.array();
    }

    private static Optional<MicFrame> decodeVersioned(byte[] data, long arrivalNanos) {
        if (data.length < HEADER_SIZE) {
            return Optional.empty();
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(4); // skip magic
        int protocolVersion = buffer.get() & 0xFF;
        int channels = buffer.get() & 0xFF;
        byte pcmFormat = buffer.get();
        buffer.get(); // reserved
        long streamGeneration = Integer.toUnsignedLong(buffer.getInt());
        long sequence = Integer.toUnsignedLong(buffer.getInt());
        int sampleRate = buffer.getInt();
        int sampleCount = buffer.getShort() & 0xFFFF;
        buffer.getShort(); // reserved

        int expectedPayload = sampleCount * 2;
        if (data.length != HEADER_SIZE + expectedPayload) {
            return Optional.empty();
        }

        byte[] payload = new byte[expectedPayload];
        buffer.get(payload);

        try {
            return Optional.of(new MicFrame(
                    protocolVersion,
                    streamGeneration,
                    sequence,
                    sampleRate,
                    channels,
                    pcmFormat,
                    sampleCount,
                    payload,
                    arrivalNanos,
                    false
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static boolean hasSvtxMagic(byte[] data) {
        return data.length >= MAGIC.length
                && data[0] == MAGIC[0]
                && data[1] == MAGIC[1]
                && data[2] == MAGIC[2]
                && data[3] == MAGIC[3];
    }
}
