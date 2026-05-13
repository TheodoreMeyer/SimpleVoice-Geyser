package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Compiles audio payloads to websocket-ready byte frames.
 */
public final class AudioByteCompiler {

    public static final byte CODEC_OPUS = 1;
    public static final byte CODEC_PCM16_LE = 2;

    public static final byte FLAG_STATIC_PACKET = 0x01;
    public static final byte FLAG_DISTANCE_ATTENUATED = 0x02;
    public static final byte FLAG_HAS_PAN = 0x04;

    private static final byte MAGIC_0 = 'S';
    private static final byte MAGIC_1 = 'V';
    private static final byte VERSION_2 = 2;
    private static final int HEADER_SIZE = 20;
    private static final int DEFAULT_SAMPLE_RATE = 48_000;

    public byte[] compileLegacyStereoPcm(short[] stereoPcm) {
        ByteBuffer buffer = ByteBuffer.allocate(stereoPcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : stereoPcm) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }

    public byte[] compileSvgV2Opus(
            long sequenceNumber,
            float pan,
            float gain,
            byte flags,
            byte[] opusPayload
    ) {
        byte[] payload = opusPayload == null ? new byte[0] : opusPayload;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payload.length).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(MAGIC_0);
        buffer.put(MAGIC_1);
        buffer.put(VERSION_2);
        buffer.put(flags);
        buffer.putInt((int) sequenceNumber);
        buffer.putShort(quantizeSignedQ15(pan));
        buffer.putShort(quantizeUnsignedQ15(gain));
        buffer.putShort((short) (DEFAULT_SAMPLE_RATE & 0xFFFF));
        buffer.put((byte) 1); // Opus source packets are expected mono.
        buffer.put(CODEC_OPUS);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    private short quantizeSignedQ15(float value) {
        float clamped = Math.max(-1.0f, Math.min(1.0f, value));
        return (short) Math.round(clamped * 32767.0f);
    }

    private short quantizeUnsignedQ15(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        int quantized = Math.round(clamped * 32767.0f);
        return (short) (quantized & 0xFFFF);
    }
}
