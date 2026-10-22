package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Compiles audio payloads to websocket-ready byte frames.
 */
public final class AudioByteCompiler {

    /**
     * Code for Opus
     */
    public static final byte CODEC_OPUS = 1;

    /**
     * Code for Legacy Pcm audio
     */
    public static final byte CODEC_PCM16_LE = 2;

    /**
     * Code to say it's a static packet
     */
    public static final byte FLAG_STATIC_PACKET = 0x01;

    /**
     * Code to say It has distance data
     */
    public static final byte FLAG_DISTANCE_ATTENUATED = 0x02;

    /**
     * Code to say the packet has a pan
     */
    public static final byte FLAG_HAS_PAN = 0x04;

    /**
     * Frame includes a 16-byte talker UUID after the header (before payload).
     * Used on <strong>server → web client</strong> frames only: one websocket still
     * receives voice from many in-game players; this tags which player produced the frame
     * (Adjust Volumes + per-sender Opus decode). It does not re-identify the websocket owner.
     */
    public static final byte FLAG_HAS_SENDER = 0x08;

    private static final byte MAGIC_0 = 'S';
    private static final byte MAGIC_1 = 'V';
    private static final byte VERSION_2 = 2;
    private static final int HEADER_SIZE = 20;
    private static final int SENDER_UUID_SIZE = 16;
    private static final int DEFAULT_SAMPLE_RATE = 48_000;

    /**
     * No arg-constructor for the class
     */
    public AudioByteCompiler() {}


    /**
     * Compile audio for older clients that can't use the audio.
     * @param stereoPcm audio to compile
     * @return compiled audio as a packet
     */
    public byte[] compileLegacyStereoPcm(short[] stereoPcm) {
        ByteBuffer buffer = ByteBuffer.allocate(stereoPcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : stereoPcm) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }

    /**
     * Compile audio into Bytes (no sender UUID).
     * @param sequenceNumber number of the packet
     * @param pan audio pan
     * @param gain audio gain
     * @param flags any flags
     * @param opusPayload the audio itseld
     * @return a byte array ready to be sent to the client
     */
    public byte[] compileSvgV2Opus(
            long sequenceNumber,
            float pan,
            float gain,
            byte flags,
            byte[] opusPayload
    ) {
        return compileSvgV2Opus(sequenceNumber, pan, gain, flags, opusPayload, null);
    }

    /**
     * Compile svg-v2 Opus with optional sender UUID for per-player receive volume.
     * When {@code sender} is non-null, {@link #FLAG_HAS_SENDER} is set and 16 big-endian
     * UUID bytes are written after the 20-byte header (payloadLen still covers codec data only).
     */
    public byte[] compileSvgV2Opus(
            long sequenceNumber,
            float pan,
            float gain,
            byte flags,
            byte[] opusPayload,
            UUID sender
    ) {
        byte[] payload = opusPayload == null ? new byte[0] : opusPayload;
        boolean hasSender = sender != null;
        byte outFlags = flags;
        if (hasSender) {
            outFlags |= FLAG_HAS_SENDER;
        }
        int senderBytes = hasSender ? SENDER_UUID_SIZE : 0;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + senderBytes + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(MAGIC_0);
        buffer.put(MAGIC_1);
        buffer.put(VERSION_2);
        buffer.put(outFlags);
        buffer.putInt((int) sequenceNumber);
        buffer.putShort(quantizeSignedQ15(pan));
        buffer.putShort(quantizeUnsignedQ15(gain));
        buffer.putShort((short) (DEFAULT_SAMPLE_RATE & 0xFFFF));
        buffer.put((byte) 1); // Opus source packets are expected mono.
        buffer.put(CODEC_OPUS);
        buffer.putInt(payload.length);
        if (hasSender) {
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.putLong(sender.getMostSignificantBits());
            buffer.putLong(sender.getLeastSignificantBits());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
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
